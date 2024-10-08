package com.mcg.tools.remoting.impl.amqp;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.jms.JmsProperties.DeliveryMode;

import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ReturnListener;
import com.rabbitmq.utility.BlockingCell;

public class AmqpClientChannel implements ClientChannel {

	private static Log log = LogFactory.getLog(AmqpServerChannel.class);
	
	private Connection connection;
	
	private Channel clientChannel;

	private String app;
	private String service;
	
	private String exchangeName;
	private String responseQueueName;
	
	private String routingKey = null;

	private ConcurrentHashMap<String,BlockingCell<byte[]>> requestMap = new ConcurrentHashMap<>();
	
	public AmqpClientChannel(String app, String service) {
		this.app = app;
		this.service = service;
	}

	@Override
	public byte[] invoke(byte[] in) throws Exception {

		String correlationId = UUID.randomUUID().toString();
		
		try {
			
			BasicProperties props = new BasicProperties.Builder().replyTo(routingKey).correlationId(correlationId).deliveryMode(DeliveryMode.PERSISTENT.getValue()).build();
			BlockingCell<byte[]> bc = new BlockingCell<>();
			requestMap.put(correlationId, bc);
			clientChannel.basicPublish(exchangeName, "request", true, props, in);
			byte[] buff = bc.get();
			
			requestMap.remove(correlationId);
			
			return buff;
		} catch (AlreadyClosedException ace) {
			try {
				connection.close();
			} catch (Exception e) {
			}
			throw ace;
		} catch (Exception e) {
			log.error("error in client");
			throw e;
		}
	}
	
	private void listen(Connection connection) {
		this.connection = connection;
		try {
			
			if(StringUtils.isEmpty(app)) throw new RemotingException("EMPTY_APP_NAME", null);
			if(StringUtils.isEmpty(service)) throw new RemotingException("EMPTY_SERVICE_NAME", null);
			
			this.routingKey = UUID.randomUUID().toString();
			
			this.exchangeName = app + ":" + service;
			this.responseQueueName = app+":"+service+":response-"+routingKey;
			
			this.clientChannel = connection.createChannel();
			this.clientChannel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true, false, new HashMap<>());
			this.clientChannel.queueDeclare(responseQueueName, false, true, true, new HashMap<>());
			this.clientChannel.queueBind(responseQueueName, exchangeName, routingKey);
			
			this.clientChannel.addReturnListener(new ReturnListener() {
				@Override
				public void handleReturn(
						int replyCode,
						String replyText,
			            String exchange,
			            String routingKey,
			            AMQP.BasicProperties properties,
			            byte[] body) throws IOException {

					BlockingCell<byte[]> bc = requestMap.get(properties.getCorrelationId());
					if(bc!=null) {
						log.warn("no consumer available! (exchangeName: "+AmqpClientChannel.this.exchangeName+", responseQueue: "+AmqpClientChannel.this.responseQueueName+")");
						bc.set(new byte[] {});
					} else {
						log.warn(" ---- unknown correlation id: "+properties.getCorrelationId());
					}
				}
			});			
			
			this.clientChannel.basicConsume(responseQueueName, false, new DefaultConsumer(this.clientChannel) {
				@Override
				public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
					
					long deliveryTag = envelope.getDeliveryTag();
					getChannel().basicAck(deliveryTag, false);

					BlockingCell<byte[]> bc = requestMap.remove(properties.getCorrelationId());
					if(bc!=null) {
			    		bc.set(body);
					} else {
						log.warn(" ---- unknown correlation id: "+properties.getCorrelationId());
					}
				}
			});
			
			
		} catch (Exception e) {
			log.warn("error creating consumer: ",e);
		}
	}
	
	public void start(Connection connection) {
		if(connection == this.connection) return;
		this.connection = connection;
		if(connection == null) return;
		log.info("CLIENT ("+app+":"+service+") : new connection: recreating channels");
		listen(connection);
	}
	

}
