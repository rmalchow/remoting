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
public class PrintServiceConfig {

	private static Log log = LogFactory.getLog(PrintServiceConfig.class);

	@Autowired
	private ApplicationContext ctx;
	
	@Bean
	@ConditionalOnMissingBean
	public PrintService printService() throws RemotingException {
		
		ImportedService<PrintService> s = ctx.getBean(ImportedService.class, PrintService.class); 
		log.info("imported service is: "+s);
		
		PrintService ps = s.getProxy();
		//log.info("proxy is: "+ps);
		
		
		return ps;
	}
	

}
