package com.mcg.tools.remoting.ex.server.api.services;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mcg.tools.remoting.api.ImportedService;
import com.mcg.tools.remoting.api.annotations.RemotingException;

@Configuration
public class RandomServiceConfig {

	private static Log log = LogFactory.getLog(RandomServiceConfig.class);

	@Autowired
	private ApplicationContext ctx;
	
	//@Bean
	//@ConditionalOnMissingBean
	public RandomService randomService() throws RemotingException {
		ImportedService<RandomService> s = ctx.getBean(ImportedService.class, RandomService.class); 
		log.info("imported service is: "+s);
		
		RandomService ps = s.getProxy();
		//log.info("proxy is: "+ps);
		
		
		return ps;
	}
	

}
