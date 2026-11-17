# Client channels that heal themselves (release 0.4.1)

## Why

On 2026-11-17, in the platform's dev stack, every call from the `ovpn` service to
`ExportedPublicKeyProvider.getPublicKey` (served by sso) failed instantly:

```
ERROR AmqpClientChannel: error in client
WARN  ImportedServiceImpl: RPC ExportedPublicKeyProvider.getPublicKey failed (class java.util.concurrent.ExecutionException) (timeout: 10, elapsed: 0)
```

`crm` and `messaging`, started at the same moment against the same sso, made the same call
successfully. Restarting only ovpn fixed it. The log held no stack trace: "error in client" was logged
without its exception, and the failure that caused it was swallowed silently in one place
(`AmqpClientChannelProvider.createClientChannel`) and logged without the failing step in another.

Some vocabulary, using this incident:

- A **client channel** (`AmqpClientChannel`) is what an imported interface — here
  `ExportedPublicKeyProvider` in ovpn — uses to send requests. It is an AMQP *channel* (a lightweight
  session inside the one TCP connection a service holds to RabbitMQ) plus a private *response queue*
  (`authn:publicKeyProvider:response-<uuid>`) with a *consumer* listening on it for replies.
- It is created **lazily**: on the first call of the interface, inside the remoting pool thread that
  runs that call. A 10 s **connection check** in `AmqpClientChannelProvider` reopens a closed
  connection and (re)starts channels.

What went wrong, as far as the code shows (the ovpn log had no further detail):

1. `AmqpClientChannel.start(connection)` stored the connection *before* setting the channel up. If
   setup failed — at `createChannel`, a declare, the bind or `basicConsume` — every later `start` with
   the same connection returned immediately ("already started"), the 10 s check included. The channel
   field stayed `null`, every call failed with a `NullPointerException` logged as "error in client",
   and since that is not a "connection closed" error nothing ever replaced the connection: **broken
   until restart**. The server side had the same defect and got the same fix in 0.3.1.
2. Several threads could start the same channel at once: `start()` was not synchronized and the channel
   field not `volatile`, so a call could see "already started" while another thread was still
   setting up, and use a `null` channel. Two concurrent first calls could also create two channels
   for one interface (`ImportedServiceImpl.getClientChannel` was not synchronized).
3. `createClientChannel` added to a plain `ArrayList` while the 10 s check iterated it, and restarted
   *all* channels; a `ConcurrentModificationException` there was swallowed without a log line.

## What changes

- A client channel is either **ready** (open channel, open connection, active reply consumer) or not.
  Starting it is idempotent and retryable: a failed start leaves it not ready, and any later start —
  from a call, from the 10 s check, from lazy creation — tries again.
- A **call that finds its channel not ready repairs it inline** (two attempts, 250 ms apart, asking the
  provider for an open connection, reconnecting if needed) and only then fails with
  `IllegalStateException: CLIENT_CHANNEL_NOT_READY: <app>:<service>`. The next call tries again, so the
  service recovers as soon as the cause is gone — no restart.
- If a publish finds the channel closed, the channel is rebuilt and the request sent once more. That is
  safe: the failed publish sent nothing.
- The channel notices breakage itself: a broker-side channel close or a cancelled reply consumer (e.g.
  its queue deleted) marks it not ready.
- Every failure is logged with its exception and step.
- No change to the wire format, the RabbitMQ topology (names, durability, delivery mode) or any API.
  0.3.x and 0.4.0 peers are unaffected.

## Rejected

- **Close the whole connection on any client error** (the old reaction to `AlreadyClosedException`):
  it interrupts every other interface's calls to fix one channel.
- **Unbounded retry inside a call:** the caller's timeout (10 s by default) would be spent waiting; a
  bounded inline repair plus the 10 s check is enough, and the error stays visible.
- **RabbitMQ's automatic connection recovery:** remoting turns it off deliberately (it restores
  topology on its own, which overlaps with remoting's own setup); changing that is a separate decision.
