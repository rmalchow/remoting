package com.mcg.tools.remoting.common.codec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.tools.remoting.api.entities.RemotingRequest;

public class ProxyCodec<T> extends SimpleRemotingCodec implements InvocationHandler {

	private Object target;
	
	public ProxyCodec(T target, Class<T> serviceInterface) {
		super(serviceInterface);
		this.target = target;
	}
	
	public T getProxy() {
		return (T)Proxy.newProxyInstance(target.getClass().getClassLoader(), new Class[] { serviceInterface } , this);
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		RemotingRequest rr = super.encodeRequest(method, args);
		ObjectMapper om = new ObjectMapper();
		rr = om.readValue(om.writeValueAsBytes(rr), RemotingRequest.class);
		return super.invoke(rr, target);
	}
	
	
}
