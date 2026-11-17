package com.mcg.tools.remoting.impl.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.mcg.tools.remoting.common.ExportedService;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * The client channel lifecycle against a real RabbitMQ. Runs only with an AMQP URI pointing at a vhost of its own:
 * {@code REMOTING_IT_AMQP_URI=amqp://user:pass@localhost:5672/remoting-it041 mvn -pl remoting-impl-amqp -am test
 * -Dtest=AmqpClientChannelBrokerIT -Dsurefire.failIfNoSpecifiedTests=false}
 */
@EnabledIfEnvironmentVariable(named = "REMOTING_IT_AMQP_URI", matches = "amqp://.+")
class AmqpClientChannelBrokerIT {

	private ConnectionFactory cf;
	private Connection serverConnection;
	private Connection adminConnection;
	private final List<String> exchanges = new ArrayList<>();

	/** an exported service that answers "reply:" + request */
	static class Echo extends ExportedService {
		Echo() {
			super(new Object());
		}

		@Override
		public byte[] handle(byte[] in) {
			return ("reply:" + new String(in)).getBytes();
		}
	}

	@BeforeEach
	void connect() throws Exception {
		cf = new ConnectionFactory();
		cf.setUri(System.getenv("REMOTING_IT_AMQP_URI"));
		cf.setAutomaticRecoveryEnabled(false);
		serverConnection = cf.newConnection();
		adminConnection = cf.newConnection();
	}

	@AfterEach
	void cleanUp() throws Exception {
		try (Channel admin = adminConnection.createChannel()) {
			for (String e : exchanges) {
				admin.exchangeDelete(e);
				admin.queueDelete(e + ":request");
			}
		}
		adminConnection.close();
		serverConnection.close();
	}

	private void startServer(String service) {
		exchanges.add("it:" + service);
		new AmqpServerChannel("it", service, new Echo()).start(serverConnection);
	}

	private AmqpClientChannelProvider provider() {
		AmqpClientChannelProvider p = new AmqpClientChannelProvider();
		p.setConnectionFactory(cf);
		return p;
	}

	private static String call(AmqpClientChannel cc, String body) throws Exception {
		return new String(cc.invoke(body.getBytes()));
	}

	@Test
	void burstOfCallsRightAfterLazyCreation() throws Exception {
		startServer("burst");
		AmqpClientChannelProvider p = provider();
		int n = 20;
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(n);
		try {
			// like ImportedServiceImpl: the first caller creates the channel, everyone calls at once
			AmqpClientChannel cc = (AmqpClientChannel) p.createClientChannel("it", "burst");
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				String body = "b" + i;
				results.add(pool.submit(() -> {
					go.await();
					return call(cc, body);
				}));
			}
			go.countDown();
			for (int i = 0; i < n; i++) {
				assertEquals("reply:b" + i, results.get(i).get());
			}
		} finally {
			pool.shutdownNow();
		}
	}

	/** The ovpn shape: channel setup fails at creation; once the cause is gone, calls work without a restart. */
	@Test
	void setupFailureAtCreationHealsWithoutRestart() throws Exception {
		exchanges.add("it:conflict");
		try (Channel admin = adminConnection.createChannel()) {
			// an exchange of the wrong type makes the client's exchangeDeclare fail (PRECONDITION_FAILED)
			admin.exchangeDeclare("it:conflict", BuiltinExchangeType.FANOUT, true);
		}
		AmqpClientChannelProvider p = provider();
		AmqpClientChannel cc = (AmqpClientChannel) p.createClientChannel("it", "conflict");
		assertFalse(cc.isReady());
		assertThrows(IllegalStateException.class, () -> call(cc, "x"));

		try (Channel admin = adminConnection.createChannel()) {
			admin.exchangeDelete("it:conflict");
		}
		startServer("conflict");
		assertEquals("reply:y", call(cc, "y"));
	}

	/** The broker closes the client's channel (channel error); the next call gets a new one. */
	@Test
	void channelClosedByBroker() throws Exception {
		startServer("chclose");
		AmqpClientChannel cc = (AmqpClientChannel) provider().createClientChannel("it", "chclose");
		assertEquals("reply:x", call(cc, "x"));

		Channel clientChannel = readyField(cc, "channel", Channel.class);
		// 404 NOT_FOUND: the broker closes this channel
		assertThrows(java.io.IOException.class, () -> clientChannel.queueDeclarePassive("it:does-not-exist"));
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			while (cc.isReady()) Thread.sleep(20);
		});
		assertEquals("reply:y", call(cc, "y"));
	}

	@Test
	void clientConnectionLost() throws Exception {
		startServer("conn");
		AmqpClientChannelProvider p = provider();
		AmqpClientChannel cc = (AmqpClientChannel) p.createClientChannel("it", "conn");
		assertEquals("reply:x", call(cc, "x"));

		p.connection().abort();
		assertEquals("reply:y", call(cc, "y"));
	}

	private static <T> T readyField(AmqpClientChannel cc, String component, Class<T> type) throws Exception {
		Field f = AmqpClientChannel.class.getDeclaredField("ready");
		f.setAccessible(true);
		Object ready = f.get(cc);
		Method m = ready.getClass().getDeclaredMethod(component);
		m.setAccessible(true);
		return type.cast(m.invoke(ready));
	}
}
