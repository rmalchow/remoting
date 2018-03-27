package com.mcg.tools.remoting.impl.amqp;

import javax.annotation.PostConstruct;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.common.AbstractRemotingService;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

@Service
public class AmqpRemotingService extends AbstractRemotingService {

	@Autowired
	private ConnectionFactory connectionFactory;

	@Override
	public ClientChannel createClientChannel(String app, String service) {
		return new AmqpClientChannel(connectionFactory, app, service);
	}

	@Override
	public ServerChannel createServerChannel(String app, String service, ExportedService exportedService) {
		return new AmqpServerChannel(connectionFactory, app, service, exportedService);
	}
	
	@PostConstruct
	public void init() {
		super.init();
	}
	
	
}
