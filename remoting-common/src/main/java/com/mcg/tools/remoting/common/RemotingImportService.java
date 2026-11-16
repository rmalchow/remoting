package com.mcg.tools.remoting.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Configuration
public class RemotingImportService {

	private static Log log = LogFactory.getLog(RemotingImportService.class);

	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public <T> ImportedServiceImpl<T> importService(Class<T> serviceInterface) {
		log.info(" = = = = = importing service: "+serviceInterface.getName());
		return new ImportedServiceImpl(serviceInterface);
	}

}
