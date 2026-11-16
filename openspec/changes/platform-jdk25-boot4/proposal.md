# Make remoting ready for JDK 25 and Spring Boot 4 (release 0.3.0)

## Why

The selfservice platform (`mcg-automation`, repo `gitlab-mcww-automation/automation/all`) is
moving in this order:

1. Spring Boot 3.4.4 → **3.5.x**, together with **JDK 25** (the current long-term-support Java
   release).
2. A few weeks later, Spring Boot 3.5 → **4.x**. Boot 4 brings Spring Framework 7 and Jackson 3,
   the next major version of the JSON library, which lives in a new Java package (`tools.jackson`
   instead of `com.fasterxml.jackson`).

Boot 3.5 lost open-source support on 2026-06-30, so the time spent on step 1 is meant to be short.

remoting is the library the eight platform services use to call each other:

- Each service publishes a Java interface annotated `@RemoteEndpoint`, for example
  `AuthenticationService`, `AuditService` or `LdapServiceV2`.
- A caller gets a `java.lang.reflect.Proxy` of that interface: an object generated at runtime
  that implements the interface by handing every call to one handler method.
- That handler turns the call into a JSON message with `SimpleRemotingCodec` and sends it over
  RabbitMQ (AMQP).
- The receiving service turns the JSON back into a method call on its real implementation, and
  sends the return value back the same way.

An audit of the platform (`docs/proposals/updating/report.html` §5 in the platform repo) found that
remoting 0.2.2 compiles and runs on JDK 25 as it is, but has two problems under Boot 4:

- **A hard break.** `AmqpClientChannel` imports
  `org.springframework.boot.autoconfigure.jms.JmsProperties.DeliveryMode`, only to read the number
  `2` from it. `2` is AMQP's "persistent" delivery mode: the broker writes the message to disk.
  - The import is at `AmqpClientChannel.java:11` and the use at `:56`.
  - The class belongs to Boot's *auto-configuration*, the part of Boot that creates beans
    automatically depending on what is on the classpath.
  - Boot 4 moves JMS auto-configuration into a separate module. Every RPC client would then fail
    with `NoClassDefFoundError` the first time it sends a message.
- **A silent break.** `SimpleRemotingCodec` declares
  `@Autowired(required=false) private ObjectMapper objectMapper = new ObjectMapper();`
  (`SimpleRemotingCodec.java:30–31`). The `ObjectMapper` is Jackson 2's JSON converter.
  - Today Spring overwrites the field with the `ObjectMapper` bean that Boot 3 creates
    automatically, configured with Boot's defaults.
  - Boot 4 creates a Jackson **3** mapper instead and no longer creates a Jackson 2 bean. The field
    then silently keeps the plain `new ObjectMapper()`, whose defaults differ: dates become numbers,
    and an unknown field in a reply becomes an exception. `design.md` has the worked example.
  - Nothing fails at startup. The JSON on the wire just changes, and a service on Boot 3 and one
    on Boot 4 stop understanding each other.

The audit also found smaller problems worth fixing in the same release. The most serious is that a
call whose reply never arrives blocks one of the eight pool threads that carry remote calls
forever, even though the caller itself gives up after its timeout. After eight such calls, every
remote call from that service fails, including calls to healthy services. All of them are listed in `design.md`.

## What Changes

- **No Spring Boot classes in remoting's code.**
  - The JMS import is replaced by a constant `2`.
  - The two `@ConditionalOnMissingBean` annotations, also Boot auto-configuration classes, are
    dropped.
  - The `spring-boot-autoconfigure` dependency of `remoting-common` goes.
- **The wire format becomes a contract, owned by remoting.** The *wire format* is the exact JSON
  that travels between services.
  - The codec builds its own Jackson 2 mapper and configures it explicitly to produce exactly what
    Boot 3's mapper produces today.
  - It no longer takes whatever mapper the application happens to have.
  - *Golden files* pin the format down: JSON files recorded from today's 0.2.2 code running with
    Boot's real Jackson auto-configuration, which the tests compare the new code's output against.
    They are recorded once, before the codec is changed, by a throwaway generator.
- **Calls really time out.** When the caller's timeout expires, the waiting thread is interrupted
  and the pending reply slot is removed, so neither leaks.
- **Payloads are not logged by default.** The codec currently logs every request body at DEBUG.
  That includes JWTs (signed login tokens) passed to `AuthenticationService` and personal data.
  Bodies now go only to a dedicated logger at TRACE. This is the same rule dbutils 0.3.0 applies to
  SQL parameter values.
- Smaller fixes:
  - proxy target lookup through Spring's `AopProxyUtils`;
  - `String` comparison with `equals`;
  - nested interfaces no longer break the export;
  - the deprecated `StringUtils.isEmpty` is replaced;
  - `RemotingImportService` drops an empty `BeanPostProcessor` implementation and the
    `ApplicationContextAware` plumbing that only the commented-out code used;
  - the mapper's own `TypeFactory` is used instead of the static one;
  - `AmqpClientChannel` logs under its own name instead of `AmqpServerChannel`'s.
- Build:
  - `maven.compiler.release` **25**, instead of source/target 11;
  - remove the unused dependencies `org.reflections` and `classgraph`;
  - JUnit Jupiter instead of the unused JUnit 4. remoting has no tests today, only leftover
    fixtures; this change adds the first ones (wire format, timeouts, payload logging, export);
  - compiler and surefire plugin versions pinned;
  - the build is verified against **both** the Boot 3.5 and the Boot 4 dependency versions, and the
    example applications are run end to end against a throwaway RabbitMQ container on both lines,
    once with a 0.2.2 server talking to a 0.3.0 client.
- **Docs**: the README states the supported versions, the wire-format guarantee and the logging
  switch.
- Release **0.3.0**, which works unchanged with services on Boot 3.5 *and* on Boot 4. That way the
  platform can take 0.3.0 while it is still on 3.5, and the Boot 4 step does not need a library
  release.

Not changing:

- the RabbitMQ topology: exchange and queue names, durability, delivery mode;
- the `@RemoteEndpoint` / `@RemotingEndpoint` / `@RemoteImport` API;
- the message envelope, i.e. the fields of `RemotingRequest` and `RemotingResponse`;
- `Proxy.newProxyInstance` and `setAutomaticRecoveryEnabled(false)`. They work, and changing them
  is outside this change.

## Capabilities

### New Capabilities

- `wire-format`: the JSON format of RPC payloads is defined by remoting, does not depend on the
  host application's Jackson configuration, and is stable across releases.
- `platform-compatibility`: one remoting release runs on every Spring Boot line that the platform
  is currently migrating between, on Java 25, without referencing Boot's internals.
- `rpc-invocation`: how a remote call behaves when no reply arrives.
- `payload-logging`: what is logged about RPC payloads by default.

### Modified Capabilities

None. This repo has no capability specs yet.

## Impact

- Code:
  - `remoting-common`: codec, `ImportedServiceImpl`, `RemotingImportService`, `CglibHelper`,
    `ExportedService`, and the two `*Config` classes;
  - `remoting-impl-amqp`: `AmqpClientChannel`;
  - new tests and golden JSON files under `remoting-common/src/test`, one test under
    `remoting-impl-amqp/src/test`;
  - all poms;
  - the README.
- Consumers:
  - The 8 `mcg-automation` services bump `remoting.version` 0.2.2 → 0.3.0 in `services/pom.xml`.
    No code change is needed on their side; they define no codec, executor or mapper of their own.
    This was checked in the platform repo.
  - Services still on 0.2.2 can talk to services on 0.3.0, because the wire format is unchanged
    and a test proves it.
  - **Java 25 becomes the minimum.** Some private projects outside the platform also use remoting
    (under `gitlab.rand0m.me`). They either move to Java 25 or stay on 0.2.x.
- Releasing uses the existing `release.sh`.
