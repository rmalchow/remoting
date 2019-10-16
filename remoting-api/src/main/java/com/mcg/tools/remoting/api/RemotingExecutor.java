package com.mcg.tools.remoting.api;

import java.util.concurrent.FutureTask;

import com.mcg.tools.remoting.api.entities.RemotingResponse;

public interface RemotingExecutor {

	void execute(FutureTask<RemotingResponse> t);

}
