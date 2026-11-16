package com.mcg.tools.remoting.common.loop;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingTimeout;

@RemoteEndpoint(app = "test", name = "lost")
public interface LostService {

	@RemotingTimeout(1)
	String call();

}
