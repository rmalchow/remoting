package com.mcg.tools.remoting.common.interfaces;

public interface ClientChannelProvider {

	public ClientChannel createClientChannel(String app, String service);
	
}
