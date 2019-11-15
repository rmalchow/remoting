package com.mcg.tools.remoting.common;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.common.codec.SimpleRemotingCodec;
import com.mcg.tools.remoting.common.interfaces.ServerChannelProvider;
import com.mcg.tools.remoting.common.util.CglibHelper;

public class ExportedService {

	private static Log log = LogFactory.getLog(ExportedService.class);
	
	private Object service;
	private Class<?> serviceInterface;

	@Autowired
	private RemotingCodec codec;

	@Autowired
	private ServerChannelProvider serverChannelProvider;
	
	public ExportedService(Object service) {
		this.service = service;
	}

	public byte[] handle(byte[] in) throws Exception {
		if(serviceInterface == null) {
			return null;
		}
		try {
			
			RemotingRequest request = codec.decodeRequest(in);
			
			log.debug("Exported Service: <<< calling "+service.getClass().getSimpleName()+"."+request.getMethodName()+"()");
			Object o = codec.invoke(serviceInterface,request, service);
			log.debug("Exported Service: >>> returning result "+service.getClass().getSimpleName()+"."+request.getMethodName()+"()");
			
			return codec.createResponse(o, true, null);
			
		} catch (Exception e) {
			log.info("Exported Service: >>> returning error "+service.getClass().getSimpleName());
			log.warn("failed to invoke exported method: ",e);
			return codec.createResponse(null, false, e);
		}
	}
	
	@PostConstruct
	public void init() throws RemotingException, ClassNotFoundException {
		RemotingEndpoint rec = service.getClass().getAnnotation(RemotingEndpoint.class);
		if(rec==null) {
			// could not find annotation, maybe its a proxy
			CglibHelper h = new CglibHelper(service);
			Object proxiedService = h.getTargetObject();
			rec = proxiedService.getClass().getAnnotation(RemotingEndpoint.class);
			
			if(rec==null) {
				throw new RemotingException("NOT_AN_EXPORTABLE_ENDPOINT: "+service.getClass(), null);
			}
		}

		Class<?> c = Class.forName(rec.value().getCanonicalName());
		
		RemoteEndpoint rei = c.getAnnotation(RemoteEndpoint.class);
		if(rei==null) throw new RemotingException("NOT_A_REMOTE_ENDPOINT: "+c.getClass()+" is missing the necessary annotation", null);			
		
		String appname = rei.app();
		if(StringUtils.isEmpty(appname)) {
			throw new RemotingException("EMPTY_APP_NAME", null);
		}

		String servicename = rei.name();
		if(StringUtils.isEmpty(servicename)) {
			throw new RemotingException("EMPTY_SERVICE_NAME", null);
		}

		for(Class<?> ci : service.getClass().getInterfaces()) {
			if(ci.getName() != c.getName()) continue;
			if(!(ci.isAssignableFrom(service.getClass()))) continue;
			this.serviceInterface = ci;
			break;
		}

		if(this.serviceInterface == null) {
			throw new RemotingException("INTERFACE_NOT_FOUND: "+c.getClass(), null);
		}
		
		serverChannelProvider.createServerChannel(appname,servicename,this);  
		
	}
	
	
}
