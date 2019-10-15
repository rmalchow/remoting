package com.mcg.tools.remoting.common;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingTimeout;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;
import com.mcg.tools.remoting.common.io.ClientChannel;

public class ImportedService implements InvocationHandler {

	private static Log log = LogFactory.getLog(ImportedService.class);
	
	private ObjectMapper mapper = new ObjectMapper();
	
	private Class serviceInterface;
	private Object proxy;

	@Autowired(required =  false)
	private List<RemotingInterceptor> remotingInterceptors = new ArrayList<RemotingInterceptor>();
	
	@Autowired
	private ClientChannelProvider clientChannelProvider;
	private ClientChannel clientChannel;
	
	@Autowired
	private RemotingCodec remotingCodec;

	private Executor executor = Executors.newScheduledThreadPool(8);
	
	public ImportedService(Class serviceInterface) {
		this.serviceInterface = serviceInterface;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		
		RemotingRequest request = remotingCodec.encodeRequest(method, args);
		
		for(RemotingInterceptor ri : remotingInterceptors) {
			ri.beforeSend(request);
		}

		try {
			
			byte[] in = mapper.writeValueAsBytes(request);
			
			InvokeCallable ic = new InvokeCallable(in);
	
			FutureTask<RemotingResponse> t = new FutureTask<RemotingResponse>(ic);
	
			log.debug("Importing Service: >>> calling "+serviceInterface.getSimpleName()+"."+request.getMethodName()+"()");
			executor.execute(t);
			
			long timeout = 10;
			
			if(method.getAnnotation(RemotingTimeout.class)!=null) {
				timeout = method.getAnnotation(RemotingTimeout.class).value();
			}
			
			RemotingResponse response = t.get(timeout, TimeUnit.SECONDS);
			log.debug("Importing Service: <<< response received "+serviceInterface.getSimpleName()+"."+request.getMethodName()+"()");
			
			for(RemotingInterceptor ri : remotingInterceptors) {
				ri.afterReceive(request, response);
			}
	
			if(!response.isSuccess()) throw new RuntimeException("REMOTE_CALL_FAILED");
			if(response.getReturnValue()==null) return null;
			return mapper.readValue(mapper.writeValueAsBytes(response.getReturnValue()), TypeFactory.defaultInstance().constructType(method.getGenericReturnType()));
			
		} catch (Throwable t) {
			log.warn("RPC failed:",t);
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
		
		public InvokeCallable(byte[] in) {
			this.in = in;
		}

		@Override
		public RemotingResponse call() throws Exception {
			byte[] out = getClientChannel().invoke(in);
			return mapper.readValue(out, RemotingResponse.class);		
		}
	};
	

	
	
}
