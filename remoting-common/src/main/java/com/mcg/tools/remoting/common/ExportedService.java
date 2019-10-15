package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;
import com.mcg.tools.remoting.common.util.CglibHelper;

public class ExportedService {

	private static Log log = LogFactory.getLog(ExportedService.class);
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private Object service;
	private Class<?> serviceInterface;

	@Autowired
	private RemotingCodec codec;

	@Autowired(required =  false)
	private List<RemotingInterceptor> remotingInterceptors = new ArrayList<RemotingInterceptor>();
	
	@Autowired
	private ServerChannelProvider serverChannelProvider;
	
	public ExportedService(Object service) {
		this.service = service;
	}

	public byte[] handle(byte[] in) throws Exception {
		if(serviceInterface == null) {
			return null;
		}
		RemotingResponse response = new RemotingResponse();
		try {
			RemotingRequest request = mapper.readValue(in, RemotingRequest.class);
			List<RemotingInterceptor> interceptors = remotingInterceptors; 
			for(RemotingInterceptor ri : interceptors) {
				ri.beforeHandle(request);
			}
			log.debug("Exported Service: <<< calling "+service.getClass().getSimpleName()+"."+request.getMethodName()+"()");
			Object o = codec.invoke(serviceInterface,request, service);
			log.debug("Exported Service: >>> returning result "+service.getClass().getSimpleName()+"."+request.getMethodName()+"()");
			
			for(RemotingInterceptor ri : interceptors) {
				ri.afterHandle(request, response);
			}
			response.setSuccess(true);
			response.setReturnValue(o);
		} catch (Exception e) {
			log.info("Exported Service: >>> returning error "+service.getClass().getSimpleName());
			log.warn("failed to invoke exported method: ",e);
		}
		return mapper.writeValueAsBytes(response);
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

		this.serviceInterface = c;
		
		serverChannelProvider.createServerChannel(appname,servicename,this);  
		
	}
	
	
}
