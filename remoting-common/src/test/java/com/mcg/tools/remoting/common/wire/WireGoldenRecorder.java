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
 * Writes golden files. Only runs on request: {@code mvn -pl remoting-common -am test -Dtest=WireGoldenRecorder
 * -Dsurefire.failIfNoSpecifiedTests=false -Dwire.record=jackson2|current}
 * <ul>
 * <li>{@code jackson2}: request-more.json / response-more.json with the 0.3.x reference codec (what old peers send)</li>
 * <li>{@code current}: request-more-040.json / response-more-040.json with the codec under test. Only for a declared
 * format change; WireFormatTest checks how these differ from the jackson2 files.</li>
 * </ul>
 * The files of 0.3.0 (request-all, request-nulls, ...) were recorded from 0.2.2 and are not rewritten here.
 */
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
	@EnabledIfSystemProperty(named = "wire.record", matches = "jackson2")
	void recordJackson2() throws IOException {
		record(Jackson2RemotingCodec.class, "");
	}

	@Test
	@EnabledIfSystemProperty(named = "wire.record", matches = "current")
	void recordCurrent() throws IOException {
		record(com.mcg.tools.remoting.common.codec.SimpleRemotingCodec.class, "-040");
	}

	private static void record(Class<? extends RemotingCodec> type, String suffix) throws IOException {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean(type);
		ctx.refresh();
		RemotingCodec codec = ctx.getBean(RemotingCodec.class);
		Path dir = Path.of("src/test/resources/wire");
		Files.write(dir.resolve("request-more" + suffix + ".json"), codec.encodeRequest(MORE, WireFixtures.moreArgs()));
		Files.write(dir.resolve("response-more" + suffix + ".json"), codec.createResponse(WireFixtures.MORE, true, null));
		ctx.close();
	}

}
