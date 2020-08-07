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

import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.interfaces.ServerChannel;
import com.mcg.tools.remoting.common.interfaces.ServerChannelProvider;

@Component
public class AmqpServerChannelProvider implements ServerChannelProvider, ConnectionListener {
	
	private static Log log = LogFactory.getLog(AmqpServerChannelProvider.class);

	private List<AmqpServerChannel> serverChannels = new ArrayList<>();

	@Autowired
	private ConnectionFactory connectionFactory;
	
	private Connection connection;

	@Override
	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService) {
		AmqpServerChannel s = new AmqpServerChannel(app, service, exportedService);
		serverChannels.add(s);
		listen();
		return s;
	}
	
	public void listen() {
		for(AmqpServerChannel sc : serverChannels) {
			log.info("connection openend: "+sc.getExportedService().getClass());
			sc.start(connection);
		}
	}
	
	public void onCreate(Connection connection) {
		AmqpServerChannelProvider.this.connection = connection;
		log.info("connection openend - restartign exported services ... ");
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
		ccf.addConnectionListener(this);
		ccf.createConnection();
	}



}
