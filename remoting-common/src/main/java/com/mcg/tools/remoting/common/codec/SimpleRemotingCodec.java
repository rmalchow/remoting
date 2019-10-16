package com.mcg.tools.remoting.common.codec;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public class SimpleRemotingCodec implements RemotingCodec {
	
	private static Log log = LogFactory.getLog(SimpleRemotingCodec.class);

	@Autowired(required =  false)
	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired(required =  false)
	private List<RemotingInterceptor> remotingInterceptors = new ArrayList<RemotingInterceptor>();
	
	public SimpleRemotingCodec () {
	}
	
	private ObjectMapper getObjectMapper() {
		if(objectMapper == null) {
			objectMapper = new ObjectMapper();
		}
		return objectMapper;
	}
	
	@Override
	public Object invoke(Class<?> serviceInterface, RemotingRequest request, Object target) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
		String name = request.getMethodName();
		Object[] args = request.getParams();
		if(args==null) {
			args = new Object[] {};
		}
		int paramCount = args.length;
		Method method = null;
		Object[] params = null;
		for(Method m : serviceInterface.getMethods()) {
			if(m.getName().compareTo(name)!=0) continue;
			if(m.getParameterCount()!=paramCount) continue;
			try {
				params = conform(args,m.getParameters());
				method = m;
				break;
			} catch (JsonMappingException e) {
				// cannot conform this, might just be same method name
			} catch (Exception e) {
				log.warn("error trying to conform parameters.",e);
			}
		}
		if(method!=null) {
			return method.invoke(target, params);
		}
		
		throw new NoSuchMethodException();
	}

	protected Object[] conform(Object[] args, Parameter[] parameters) throws JsonParseException, JsonMappingException, IOException {
		Object[] out = new Object[args.length];
		for(int i=0;i < args.length; i++) {
			out[i] = conform(args[i], parameters[i].getParameterizedType()); 
		}
		return out;
	}
	
	protected Object conform(Object arg, Type type) throws JsonParseException, JsonMappingException, JsonProcessingException, IOException {
		ObjectMapper om = getObjectMapper();
		return om.readValue(om.writeValueAsBytes(arg), TypeFactory.defaultInstance().constructType(type));
	}

	@Override
	public byte[] encodeRequest(Method m, Object[] args) {
		try {
			RemotingRequest out = new RemotingRequest();
			out.setMethodName(m.getName());
			out.setParams(args);
			for(RemotingInterceptor ri : remotingInterceptors) {
				ri.beforeSend(out);
			}
			return getObjectMapper().writeValueAsBytes(out);
		} catch (Exception e) {
			throw new RuntimeException("unable to map request",e);
		}
	}

	@Override
	public RemotingResponse decodeResponse(byte[] response, Type returnType) {
		try {
			RemotingResponse out = getObjectMapper().readValue(response, RemotingResponse.class);

			for(RemotingInterceptor ri : remotingInterceptors) {
				ri.afterReceive(out);
			}
			
			if(out.getReturnValue()!=null) {
				out.setReturnValue(conform(out.getReturnValue(), returnType));
			}
			return out;
		} catch (Exception e) {
			throw new RuntimeException("unable to map request",e);
		}
	}

	@Override
	public RemotingRequest decodeRequest(byte[] body) {
		try {
			return getObjectMapper().readValue(body, RemotingRequest.class);
		} catch (Exception e) {
			throw new RuntimeException("error reading request", e);
		}
	}

	@Override
	public byte[] createResponse(Object returnValue, boolean success, Exception e) {
		try {
			RemotingResponse rr = new RemotingResponse();
			rr.setSuccess(success);
			rr.setReturnValue(returnValue);
			// ignore exceptions for now rr.set
			for(RemotingInterceptor ri : remotingInterceptors) {
				ri.afterHandle(rr);
			}
			return getObjectMapper().writeValueAsBytes(rr);
		} catch (Exception ex) {
			throw new RuntimeException("error encoding response", ex);
		}
	}
	
	
	
	

}
