package com.mcg.tools.remoting.api.entities;

import com.mcg.tools.remoting.api.RemotingInterceptor;

public class RemotingInterceptorAdapter implements RemotingInterceptor {

	@Override
	public void beforeSend(RemotingRequest request) {
	}
	
	@Override
	public void beforeHandle(RemotingRequest request) {
	}
	
	@Override
	public void afterHandle(RemotingResponse response) {
	}
	
	@Override
	public void afterReceive(RemotingResponse response) {
	}
	
}
