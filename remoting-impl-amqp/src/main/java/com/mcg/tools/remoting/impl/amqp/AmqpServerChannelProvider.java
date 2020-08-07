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
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.TopologyRecoveryException;

@Component
public class AmqpServerChannelProvider implements ServerChannelProvider, ConnectionListener, ExceptionHandler {
	
	private static Log log = LogFactory.getLog(AmqpServerChannelProvider.class);

	private List<AmqpServerChannel> serverChannels = new ArrayList<>();

	@Autowired
	private ConnectionFactory connectionFactory;
	
	private Connection connection;

	private ExceptionHandler parentHandler;
	
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
		com.rabbitmq.client.ConnectionFactory cf = ccf.getRabbitConnectionFactory();
		parentHandler = cf.getExceptionHandler();
		cf.setExceptionHandler(this);
		ccf.addConnectionListener(this);
		ccf.createConnection();
	}

	@Override
	public void handleUnexpectedConnectionDriverException(com.rabbitmq.client.Connection conn, Throwable exception) {
		try {
			this.connection.close();
		} catch (Exception e) {
		}
		try {
			init();
		} catch (Exception e) {
		}
	}

	@Override
	public void handleReturnListenerException(Channel channel, Throwable exception) {
		// TODO Auto-generated method stub
		log.info("connection exception: ",exception);
		parentHandler.handleReturnListenerException(channel, exception);
	}

	@Override
	public void handleConfirmListenerException(Channel channel, Throwable exception) {
		// TODO Auto-generated method stub
		log.info("connection exception: ",exception);
		parentHandler.handleConfirmListenerException(channel, exception);
	}

	@Override
	public void handleBlockedListenerException(com.rabbitmq.client.Connection connection, Throwable exception) {
		// TODO Auto-generated method stub
		log.info("connection exception: ",exception);
		parentHandler.handleBlockedListenerException(connection, exception);
	}

	@Override
	public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag,
			String methodName) {
		// TODO Auto-generated method stub
		log.info("connection exception: ",exception);
		parentHandler.handleConsumerException(channel, exception, consumer, consumerTag, methodName);
	}

	@Override
	public void handleConnectionRecoveryException(com.rabbitmq.client.Connection conn, Throwable exception) {
		log.info("connection exception: ",exception);
		try {
			this.connection.close();
		} catch (Exception e) {
		}
		try {
			init();
		} catch (Exception e) {
		}
	}

	@Override
	public void handleChannelRecoveryException(Channel ch, Throwable exception) {
		log.info("connection exception: ",exception);
		try {
			this.connection.close();
		} catch (Exception e) {
		}
		try {
			init();
		} catch (Exception e) {
		}
	}

	@Override
	public void handleTopologyRecoveryException(com.rabbitmq.client.Connection conn, Channel ch, TopologyRecoveryException exception) {
		log.info("connection exception: ",exception);
		try {
			this.connection.close();
		} catch (Exception e) {
		}
		try {
			init();
		} catch (Exception e) {
		}
	}
}
