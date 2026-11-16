package com.mcg.tools.remoting.common.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.mcg.tools.remoting.common.RemotingImportService;
import com.mcg.tools.remoting.common.codec.AInterface;

class TimeoutTest {

	@Test
	void lostRepliesFreeThePool() {
		try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(LoopbackConfig.class)) {
			RemotingImportService ris = ctx.getBean(RemotingImportService.class);
			LostService lost = ris.importService(LostService.class).getProxy();
			AInterface a = ris.importService(AInterface.class).getProxy();
			AtomicInteger interrupts = ctx.getBean(LoopbackConfig.class).lostInterrupts;

			// as many lost calls as the pool (default 8) has threads
			List<CompletableFuture<Throwable>> calls = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				calls.add(CompletableFuture.supplyAsync(() -> {
					long start = System.nanoTime();
					Throwable t = assertThrows(UndeclaredThrowableException.class, lost::call);
					long ms = (System.nanoTime() - start) / 1_000_000;
					if (ms < 900 || ms > 3000) throw new AssertionError("timed out after " + ms + " ms");
					return t.getCause();
				}, Thread.ofPlatform().daemon()::start));
			}
			for (CompletableFuture<Throwable> c : calls) {
				assertInstanceOf(TimeoutException.class, assertTimeoutPreemptively(Duration.ofSeconds(5), () -> c.join()));
			}

			assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
				while (interrupts.get() < 8) Thread.onSpinWait();
			});
			assertEquals(8, interrupts.get());

			// on 0.2.2 all pool threads are still stuck and this times out
			assertEquals(5, assertTimeoutPreemptively(Duration.ofSeconds(2), () -> a.add(2, 3)));
		}
	}

}
