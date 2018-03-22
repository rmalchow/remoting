package com.mcg.tools.remoting.api;

import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public interface RemotingInterceptor {

	void afterReceive(RemotingRequest request, RemotingResponse response);

	void afterHandle(RemotingRequest request, RemotingResponse response);

	void beforeHandle(RemotingRequest request);

	void beforeSend(RemotingRequest request);
	
	

}
