package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;

@Service
public class RemotingExportService {

	private static Log log = LogFactory.getLog(RemotingExportService.class);
	
	private List<ExportedService> exportedServices = new ArrayList<>();
	
	@Autowired
	public ApplicationContext ctx;

	@Autowired
	private RemotingCodec remotingCodec;
	
	@Autowired 
	private ServerChannelProvider serverChannelProvider; 

	@Bean
	@Scope(scopeName = DefaultListableBeanFactory.SCOPE_PROTOTYPE)
	public ExportedService exportService(Object service) {
		ExportedService ex = new ExportedService(service);
		return ex;
	}
	
	
	@PostConstruct
	public void init() {
		for(String s : ctx.getBeanNamesForAnnotation(RemotingEndpoint.class)) {
			Object o = ctx.getBean(s);
			log.info(" == REMOTING SERVICE: EXPORT SERVICE: "+o.getClass());
			try {
				ExportedService es = ctx.getBean(ExportedService.class, o);
				exportedServices.add(es);
			} catch (Exception e) {
				log.warn("failed to export: "+o.getClass(),e);
			}
		}
	}

	
}
