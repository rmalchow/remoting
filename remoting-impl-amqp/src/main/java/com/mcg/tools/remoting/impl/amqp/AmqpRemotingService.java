package com.mcg.tools.remoting.impl.amqp;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.common.AbstractRemotingService;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

@Service
public class AmqpRemotingService extends AbstractRemotingService {

	private static Log log = LogFactory.getLog(AmqpRemotingService.class);
	
	@Autowired
	private ConnectionFactory connectionFactory;
	
	private List<AmqpClientChannel> clientChannels = new ArrayList<>();
	private List<AmqpServerChannel> serverChannels = new ArrayList<>();

	@Override
	public ClientChannel createClientChannel(String app, String service) {
		AmqpClientChannel c = new AmqpClientChannel(app, service);
		clientChannels.add(c);
		if(connectionFactory!=null) {
			c.start(connectionFactory);
		}
		return c;
	}

	@Override
	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService) {
		AmqpServerChannel s = new AmqpServerChannel(app, service, exportedService);
		serverChannels.add(s);
		if(connectionFactory!=null) {
			s.start(connectionFactory);
		}
		return s;
	}
	
	
	public void init() {
		log.info(" ||| server channels: "+serverChannels.size());
		log.info(" ||| client channels: "+clientChannels.size());
		for(AmqpClientChannel cc : clientChannels) {
			try {
				cc.start(connectionFactory);
			} catch (Exception e) {
				log.warn("error initializing client: ",e);
			}
		}
		for(AmqpServerChannel sc : serverChannels) {
			try {
				sc.start(connectionFactory);
			} catch (Exception e) {
				log.warn("error initializing server: ",e);
			}
		}
		super.init();
	}
	
	
}
