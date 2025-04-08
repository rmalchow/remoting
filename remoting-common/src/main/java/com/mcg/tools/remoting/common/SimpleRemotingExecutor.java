package com.mcg.tools.remoting.common;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.springframework.beans.factory.annotation.Value;

import com.mcg.tools.remoting.api.RemotingExecutor;
import com.mcg.tools.remoting.api.entities.RemotingResponse;

import jakarta.annotation.PostConstruct;

public class SimpleRemotingExecutor implements RemotingExecutor {

	@Value(value = "${mcg.remoting.poolsize:8}")
	private int poolsize = 8;
	
	private Executor e;
	
	@Override
	public void execute(FutureTask<RemotingResponse> t) {
		e.execute(t);
	}

	@PostConstruct
	public void init() {
		e = Executors.newFixedThreadPool(poolsize);
	}
	
	
}
