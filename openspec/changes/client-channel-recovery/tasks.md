# Tasks: remoting 0.4.1 (client channel recovery)

- [x] 1. `AmqpClientChannel`: immutable `Ready` state, synchronized retryable `start`, bounded inline repair, resend after closed channel, self-detection via `handleCancel`/`handleShutdownSignal` (design D1–D4)
- [x] 2. `AmqpClientChannelProvider`: thread-safe connection and list, start only the new channel, check restarts channels that are not ready (D5)
- [x] 3. `ImportedServiceImpl.getClientChannel` synchronized
- [x] 4. Logging with exception and step everywhere in the client lifecycle; step names in server setup failures too
- [x] 5. `FakeBroker` tests incl. races; mutation check (D6)
- [x] 6. Real-broker IT on its own vhost (`remoting-it041`), 4/4 green
- [x] 7. README "Changes in 0.4.1"; `mvn verify` on Boot 3.5.16 and 4.0.9
- [ ] 8. Dev stack: services on 0.4.1 (platform session)
- [ ] 9. Release 0.4.1 (owner)
