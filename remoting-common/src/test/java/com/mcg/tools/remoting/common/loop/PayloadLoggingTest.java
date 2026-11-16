package com.mcg.tools.remoting.common.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.mcg.tools.remoting.common.RemotingImportService;
import com.mcg.tools.remoting.common.codec.AInterface;

/**
 * No SLF4J/Log4j on the test classpath, so commons-logging writes to java.util.logging:
 * DEBUG is FINE, TRACE is FINEST.
 */
class PayloadLoggingTest {

	private static final String SECRET = "jane@example.org";

	// strong references: java.util.logging only holds loggers weakly
	private final Logger mcg = Logger.getLogger("com.mcg");
	private final Logger payload = Logger.getLogger("com.mcg.tools.remoting.payload");
	private final List<LogRecord> records = new CopyOnWriteArrayList<>();
	private final Handler handler = new Handler() {
		@Override
		public void publish(LogRecord r) {
			records.add(r);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};
	private Level mcgLevel;
	private Level payloadLevel;

	@BeforeEach
	void attach() {
		mcgLevel = mcg.getLevel();
		payloadLevel = payload.getLevel();
		handler.setLevel(Level.ALL);
		mcg.addHandler(handler);
		mcg.setLevel(Level.FINE);
	}

	@AfterEach
	void restore() {
		mcg.removeHandler(handler);
		mcg.setLevel(mcgLevel);
		payload.setLevel(payloadLevel);
	}

	@Test
	void debugLogsNoValues() {
		assertEquals(List.of(SECRET), echo());
		assertTrue(records.stream().noneMatch(r -> text(r).contains(SECRET)), "a log record contains the payload");
		assertTrue(records.stream().anyMatch(r -> text(r).contains("AInterface") && text(r).contains("echoList")), "no record names AInterface.echoList");
	}

	@Test
	void payloadLoggerAtTraceLogsBodies() {
		payload.setLevel(Level.FINEST);
		assertEquals(List.of(SECRET), echo());
		assertTrue(records.stream().anyMatch(r -> payload.getName().equals(r.getLoggerName()) && text(r).contains(SECRET)), "payload logger did not log the body");
	}

	private List<String> echo() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(LoopbackConfig.class)) {
			AInterface a = ctx.getBean(RemotingImportService.class).importService(AInterface.class).getProxy();
			return a.echoList(List.of(SECRET));
		}
	}

	private static String text(LogRecord r) {
		return r.getMessage() + " " + r.getThrown();
	}

}
