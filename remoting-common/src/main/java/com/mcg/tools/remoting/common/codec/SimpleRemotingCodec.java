package com.mcg.tools.remoting.common.codec;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.ser.std.ToStringSerializer;
import tools.jackson.databind.util.StdDateFormat;

import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public class SimpleRemotingCodec implements RemotingCodec {
	
	private static Log log = LogFactory.getLog(SimpleRemotingCodec.class);

	private static final Log payloadLog = LogFactory.getLog("com.mcg.tools.remoting.payload");

	/*
	 * The wire format: Spring Boot 3's Jackson 2 defaults as of 0.3.x (design D1 of platform-jdk25-boot4),
	 * reproduced with Jackson 3 (change remoting-jackson3). Every line below exists because a Jackson 3 default
	 * differs from what 0.3.x writes or accepts; WireFormatTest pins the result byte for byte.
	 */
	private final ObjectMapper objectMapper = JsonMapper.builder()
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
			.disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
			.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
			.disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
			.disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
			// java.util.Date as "2026-11-16T12:00:00.000+00:00" (Jackson 3 would write "...000Z")
			.defaultDateFormat(new StdDateFormat().withColonInTimeZone(true).withZeroOffsetAsZ(false))
			// Locale as "de_DE" (Jackson 3 would write the language tag "de-DE")
			.addModule(new SimpleModule("remoting-wire-format")
					.addSerializer(Locale.class, ToStringSerializer.instance)
					// BigDecimal as a string ("1234.50"): arguments and return values are first read untyped, and a JSON
					// number would pass through a double there, losing scale and precision. A string survives that step
					// and is coerced into the declared BigDecimal. JSON numbers (0.3.x senders) are still accepted.
					.addSerializer(BigDecimal.class, BigDecimalAsPlainString.INSTANCE))
			.build();

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

	protected Object[] conform(Object[] args, Parameter[] parameters) throws IOException {
		Object[] out = new Object[args.length];
		for(int i=0;i < args.length; i++) {
			out[i] = conform(args[i], parameters[i].getParameterizedType()); 
		}
		return out;
	}
	
	protected Object conform(Object arg, Type type) throws IOException {
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

	/** Writes the exact value with its scale and without exponent, e.g. "1234.50" or "1000" (not "1E+3"). */
	static final class BigDecimalAsPlainString extends StdSerializer<BigDecimal> {
		static final BigDecimalAsPlainString INSTANCE = new BigDecimalAsPlainString();

		private BigDecimalAsPlainString() {
			super(BigDecimal.class);
		}

		@Override
		public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctx) {
			gen.writeString(value.toPlainString());
		}
	}

}
