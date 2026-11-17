package com.mcg.tools.remoting.impl.amqp;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mcg.tools.remoting.api.annotations.RemotingException;
import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.utility.BlockingCell;

/**
 * The client side of one remote interface: publishes requests to the service's exchange and receives the replies on a
 * private response queue.
 * <p>
 * Lifecycle (0.4.1): the channel is either <em>ready</em> (an open AMQP channel with an active reply consumer) or not.
 * {@link #start(Connection)} makes it ready and can be called any number of times, from any thread; a failed attempt
 * leaves it not ready, so the next attempt (the next call, or the provider's 10 s check) tries again. A call that finds
 * the channel not ready repairs it inline before failing.
 */
public class AmqpClientChannel implements ClientChannel {

	private static Log log = LogFactory.getLog(AmqpClientChannel.class);

	private static final int PERSISTENT_DELIVERY_MODE = 2;

	/** Supplies an open connection, opening a new one if needed. */
	@FunctionalInterface
	public interface ConnectionSource {
		Connection get() throws Exception;
	}

	/** Everything a call needs, replaced as a whole on every (re)start, so a call never mixes two generations. */
	private record Ready(Connection connection, Channel channel, String routingKey) {
	}

	private final String app;
	private final String service;
	private final String exchangeName;
	private final ConnectionSource connectionSource;

	/** how many inline repair attempts a call makes before it fails, and the pause between them */
	private final int repairAttempts;
	private final long repairPauseMillis;

	private volatile Ready ready;

	private final ConcurrentHashMap<String, BlockingCell<byte[]>> requestMap = new ConcurrentHashMap<>();

	public AmqpClientChannel(String app, String service, ConnectionSource connectionSource) {
		this(app, service, connectionSource, 2, 250);
	}

	AmqpClientChannel(String app, String service, ConnectionSource connectionSource, int repairAttempts, long repairPauseMillis) {
		this.app = app;
		this.service = service;
		this.exchangeName = app + ":" + service;
		this.connectionSource = connectionSource;
		this.repairAttempts = repairAttempts;
		this.repairPauseMillis = repairPauseMillis;
	}

	@Override
	public byte[] invoke(byte[] in) throws Exception {

		String correlationId = UUID.randomUUID().toString();
		BlockingCell<byte[]> bc = new BlockingCell<>();
		requestMap.put(correlationId, bc);

		String step = "ensure channel ready";
		try {
			Ready r = ensureReady();
			step = "publish";
			try {
				publish(r, correlationId, in);
			} catch (AlreadyClosedException ace) {
				// the channel (or its connection) closed under us; nothing was sent, so repair and publish once more
				log.warn("CLIENT (" + exchangeName + "): channel closed at publish, restarting it and resending: ", ace);
				markBroken(r);
				step = "ensure channel ready (after closed channel)";
				r = ensureReady();
				step = "publish (retry)";
				publish(r, correlationId, in);
			}
			step = "wait for reply";
			return bc.get();
		} catch (InterruptedException ie) {
			// the caller timed out and cancelled this call
			throw ie;
		} catch (Exception e) {
			log.error("error in client (" + exchangeName + ") at step [" + step + "]: ", e);
			throw e;
		} finally {
			requestMap.remove(correlationId);
		}
	}

	private void publish(Ready r, String correlationId, byte[] in) throws IOException {
		BasicProperties props = new BasicProperties.Builder().replyTo(r.routingKey()).correlationId(correlationId).deliveryMode(PERSISTENT_DELIVERY_MODE).build();
		r.channel().basicPublish(exchangeName, "request", true, props, in);
	}

	/**
	 * Returns the ready state, repairing it if needed: up to {@code repairAttempts} attempts, with a short pause in
	 * between. Throws if the channel still cannot be started.
	 */
	private Ready ensureReady() throws Exception {
		Exception last = null;
		for (int attempt = 0; attempt < repairAttempts; attempt++) {
			Ready r = ready;
			if (isHealthy(r)) {
				return r;
			}
			if (attempt > 0) {
				Thread.sleep(repairPauseMillis);
			}
			try {
				// fetch the connection without holding this channel's lock (the provider may reconnect and restart channels)
				start(connectionSource.get());
			} catch (InterruptedException ie) {
				throw ie;
			} catch (Exception e) {
				log.warn("CLIENT (" + exchangeName + "): repair attempt " + (attempt + 1) + "/" + repairAttempts + ": cannot get a connection: ", e);
				last = e;
			}
		}
		Ready r = ready;
		if (isHealthy(r)) {
			return r;
		}
		throw new IllegalStateException("CLIENT_CHANNEL_NOT_READY: " + exchangeName, last);
	}

	private static boolean isHealthy(Ready r) {
		return r != null && r.channel().isOpen() && r.connection().isOpen();
	}

	/** Forgets the given generation, if it is still the current one, so the next start() rebuilds it. */
	private void markBroken(Ready r) {
		synchronized (this) {
			if (ready == r) {
				ready = null;
			}
		}
		closeQuietly(r.channel());
	}

	/**
	 * Makes this channel ready on the given connection. Does nothing if it already is. A failed attempt is logged and
	 * leaves the channel not ready, so calling again retries.
	 *
	 * @return whether the channel is ready afterwards
	 */
	public synchronized boolean start(Connection connection) {
		if (connection == null) {
			return false;
		}
		Ready current = ready;
		if (current != null && current.connection() == connection && isHealthy(current)) {
			return true;
		}
		if (current != null) {
			ready = null;
			closeQuietly(current.channel());
		}
		log.info("CLIENT (" + exchangeName + "): (re)creating channel");
		Ready r = listen(connection);
		ready = r;
		return r != null;
	}

	private Ready listen(Connection connection) {
		String step = "check names";
		Channel channel = null;
		try {
			if (StringUtils.isEmpty(app)) throw new RemotingException("EMPTY_APP_NAME", null);
			if (StringUtils.isEmpty(service)) throw new RemotingException("EMPTY_SERVICE_NAME", null);

			String routingKey = UUID.randomUUID().toString();
			String responseQueueName = exchangeName + ":response-" + routingKey;

			step = "createChannel";
			channel = connection.createChannel();
			if (channel == null) {
				throw new IOException("no channel available on the connection");
			}
			step = "exchangeDeclare " + exchangeName;
			channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, true, false, new HashMap<>());
			step = "queueDeclare " + responseQueueName;
			channel.queueDeclare(responseQueueName, false, true, true, new HashMap<>());
			step = "queueBind";
			channel.queueBind(responseQueueName, exchangeName, routingKey);

			channel.addReturnListener((replyCode, replyText, exchange, rk, properties, body) -> {
				BlockingCell<byte[]> bc = requestMap.get(properties.getCorrelationId());
				if (bc != null) {
					log.warn("no consumer available! (exchangeName: " + exchangeName + ", responseQueue: " + responseQueueName + ")");
					bc.set(new byte[] {});
				} else {
					log.warn(" ---- unknown correlation id: " + properties.getCorrelationId());
				}
			});

			step = "basicConsume " + responseQueueName;
			Channel ch = channel;
			channel.basicConsume(responseQueueName, false, new DefaultConsumer(channel) {
				@Override
				public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
					getChannel().basicAck(envelope.getDeliveryTag(), false);
					BlockingCell<byte[]> bc = requestMap.remove(properties.getCorrelationId());
					if (bc != null) {
						bc.set(body);
					} else {
						log.warn(" ---- unknown correlation id: " + properties.getCorrelationId());
					}
				}

				@Override
				public void handleCancel(String consumerTag) {
					// the broker removed our reply consumer (e.g. the response queue was deleted)
					log.warn("CLIENT (" + exchangeName + "): reply consumer cancelled by the broker, channel will be recreated");
					forget(ch);
				}

				@Override
				public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
					if (!sig.isInitiatedByApplication()) {
						log.warn("CLIENT (" + exchangeName + "): channel shut down, will be recreated: " + sig.getMessage());
					}
					forget(ch);
				}
			});

			return new Ready(connection, channel, routingKey);

		} catch (Exception e) {
			log.warn("error creating consumer (" + exchangeName + ") at step [" + step + "], retrying with the next call or connection check: ", e);
			closeQuietly(channel);
			return null;
		}
	}

	/** Called from the consumer: drop the generation that owns this channel. */
	private void forget(Channel channel) {
		Ready r = ready;
		if (r != null && r.channel() == channel) {
			markBroken(r);
		}
	}

	private static void closeQuietly(Channel channel) {
		if (channel == null) return;
		try {
			if (channel.isOpen()) channel.close();
		} catch (Exception e) {
			log.debug("closing a client channel failed: ", e);
		}
	}

	boolean isReady() {
		return isHealthy(ready);
	}

}
