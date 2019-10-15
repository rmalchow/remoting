package com.mcg.tools.remoting.common.codec;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.entities.RemotingRequest;

public class SimpleRemotingCodec implements RemotingCodec {
	
	private static Log log = LogFactory.getLog(SimpleRemotingCodec.class);

	private ObjectMapper objectMapper = new ObjectMapper();
	
	public SimpleRemotingCodec () {
	}
	
	@Override
	public RemotingRequest encodeRequest(Method m, Object[] args) {
		RemotingRequest out = new RemotingRequest();
		out.setMethodName(m.getName());
		out.setParams(args);
		return out;
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

	private Object[] conform(Object[] args, Parameter[] parameters) throws JsonParseException, JsonMappingException, IOException {
		Object[] out = new Object[args.length];
		for(int i=0;i < args.length; i++) {
			byte[] x = objectMapper.writeValueAsBytes(args[i]);
			out[i] = objectMapper.readValue(x,TypeFactory.defaultInstance().constructType(parameters[i].getParameterizedType())); 
		}
		return out;
	}
	
	
	
	

}
