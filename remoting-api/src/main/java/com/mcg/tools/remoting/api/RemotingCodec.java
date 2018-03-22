package com.mcg.tools.remoting.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.mcg.tools.remoting.api.entities.RemotingRequest;

public interface RemotingCodec {

	Object invoke(RemotingRequest request, Object target)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException;

	RemotingRequest encodeRequest(Method m, Object[] args);

}
