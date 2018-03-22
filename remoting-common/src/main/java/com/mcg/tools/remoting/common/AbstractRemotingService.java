package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
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
	
	private Map<String,ServerChannel> exported = new HashMap<>();
	private Map<String,ImportedService<?>> imported = new HashMap<>();
	
	@Autowired(required=false)
	protected List<RemotingInterceptor> remotingInterceptors = new ArrayList<>();
	
	private Executor executor = Executors.newFixedThreadPool(10);
	
	@Override
	public <T> T importService(Class<T> serviceInterface) throws RemotingException {
		RemoteEndpoint rei = serviceInterface.getAnnotation(RemoteEndpoint.class);
		if(rei==null) throw new RemotingException("NOT_A_REMOTE_ENDPOINT", null);			
		if(StringUtils.isEmpty(rei.app())) {
			throw new RemotingException("NO_APP_NAME", null);
		}
		String appname = rei.app();
		String servicename = rei.name();
		ClientChannel cc = createChannel(appname, servicename);
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

		if(StringUtils.isEmpty(rei.app())) {
			throw new RemotingException("NO_APP_NAME", null);
		}
		
		String appname = rei.app();
		String servicename = rei.name();

		
		if(StringUtils.isEmpty(servicename)) {
			servicename = rec.value().getSimpleName().toLowerCase();
		}
		
		RemotingCodec rc = createCodec(serviceInterface);
		
		ExportedService es = createExportedService(serviceInterface, service, remotingInterceptors, rc);
		ServerChannel sc = createChannel(appname,servicename,es);  
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

	public abstract ClientChannel createChannel(String app, String service); 
	
	public abstract ServerChannel createChannel(String app, String service, ExportedService stub);

}
