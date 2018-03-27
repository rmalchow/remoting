package com.mcg.tools.remoting.common.channel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mcg.tools.remoting.api.RemotingInterceptor;
import com.mcg.tools.remoting.common.AbstractRemotingService;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.io.ClientChannel;
import com.mcg.tools.remoting.common.io.ServerChannel;

public class LocalChannelRemotingService extends AbstractRemotingService {

	private Map<String,LocalServerChannel> scMap = new HashMap<>();
	
	private Map<String,ClientChannel> ccMap = new HashMap<>();
	
	
	@Override
	public ClientChannel createClientChannel(String app, String service) {
		ClientChannel cc = new LocalClientChannel(app+":"+service);
		ccMap.put(app+":"+service, cc);
		return cc;
	}

	@Override
	public ServerChannel createServerChannel(String app, String service, ExportedService stub) {
		LocalServerChannel sc = new LocalServerChannel(stub);
		scMap.put(app+":"+service, sc);
		return sc;
	}
	
	public List<RemotingInterceptor> getInterceptors() {
		return remotingInterceptors;
	}
	
	
	private class LocalServerChannel implements ServerChannel {
		
		private ExportedService stub;
		
		public LocalServerChannel(ExportedService stub) {
			this.stub = stub;
		}
		
		public byte[] execute(byte[] in) throws Exception {
			byte[] out = stub.handle(in);
			return out;
		}
		
		
	}
	
	private class LocalClientChannel implements ClientChannel {

		private String name;
		
		public LocalClientChannel(String name) {
			this.name = name;
		}
		
		
		@Override
		public byte[] invoke(byte[] in) throws Exception {
			return scMap.get(name).execute(in);
		}
		
	}
	

}
