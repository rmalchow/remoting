package com.mcg.tools.remoting.impl.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

/** Client channel lifecycle (0.4.1): failures at startup or later must heal without a process restart. */
class AmqpClientChannelTest {

	private static AmqpClientChannelProvider provider(FakeBroker broker) {
		AmqpClientChannelProvider p = new AmqpClientChannelProvider();
		p.setConnectionFactory(broker.connectionFactory());
		return p;
	}

	private static String call(AmqpClientChannel cc, String body) throws Exception {
		return new String(cc.invoke(body.getBytes()));
	}

	// --- the ovpn case: channel setup failed once, later calls must recover

	@Test
	void failedCreateChannelAtLazyCreateIsRepairedByTheFirstCall() throws Exception {
		FakeBroker broker = new FakeBroker();
		broker.failCreateChannel.set(1);
		AmqpClientChannel cc = (AmqpClientChannel) provider(broker).createClientChannel("authn", "publicKeyProvider");
		assertFalse(cc.isReady(), "setup failed at creation");

		assertEquals("reply:x", call(cc, "x"), "the call repairs the channel inline");
		assertEquals(1, broker.connectionsOpened.get(), "same connection");
	}

	@Test
	void failedBasicConsumeIsRetried() throws Exception {
		FakeBroker broker = new FakeBroker();
		broker.failBasicConsume.set(1);
		AmqpClientChannel cc = (AmqpClientChannel) provider(broker).createClientChannel("authn", "publicKeyProvider");
		assertFalse(cc.isReady());
		assertEquals("reply:x", call(cc, "x"));
	}

	@Test
	void callFailsWhileSetupIsImpossibleAndSucceedsOnceItWorksAgain() throws Exception {
		FakeBroker broker = new FakeBroker();
		broker.createChannelReturnsNull.set(true);
		AmqpClientChannelProvider p = provider(broker);
		AmqpClientChannel cc = (AmqpClientChannel) p.createClientChannel("authn", "publicKeyProvider");

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> call(cc, "x"));
		assertTrue(e.getMessage().startsWith("CLIENT_CHANNEL_NOT_READY"), e.getMessage());

		broker.createChannelReturnsNull.set(false);
		assertEquals("reply:y", call(cc, "y"), "a later call succeeds without restart");
	}

	@Test
	void scheduledCheckRepairsWithoutAnyCall() {
		FakeBroker broker = new FakeBroker();
		broker.failCreateChannel.set(1);
		AmqpClientChannelProvider p = provider(broker);
		AmqpClientChannel cc = (AmqpClientChannel) p.createClientChannel("authn", "publicKeyProvider");
		assertFalse(cc.isReady());

		p.check();
		assertTrue(cc.isReady(), "same connection, but the failed start is retried");
	}

	// --- breakage after a successful start

	@Test
	void channelClosedAtPublishIsRecreatedAndTheRequestResent() throws Exception {
		FakeBroker broker = new FakeBroker();
		AmqpClientChannel cc = (AmqpClientChannel) provider(broker).createClientChannel("authn", "publicKeyProvider");
		broker.closeChannelOnPublish.set(1);

		assertEquals("reply:x", call(cc, "x"));
		assertEquals(2, broker.channelsCreated.get(), "a new channel");
		assertEquals(1, broker.published.get(), "sent once (the failed publish sent nothing)");
	}

	@Test
	void closedConnectionIsReopened() throws Exception {
		FakeBroker broker = new FakeBroker();
		AmqpClientChannel cc = (AmqpClientChannel) provider(broker).createClientChannel("authn", "publicKeyProvider");
		assertEquals("reply:x", call(cc, "x"));

		broker.lastConnection.close();
		assertFalse(cc.isReady());
		assertEquals("reply:y", call(cc, "y"));
		assertEquals(2, broker.connectionsOpened.get());
	}

	@Test
	void consumerCancelledByBrokerIsRecreated() throws Exception {
		FakeBroker broker = new FakeBroker();
		AmqpClientChannel cc = (AmqpClientChannel) provider(broker).createClientChannel("authn", "publicKeyProvider");
		broker.channels.get(0).cancelConsumer();
		assertFalse(cc.isReady(), "no reply consumer: not ready");

		assertEquals("reply:x", call(cc, "x"));
		assertEquals(2, broker.channelsCreated.get());
	}

	// --- races

	/** Many first calls at once on a channel whose setup is slow, while the scheduled check runs: none may fail. */
	@Test
	void concurrentFirstCallsAndCheckDuringSlowSetup() throws Exception {
		FakeBroker broker = new FakeBroker();
		broker.createChannelReturnsNull.set(true);
		AmqpClientChannelProvider p = provider(broker);
		AmqpClientChannel cc = (AmqpClientChannel) p.createClientChannel("authn", "publicKeyProvider");
		broker.createChannelReturnsNull.set(false);
		broker.createChannelDelayMillis = 100;

		int n = 16;
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(n + 1);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				String body = "r" + i;
				results.add(pool.submit(() -> {
					go.await();
					return call(cc, body);
				}));
			}
			Future<?> checker = pool.submit(() -> {
				go.await();
				for (int i = 0; i < 5; i++) p.check();
				return null;
			});
			go.countDown();
			for (int i = 0; i < n; i++) {
				assertEquals("reply:r" + i, results.get(i).get());
			}
			checker.get();
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, broker.channelsCreated.get(), "one channel, not one per racing thread");
	}

	/** Lazy creation of many interfaces at once, concurrent with the check (0.4.0: ArrayList + listen-all, CME swallowed). */
	@Test
	void concurrentLazyCreationAndCheck() throws Exception {
		FakeBroker broker = new FakeBroker();
		AmqpClientChannelProvider p = provider(broker);
		int n = 32;
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(n + 1);
		AtomicBoolean stop = new AtomicBoolean();
		try {
			List<Future<AmqpClientChannel>> created = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				String service = "s" + i;
				created.add(pool.submit((Callable<AmqpClientChannel>) () -> {
					go.await();
					return (AmqpClientChannel) p.createClientChannel("app", service);
				}));
			}
			AtomicReference<Throwable> checkFailure = new AtomicReference<>();
			pool.submit(() -> {
				try {
					go.await();
					while (!stop.get()) p.check();
				} catch (Throwable t) {
					checkFailure.set(t);
				}
			});
			go.countDown();
			for (Future<AmqpClientChannel> f : created) {
				AmqpClientChannel cc = f.get();
				assertTrue(cc.isReady());
				assertEquals("reply:z", call(cc, "z"));
			}
			stop.set(true);
			assertEquals(null, checkFailure.get());
		} finally {
			pool.shutdownNow();
		}
		assertEquals(1, broker.connectionsOpened.get(), "one shared connection");
		assertEquals(n, broker.channelsCreated.get(), "exactly one channel per interface");
	}

	// --- unchanged behaviour

	@Test
	void interruptedInvokeFreesCorrelation() throws Exception {
		Channel channel = stub(Channel.class, null);
		Connection connection = stub(Connection.class, channel);

		AmqpClientChannel cc = new AmqpClientChannel("test", "a", () -> connection);
		assertTrue(cc.start(connection));

		Field f = AmqpClientChannel.class.getDeclaredField("requestMap");
		f.setAccessible(true);
		Map<?, ?> requestMap = (Map<?, ?>) f.get(cc);

		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread t = Thread.ofPlatform().daemon().start(() -> {
			try {
				cc.invoke(new byte[] { 1 });
			} catch (Throwable e) {
				thrown.set(e);
			}
		});

		assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
			while (requestMap.size() != 1) Thread.onSpinWait();
		});
		t.interrupt();
		assertTrue(t.join(Duration.ofSeconds(3)), "invoke did not return after interrupt");

		assertInstanceOf(InterruptedException.class, thrown.get());
		assertTrue(requestMap.isEmpty(), "correlation entry left behind");
	}

	/** Every method returns null (false), except createChannel and isOpen. */
	private static <T> T stub(Class<T> type, Channel channel) {
		return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(p, m, a) -> switch (m.getName()) {
					case "createChannel" -> channel;
					case "isOpen" -> true;
					default -> m.getReturnType() == boolean.class ? false : null;
				}));
	}

}
