package com.mcg.tools.remoting.ex.server.api.services;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.RemotingImportService;

@Configuration
public class PrintServiceConfig {

	@Bean
	@ConditionalOnMissingBean
	public static PrintService printService(RemotingImportService remotingService) throws RemotingException {
		return remotingService.importService(PrintService.class).getProxy();
	}
	

}
