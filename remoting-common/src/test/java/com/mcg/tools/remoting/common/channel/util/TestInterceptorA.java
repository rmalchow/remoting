package com.mcg.tools.remoting.common.channel.util;

import com.mcg.tools.remoting.api.entities.RemotingInterceptorAdapter;
import com.mcg.tools.remoting.api.entities.RemotingRequest;

public class TestInterceptorA extends RemotingInterceptorAdapter {

	@Override
	public void beforeSend(RemotingRequest request) {
		request.getHeaders().put("A-Value", "AAAA");
	}
	
	
}
