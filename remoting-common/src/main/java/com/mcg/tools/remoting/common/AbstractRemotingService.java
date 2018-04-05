package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;

import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.RemotingService;
import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.codec.SimpleRemotingCodec;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

public abstract class AbstractRemotingService implements RemotingService {

	private static Log log = LogFactory.getLog(AbstractRemotingService.class);
	
	private Map<String,ServerChannel> exported = new HashMap<>();
	private Map<String,ImportedService<?>> imported = new HashMap<>();
	
	@Autowired(required=false)
	protected List<RemotingInterceptor> remotingInterceptors = new ArrayList<>();
	
	@Autowired
	private ApplicationContext ctx;
	
	private Executor executor = Executors.newFixedThreadPool(10);
	
	@Override
	public <T> T importService(Class<T> serviceInterface) throws RemotingException {
		RemoteEndpoint rei = serviceInterface.getAnnotation(RemoteEndpoint.class);
		if(rei==null) throw new RemotingException("NOT_A_REMOTE_ENDPOINT", null);			

		String appname = rei.app();
		if(StringUtils.isEmpty(appname)) {
			throw new RemotingException("EMPTY_APP_NAME", null);
		}

		String servicename = rei.name();
		if(StringUtils.isEmpty(servicename)) {
			servicename = serviceInterface.getSimpleName().toLowerCase();
		}
		
		log.info(" <<< importing: "+serviceInterface+" as "+appname+":"+servicename);
		
		ClientChannel cc = createClientChannel(appname, servicename);
		RemotingCodec rc = createCodec(serviceInterface);
		ImportedService<T> s = createImportedService(serviceInterface, cc, remotingInterceptors,rc);
		imported.put(appname+":"+servicename,s);
		return s.getProxy();
	}
	
	@Override
	public void exportService(Object service) throws RemotingException {

		RemotingEndpoint rec = service.getClass().getAnnotation(RemotingEndpoint.class);
		if(rec==null) throw new RemotingException("NOT_AN_EXPORTABLE_ENDPOINT", null);			

		Class<?> serviceInterface = rec.value(); 
		
		RemoteEndpoint rei = serviceInterface.getAnnotation(RemoteEndpoint.class);
		if(rei==null) throw new RemotingException("NOT_A_REMOTE_ENDPOINT", null);			

		String appname = rei.app();
		if(StringUtils.isEmpty(appname)) {
			throw new RemotingException("EMPTY_APP_NAME", null);
		}

		String servicename = rei.name();
		if(StringUtils.isEmpty(servicename)) {
			servicename = serviceInterface.getSimpleName().toLowerCase();
		}

		log.info(" >>> exporting: "+serviceInterface+" as "+appname+":"+servicename);
		
		RemotingCodec rc = createCodec(serviceInterface);
		ExportedService es = createExportedService(serviceInterface, service, remotingInterceptors, rc);
		ServerChannel sc = createServerChannel(appname,servicename,es);  
		exported.put(appname+":"+servicename, sc);
	}
	
	public RemotingCodec createCodec(Class<?> serviceInterface) {
		return new SimpleRemotingCodec(serviceInterface);
	}

	public <T> ImportedService<T> createImportedService(Class<T> serviceInterface, ClientChannel cc, List<RemotingInterceptor> remotingInterceptors, RemotingCodec remotingCodec) {
		return new ImportedService<T>(serviceInterface, cc, remotingInterceptors, remotingCodec,executor);
	}

	public ExportedService createExportedService(Class<?> serviceInterface, Object service, List<RemotingInterceptor> remotingInterceptors, RemotingCodec codec) {
		return new ExportedService(serviceInterface, service, remotingInterceptors, codec,executor);
	}

	public abstract ClientChannel createClientChannel(String app, String service); 
	
	public abstract ServerChannel createServerChannel(String app, String service, ExportedService stub);

	
	@PostConstruct
	public void init() {
		log.info(" ================================================================= ");
		log.info(" == ");
		for(Object o : ctx.getBeansWithAnnotation(RemotingEndpoint.class).values()) {
			log.info(" == REMOTING SERVICE: EXPORT SERVICE: "+o.getClass());
			try {
				exportService(o);
			} catch (Exception e) {
				log.warn("failed to export: "+o.getClass(),e);
			}
		}
		log.info(" == ");
		log.info(" ================================================================= ");
	}
	
}
