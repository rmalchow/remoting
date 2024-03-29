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
import org.springframework.context.ApplicationContext;

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

	@Autowired
	private ApplicationContext ctx;
	
	private List<RemotingInterceptor> remotingInterceptors = new ArrayList<RemotingInterceptor>();
	
	public SimpleRemotingCodec () {
	}

	private List<RemotingInterceptor> getInterceptors() {
		if(remotingInterceptors == null) {
		}
		remotingInterceptors = new ArrayList<RemotingInterceptor>(ctx.getBeansOfType(RemotingInterceptor.class).values());
		return remotingInterceptors;
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
		
		List<Method> methods = new ArrayList<>();
		for(Method m : serviceInterface.getMethods()) {
			if(m.getName().compareTo(name)!=0) {
				if(log.isDebugEnabled()) {
					log.debug("incorrect method name: "+m.getName()+" != "+name);
				}
				continue;
			}
			if(m.getParameterCount()!=paramCount) {
				if(log.isDebugEnabled()) {
					log.debug("incorrect parameter count: "+m.getParameterCount()+" != "+paramCount);
				}
				continue;
			}
			methods.add(m);
		}
		
		if(methods.size()<1) {
			throw new NoSuchMethodException("cannot find method ("+request.getMethodName()+") with ("+paramCount+") parameters in interface "+serviceInterface.getName());
		}
		
		Method method = null;
		Object[] params = null;

		for(Method m : methods) {
			try {
				params = conform(args,m.getParameters());
				method = m;
				break;
			} catch (Exception e) {
				if(log.isDebugEnabled()) {
					log.debug("cannot conform: "+m.getParameters()+" != "+args);
				}
			}
		}
		
		if(method == null) {
			throw new IllegalArgumentException("cannot conform params of ("+request.getMethodName()+") in interface "+serviceInterface.getName());
		} else {
			log.debug("target: "+target.getClass()+" / "+method.getName());
			log.debug("interface: "+serviceInterface);
			try {
				return method.invoke(target, params);
			} catch (Exception e) {
				log.error("error invoking method: { interface: "+serviceInterface.getName()+", object: "+target.getClass()+", method: "+method.getName()+"}",e);
				log.error("interface: "+serviceInterface.getClassLoader());
				log.error("   target: "+target.getClass().getClassLoader());
				throw e;
			}
		}
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
			List<RemotingInterceptor> interceptors = getInterceptors();
			log.debug("invoking interceptors: "+interceptors.size());
			for(RemotingInterceptor ri : interceptors) {
				log.debug("invoking interceptor: "+ri.getClass().getName());
				ri.beforeSend(out);
			}
			if(log.isDebugEnabled()) {
				log.debug(getObjectMapper().writeValueAsString(out));
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

			List<RemotingInterceptor> interceptors = getInterceptors();
			log.debug("invoking interceptors: "+interceptors.size());
			for(RemotingInterceptor ri : interceptors) {
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
			if(log.isDebugEnabled()) {
				log.debug(new String(body));
			}
			
			RemotingRequest rr = getObjectMapper().readValue(body, RemotingRequest.class); 
			
			List<RemotingInterceptor> interceptors = getInterceptors();
			log.debug("invoking interceptors: "+interceptors.size());
			for(RemotingInterceptor ri : interceptors) {
				ri.beforeHandle(rr);
			}
			return rr; 
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
			List<RemotingInterceptor> interceptors = getInterceptors();
			log.debug("invoking interceptors: "+interceptors.size());
			for(RemotingInterceptor ri : interceptors) {
				ri.afterHandle(rr);
			}
			if(e!=null) {
				rr.exception(e);
			}
			return getObjectMapper().writeValueAsBytes(rr);
		} catch (Exception ex) {
			throw new RuntimeException("error encoding response", ex);
		}
	}
	
	
	
	

}
