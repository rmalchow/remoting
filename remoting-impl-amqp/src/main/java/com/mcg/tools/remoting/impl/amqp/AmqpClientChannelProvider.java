package com.mcg.tools.remoting.impl.amqp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.mcg.tools.remoting.common.interfaces.ClientChannelProvider;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@Component
public class AmqpClientChannelProvider implements ClientChannelProvider {
	
	private static Log log = LogFactory.getLog(AmqpClientChannelProvider.class);

	private List<AmqpClientChannel> clientChannels = new ArrayList<>();

	private ConnectionFactory cf;
	
	@Autowired
	private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;
	
	private Connection connection;
	
	private ScheduledExecutorService ste = Executors.newScheduledThreadPool(1);
	
	@Override
	public ClientChannel createClientChannel(String app, String service) {
		AmqpClientChannel s = new AmqpClientChannel(app, service);
		clientChannels.add(s);
		try {
			if(connection!=null) {
				listen();
			} else {
				reconnect();
			}
		} catch (Exception e) {
		}
		return s;
	}
	
	public void listen() {
		for(AmqpClientChannel cc : clientChannels) {
			cc.start(connection);
		}
	}

	public void reconnect() throws IOException, TimeoutException {
		try {
			connection.close();
		} catch (Exception e) {
		}
		connection = cf.newConnection();
		log.info("client connection openend!");
		listen();
	}
	

	@PostConstruct
	public void init() throws IOException, TimeoutException {
		
		CachingConnectionFactory ccf = (CachingConnectionFactory)connectionFactory;
		cf = ccf.getRabbitConnectionFactory();
		cf.setAutomaticRecoveryEnabled(false);
		reconnect();

		ste.scheduleWithFixedDelay(
			new Runnable() {
				
				@Override
				public void run() {
					try {
						if(connection!=null && connection.isOpen()) {
							listen();
							return;
						}
						log.info("CLIENT: connection not open");
						reconnect();
					} catch (Exception e) {
					}
				}
			}, 5, 10L, TimeUnit.SECONDS);
		
	}
	


}
