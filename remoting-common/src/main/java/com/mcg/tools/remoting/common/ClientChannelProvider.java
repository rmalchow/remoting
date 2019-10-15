package com.mcg.tools.remoting.common;

import com.mcg.tools.remoting.common.io.ClientChannel;

public interface ClientChannelProvider {

	public ClientChannel createClientChannel(String app, String service);
	
}
