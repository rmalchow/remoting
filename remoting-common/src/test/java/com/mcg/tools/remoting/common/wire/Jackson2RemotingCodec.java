package com.mcg.tools.remoting.common.wire;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

/**
 * The 0.3.x codec (Jackson 2), kept verbatim as the reference for the wire format: it records golden files
 * ({@link WireGoldenRecorder}) and plays the old peer in {@link WireFormatTest}. Do not "fix" it.
 */
public class Jackson2RemotingCodec implements RemotingCodec {
	
	private static Log log = LogFactory.getLog(Jackson2RemotingCodec.class);

	private static final Log payloadLog = LogFactory.getLog("com.mcg.tools.remoting.payload");

	// the wire format (design D1): Spring Boot 3's auto-configured Jackson defaults, independent of the host's mapper
	private final ObjectMapper objectMapper = JsonMapper.builder()
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
			.addModule(new Jdk8Module())
			.addModule(new JavaTimeModule())
			.addModule(new ParameterNamesModule(JsonCreator.Mode.DEFAULT))
			.build();

	@Autowired
	private ApplicationContext ctx;
	
	private List<RemotingInterceptor> remotingInterceptors = new ArrayList<RemotingInterceptor>();
	
	public Jackson2RemotingCodec () {
	}

	private List<RemotingInterceptor> getInterceptors() {
		if(remotingInterceptors == null) {
		}
		remotingInterceptors = new ArrayList<RemotingInterceptor>(ctx.getBeansOfType(RemotingInterceptor.class).values());
		return remotingInterceptors;
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
		return objectMapper.readValue(objectMapper.writeValueAsBytes(arg), objectMapper.getTypeFactory().constructType(type));
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
			byte[] body = objectMapper.writeValueAsBytes(out);
			if(payloadLog.isTraceEnabled()) {
				payloadLog.trace("request: "+new String(body, StandardCharsets.UTF_8));
			}
			return body;
		} catch (Exception e) {
			throw new RuntimeException("unable to map request",e);
		}
	}

	@Override
	public RemotingResponse decodeResponse(byte[] response, Type returnType) {
		try {
			if(payloadLog.isTraceEnabled()) {
				payloadLog.trace("response: "+new String(response, StandardCharsets.UTF_8));
			}
			RemotingResponse out = objectMapper.readValue(response, RemotingResponse.class);

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
			if(payloadLog.isTraceEnabled()) {
				payloadLog.trace("request: "+new String(body, StandardCharsets.UTF_8));
			}
			
			RemotingRequest rr = objectMapper.readValue(body, RemotingRequest.class); 
			
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
			byte[] body = objectMapper.writeValueAsBytes(rr);
			if(payloadLog.isTraceEnabled()) {
				payloadLog.trace("response: "+new String(body, StandardCharsets.UTF_8));
			}
			return body;
		} catch (Exception ex) {
			throw new RuntimeException("error encoding response", ex);
		}
	}
	
	
	
	

}
