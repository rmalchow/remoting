package com.mcg.tools.remoting.impl.amqp;

import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.rabbitmq.client.Connection;

import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.interfaces.ServerChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class AmqpServerChannel implements ServerChannel{

	private static Log log = LogFactory.getLog(AmqpServerChannel.class);
	
	private Channel serverChannel;

	private String app;
	private String service;
	private ExportedService exportedService;

	private String exchangeName;
	private String requestQueueName;
	
	private Connection connection;
	
	
	public AmqpServerChannel(String app, String service, ExportedService exportedService) {
		this.app = app;
		this.service = service;
		this.setExportedService(exportedService);
	}

	private void listen(Connection connection) {
		
		try {

			if(StringUtils.isEmpty(app)) throw new RemotingException("EMPTY_APP_NAME", null);
			if(StringUtils.isEmpty(service)) throw new RemotingException("EMPTY_SERVICE_NAME", null);

			this.exchangeName = app + ":" + service;
			this.requestQueueName = app+":"+service+":request";
			
			this.serverChannel = connection.createChannel();
			this.serverChannel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true, false, new HashMap<>());
			this.serverChannel.queueDeclare(requestQueueName, false, false, true, new HashMap<>());
			this.serverChannel.queueBind(requestQueueName, exchangeName, "request");
			this.serverChannel.basicConsume(requestQueueName, false, new DefaultConsumer(this.serverChannel) {
				
				@Override
				public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
					try {

						byte[] responseBody = getExportedService().handle(body);
						String correlationId = properties.getCorrelationId();
						String routingKey = properties.getReplyTo();
						BasicProperties props = new BasicProperties.Builder().correlationId(correlationId).build();
						long deliveryTag = envelope.getDeliveryTag();
						getChannel().basicAck(deliveryTag, false);
						log.debug("responding: ("+app+":"+service+":"+routingKey+")");
						getChannel().basicPublish(app+":"+service, routingKey, props, responseBody);
					} catch (Exception e) {
						throw new IOException(e);
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
		log.info("SERVER ("+app+":"+service+"): new connection: recreating channels");
		listen(connection);
	}

	public ExportedService getExportedService() {
		return exportedService;
	}

	public void setExportedService(ExportedService exportedService) {
		this.exportedService = exportedService;
	}

	
	
}
