# Design: client channel recovery

## D1. One immutable "ready" state

`AmqpClientChannel` keeps everything a call needs in one record, replaced as a whole:

```java
private record Ready(Connection connection, Channel channel, String routingKey) {}
private volatile Ready ready;   // null = not ready
```

Before, `clientChannel`, `routingKey` and `connection` were separate, non-volatile fields set one after
another inside `listen()`, so a call on another thread could see a new routing key with an old channel,
or a `null` channel. Now a call reads `ready` once and uses that generation consistently. "Healthy"
means: `ready != null`, its channel open, its connection open.

## D2. start() is synchronized, idempotent and remembers nothing on failure

```text
start(conn):  if ready is healthy on conn -> return true
              close the old channel, build a new Ready via listen(conn)
              ready = result (null on failure) -> a later start retries
```

`listen()` records the current step (`createChannel`, `exchangeDeclare <name>`, `queueDeclare <name>`,
`queueBind`, `basicConsume <name>`) and logs `error creating consumer (<app>:<service>) at step [<step>]`
with the exception, then closes the half-built channel.

## D3. Calls repair inline, bounded

`invoke()` calls `ensureReady()`: up to two attempts, 250 ms apart, each asking the provider for an open
connection (`AmqpClientChannelProvider.connection()`, which reconnects if needed) and calling `start`.
The connection is fetched **before** taking the channel's lock, so the lock order is always
provider → channel and cannot deadlock with the 10 s check. If the channel is still not ready the call
fails with `CLIENT_CHANNEL_NOT_READY` (cause attached). The total extra wait is about 250 ms plus
connect time, well inside the default 10 s timeout.

A publish that throws `AlreadyClosedException` marks the generation broken, repairs, and publishes once
more; any other publish failure is not retried (it may have reached the broker).

## D4. The channel watches itself

The reply consumer overrides `handleCancel` (broker removed the consumer, e.g. queue deleted) and
`handleShutdownSignal` (channel or connection closed); both drop the current generation if it is still
the one they belong to, so the next call or check rebuilds it.

## D5. Provider: one connection, thread-safe list, no restart-all on create

- `connection()` returns the open connection or opens one; double-checked under a lock so concurrent
  first calls open exactly one.
- `createClientChannel` starts only the new channel (not all of them), and logs a failure with the
  exception instead of swallowing it.
- The channel list is a `CopyOnWriteArrayList`; the 10 s `check()` starts every channel that is not
  ready.
- `ImportedServiceImpl.getClientChannel()` is synchronized, so one interface gets one channel.

## D6. Tests

`AmqpClientChannelTest` runs against `FakeBroker`, an in-memory stand-in whose steps fail on demand:

| test | scenario |
|---|---|
| `failedCreateChannelAtLazyCreateIsRepairedByTheFirstCall` | the ovpn case: setup failed at creation, first call repairs |
| `failedBasicConsumeIsRetried` | failure at a later setup step |
| `callFailsWhileSetupIsImpossibleAndSucceedsOnceItWorksAgain` | fails with `CLIENT_CHANNEL_NOT_READY`, later call succeeds, no restart |
| `scheduledCheckRepairsWithoutAnyCall` | same connection, failed start retried by the check |
| `channelClosedAtPublishIsRecreatedAndTheRequestResent` | sent exactly once |
| `closedConnectionIsReopened` / `consumerCancelledByBrokerIsRecreated` | breakage after start |
| `concurrentFirstCallsAndCheckDuringSlowSetup` | 16 first calls + checks during a slow setup: all succeed, one channel |
| `concurrentLazyCreationAndCheck` | 32 interfaces created concurrently with the check: one connection, 32 channels |

Mutation check: restoring the 0.4.0 "remember the connection before setup" makes 7 of these fail;
removing `synchronized` from `start()` makes both race tests fail. (Restoring the plain `ArrayList` is
not caught: since `createClientChannel` no longer iterates the list, that race is now theoretical.)

`AmqpClientChannelBrokerIT` runs the same ideas against a real RabbitMQ (only with
`REMOTING_IT_AMQP_URI` set, on a vhost of its own): a 20-call burst right after creation; a setup
failure at `exchangeDeclare` (an exchange of the wrong type, `PRECONDITION_FAILED`) that heals once the
exchange is fixed; a channel closed by the broker (404 on the client's channel); a lost connection.
