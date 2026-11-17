package com.mcg.tools.remoting.common.wire;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.common.wire.WireFixtures.Level;
import com.mcg.tools.remoting.common.wire.WireFixtures.More;
import com.mcg.tools.remoting.common.wire.WireFixtures.Point;
import com.mcg.tools.remoting.common.wire.WireFixtures.WireService;

/**
 * Writes the 0.4.0 golden files with the Jackson 2 reference codec. Only runs on request:
 * {@code mvn -pl remoting-common test -Dtest=WireGoldenRecorder -Dwire.record=true}
 * The files of 0.3.0 (request-all, request-nulls, ...) were recorded from 0.2.2 and are not rewritten here.
 */
@EnabledIfSystemProperty(named = "wire.record", matches = "true")
class WireGoldenRecorder {

	static final Method MORE;
	static {
		try {
			MORE = WireService.class.getMethod("more", More.class, BigDecimal.class, long.class, Optional.class, Level.class, Point.class, Map.class);
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@Test
	void record() throws IOException {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean(Jackson2RemotingCodec.class);
		ctx.refresh();
		RemotingCodec codec = ctx.getBean(RemotingCodec.class);
		Path dir = Path.of("src/test/resources/wire");
		Files.write(dir.resolve("request-more.json"), codec.encodeRequest(MORE, WireFixtures.moreArgs()));
		Files.write(dir.resolve("response-more.json"), codec.createResponse(WireFixtures.MORE, true, null));
		ctx.close();
	}

}
