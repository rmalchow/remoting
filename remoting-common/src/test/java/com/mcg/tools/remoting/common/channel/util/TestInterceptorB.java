package com.mcg.tools.remoting.common.channel.util;

import com.mcg.tools.remoting.api.entities.RemotingInterceptorAdapter;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public class TestInterceptorB extends RemotingInterceptorAdapter {

	@Override
	public void beforeHandle(RemotingRequest request) {
		HeaderHolder.clear();
		HeaderHolder.headers(request.getHeaders());
	}

	@Override
	public void afterHandle(RemotingRequest request, RemotingResponse response) {
		HeaderHolder.clear();
	}
	
	
}
