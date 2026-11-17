package com.mcg.tools.remoting.impl.amqp;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * An in-memory stand-in for RabbitMQ, enough for the client channel lifecycle: connections and channels that can be
 * closed, steps that fail on demand, and a server that answers every request with "reply:" + request body.
 */
class FakeBroker {

	/** how many of the next createChannel calls fail with an IOException */
	final AtomicInteger failCreateChannel = new AtomicInteger();
	/** while true, createChannel returns null (no channel number available) */
	final AtomicBoolean createChannelReturnsNull = new AtomicBoolean();
	/** how many of the next basicConsume calls fail */
	final AtomicInteger failBasicConsume = new AtomicInteger();
	/** how many of the next publishes find their channel already closed */
	final AtomicInteger closeChannelOnPublish = new AtomicInteger();
	/** pause inside createChannel, to widen race windows */
	volatile long createChannelDelayMillis;

	final AtomicInteger connectionsOpened = new AtomicInteger();
	final AtomicInteger channelsCreated = new AtomicInteger();
	final AtomicInteger published = new AtomicInteger();

	final List<FakeChannel> channels = new CopyOnWriteArrayList<>();
	volatile FakeConnection lastConnection;

	private final ExecutorService server = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "fake-broker");
		t.setDaemon(true);
		return t;
	});

	ConnectionFactory connectionFactory() {
		return new ConnectionFactory() {
			@Override
			public Connection newConnection() {
				return newFakeConnection().proxy;
			}
		};
	}

	FakeConnection newFakeConnection() {
		connectionsOpened.incrementAndGet();
		FakeConnection c = new FakeConnection();
		lastConnection = c;
		return c;
	}

	class FakeConnection {
		final AtomicBoolean open = new AtomicBoolean(true);
		final Connection proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
				(p, m, a) -> switch (m.getName()) {
					case "createChannel" -> createChannel(this);
					case "isOpen" -> open.get();
					case "close" -> {
						close();
						yield null;
					}
					case "hashCode" -> System.identityHashCode(p);
					case "equals" -> p == a[0];
					case "toString" -> "FakeConnection";
					default -> null;
				});

		void close() {
			open.set(false);
			for (FakeChannel ch : channels) {
				if (ch.connection == this) ch.shutdown(false);
			}
		}
	}

	private Channel createChannel(FakeConnection c) throws Exception {
		if (!c.open.get()) throw new AlreadyClosedException(new ShutdownSignalException(true, false, null, null));
		if (createChannelDelayMillis > 0) Thread.sleep(createChannelDelayMillis);
		if (failCreateChannel.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) throw new IOException("createChannel refused (test)");
		if (createChannelReturnsNull.get()) return null;
		channelsCreated.incrementAndGet();
		FakeChannel ch = new FakeChannel(c);
		channels.add(ch);
		return ch.proxy;
	}

	class FakeChannel {
		final FakeConnection connection;
		final AtomicBoolean open = new AtomicBoolean(true);
		volatile Consumer consumer;
		volatile String consumerTag;
		final Channel proxy;

		FakeChannel(FakeConnection connection) {
			this.connection = connection;
			this.proxy = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] { Channel.class },
					(p, m, a) -> switch (m.getName()) {
						case "isOpen" -> open.get() && connection.open.get();
						case "close" -> {
							shutdown(true);
							yield null;
						}
						case "basicConsume" -> basicConsume((Consumer) a[a.length - 1]);
						case "basicPublish" -> {
							basicPublish((AMQP.BasicProperties) a[a.length - 2], (byte[]) a[a.length - 1]);
							yield null;
						}
						case "hashCode" -> System.identityHashCode(p);
						case "equals" -> p == a[0];
						case "toString" -> "FakeChannel";
						default -> defaultValue(m.getReturnType());
					});
		}

		private String basicConsume(Consumer c) throws IOException {
			if (failBasicConsume.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) throw new IOException("basicConsume refused (test)");
			consumer = c;
			consumerTag = "tag-" + System.identityHashCode(this);
			return consumerTag;
		}

		private void basicPublish(AMQP.BasicProperties props, byte[] body) {
			if (closeChannelOnPublish.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
				open.set(false);
			}
			if (!open.get() || !connection.open.get()) {
				throw new AlreadyClosedException(new ShutdownSignalException(false, false, null, null));
			}
			published.incrementAndGet();
			Consumer c = consumer;
			String tag = consumerTag;
			server.execute(() -> {
				try {
					AMQP.BasicProperties reply = new AMQP.BasicProperties.Builder().correlationId(props.getCorrelationId()).build();
					c.handleDelivery(tag, new Envelope(1, false, "", props.getReplyTo()), reply, ("reply:" + new String(body)).getBytes());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}

		/** what the broker does when the queue behind the reply consumer is deleted */
		void cancelConsumer() throws IOException {
			consumer.handleCancel(consumerTag);
		}

		void shutdown(boolean byApplication) {
			if (open.getAndSet(false) && consumer != null) {
				consumer.handleShutdownSignal(consumerTag, new ShutdownSignalException(false, byApplication, null, null));
			}
		}
	}

	private static Object defaultValue(Class<?> type) {
		if (type == boolean.class) return Boolean.FALSE;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		return null;
	}
}
