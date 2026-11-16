# Design: remoting 0.3.0 for JDK 25 and Spring Boot 4

## Context: how a remote call travels today

Take a caller in the crm service that wants to know who is logged in. It calls
`AuthenticationService.getUser(jwt)` on the interface that sso exports:

1. **Proxy.** The call hits a `java.lang.reflect.Proxy` created in `ImportedServiceImpl.java:97`.
   Its handler, `ImportedServiceImpl.invoke`, runs on the caller's thread.
2. **Encode.** `SimpleRemotingCodec.encodeRequest` builds a `RemotingRequest`: the method name, the
   argument values, and a `headers` map that *interceptors* can fill. An interceptor is a bean
   implementing `RemotingInterceptor`; the platform uses one to attach the caller's authentication.
   The request is turned into JSON with the codec's `objectMapper` field.
3. **Hand-off and wait.** The handler wraps the actual send in a `FutureTask` (a `Runnable` whose
   result another thread can wait for) and gives it to `SimpleRemotingExecutor`, a fixed pool of 8
   threads (`SimpleRemotingExecutor.java:16–17`, property `mcg.remoting.poolsize`). The caller's
   thread then waits on `t.get(timeout, SECONDS)` (`ImportedServiceImpl.java:73`). The timeout is
   10 s (`mcg.remoting.timeout`), or a method's `@RemotingTimeout`.
4. **Send.** On the pool thread, `AmqpClientChannel.invoke` publishes the bytes to RabbitMQ. It
   tags them with a fresh random *correlation id*, the AMQP field that lets a reply be matched to
   its request. It puts a `BlockingCell` (a one-shot box from the RabbitMQ client that a thread can
   wait on) into `requestMap` under that id, and waits on `bc.get()` (`AmqpClientChannel.java:60`).
5. **Serve.** sso's `AmqpServerChannel` receives the message and hands it to `ExportedService.handle`,
   which decodes it with *its own* codec, converts the arguments to the parameter types
   (`SimpleRemotingCodec.conform`), calls the real bean, and encodes the return value (or the
   exception, as a list of stack-trace strings) into a `RemotingResponse`.
6. **Reply.** The reply arrives on crm's response queue. The consumer looks up the `BlockingCell` by
   correlation id and puts the bytes in. The pool thread wakes up and decodes the reply into
   `Method.getGenericReturnType()` (`SimpleRemotingCodec.java:127`, using
   `TypeFactory.defaultInstance()`), and the caller's `t.get` returns.

Steps 2 and 5 happen in two different JVMs. **Both sides must agree on the JSON.** Today they agree
by accident: every service runs Boot 3, and Boot 3 auto-configures one Jackson 2 `ObjectMapper` bean
with the same defaults everywhere. `@Autowired(required=false)` on the codec's field picks that
bean up (`SimpleRemotingCodec.java:30–31`). The codec is created by a `@Bean` method
(`SimpleRemotingCodecConfig.java:12–16`), and Spring still processes `@Autowired` fields on objects
returned from `@Bean` methods.

The repository has **no tests today**. `remoting-common/src/test/java` holds only fixtures left over
from deleted tests: an endpoint pair `codec/AInterface` + `codec/AClass` (app `test`, name `a`) and
two interceptors, `channel/util/TestInterceptorA` (adds the header `A-Value: AAAA` before sending)
and `TestInterceptorB`. This change reuses them and adds the first real tests.

## The silent break, with values

The payloads carry `java.util.Date`, `Locale`, `Map<String,Object>`, `byte[]`, enums and DTOs (plain
data classes). They are in the signatures of `AuditService`, `MailService`, `BillingService`,
`AuthenticationService`, `ContextService`, `LdapServiceV2` and `ExportedPublicKeyProvider`.

| Value | Boot 3 auto-configured mapper (today) | plain `new ObjectMapper()` (Boot 4, nothing done) |
|---|---|---|
| `Date` 2026-11-16 12:00 UTC | `"2026-11-16T12:00:00.000+00:00"` | `1794830400000` |
| `java.time.Instant` | `"2026-11-16T12:00:00Z"` (JSR-310 module registered) | exception: Java 8 date/time types not supported without the module |
| reply DTO has a field the caller's class lacks | field ignored | `UnrecognizedPropertyException`, so the call fails |

Where these defaults come from matters for D2, because two different Spring classes contribute them.
Both were checked with `javap` against the jars in the local Maven repository
(`spring-boot-autoconfigure` 3.5.16, `spring-web` 6.2):

- Spring Framework's `Jackson2ObjectMapperBuilder` (in `spring-web`) disables
  `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` and `MapperFeature.DEFAULT_VIEW_INCLUSION`,
  and registers the "well-known" modules `Jdk8Module` (`Optional`), `JavaTimeModule` (`java.time`)
  and `ParameterNamesModule` (constructor parameters by name) if they are on the classpath.
- Spring Boot's `JacksonAutoConfiguration` (in `spring-boot-autoconfigure`) additionally disables
  `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` and `SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS`
  (its static `featureDefaults` map), and contributes its own
  `new ParameterNamesModule(JsonCreator.Mode.DEFAULT)` bean.

So the row "`Date` → ISO string" above is Boot's doing, not the builder's. A mapper built with
`Jackson2ObjectMapperBuilder` alone would still write `1794830400000`.

`MapperFeature.DEFAULT_VIEW_INCLUSION` only matters when a `@JsonView` is active, and remoting never
activates one, so it has no effect on the wire. It is set anyway, to match Boot exactly.

The platform services set no `spring.jackson.*` properties, define no `ObjectMapper` bean, no
`Jackson2ObjectMapperBuilderCustomizer` and no `@JsonComponent`. This was checked in the platform
repo (`gitlab-mcww-automation/automation/all`) on 2026-11-16; the only hit for "ObjectMapper config",
`sso/…/repos/util/LdapObjectMapperConfig`, defines an LDAP helper class that merely *uses* a private
`ObjectMapper`, not a bean of that type. So these defaults are the whole wire format today.

The failure would show up in production only. During a rolling Boot 4 deployment, sso (Boot 4)
would answer crm (Boot 3) with epoch-number dates. crm's reader still accepts those, but the next
DTO field added anywhere would make the old side throw.

## Decisions

### D1. remoting owns its mapper. It stays on Jackson 2 for now.

`SimpleRemotingCodec` builds its own mapper once, as a `private final` field, with exactly the
defaults listed above:

```java
private final ObjectMapper objectMapper = JsonMapper.builder()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        .addModule(new Jdk8Module())
        .addModule(new JavaTimeModule())
        .addModule(new ParameterNamesModule(JsonCreator.Mode.DEFAULT))
        .build();
```

(`JsonMapper` is Jackson 2's JSON-specific subclass of `ObjectMapper`, in
`com.fasterxml.jackson.databind.json`; its builder is the non-deprecated way to set features.)

- The `@Autowired(required = false)` goes, and so does the lazy `getObjectMapper()` null check
  (`SimpleRemotingCodec.java:49–54`); the field is final and never null.
- `remoting-common` depends on `com.fasterxml.jackson.datatype:jackson-datatype-jdk8`,
  `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` and
  `com.fasterxml.jackson.module:jackson-module-parameter-names`, compile scope, not optional, no
  explicit version.
- Versions come from the Spring Boot BOM, as today. A BOM ("bill of materials") is a POM that
  contains only dependency versions; remoting's root `pom.xml` imports Boot's. Both Boot lines still
  manage **Jackson 2**: Boot 3.5.16 at 2.21.4 (property `jackson-bom.version`), Boot 4.0.9 at 2.21.7
  (property `jackson-2-bom.version`, next to `jackson-bom.version` = 3.1.7 for Jackson 3, which lives
  under the separate group id `tools.jackson`). So the same code compiles and the same golden test
  (D2) runs on both, against the Jackson 2 version each line ships — which is what a consuming
  service will get, too.

Rejected alternatives:

- **Keep autowiring, and require each service to declare a Jackson 2 bean under Boot 4.** That
  moves the contract into eight places and fails silently when one of them forgets.
- **Port to Jackson 3 now.** Jackson 3's defaults differ again (for example, it writes dates as ISO
  strings by default but differs in other details). The JSON would have to be proven equal anyway,
  and the services would need Jackson 3 on the classpath while still on Boot 3.5. That is possible
  later, as its own change, once every service runs Boot 4.
- **Accept an optional `RemotingObjectMapperCustomizer` bean.** Not needed today, and it reopens the
  door to per-service drift.
- **Import `com.fasterxml.jackson:jackson-bom` in remoting's own `dependencyManagement`, before
  Boot's**, so that remoting's build tests the same Jackson 2 version on both lines. Not needed: both
  BOMs manage Jackson 2 already, and a consuming service's own BOM decides the version in production
  anyway. Testing against each line's own Jackson version is the more realistic check.

### D2. Golden files pin the format

A *golden file* is a recorded output that later code must reproduce exactly. They live in
`remoting-common/src/test/resources/wire/` and are written by the **unchanged 0.2.2 codec**, running
inside a Spring context that contains **Boot's real `JacksonAutoConfiguration`**. That is the mapper
production uses today, not D1's re-creation of it — otherwise the files would record the new code's
opinion of the format. It must be the auto-configuration and not `Jackson2ObjectMapperBuilder` on
its own: as shown above, the builder alone writes dates as numbers, and the files would then
"prove" exactly the broken format this change exists to prevent.

The working tree's code at the start of this change *is* 0.2.2: `git diff 0.2.2 HEAD` only touches
version numbers in poms.

**Fixtures.** One test source file, `remoting-common/src/test/java/com/mcg/tools/remoting/common/wire/WireFixtures.java`,
holds everything both the generator and the permanent test use:

- `@RemoteEndpoint(app = "test", name = "wire") interface WireService` with
  - `Sample all(Date date, Instant instant, Locale locale, byte[] bytes, Color color, Sample sample, Map<String,Object> map)`
  - `void fire(String text, Integer number)`
- `enum Color { RED, GREEN }`
- `class Sample` (public no-arg constructor, getters/setters, `equals`/`hashCode`):
  `String name`, `Date when`, `Instant at`, `Color color`, `List<Sample> children`,
  `Map<String,String> tags`
- fixed values, so that every run produces the same bytes:
  - `DATE = new Date(1794830400000L)` (2026-11-16T12:00:00Z), `INSTANT = DATE.toInstant()`;
  - `Locale.GERMANY`; bytes `{1, 2, 3, (byte) 255}`; `Color.GREEN`;
  - a `Sample` named `"parent"` with one child `"child"`, both with `DATE`/`INSTANT`, tags `{"k": "v"}`;
  - the map `{"s": "x", "n": 42, "b": true, "l": [1, "two"], "nested": {"k": "v"}}`. Its values are
    JSON-native on purpose: a `Map<String,Object>` comes back with untyped values (a `Date` inside it
    would come back as a `String`), so only JSON-native values can be compared after decoding.
  - an exception with a **fixed** stack trace: `new IllegalStateException("boom")` with
    `setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.Remote", "call", "Remote.java", 42) })`.
    `RemotingResponse.exception` writes class, method, file and *line number* of every frame, so a
    real stack trace would differ between the generator and the test.

**The files** (one JSON document each, written exactly as the codec returns the bytes):

| File | Produced by |
|---|---|
| `request-all.json` | `encodeRequest(all, <all fixed values>)` |
| `request-nulls.json` | `encodeRequest(fire, {null, null})` |
| `request-headers.json` | `encodeRequest(fire, {"hello", 7})` in a context that also has `TestInterceptorA` |
| `response-sample.json` | `createResponse(<the Sample>, true, null)` |
| `response-void.json` | `createResponse(null, true, null)` |
| `response-error.json` | `createResponse(null, false, <the fixed exception>)` |

**The generator** is a **one-off**: `wire/GoldenFileGenerator.java` in the same test package. Its
name deliberately does not match surefire's default test patterns (`*Test`, `Test*`, `*Tests`,
`*TestCase`), so the normal build never runs it, without any pom configuration. It is run once, by
name:

```
mvn -pl remoting-common -am test -Dtest=GoldenFileGenerator -Dsurefire.failIfNoSpecifiedTests=false
```

It starts its context the way a Boot application would, but with nothing else auto-configured:

```java
@Configuration
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import(SimpleRemotingCodecConfig.class)
static class GeneratorConfig {}

new SpringApplicationBuilder(GeneratorConfig.class).web(WebApplicationType.NONE).run();
```

The codec bean then gets Boot's `ObjectMapper` autowired, exactly as in a 0.2.2 service. The
generator asserts that it did (reading the codec's private `objectMapper` field reflectively and
comparing it with the context's `ObjectMapper` bean), and that `request-all.json` contains
`"2026-11-16T12:00:00.000+00:00"` — a cheap guard against the epoch-number trap.

It needs `spring-web` (test scope, for `Jackson2ObjectMapperBuilder`, which the auto-configuration
requires) and `spring-boot-autoconfigure`, which `remoting-common` still depends on at that point.
It cannot stay in the build: in Boot 4, Jackson 2 auto-configuration is no longer where Boot 3 has
it, and `spring-boot-autoconfigure` leaves `remoting-common` (D3). So the finalize step deletes the
generator and the `spring-web` test dependency. The JSON files stay.

**Ordering instead of committing.** The files must be written before anyone touches
`SimpleRemotingCodec`. That is guaranteed by running the generator as its own step right after the
build baseline and before the codec step. Nothing is committed by the implementing agents; the files
are ordinary new files in the working tree.

**The permanent test**, `wire/WireFormatTest.java`, needs only the codec, the fixtures and the files.
It builds the codec through `new AnnotationConfigApplicationContext(SimpleRemotingCodecConfig.class)`
(plus `TestInterceptorA.class` for the headers case), because the codec reads its interceptors from
the `ApplicationContext`. It asserts:

- **encode:** the 0.3.0 codec turns the same fixture values into the same JSON. "Same" means equal as
  JSON trees: `assertEquals(mapper.readTree(expected), mapper.readTree(actual))`. Jackson's
  `ObjectNode.equals` compares fields as a map, so key order does not matter, but types and values
  do (`1` and `1.0` differ).
- **decode, server side:** `decodeRequest` of each request file, then `codec.invoke(WireService.class, request, recorder)`
  with a recording `WireService` implementation, delivers arguments equal to the fixture values
  (`assertArrayEquals` for `byte[]`). This exercises the argument conversion (`conform`) too.
  The headers file decodes to `headers == {"A-Value": "AAAA"}`.
- **decode, client side:** `decodeResponse(file, method.getGenericReturnType())` returns the fixture
  `Sample`; the error file yields `success == false` and the expected stack-trace list.
- **unknown properties:** `response-sample.json` with an extra `"unexpected": 1` added to
  `returnValue` (in memory) still decodes to the same `Sample`.
- **host mapper ignored:** in a context that also defines an `ObjectMapper` bean with
  `WRITE_DATES_AS_TIMESTAMPS` and `FAIL_ON_UNKNOWN_PROPERTIES` **enabled**, the codec still produces
  `request-all.json`.

Because the build runs on both Boot BOMs (D4), this one test also proves that a Boot 3.5 service and
a Boot 4 service produce the same JSON.

Rejected alternatives:

- **A JSON-comparison library (JSONAssert, AssertJ-JSON).** Jackson's own tree equality already does
  order-independent comparison; one dependency fewer.
- **Generate with `Jackson2ObjectMapperBuilder` directly.** Records epoch dates; see above.
- **Commit the files in a separate commit before the codec is changed.** Implementing agents do not
  commit; task ordering gives the same guarantee.
- **Compare one file by hand with a real message from the platform's dev-stack RabbitMQ.** The
  generator now uses Boot's own auto-configuration, so the file *is* what production writes;
  browsing a shared broker's queues is a write-adjacent action on a shared system. Replaced by the
  mixed-version end-to-end run below (D4, "Verification").

### D3. No Spring Boot classes in library code

- `AmqpClientChannel` uses `private static final int PERSISTENT_DELIVERY_MODE = 2;` instead of
  `DeliveryMode.PERSISTENT.getValue()` (import at `AmqpClientChannel.java:11`, use at `:56`).
  - Rejected: Spring AMQP's `MessageProperties.DEFAULT_DELIVERY_MODE`. It is a
    `MessageDeliveryMode` enum, not the `int` the RabbitMQ client wants, so it would need
    `MessageDeliveryMode.toInt(...)`.
  - Rejected: the RabbitMQ client's `MessageProperties.PERSISTENT_BASIC`. It is a whole property
    set, and it shares a class name with Spring's, which invites confusion.
- `SimpleRemotingCodecConfig` and `SimpleRemotingExecutorConfig` drop `@ConditionalOnMissingBean`
  (`SimpleRemotingCodecConfig.java:3, 13`, `SimpleRemotingExecutorConfig.java:3, 13`) and become
  plain `@Configuration` / `@Bean`. `@ConditionalOnMissingBean` is a Boot annotation that skips a
  `@Bean` method when a bean of the same type already exists. The question was whether a service
  ever supplies its own codec or executor. The evidence from the platform repo:
  - every service scans `com.mcg` (`@ComponentScan(basePackages = "com.mcg")`), which includes
    `com.mcg.tools.remoting`, so these configs are found by scanning;
  - no service implements `RemotingCodec` or `RemotingExecutor`.

  So the condition protects nothing. On a scanned (not auto-configured) class it is also unreliable:
  it only sees beans registered *before* it, and that order depends on scanning. A consumer that does
  want its own codec now marks it `@Primary`; the README says so.
  - Rejected: registering the configs through Boot's `AutoConfiguration.imports`. Because of the
    `com.mcg` scan they would be registered twice, and it would keep a Boot dependency.
- `remoting-common` drops its `spring-boot-autoconfigure` dependency (after the golden files are
  generated, D2).
  - `remoting-impl-amqp` keeps `spring-boot-starter-amqp`, because it relies on the
    `CachingConnectionFactory` bean that Boot creates in the consuming service. It references no Boot
    class in its code.
- `remoting-examples` are Boot applications (`@SpringBootApplication`) and are exempt; they are not
  library code. Their `PrintServiceConfig` / `RandomServiceConfig` keep their own
  `@ConditionalOnMissingBean`, and their poms keep `spring-boot-autoconfigure`.

### D4. One release for Boot 3.5 and Boot 4; Java 25

- The root `pom.xml` keeps importing `org.springframework.boot:spring-boot-dependencies:${springboot.version}`.
  The default becomes **3.5.16** (the latest 3.5.x, present in the local Maven repository).
- `mvn -Dspringboot.version=4.0.9 verify` must pass as well; 4.0.9 is the latest 4.0.x and also
  resolved for dbutils. The README documents both commands. The repo has no CI.
- The code may use only APIs that exist in both lines. The APIs actually used are
  `CachingConnectionFactory`, the RabbitMQ `Channel` and `BlockingCell`, `AopProxyUtils`,
  `StringUtils`, `@Configuration`/`@Bean`/`@Value`, `ApplicationContext.getBeansOfType` and Jackson 2.
  All exist in Spring Framework 6.2 (Boot 3.5) and 7.0 (Boot 4), Spring AMQP 3.2 and 4.0, and
  amqp-client 5.25 and 5.27.
- `maven.compiler.release` is **25**, the same as dbutils 0.3.0, replacing
  `maven.compiler.source`/`target` 11. The platform runs on 25, and 0.3.0 is the release where
  consumers have to look at their setup anyway.
  - Rejected: 17. It would keep the jar usable by the private projects that still use remoting on
    older JDKs, but those can stay on 0.2.x, and one floor for both libraries is simpler to state.
- Tests use JUnit Jupiter (`org.junit.jupiter:junit-jupiter`, test scope, version from the BOM:
  5.12.2 on Boot 3.5.16, 6.0.3 on Boot 4.0.9), replacing the unused `junit:junit` 4. Tests use only
  API common to both.
- Plugins are pinned in the root `pluginManagement`, to versions in the local Maven repository that
  are also what both Boot BOMs name: `maven-compiler-plugin` 3.14.1, `maven-surefire-plugin` 3.5.6.
- `org.reflections` 0.9.11 and `io.github.classgraph` 4.8.47 are removed: no code imports them.
- `project.build.sourceEncoding` is set to UTF-8 (missing today).
- The release version: `mvn versions:set -DnewVersion=0.3.0-SNAPSHOT`; `release.sh` strips
  `-SNAPSHOT`. `versions:set` also rewrites the hard-coded `0.2.3-SNAPSHOT` inter-module dependency
  versions in the module poms, as it has for every earlier release.

**Order of the pom edits.** Edits that *add* come first (build-baseline step), edits that *remove*
something code still uses come last (finalize step), so the build is green after every step:
`spring-boot-autoconfigure` and the test-scope `spring-web` stay in `remoting-common/pom.xml` until
the generator is deleted and the codec configs no longer import `ConditionalOnMissingBean`.

**Verification** (finalize step), all on JDK 25:

1. `mvn clean verify` with the default BOM and with `-Dspringboot.version=4.0.9`; same test counts.
2. An end-to-end run of the example applications against a **throwaway RabbitMQ container**, never
   the platform's dev stack:
   `docker run -d --rm --name remoting-e2e-rabbit -p 35672:5672 -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin rabbitmq:4.3.0`
   (that image is present locally). The examples read `RABBITMQ_HOSTS` / `RABBITMQ_PORT` from the
   environment (`remoting-examples/*/application.yml`); `--server.port=0` avoids port clashes. Each
   app runs from its module classpath
   (`mvn -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/cp.txt`, then
   `java -cp target/classes:$(cat target/cp.txt) <main class>`); nothing is installed into `~/.m2`.
   Runs:
   - 0.3.0 server + 0.3.0 client, Boot 3.5.16;
   - the same on Boot 4.0.9;
   - **mixed:** a 0.2.2 server (extracted with `git archive 0.2.2 | tar -x -C <scratch dir>`, built
     there, Boot 3.4.4) + the 0.3.0 client on Boot 4.0.9. This sends 0.3.0 requests to 0.2.2 and
     0.2.2 replies to 0.3.0.

   Success: the client (`ClientService`) logs `my random number is:`, `average at 1000`,
   `short: wait 5 seconds ... ERROR (as expected)` and `short: wait one second ... OK!`; after the
   expected timeout, the late reply produces the `unknown correlation id` warning (D5).

### D5. Calls time out and release their resources

Today, if sso never answers:

- the caller's `t.get(10, SECONDS)` throws `TimeoutException` after 10 s, so the caller is fine;
- but the pool thread is still inside `bc.get()`, which waits forever, and nobody cancels the
  `FutureTask`;
- the `BlockingCell` also stays in `requestMap` (`AmqpClientChannel.java:62` only removes it on
  success).

Eight such calls, and all eight pool threads are stuck. Every later remote call from that service,
even one whose server answers instantly, then waits in the pool's queue until its own timeout and
fails. A worked example with a pool of 8: calls 1–8 to a dead service each time out after 10 s, as
expected; call 9 goes to a healthy service, but no thread ever picks it up, so it also "times out"
after 10 s — and so does every call after it, until the service is restarted.

Fix, without changing the `ClientChannel` interface:

1. `ImportedServiceImpl.invoke`: on `TimeoutException` from `t.get`, call `t.cancel(true)`, then
   rethrow. `cancel(true)` interrupts the pool thread (or, if the task is still queued, makes the
   pool skip it), and `BlockingCell.get()` responds to interruption by throwing
   `InterruptedException`.
2. `AmqpClientChannel.invoke`: `requestMap.remove(correlationId)` moves into a `finally` block, so
   the slot is freed on success, timeout and error alike. An `InterruptedException` is rethrown
   without the `error in client` ERROR line, since it is the expected end of a timed-out call.
3. The timeout is logged at WARN, as today, now with interface and method name in addition to the
   timeout and elapsed time, and saying that the call was cancelled.

What the caller sees does not change: the proxy rethrows the `TimeoutException`, and because
interface methods do not declare it, `java.lang.reflect.Proxy` wraps it in an
`UndeclaredThrowableException` whose cause is the `TimeoutException`.

If a reply arrives after the cancel, its correlation id is no longer in `requestMap`, and the
existing `unknown correlation id` warning (`AmqpClientChannel.java:125`) fires. That is correct.

**How it is tested without a broker.** A small test configuration
(`remoting-common/src/test/java/com/mcg/tools/remoting/common/loop/LoopbackConfig.java`) puts the
real codec, executor and `RemotingImportService` into an `AnnotationConfigApplicationContext`, plus
a stub `ClientChannelProvider`:

- for service name `a` (the existing `AInterface`/`AClass` fixture) it returns a channel that hands
  the bytes straight to an `ExportedService` wrapping `new AClass()` — a full round trip in memory;
- for service name `lost` (a test interface `LostService` with `@RemotingTimeout(1)`) it returns a
  channel that blocks on a latch that is never released and counts how often it was interrupted.

`TimeoutTest` then starts 8 `LostService` calls in parallel (the pool's default size), checks that
each fails after about 1 s with a `TimeoutException` cause and that all 8 blocked channels were
interrupted, and then makes one `AInterface.add(2, 3)` call, which must return 5 promptly. On 0.2.2
that last call times out. A separate `AmqpClientChannelTest` in `remoting-impl-amqp` gives
`AmqpClientChannel.start` a `java.lang.reflect.Proxy` stub of the RabbitMQ `Connection`/`Channel`
(every method returns `null`, `createChannel` returns the channel stub), runs `invoke` on a thread,
interrupts it, and checks that `requestMap` is empty afterwards.

Rejected alternative: **`bc.uninterruptibleGet(timeout)` inside the channel.** The channel does not
know the timeout. It would have to be passed through `ClientChannel.invoke`, which changes an
interface, for the same effect.

### D6. Payloads are not logged by default

`SimpleRemotingCodec` logs the full request JSON at DEBUG when encoding
(`SimpleRemotingCodec.java:142–144`) and the raw body when decoding (`:174–176`). Those bodies contain:

- JWTs, as in `AuthenticationService.getUser(jwt)`, which are valid credentials until they expire;
- personal data, such as LDAP entries and mail recipients.

DEBUG on `com.mcg` is a common thing to switch on while hunting a bug in production.

New rule, the same as dbutils 0.3.0 applies to SQL parameter values:

- class loggers log interface, method name, argument count and duration, never values;
- bodies go only to the logger named `com.mcg.tools.remoting.payload`, at TRACE, in four places of
  `SimpleRemotingCodec`: the request in `encodeRequest` and `decodeRequest`, the response in
  `createResponse` and `decodeResponse`. Each is guarded by `isTraceEnabled()`, so the JSON string is
  never built otherwise.
- `ImportedServiceImpl` replaces its three DEBUG lines (`:69`, `:71`, `:74`; the one at `:71` claims
  "response received" before the reply can have arrived) with one DEBUG line after the reply, for example
  `AInterface.add (2 args) took 3 ms`.

Concretely, with `logging.level.com.mcg=DEBUG`, a call to `AuthenticationService.getUser("eyJhbGciOi…")`
logs `AuthenticationService.getUser (1 args) took 12 ms` and nothing containing `eyJ`. Only
`logging.level.com.mcg.tools.remoting.payload=TRACE` brings back the bodies.

**Known limit:** exception messages are logged as they are — the remote stack trace that
`ImportedServiceImpl` logs at ERROR (`:77–79`) and the `failed to invoke exported method` WARN in
`ExportedService` include whatever message the remote exception carried. remoting cannot know what a
service puts into its exception messages.

**How it is tested.** remoting-common's test classpath has no SLF4J or Log4j, so commons-logging
(through `spring-jcl` on Spring 6, Apache commons-logging 1.3 on Spring 7) writes to
`java.util.logging`, where DEBUG is `FINE` and TRACE is `FINEST`. `PayloadLoggingTest` attaches a
`java.util.logging.Handler` to the `com.mcg` logger (keeping a strong reference to it), sets it to
`FINE`, makes a loopback call (`LoopbackConfig`) with a distinctive value such as `jane@example.org`,
and asserts that no record contains it while some record names `AInterface` and the method. With
`com.mcg.tools.remoting.payload` at `FINEST`, the value appears on that logger. Levels and handlers
are restored afterwards.

Rejected: **masking JWT-looking strings.** The library cannot know which arguments are sensitive.

### D7. Smaller fixes, all from the audit

| Where | Today | Fix |
|---|---|---|
| `SimpleRemotingCodec.java:127` | `TypeFactory.defaultInstance()`, a static, global type factory that Jackson 3 removes | `objectMapper.getTypeFactory()` |
| `RemotingImportService.java:18, 22, 31–78` | implements `BeanPostProcessor, Ordered, ApplicationContextAware`. The post-processor body is commented out; the stored `applicationContext` is only used by that commented-out code. A `BeanPostProcessor` is created before all other beans, which drags this one in early for nothing. | drop all three interfaces, the field and the three methods; the class keeps `@Service @Configuration` and its `importService` `@Bean` method |
| `util/CglibHelper.java:14–50` | finds Spring proxies by `getClass().getName().contains("cglib")`, then calls a method named `getTargetSource` reflectively | `Object t = AopProxyUtils.getSingletonTarget(proxied); return t != null ? t : proxied;` — same public class and method, so its two callers do not change |
| `ExportedService.java:70` | `Class.forName(rec.value().getCanonicalName())`. For a nested interface `Outer.Inner` is the canonical name, but the class is called `Outer$Inner`, so this throws `ClassNotFoundException` | `Class<?> c = rec.value();` |
| `ExportedService.java:86` | `ci.getName() != c.getName()` compares object identity, not text | `!ci.equals(c)` |
| `ExportedService.java:76, 81` | Spring's `StringUtils.isEmpty(Object)`, deprecated | `!StringUtils.hasLength(...)` |
| `AmqpClientChannel.java:28` | logger created for `AmqpServerChannel.class`, so client messages appear under the server's category | `AmqpClientChannel.class` |
| `pom.xml:17–21, 32–41, 57–61` | source/target 11, unused `org.reflections` and `classgraph`, JUnit 4 | release 25, remove both, JUnit Jupiter (D4) |

A test (`remoting-common/src/test/java/com/mcg/tools/remoting/common/export/ExportTest.java`) covers
the export fixes: a context with `SimpleRemotingCodecConfig`, `RemotingExportServiceConfig`, a stub
`ServerChannelProvider` that records `(app, service)`, and a `@RemotingEndpoint` bean implementing a
**nested** `@RemoteEndpoint` interface → the stub records that interface's app and name. A second
case registers the endpoint as a CGLIB proxy (`ProxyFactory` with `setProxyTargetClass(true)`) and
expects the same.

## Risks

- **The golden files are generated wrong**, and the change then "proves" a broken format.
  Mitigation: D2's generator runs Boot's own auto-configuration on the 0.2.2 code, asserts that the
  codec really received Boot's mapper and that dates are ISO strings; the mixed 0.2.2/0.3.0
  end-to-end run checks the envelope against a real 0.2.2 build.
- **The timeout fix changes behaviour.** Pool threads now actually stop at the timeout. Any code
  that relied on a late reply still being processed after the caller gave up loses that; nothing
  is known to.
- **Dropping `@ConditionalOnMissingBean`** turns a codec or executor bean of a consumer into a
  `NoUniqueBeanDefinitionException` at startup instead of a silent override, unless that bean is
  marked `@Primary`. No platform service has one (checked). The private projects have not been
  checked.
- **The Java 25 floor** breaks the private projects on older JDKs if they upgrade.
- **A consumer whose Jackson 2 version differs** (its BOM decides) could in theory serialise
  differently. Both Boot lines are tested (2.21.4 and 2.21.7); a patch-level difference that changes
  the format would be a Jackson bug.

## Migration and rollout

1. Release 0.3.0 with `release.sh`. Note that it runs `git add .` — the uncommitted `.classpath`
   changes, `.claude/` and `openspec/` in the working tree would end up in the release commit unless
   dealt with first.
2. The platform bumps `remoting.version` (`services/pom.xml:41`) while still on Boot 3.5.
3. Mixed 0.2.2 and 0.3.0 services interoperate thanks to D2, so a rolling deployment is safe.
4. The Boot 4 step in the platform needs no further remoting release.
