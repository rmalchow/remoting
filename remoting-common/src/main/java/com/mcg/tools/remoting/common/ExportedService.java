package com.mcg.tools.remoting.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public class ExportedService {

	@Autowired
	private ApplicationContext ctx;
	
	private static Log log = LogFactory.getLog(ExportedService.class);
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private Object service;
	private RemotingCodec codec;
	private List<RemotingInterceptor> remotingInterceptors;
	
	private List<RemotingInterceptor> getInterceptors() {
		if(remotingInterceptors==null) {
			List<RemotingInterceptor> t = new ArrayList<>();
			for(Map.Entry<String,RemotingInterceptor> e : ctx.getBeansOfType(RemotingInterceptor.class).entrySet()) {
				t.add(e.getValue());
			}
			remotingInterceptors = t;
		}
		return remotingInterceptors;
	}

	
	public ExportedService(Class<?> serviceInterface, Object service, ApplicationContext ctx, RemotingCodec codec, Executor executor) {
		this.service = service;
		this.ctx = ctx;
		this.codec = codec;
	}

	public byte[] handle(byte[] in) throws Exception {
		RemotingResponse response = new RemotingResponse();
		try {
			RemotingRequest request = mapper.readValue(in, RemotingRequest.class);
			List<RemotingInterceptor> interceptors = getInterceptors(); 
			for(RemotingInterceptor ri : interceptors) {
				ri.beforeHandle(request);
			}

			log.info("Exported Service: <<< calling "+service.getClass().getSimpleName()+"."+request.getMethodName()+"()");
			Object o = codec.invoke(request, service);
			log.info("Exported Service: >>> returning result "+service.getClass().getSimpleName()+"."+request.getMethodName()+"()");
			
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
	
}
