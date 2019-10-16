package com.mcg.tools.remoting.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mcg.tools.remoting.api.RemotingExecutor;

@Configuration
public class SimpleRemotingExecutorConfig {

	@Bean
	@ConditionalOnMissingBean()
	public RemotingExecutor remotingExecutor() {
		return new SimpleRemotingExecutor();
	}

	
}
