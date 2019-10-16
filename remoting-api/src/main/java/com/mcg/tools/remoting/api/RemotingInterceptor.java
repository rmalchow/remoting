package com.mcg.tools.remoting.api;

import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

public interface RemotingInterceptor {

	void afterReceive(RemotingResponse response);

	void afterHandle(RemotingResponse response);

	void beforeHandle(RemotingRequest request);

	void beforeSend(RemotingRequest request);
	
	

}
