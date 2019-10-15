package com.mcg.tools.remoting.impl.amqp;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mcg.tools.remoting.common.ClientChannelProvider;
import com.mcg.tools.remoting.common.io.ClientChannel;

@Component
public class AmqpClientChannelProvider implements ClientChannelProvider {
	
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
			cc.start(connection);
		}
	}
	
	
	@PostConstruct
	public void init() {
		CachingConnectionFactory ccf = (CachingConnectionFactory)connectionFactory;
		connection = ccf.createConnection();
		ccf.getRabbitConnectionFactory().setAutomaticRecoveryEnabled(true);
		listen();
	}



}
