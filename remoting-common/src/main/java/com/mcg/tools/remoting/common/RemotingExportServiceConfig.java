package com.mcg.tools.remoting.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.common.util.CglibHelper;

@Configuration
public class RemotingExportServiceConfig {

	private static Log log = LogFactory.getLog(RemotingExportServiceConfig.class);
	

	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public ExportedService exportService(Object service) {
		ExportedService ex = new ExportedService(service);
		return ex;
	}
	
	
	@Bean
	public RemotingExportService init(ApplicationContext ctx) {
		RemotingExportService out = new RemotingExportService();
		for(String s : ctx.getBeanNamesForAnnotation(RemotingEndpoint.class)) {
			Object o = ctx.getBean(s);
			log.info(" == REMOTING SERVICE: EXPORT SERVICE: "+o.getClass());
			try {
				CglibHelper h = new CglibHelper(o);
				o = h.getTargetObject();
			} catch (Exception e) {
				log.warn("error getting proxied instance: "+e.getMessage());
			}
			try {
				ExportedService es = ctx.getBean(ExportedService.class, o);
				out.add(es);
			} catch (Exception e) {
				log.warn("failed to export: "+o.getClass(),e);
			}
		}
		return out;
	}

	
}
