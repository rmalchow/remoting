package com.mcg.tools.remoting.impl.amqp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mcg.tools.remoting.common.interfaces.ClientChannel;
import com.mcg.tools.remoting.common.interfaces.ClientChannelProvider;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import jakarta.annotation.PostConstruct;

/**
 * Owns the client connection and all client channels. Channels are created lazily, on the first call of an imported
 * interface, often from several pool threads at once; every 10 s a check reopens a closed connection and restarts
 * channels that are not ready.
 */
@Component
public class AmqpClientChannelProvider implements ClientChannelProvider {

	private static Log log = LogFactory.getLog(AmqpClientChannelProvider.class);

	private final List<AmqpClientChannel> clientChannels = new CopyOnWriteArrayList<>();

	private ConnectionFactory cf;

	@Autowired
	private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

	private volatile Connection connection;
	private final Object connectionLock = new Object();

	private ScheduledExecutorService ste = Executors.newScheduledThreadPool(1, r -> {
		Thread t = new Thread(r, "remoting-client-check");
		t.setDaemon(true);
		return t;
	});

	@Override
	public ClientChannel createClientChannel(String app, String service) {
		AmqpClientChannel s = new AmqpClientChannel(app, service, this::connection);
		clientChannels.add(s);
		// start only this channel; if it fails, its first call and the scheduled check retry
		try {
			s.start(connection());
		} catch (Exception e) {
			log.warn("CLIENT (" + app + ":" + service + "): lazy create: cannot open connection, the channel starts with its first call or the next check: ", e);
		}
		return s;
	}

	/**
	 * Returns an open connection, opening a new one if the current one is missing or closed. Safe to call from any
	 * thread; at most one thread opens a connection at a time.
	 */
	Connection connection() throws Exception {
		Connection c = connection;
		if (c != null && c.isOpen()) {
			return c;
		}
		synchronized (connectionLock) {
			c = connection;
			if (c != null && c.isOpen()) {
				return c;
			}
			if (c != null) {
				log.info("CLIENT: connection not open, reconnecting");
				try {
					c.close();
				} catch (Exception e) {
					log.debug("CLIENT: closing the old connection failed: ", e);
				}
			}
			c = cf.newConnection();
			connection = c;
			log.info("client connection opened!");
			return c;
		}
	}

	/** Restarts every channel that is not ready. Called by the scheduled check. */
	void check() {
		Connection c;
		try {
			c = connection();
		} catch (Exception e) {
			log.warn("CLIENT: connection check: cannot open connection: ", e);
			return;
		}
		for (AmqpClientChannel cc : clientChannels) {
			cc.start(c);
		}
	}

	@PostConstruct
	public void init() {
		CachingConnectionFactory ccf = (CachingConnectionFactory) connectionFactory;
		cf = ccf.getRabbitConnectionFactory();
		cf.setAutomaticRecoveryEnabled(false);
		try {
			connection();
		} catch (Exception e) {
			log.warn("CLIENT: cannot open connection at startup, retrying every 10 s: ", e);
		}
		ste.scheduleWithFixedDelay(() -> {
			try {
				check();
			} catch (Exception e) {
				log.warn("CLIENT: connection check failed: ", e);
			}
		}, 5, 10L, TimeUnit.SECONDS);
	}

	/** for tests */
	void setConnectionFactory(ConnectionFactory cf) {
		this.cf = cf;
	}

}
