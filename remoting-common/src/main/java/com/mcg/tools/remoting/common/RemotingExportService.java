package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.common.util.CglibHelper;

@Service
@Order()
public class RemotingExportService {

	private static Log log = LogFactory.getLog(RemotingExportService.class);
	
	private List<ExportedService> exportedServices = new ArrayList<>();
	
	@Autowired
	public ApplicationContext ctx;
	
	@PostConstruct
	public void init() {
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
				exportedServices.add(es);
			} catch (Exception e) {
				log.warn("failed to export: "+o.getClass(),e);
			}
		}
	}

	
}
