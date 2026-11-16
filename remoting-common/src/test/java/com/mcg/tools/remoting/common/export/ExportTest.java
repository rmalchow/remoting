package com.mcg.tools.remoting.common.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.mcg.tools.remoting.api.annotations.RemoteEndpoint;
import com.mcg.tools.remoting.api.annotations.RemotingEndpoint;
import com.mcg.tools.remoting.common.RemotingExportServiceConfig;
import com.mcg.tools.remoting.common.codec.SimpleRemotingCodecConfig;
import com.mcg.tools.remoting.common.interfaces.ServerChannelProvider;

class ExportTest {

	@RemoteEndpoint(app = "export-app", name = "export-service")
	public interface Nested {
		String hello();
	}

	@RemotingEndpoint(Nested.class)
	public static class Impl implements Nested {
		public String hello() {
			return "hi";
		}
	}

	static final List<String> recorded = new ArrayList<>();

	@Configuration
	@Import({ SimpleRemotingCodecConfig.class, RemotingExportServiceConfig.class })
	static class BaseConfig {
		@Bean
		ServerChannelProvider serverChannelProvider() {
			return (app, service, exportedService) -> {
				recorded.add(app + "/" + service);
				return null;
			};
		}
	}

	@Configuration
	static class PlainConfig extends BaseConfig {
		@Bean
		Impl impl() {
			return new Impl();
		}
	}

	@Configuration
	static class ProxyConfig extends BaseConfig {
		@Bean
		Impl impl() {
			ProxyFactory pf = new ProxyFactory(new Impl());
			pf.setProxyTargetClass(true);
			return (Impl) pf.getProxy();
		}
	}

	private static void check(Class<?> config) {
		recorded.clear();
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(config)) {
			assertEquals(List.of("export-app/export-service"), recorded);
		}
	}

	@Test
	void nestedInterfaceIsExported() {
		check(PlainConfig.class);
	}

	@Test
	void cglibProxyIsExported() {
		check(ProxyConfig.class);
	}
}
