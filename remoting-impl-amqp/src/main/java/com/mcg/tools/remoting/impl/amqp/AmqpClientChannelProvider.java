package com.mcg.tools.remoting.impl.amqp;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.mcg.tools.remoting.common.interfaces.ClientChannelProvider;

@Component
public class AmqpClientChannelProvider implements ClientChannelProvider, ConnectionListener {
	
	private static Log log = LogFactory.getLog(AmqpClientChannelProvider.class);

	private List<AmqpClientChannel> clientChannels = new ArrayList<>();

	@Autowired
	private ConnectionFactory connectionFactory;
	
	private Connection connection;

	@Override
	public ClientChannel createClientChannel(String app, String service) {
		AmqpClientChannel s = new AmqpClientChannel(app, service);
		clientChannels.add(s);
		listen();
		return s;
	}
	
	public void listen() {
		for(AmqpClientChannel cc : clientChannels) {
			log.info("connection openend: opening client channel");
			cc.start(connection);
		}
	}
	
	public void onCreate(Connection connection) {
		AmqpClientChannelProvider.this.connection = connection;
		log.info("connection openend - restartign imported services ... ");
		listen();
	}
	
	public void onClose(Connection connection) {
		try {
			log.info("connection closed - reconnecting ... ");
			Thread.sleep(1000);
		} catch (Exception e) {
		}
		connectionFactory.createConnection();
	};

	
	@PostConstruct
	public void init() {
		CachingConnectionFactory ccf = (CachingConnectionFactory)connectionFactory;
		com.rabbitmq.client.ConnectionFactory cf = ccf.getRabbitConnectionFactory();
		cf.setAutomaticRecoveryEnabled(true);
		cf.setTopologyRecoveryEnabled(true);
		ccf.addConnectionListener(this);
		listen();
	}


}
