package com.mcg.tools.remoting.common.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcg.tools.remoting.common.RemotingImportService;
import com.mcg.tools.remoting.common.wire.WireFixtures.Color;
import com.mcg.tools.remoting.common.wire.WireFixtures.Sample;

/** Regression tests through proxy, codec, exported service, codec (loopback, no broker). */
class RegressionTest {

	private static final ObjectMapper TREE = new ObjectMapper();

	private AnnotationConfigApplicationContext ctx;
	private RegConfig cfg;
	private RegService svc;

	@BeforeEach
	void start() {
		ctx = new AnnotationConfigApplicationContext(RegConfig.class);
		cfg = ctx.getBean(RegConfig.class);
		svc = ctx.getBean(RemotingImportService.class).importService(RegService.class).getProxy();
	}

	@AfterEach
	void stop() {
		ctx.close();
	}

	private JsonNode request() throws Exception {
		return TREE.readTree(cfg.lastRequest);
	}

	private JsonNode response() throws Exception {
		return TREE.readTree(cfg.lastResponse);
	}

	// 1. generics decode to typed elements

	@Test
	void listElementsAreTyped() {
		List<Sample> l = svc.samples();
		assertEquals(2, l.size());
		l.forEach(s -> assertInstanceOf(Sample.class, s));
		assertEquals("b", l.get(1).getName());
	}

	@Test
	void mapValuesAreTyped() {
		Map<String, Sample> m = svc.sampleMap();
		assertInstanceOf(Sample.class, m.get("x"));
		assertEquals("x", m.get("x").getName());
		assertEquals(Color.RED, m.get("x").getColor());
	}

	@Test
	void setOfEnumIsTyped() {
		Set<Color> s = svc.colors();
		assertEquals(Set.of(Color.RED, Color.GREEN), s);
		s.forEach(c -> assertInstanceOf(Color.class, c));
	}

	@Test
	void nestedGenericDtoIsTyped() {
		RegService.Box<Sample> b = svc.box();
		assertInstanceOf(Sample.class, b.value);
		assertEquals("v", b.value.getName());
		assertInstanceOf(Sample.class, b.more.get(0));
		assertEquals("m", b.more.get(0).getName());
	}

	// 2. remote exception

	@Test
	void remoteExceptionBecomesRemoteCallFailed() {
		RuntimeException e = assertThrows(RuntimeException.class, svc::fail);
		assertEquals(RuntimeException.class, e.getClass());
		assertEquals("REMOTE_CALL_FAILED", e.getMessage());
		assertNull(e.getCause());
		// the proxy stays usable
		assertEquals("pong", svc.ping());
	}

	// 3. overloads: dispatch is the first method in getMethods() order whose parameters conform

	@Test
	void overloadDeterministicCases() {
		// "abc" cannot become an int; Date and Sample JSON shapes (string vs object) exclude each other
		assertEquals("find(String):abc", svc.find("abc"));
		assertEquals("put(Date)", svc.put(new Date(0)));
		assertEquals("put(Sample)", svc.put(RegServiceImpl.sample("s")));
	}

	@Test
	void overloadAmbiguousCasesPickOneOfTheTwo() {
		// Jackson coerces 5 -> "5" and "5" -> 5, so both overloads conform and the winner depends on
		// the unspecified order of Class.getMethods(): only assert that one of the two is invoked.
		String a = svc.find(5);
		assertTrue(a.equals("find(int):5") || a.equals("find(String):5"), a);
		String b = svc.find("5");
		assertTrue(b.equals("find(int):5") || b.equals("find(String):5"), b);
	}

	// 4. interceptor headers

	@Test
	void interceptorHeadersTravelBothWays() {
		assertEquals("pong", svc.ping());
		assertEquals("req-value", cfg.seen.get("beforeHandle"));
		assertEquals("resp-value", cfg.seen.get("afterReceive"));
	}

	// 5. encoding of types the golden files do not cover

	@Test
	void durationIsIsoString() throws Exception {
		assertEquals(Duration.ofSeconds(1), svc.duration(Duration.ofSeconds(1)));
		assertEquals("PT1S", request().get("params").get(0).asText());
		assertTrue(request().get("params").get(0).isTextual());
		assertTrue(response().get("returnValue").isTextual());
		assertEquals("PT1S", response().get("returnValue").asText());
	}

	@Test
	void localDateIsIsoString() throws Exception {
		LocalDate d = LocalDate.of(2026, 11, 16);
		assertEquals(d, svc.date(d));
		assertEquals("2026-11-16", request().get("params").get(0).asText());
		assertEquals("2026-11-16", response().get("returnValue").asText());
	}

	@Test
	void optionalPresentIsUnwrapped() throws Exception {
		assertEquals(Optional.of("x"), svc.optional(Optional.of("x")));
		assertEquals("x", request().get("params").get(0).asText());
		assertEquals("x", response().get("returnValue").asText());
	}

	@Test
	void optionalEmptyIsNull() throws Exception {
		// pins current behaviour: empty goes over the wire as null, and decodeResponse skips conversion
		// of a null return value, so the caller sees a plain null, NOT Optional.empty()
		assertNull(svc.optional(Optional.empty()));
		assertTrue(request().get("params").get(0).isNull());
		assertTrue(response().get("returnValue").isNull());
	}
}
