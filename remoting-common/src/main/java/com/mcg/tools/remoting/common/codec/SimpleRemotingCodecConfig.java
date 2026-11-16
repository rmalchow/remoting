package com.mcg.tools.remoting.common.codec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mcg.tools.remoting.api.RemotingCodec;

@Configuration
public class SimpleRemotingCodecConfig {
	
	@Bean
	public RemotingCodec remotingCodec() {
		return new SimpleRemotingCodec();
	}

}
