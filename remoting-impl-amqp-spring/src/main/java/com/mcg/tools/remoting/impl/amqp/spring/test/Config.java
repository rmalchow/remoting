package com.mcg.tools.remoting.impl.amqp.spring.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mcg.tools.remoting.api.RemotingService;
import com.mcg.tools.remoting.api.annotations.RemotingException;

@Configuration
public class Config {

	
	
	@Bean
	public AInterface ai(RemotingService remotingService) throws RemotingException {
		remotingService.exportService(new AClass());
		remotingService.exportService(new AClass());
		remotingService.exportService(new AClass());
		remotingService.exportService(new AClass());
		return remotingService.importService(AInterface.class);
	}

	
	
	
	
}
