package com.mcg.tools.remoting.impl.amqp;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.beans.factory.annotation.Autowired;

import com.mcg.tools.remoting.common.AbstractRemotingService;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

public class AmqpRemotingService extends AbstractRemotingService  implements ConnectionListener {

	private static Log log = LogFactory.getLog(AmqpRemotingService.class);
	
	@Autowired
	private ConnectionFactory connectionFactory;
	
	private Connection connection;

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
	
	@Override
	public void onClose(Connection connection) {
		// TODO Auto-generated method stub
		if(this.connection == connection) {
			this.connection = null;
		}
		while(this.connection == null) {
			try {
				Thread.sleep(1000);
				connectionFactory.createConnection();
			} catch (Exception e) {
				log.warn("error reconnecting: ",e);
			}
		}
	}
	
	@Override
	public void onCreate(Connection connection) {
		log.info("connection created, reinitializing client channels ... ");
		this.connection = connection;
		for(AmqpClientChannel cc : clientChannels) {
			try {
				cc.start(connection);
			} catch (Exception e) {
				log.warn("error initializing client: ",e);
			}
		}
		log.info("connection created, reinitializing server channels ... ");
		for(AmqpServerChannel sc : serverChannels) {
			try {
				sc.start(connection);
			} catch (Exception e) {
				log.warn("error initializing server: ",e);
			}
		}
	}
	
	public void init() {
		
		log.info(" ||| remoting service: "+this.hashCode());
		log.info(" ||| server channels: "+serverChannels.size());
		log.info(" ||| client channels: "+clientChannels.size());
		super.init();
		
		connectionFactory.addConnectionListener(this);
		connectionFactory.createConnection();
		
	}
	
	
}
