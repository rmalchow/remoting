package com.mcg.tools.remoting.common.wire;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcg.tools.remoting.api.RemotingCodec;
import com.mcg.tools.remoting.api.entities.RemotingRequest;
import com.mcg.tools.remoting.api.entities.RemotingResponse;
import com.mcg.tools.remoting.common.channel.util.TestInterceptorA;
import com.mcg.tools.remoting.common.codec.SimpleRemotingCodecConfig;
import com.mcg.tools.remoting.common.wire.WireFixtures.Color;
import com.mcg.tools.remoting.common.wire.WireFixtures.Level;
import com.mcg.tools.remoting.common.wire.WireFixtures.More;
import com.mcg.tools.remoting.common.wire.WireFixtures.Point;
import com.mcg.tools.remoting.common.wire.WireFixtures.Sample;
import com.mcg.tools.remoting.common.wire.WireFixtures.WireService;

/**
 * The codec against the recorded JSON: files from 0.2.2 (design D2 of platform-jdk25-boot4) and the *-more files
 * recorded with the 0.3.x Jackson 2 codec (change remoting-jackson3). Encoding must reproduce the files byte for byte;
 * decoding must give the same values as {@link Jackson2RemotingCodec}, the old peer.
 */
class WireFormatTest {

	private static final ObjectMapper TREE = new ObjectMapper();

	private static final Method ALL;
	private static final Method FIRE;
	static {
		try {
			ALL = WireService.class.getMethod("all", Date.class, Instant.class, Locale.class, byte[].class, Color.class, Sample.class, Map.class);
			FIRE = WireService.class.getMethod("fire", String.class, Integer.class);
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static class HostMapperConfig {
		@Bean
		ObjectMapper hostMapper() {
			return new ObjectMapper().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		}
	}

	/** Records the arguments of the last call. */
	static class Recorder implements WireService {
		Object[] args;

		@Override
		public Sample all(Date date, Instant instant, Locale locale, byte[] bytes, Color color, Sample sample, Map<String, Object> map) {
			args = new Object[] { date, instant, locale, bytes, color, sample, map };
			return null;
		}

		@Override
		public void fire(String text, Integer number) {
			args = new Object[] { text, number };
		}

		@Override
		public More more(More more, BigDecimal amount, long big, Optional<String> optional, Level level, Point point, Map<Date, String> byDate) {
			args = new Object[] { more, amount, big, optional, level, point, byDate };
			return null;
		}
	}

	private static byte[] golden(String name) throws IOException {
		try (InputStream in = WireFormatTest.class.getResourceAsStream("/wire/" + name)) {
			return in.readAllBytes();
		}
	}

	private static void assertSameJson(String file, byte[] actual) throws IOException {
		assertEquals(TREE.readTree(golden(file)), TREE.readTree(actual), file);
		assertEquals(new String(golden(file), StandardCharsets.UTF_8), new String(actual, StandardCharsets.UTF_8), "byte-identical: " + file);
	}

	private static RemotingCodec jackson2() {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.registerBean(Jackson2RemotingCodec.class);
		ctx.refresh();
		return ctx.getBean(RemotingCodec.class);
	}

	private static RemotingCodec codec(Class<?>... extra) {
		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(SimpleRemotingCodecConfig.class);
		if (extra.length > 0) {
			ctx.register(extra);
		}
		ctx.refresh();
		return ctx.getBean(RemotingCodec.class);
	}

	// --- encode

	@Test
	void encodesGoldenFiles() throws IOException {
		RemotingCodec codec = codec();
		assertSameJson("request-all.json", codec.encodeRequest(ALL, WireFixtures.allArgs()));
		assertSameJson("request-nulls.json", codec.encodeRequest(FIRE, new Object[] { null, null }));
		assertSameJson("response-sample.json", codec.createResponse(WireFixtures.SAMPLE, true, null));
		assertSameJson("response-void.json", codec.createResponse(null, true, null));
		assertSameJson("response-error.json", codec.createResponse(null, false, WireFixtures.exception()));
	}

	@Test
	void encodesHeaders() throws IOException {
		assertSameJson("request-headers.json", codec(TestInterceptorA.class).encodeRequest(FIRE, new Object[] { "hello", 7 }));
	}

	@Test
	void writesDatesAsIsoStrings() throws IOException {
		String json = new String(codec().encodeRequest(ALL, WireFixtures.allArgs()));
		assertTrue(json.contains("\"2026-11-16T12:00:00.000+00:00\""), json);
		assertTrue(json.contains("\"2026-11-16T12:00:00Z\""), json);
	}

	@Test
	void ignoresHostMapper() throws IOException {
		RemotingCodec codec = codec(HostMapperConfig.class);
		assertSameJson("request-all.json", codec.encodeRequest(ALL, WireFixtures.allArgs()));
		assertSameJson("response-sample.json", codec.createResponse(WireFixtures.SAMPLE, true, null));
	}

	// --- decode, server side

	private static Object[] invoke(RemotingCodec codec, RemotingRequest request) throws Exception {
		Recorder recorder = new Recorder();
		codec.invoke(WireService.class, request, recorder);
		return recorder.args;
	}

	@Test
	void decodesRequestAll() throws Exception {
		RemotingCodec codec = codec();
		Object[] args = invoke(codec, codec.decodeRequest(golden("request-all.json")));
		Object[] expected = WireFixtures.allArgs();
		assertEquals(expected.length, args.length);
		for (int i = 0; i < expected.length; i++) {
			if (expected[i] instanceof byte[] b) {
				assertArrayEquals(b, (byte[]) args[i]);
			} else {
				assertEquals(expected[i], args[i], "argument " + i);
			}
		}
	}

	@Test
	void decodesRequestNulls() throws Exception {
		RemotingCodec codec = codec();
		assertArrayEquals(new Object[] { null, null }, invoke(codec, codec.decodeRequest(golden("request-nulls.json"))));
	}

	@Test
	void decodesRequestHeaders() throws Exception {
		RemotingCodec codec = codec();
		RemotingRequest request = codec.decodeRequest(golden("request-headers.json"));
		assertEquals(Map.of("A-Value", "AAAA"), request.getHeaders());
		assertArrayEquals(new Object[] { "hello", 7 }, invoke(codec, request));
	}

	// --- decode, client side

	@Test
	void decodesResponses() throws IOException {
		RemotingCodec codec = codec();
		Type sampleType = ALL.getGenericReturnType();

		RemotingResponse sample = codec.decodeResponse(golden("response-sample.json"), sampleType);
		assertTrue(sample.isSuccess());
		assertEquals(WireFixtures.SAMPLE, sample.getReturnValue());

		RemotingResponse none = codec.decodeResponse(golden("response-void.json"), FIRE.getGenericReturnType());
		assertTrue(none.isSuccess());
		assertNull(none.getReturnValue());

		RemotingResponse error = codec.decodeResponse(golden("response-error.json"), FIRE.getGenericReturnType());
		assertFalse(error.isSuccess());
		assertEquals(List.of("caused by --- class java.lang.IllegalStateException: boom",
				"error: \t com.example.Remote.call (Remote.java 42) "), error.getStackTrace());
	}

	@Test
	void ignoresUnknownProperties() throws IOException {
		ObjectNode tree = (ObjectNode) TREE.readTree(golden("response-sample.json"));
		((ObjectNode) tree.get("returnValue")).put("unexpected", 1);
		RemotingResponse response = codec().decodeResponse(TREE.writeValueAsBytes(tree), ALL.getGenericReturnType());
		assertEquals(WireFixtures.SAMPLE, response.getReturnValue());
	}

	// --- types added in 0.4.0 (Jackson 3 port)

	@Test
	void encodesMoreGoldenFiles() throws IOException {
		RemotingCodec codec = codec();
		assertSameJson("request-more.json", codec.encodeRequest(WireGoldenRecorder.MORE, WireFixtures.moreArgs()));
		assertSameJson("response-more.json", codec.createResponse(WireFixtures.MORE, true, null));
	}

	@Test
	void decodesRequestMoreLikeJackson2() throws Exception {
		RemotingCodec codec = codec();
		RemotingCodec old = jackson2();
		Object[] args = invoke(codec, codec.decodeRequest(golden("request-more.json")));
		Object[] expected = invoke(old, old.decodeRequest(golden("request-more.json")));
		assertArrayEquals(expected, args);
		// known since 0.2.x: arguments are first read untyped, so a BigDecimal passes through a double
		assertEquals(new BigDecimal("1234.5"), args[1], "BigDecimal loses trailing zeros (as in 0.3.x)");
		assertEquals(new BigDecimal("1234.5"), ((More) args[0]).getAmount(), "also inside a DTO argument");
		assertEquals(WireFixtures.BIG, args[2], "long beyond 2^53 survives");
	}

	@Test
	void decodesResponseMoreLikeJackson2() throws IOException {
		Type type = WireGoldenRecorder.MORE.getGenericReturnType();
		More actual = (More) codec().decodeResponse(golden("response-more.json"), type).getReturnValue();
		More expected = (More) jackson2().decodeResponse(golden("response-more.json"), type).getReturnValue();
		assertEquals(expected, actual);
		assertEquals(expected.getProps(), actual.getProps());
		for (String k : expected.getProps().keySet()) {
			assertEquals(expected.getProps().get(k).getClass(), actual.getProps().get(k).getClass(), "untyped value class: " + k);
		}
	}
}
