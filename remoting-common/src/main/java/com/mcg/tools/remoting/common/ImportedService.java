package com.mcg.tools.remoting.common;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingExecutor;
import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingTimeout;
import com.mcg.tools.remoting.api.entities.RemotingResponse;
import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.mcg.tools.remoting.common.interfaces.ClientChannelProvider;

public class ImportedService implements InvocationHandler {

	private static Log log = LogFactory.getLog(ImportedService.class);
	
	private Class serviceInterface;
	private Object proxy;
	
	@Autowired
	private ClientChannelProvider clientChannelProvider;
	private ClientChannel clientChannel;
	
	@Autowired
	private RemotingCodec remotingCodec;

	@Autowired
	private RemotingExecutor executor;
	
	@Value(value = "${mcg.remoting.timeout:10}")
	public long timeout;
	
	public ImportedService(Class serviceInterface) {
		this.serviceInterface = serviceInterface;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		
		long timeout = this.timeout;
		long start = System.currentTimeMillis(); 
		
		if(method.getAnnotation(RemotingTimeout.class)!=null) {
			timeout = method.getAnnotation(RemotingTimeout.class).value();
		}

		try {
			
			byte[] req = remotingCodec.encodeRequest(method, args);
			
			InvokeCallable ic = new InvokeCallable(req, method.getGenericReturnType());
	
			FutureTask<RemotingResponse> t = new FutureTask<RemotingResponse>(ic);
	
			
			log.debug("Importing Service: >>> calling "+serviceInterface.getSimpleName()+"."+method.getName()+"()");
			executor.execute(t);
			log.debug("Importing Service: <<< response received "+serviceInterface.getSimpleName()+"."+method.getName()+"()");

			
			
			RemotingResponse response = t.get(timeout, TimeUnit.SECONDS);
			log.debug("Importing Service: <<< response received "+serviceInterface.getSimpleName()+"."+method.getName()+"()");
			
			if(!response.isSuccess()) throw new RuntimeException("REMOTE_CALL_FAILED");
			
			return response.getReturnValue();
			
		} catch (Throwable t) {
			log.warn("RPC failed ("+t.getClass()+") (timeout: "+timeout+", elapsed: "+(System.currentTimeMillis()-start)+")");
			throw t;
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public Object getProxy() {
		if(proxy==null) {
			log.info(" === CREATING PROXY FOR: "+serviceInterface);
			
			proxy = Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class[] {serviceInterface}, this );
		}
		return proxy;
	}
	
	
	public ClientChannel getClientChannel() {
		if(clientChannel == null) {
			RemoteEndpoint re = (RemoteEndpoint) serviceInterface.getAnnotation(RemoteEndpoint.class);
			if(re == null) {
				throw new RuntimeException("not a remote endpoint: "+serviceInterface+" has no @RemoteEndpoint annotation");
			}
			this.clientChannel = clientChannelProvider.createClientChannel(re.app(),re.name());
		}
		return clientChannel;
	}
	
	
	private class InvokeCallable implements Callable<RemotingResponse> {
		
		private byte[] in;
		private Type returnType;
		
		public InvokeCallable(byte[] in, Type returnType) {
			this.in = in;
			this.returnType = returnType;
		}

		@Override
		public RemotingResponse call() throws Exception {
			byte[] out = getClientChannel().invoke(in);
			return remotingCodec.decodeResponse(out, returnType);
		}
	};
	

	
	
}
