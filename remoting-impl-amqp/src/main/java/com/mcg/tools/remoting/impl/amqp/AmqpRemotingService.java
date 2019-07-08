package com.mcg.tools.remoting.impl.amqp;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.mcg.tools.remoting.common.AbstractRemotingService;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

@EnableScheduling
public class AmqpRemotingService extends AbstractRemotingService {

	private static Log log = LogFactory.getLog(AmqpRemotingService.class);
	
	
	@Autowired
	private ConnectionFactory connectionFactory;
	
	private Connection connection;
	private boolean status = false; 

	private List<AmqpClientChannel> clientChannels = new ArrayList<>();
	private List<AmqpServerChannel> serverChannels = new ArrayList<>();
	
	public AmqpRemotingService() {
		log.info("INIT AMQP SERVICE");
	}
	

	@Override
	public ClientChannel createClientChannel(String app, String service) {
		AmqpClientChannel c = new AmqpClientChannel(app, service);
		clientChannels.add(c);
		return c;
	}

	@Override
	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService) {
		AmqpServerChannel s = new AmqpServerChannel(app, service, exportedService);
		serverChannels.add(s);
		return s;
	}
	
	@Scheduled(fixedDelay = 10000)
	public void checkConnection() {
		boolean open = false;
		try {
			open = this.connection.isOpen();
		} catch (Exception e) {
		}
		
		if(open) {
			onCreate(this.connection);
			return;
		}

		status = false;
		
		log.info("CLOSED connection");
		onCreate(connectionFactory.createConnection());
	}
	
	public void onCreate(Connection connection) {
		if(connection==null) return;
		if(status) return;
		log.info("connection created, initializing client channels ... ");
		this.connection = connection;

		for(AmqpClientChannel cc : clientChannels) {
			cc.start(connection);
		}
		log.info("connection created, initializing client channels ... DONE!");
		log.info("connection created, reinitializing server channels ... ");
		for(AmqpServerChannel sc : serverChannels) {
			sc.start(connection);
		}
		
		if(this.connection.isOpen()) {
			this.status = true; 
		}
		log.info("connection created, reinitializing server channels ...DONE! ");
	}
	
	public void init() {
		
		log.info(" ||| remoting service: "+this.hashCode());
		log.info(" ||| server channels: "+serverChannels.size());
		log.info(" ||| client channels: "+clientChannels.size());
		log.info(" ||| connection factory: "+connectionFactory.getClass());
		super.init();
		
		CachingConnectionFactory ccf = (CachingConnectionFactory)connectionFactory;
		ccf.getRabbitConnectionFactory().setAutomaticRecoveryEnabled(true);

	}
	
	
}
