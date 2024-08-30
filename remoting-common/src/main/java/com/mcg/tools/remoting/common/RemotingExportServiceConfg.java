package com.mcg.tools.remoting.common;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class RemotingExportServiceConfg {

	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public ExportedService exportService(Object service) {
		ExportedService ex = new ExportedService(service);
		return ex;
	}


}
