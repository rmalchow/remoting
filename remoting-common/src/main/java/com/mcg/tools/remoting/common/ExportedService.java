package com.mcg.tools.remoting.common;

import java.util.List;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public class ExportedService {

	private static Log log = LogFactory.getLog(ExportedService.class);
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private Object service;
	private RemotingCodec codec;
	private List<RemotingInterceptor> remotingInterceptors;
	private Executor executor;
	
	public ExportedService(Class<?> serviceInterface, Object service, List<RemotingInterceptor> remotingInterceptors, RemotingCodec codec, Executor executor) {
		this.service = service;
		this.remotingInterceptors = remotingInterceptors;
		this.codec = codec;
		this.executor = executor;
	}

	public byte[] handle(byte[] in) throws Exception {
		RemotingResponse response = new RemotingResponse();
		try {
			RemotingRequest request = mapper.readValue(in, RemotingRequest.class);
			if(remotingInterceptors!=null) {
				for(RemotingInterceptor ri : remotingInterceptors) {
					ri.beforeHandle(request);
				}
			}
			Object o = codec.invoke(request, service);
			if(remotingInterceptors!=null) {
				for(RemotingInterceptor ri : remotingInterceptors) {
					ri.afterHandle(request, response);
				}
			}
			response.setSuccess(true);
			response.setReturnValue(o);
		} catch (Exception e) {
			log.warn("failed to invoke exported method: ",e);
		}
		return mapper.writeValueAsBytes(response);
	}
	
}
