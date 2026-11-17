package com.mcg.tools.remoting.impl.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

class AmqpServerChannelTest {

	/** A channel stub counting basicConsume calls; the first {@code failures} calls throw. */
	static class Broker {
		final AtomicInteger consumes = new AtomicInteger();
		final AtomicInteger failures;

		Broker(int failures) {
			this.failures = new AtomicInteger(failures);
		}

		Connection connection() {
			Channel channel = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] { Channel.class },
					(p, m, a) -> {
						if (m.getName().equals("basicConsume")) {
							if (failures.getAndDecrement() > 0) throw new IOException("broker said no");
							consumes.incrementAndGet();
							return "tag";
						}
						return m.getReturnType() == boolean.class ? false : null;
					});
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
					(p, m, a) -> switch (m.getName()) {
						case "createChannel" -> channel;
						case "isOpen" -> true;
						default -> null;
					});
		}
	}

	@Test
	void failedStartIsRetriedOnSameConnection() {
		Broker broker = new Broker(1);
		Connection connection = broker.connection();
		AmqpServerChannel sc = new AmqpServerChannel("test", "a", null);

		sc.start(connection);
		assertEquals(0, broker.consumes.get(), "first attempt fails");

		sc.start(connection);
		assertEquals(1, broker.consumes.get(), "second attempt on the same connection retries");

		sc.start(connection);
		assertEquals(1, broker.consumes.get(), "already consuming: no second consumer");
	}

	@Test
	void channelsStartWhenContextIsRefreshed() throws Exception {
		Broker broker = new Broker(0);
		AmqpServerChannelProvider provider = new AmqpServerChannelProvider();
		Field f = AmqpServerChannelProvider.class.getDeclaredField("connection");
		f.setAccessible(true);
		f.set(provider, broker.connection());

		// as in a real start: the connection is open before the exported services register their channels
		provider.createServerChannel("test", "a", null);
		provider.createServerChannel("test", "b", null);
		assertEquals(0, broker.consumes.get());

		provider.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));
		assertEquals(2, broker.consumes.get(), "both channels consume without waiting for the scheduled check");

		provider.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));
		assertEquals(2, broker.consumes.get(), "a second refresh (e.g. a child context) adds no consumers");
	}
}
