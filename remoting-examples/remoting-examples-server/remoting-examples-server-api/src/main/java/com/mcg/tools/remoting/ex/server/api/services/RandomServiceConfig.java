package com.mcg.tools.remoting.ex.server.api.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.RemotingImportService;

@Configuration
public class RandomServiceConfig {

	@Bean
	@ConditionalOnMissingBean
	public static RandomService randomService(RemotingImportService remotingService) throws RemotingException {
		return remotingService.importService(RandomService.class).getProxy();
	}
	

}
