package com.mcg.tools.remoting.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public interface RemotingCodec {

	
	// ## CLIENT

	byte[] encodeRequest(Method m, Object[] args);

	RemotingResponse decodeResponse(byte[] response, Type returnType);

	
	// ## SERVER
	
	RemotingRequest decodeRequest(byte[] body);

	Object invoke(Class<?> serviceInterface, RemotingRequest request, Object target) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException;

	byte[] createResponse(Object returnValue, boolean success, Exception e);
	
}
