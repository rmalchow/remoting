package com.mcg.tools.remoting.impl.amqp;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.mcg.tools.remoting.common.AbstractRemotingService;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

public class AmqpRemotingService extends AbstractRemotingService {

	
	@Autowired
	private ConnectionFactory connectionFactory;

	@Override
	public ClientChannel createChannel(String app, String service) {
		return null;
	}

	@Override
	public ServerChannel createChannel(String app, String service, ExportedService stub) {
		return null;
	}
	
	
}
