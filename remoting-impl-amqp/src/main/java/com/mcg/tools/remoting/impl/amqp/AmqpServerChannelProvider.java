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

import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.interfaces.ServerChannel;
import com.mcg.tools.remoting.common.interfaces.ServerChannelProvider;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@Component
public class AmqpServerChannelProvider implements ServerChannelProvider {
	
	private static Log log = LogFactory.getLog(AmqpServerChannelProvider.class);

	private List<AmqpServerChannel> serverChannels = new ArrayList<>();

	@Autowired
	private org.springframework.amqp.rabbit.connection.CachingConnectionFactory connectionFactory;
	private ConnectionFactory cf;
	private Connection connection;
	
	private ScheduledExecutorService ste = Executors.newScheduledThreadPool(1);
	
	public AmqpServerChannelProvider() {
	}
	
	@Override
	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService) {
		AmqpServerChannel s = new AmqpServerChannel(app, service, exportedService);
		serverChannels.add(s);
		return s;
	}
	
	public void listen() {
		for(AmqpServerChannel sc : serverChannels) {
			sc.start(connection);
		}
	}
	
	public void reconnect() throws IOException, TimeoutException {
		try {
			connection.close();
		} catch (Exception e) {
		}
		connection = cf.newConnection();
		log.info("server connection openend!");
		listen();
	}
	
	@PostConstruct
	public void init() throws IOException, TimeoutException {
		
		CachingConnectionFactory ccf = (CachingConnectionFactory)connectionFactory;
		cf = ccf.getRabbitConnectionFactory();
		cf.setAutomaticRecoveryEnabled(false);

		ste.scheduleWithFixedDelay(
			new Runnable() {
				
				@Override
				public void run() {
					try {
						if(connection!=null && connection.isOpen()) {
							listen();
							return;
						}
						log.info("SERVER: connection not open");
						reconnect();
					} catch (Exception e) {
					}
				}
			}, 5, 10L, TimeUnit.SECONDS);
		
	}
	
	
	
	
	
	

}
