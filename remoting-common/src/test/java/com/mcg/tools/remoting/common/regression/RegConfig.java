package com.mcg.tools.remoting.common.regression;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.mcg.tools.remoting.api.entities.RemotingInterceptorAdapter;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;
import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.RemotingExportServiceConfig;
import com.mcg.tools.remoting.common.RemotingImportService;
import com.mcg.tools.remoting.common.SimpleRemotingExecutorConfig;
import com.mcg.tools.remoting.common.codec.SimpleRemotingCodecConfig;
import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.mcg.tools.remoting.common.interfaces.ClientChannelProvider;
import com.mcg.tools.remoting.common.interfaces.ServerChannelProvider;

/** Loopback like LoopbackConfig, for RegService, recording the last raw request/response bytes. */
@Configuration
@Import({ SimpleRemotingCodecConfig.class, SimpleRemotingExecutorConfig.class, RemotingImportService.class, RemotingExportServiceConfig.class })
public class RegConfig {

	public volatile byte[] lastRequest;
	public volatile byte[] lastResponse;

	/** headers seen by the interceptor, keyed by hook name */
	public final Map<String, String> seen = new ConcurrentHashMap<>();

	@Bean
	public ServerChannelProvider serverChannelProvider() {
		return (app, service, exportedService) -> null;
	}

	@Bean
	public ClientChannelProvider clientChannelProvider(ApplicationContext ctx) {
		return (app, service) -> {
			ExportedService es = ctx.getBean(ExportedService.class, new RegServiceImpl());
			return (ClientChannel) in -> {
				lastRequest = in;
				lastResponse = es.handle(in);
				return lastResponse;
			};
		};
	}

	@Bean
	public RemotingInterceptorAdapter headerInterceptor() {
		return new RemotingInterceptorAdapter() {
			@Override
			public void beforeSend(RemotingRequest r) {
				r.getHeaders().put("X-Req", "req-value");
			}

			@Override
			public void beforeHandle(RemotingRequest r) {
				seen.put("beforeHandle", r.getHeaders().get("X-Req"));
			}

			@Override
			public void afterHandle(RemotingResponse r) {
				r.getHeaders().put("X-Resp", "resp-value");
			}

			@Override
			public void afterReceive(RemotingResponse r) {
				seen.put("afterReceive", r.getHeaders().get("X-Resp"));
			}
		};
	}

}
