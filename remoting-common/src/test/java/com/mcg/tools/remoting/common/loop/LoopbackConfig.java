package com.mcg.tools.remoting.common.loop;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.mcg.tools.remoting.common.ExportedService;
import com.mcg.tools.remoting.common.RemotingExportServiceConfig;
import com.mcg.tools.remoting.common.RemotingImportService;
import com.mcg.tools.remoting.common.SimpleRemotingExecutorConfig;
import com.mcg.tools.remoting.common.codec.AClass;
import com.mcg.tools.remoting.common.codec.SimpleRemotingCodecConfig;
import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.mcg.tools.remoting.common.interfaces.ClientChannelProvider;
import com.mcg.tools.remoting.common.interfaces.ServerChannelProvider;

/**
 * Real codec, executor and import service; no broker. Service "a" loops back in memory into an
 * ExportedService for AClass, service "lost" never answers and counts interrupts.
 */
@Configuration
@Import({ SimpleRemotingCodecConfig.class, SimpleRemotingExecutorConfig.class, RemotingImportService.class, RemotingExportServiceConfig.class })
public class LoopbackConfig {

	public final AtomicInteger lostInterrupts = new AtomicInteger();

	@Bean
	public ServerChannelProvider serverChannelProvider() {
		return (app, service, exportedService) -> null;
	}

	@Bean
	public ClientChannelProvider clientChannelProvider(ApplicationContext ctx) {
		CountDownLatch never = new CountDownLatch(1);
		return (app, service) -> switch (service) {
			case "a" -> {
				ExportedService es = ctx.getBean(ExportedService.class, new AClass());
				yield (ClientChannel) es::handle;
			}
			case "lost" -> in -> {
				try {
					never.await();
					return null;
				} catch (InterruptedException e) {
					lostInterrupts.incrementAndGet();
					throw e;
				}
			};
			default -> throw new IllegalArgumentException(service);
		};
	}

}
