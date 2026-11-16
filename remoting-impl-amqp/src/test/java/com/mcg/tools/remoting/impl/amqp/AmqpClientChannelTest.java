package com.mcg.tools.remoting.impl.amqp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

class AmqpClientChannelTest {

	@Test
	void interruptedInvokeFreesCorrelation() throws Exception {
		Channel channel = stub(Channel.class, null);
		Connection connection = stub(Connection.class, channel);

		AmqpClientChannel cc = new AmqpClientChannel("test", "a");
		cc.start(connection);

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

	/** Every method returns null, except createChannel. */
	private static <T> T stub(Class<T> type, Channel channel) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(p, m, a) -> m.getName().equals("createChannel") ? channel : null));
	}

}
