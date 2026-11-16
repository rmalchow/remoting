# Tasks: remoting 0.3.0 (JDK 25 / Spring Boot 4)

> **For the implementing agents.** Read `proposal.md` and `design.md` first; decision numbers
> (D1…D7) refer to `design.md`. Each task names the unit it belongs to, e.g. `[A1]`.
>
> - **Do not commit anything**, and do not run git commands that discard or hide working-tree changes
>   (`checkout`, `restore`, `stash`, `reset`, `clean`). Read-only git (`status`, `diff`, `log`,
>   `archive`) is fine. Leave your changes in the working tree and report the files you touched.
> - The uncommitted `.classpath` changes, `.claude/`, `openspec/` (apart from ticking boxes in this
>   file, if the orchestrator asks), `pom.xml.versionsBackup` and the stale `bin/` tree are not part of
>   this change. Leave them alone, and keep `bin/` and `target/` out of every grep.
> - Do not run `release.sh`, `mvn install` or `mvn deploy`, and do not push.
> - Do not change the RabbitMQ topology (exchange/queue names, durability, delivery mode value), the
>   public annotations, `RemotingRequest`/`RemotingResponse`, or the `ClientChannel` interface.
> - JDKs on this machine: 25 (default) and 21 under `/usr/lib/jvm`. Maven 3.9.11. Docker works; use
>   only throwaway containers, never the platform's dev-stack services.
> - Every unit ends with `mvn verify` (JDK 25, default BOM) green from the repo root, unless the task
>   says otherwise.
> - Paths: `common/` = `remoting-common/src/main/java/com/mcg/tools/remoting/common/`,
>   `common-test/` = `remoting-common/src/test/java/com/mcg/tools/remoting/common/`,
>   `amqp/` = `remoting-impl-amqp/src/main/java/com/mcg/tools/remoting/impl/amqp/`.

## Work units

Tasks touching the same file are in the same unit. **All pom edits belong to unit A** (A1 at the
start, A2 at the end; design D4 "Order of the pom edits").

| Unit | Tasks | Files | Depends on | Model |
|---|---|---|---|---|
| A1 Build foundation | 1.1–1.4 | `pom.xml`, `remoting-common/pom.xml` | – | sonnet |
| G Golden files | 2.1–2.3 | new `common-test/wire/WireFixtures.java`, `common-test/wire/GoldenFileGenerator.java`, `remoting-common/src/test/resources/wire/*.json` | A1 | opus |
| B Codec | 3.1–3.4 | `common/codec/SimpleRemotingCodec.java`, `common/codec/SimpleRemotingCodecConfig.java`, `common/SimpleRemotingExecutorConfig.java`; new `common-test/wire/WireFormatTest.java` | G (**must not start before G is done**) | opus |
| C Call path | 4.1–4.5 | `common/ImportedServiceImpl.java`, `amqp/AmqpClientChannel.java`; new `common-test/loop/LoopbackConfig.java`, `common-test/loop/LostService.java`, `common-test/loop/TimeoutTest.java`, `common-test/loop/PayloadLoggingTest.java`, `remoting-impl-amqp/src/test/java/com/mcg/tools/remoting/impl/amqp/AmqpClientChannelTest.java` | B | opus |
| D Export fixes | 5.1–5.3 | `common/RemotingImportService.java`, `common/util/CglibHelper.java`, `common/ExportedService.java`; new `common-test/export/ExportTest.java` (+ nested fixture types inside it) | A1 | sonnet |
| E README | 6.1 | `README.md` | – (content comes from design.md) | sonnet |
| A2 Finalize | 7.1–7.4, 8.1–8.2 | all `pom.xml` files (versions only, plus `remoting-common/pom.xml` removals); delete `common-test/wire/GoldenFileGenerator.java` | B, C, D (and E for the report) | sonnet |

**Order.** Maven compiles whole modules, so units sharing the working tree run one after another:
A1 → G → B → C → D → A2. E only touches `README.md` and can run alongside any of them. G must
finish before B starts: the golden files have to be written by the unchanged 0.2.2 codec.

## 1. Build baseline (D4) — unit A1

- [x] 1.1 [A1] Before changing anything: `mvn verify` on JDK 25 from the repo root. Record the result (there are no tests today; expect `Tests run` lines to be absent or zero).
- [x] 1.2 [A1] Root `pom.xml`:
  - properties: remove `maven.compiler.source`, `maven.compiler.target`, `org.reflections.version`, `classgraph.version`; add `maven.compiler.release` 25 and `project.build.sourceEncoding` UTF-8; `springboot.version` 3.5.16; keep `maven.compiler.debug`;
  - `dependencyManagement`: keep the `spring-boot-dependencies` import; remove the `classgraph` and `reflections` entries;
  - `dependencies`: replace `junit:junit` with `org.junit.jupiter:junit-jupiter` (test scope, no version);
  - new `build/pluginManagement`: `maven-compiler-plugin` 3.14.1, `maven-surefire-plugin` 3.5.6 (no further configuration).
- [x] 1.3 [A1] `remoting-common/pom.xml` — additions, plus the removals no code needs:
  - remove `io.github.classgraph:classgraph` and `junit:junit` (Jupiter now comes from the root);
  - add `com.fasterxml.jackson.datatype:jackson-datatype-jdk8`, `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`, `com.fasterxml.jackson.module:jackson-module-parameter-names` (compile, no version);
  - add `org.springframework:spring-web` (test scope, no version) — needed by the golden generator (D2), removed again in 7.1;
  - leave `spring-boot-autoconfigure` in place (removed in 7.1).
- [x] 1.4 [A1] `mvn verify` green on JDK 25. Report the plugin versions and `mvn -pl remoting-common dependency:tree -Dscope=test | grep -iE 'slf4j|log4j|logback'` (expected: nothing; D6's test relies on `java.util.logging`).

## 2. Record today's wire format (D2) — unit G

Do not edit anything under `src/main`.

- [x] 2.1 [G] `common-test/wire/WireFixtures.java`: `WireService`, `Color`, `Sample` and the fixed values exactly as listed in design D2 "Fixtures" (including the exception with the fixed one-frame stack trace). Public static members so tests in the same package can use them.
- [x] 2.2 [G] `common-test/wire/GoldenFileGenerator.java` (a JUnit Jupiter class with one `@Test` method; the class name must not end in `Test`/`Tests`/`TestCase` or start with `Test`): start the context as in design D2 (`@ImportAutoConfiguration(JacksonAutoConfiguration.class)`, `@Import(SimpleRemotingCodecConfig.class)`, `SpringApplicationBuilder…web(WebApplicationType.NONE)`), plus a second context that also contains `TestInterceptorA` (`common-test/channel/util/`) for `request-headers.json`. Write the six files from the D2 table to `remoting-common/src/test/resources/wire/` (resolve the path from the module base dir, e.g. `Paths.get("src/test/resources/wire")`, since surefire runs in the module directory), byte for byte as the codec returns them. Assert, in the generator: the codec's private `objectMapper` field (read reflectively) is the context's `ObjectMapper` bean; `request-all.json` contains `"2026-11-16T12:00:00.000+00:00"` and `"2026-11-16T12:00:00Z"`.
- [x] 2.3 [G] Run it once: `mvn -pl remoting-common -am test -Dtest=GoldenFileGenerator -Dsurefire.failIfNoSpecifiedTests=false`. Check the six files exist and look plausible (ISO dates, `"de_DE"`-style locale, base64 bytes, `"GREEN"`, `headers` containing `A-Value` only in `request-headers.json`, the one-frame stack trace in `response-error.json`). Paste `request-all.json` and `response-error.json` into the report. Then `mvn verify` (the generator is not picked up by the default run).

## 3. Wire format owned by remoting (D1, D3, D6, D7) — unit B

- [x] 3.1 [B] `common/codec/SimpleRemotingCodec.java`:
  - D1: replace the `@Autowired(required = false) ObjectMapper` field and `getObjectMapper()` with the `private final` `JsonMapper` from design D1; every use goes through the field;
  - D7: `TypeFactory.defaultInstance()` → `objectMapper.getTypeFactory()`;
  - D6: delete the body logging at lines 142–144 and 174–176; add a `private static final Log payloadLog = LogFactory.getLog("com.mcg.tools.remoting.payload")` and log, each guarded by `payloadLog.isTraceEnabled()`, the request JSON in `encodeRequest` and `decodeRequest` and the response JSON in `createResponse` and `decodeResponse`. Do not add any other log line containing values.
  - Do not change method signatures, `conform`'s behaviour, the interceptor calls or the exception wrapping.
- [x] 3.2 [B] D3: `common/codec/SimpleRemotingCodecConfig.java` and `common/SimpleRemotingExecutorConfig.java`: remove `@ConditionalOnMissingBean` and its import.
- [x] 3.3 [B] `common-test/wire/WireFormatTest.java`: every assertion listed in design D2 "The permanent test" (encode ×6, server-side decode of the three request files via a recording `WireService`, client-side decode of the three response files, unknown property, host mapper ignored). Compare with `JsonNode` equality; do not add a JSON-assert library.
- [x] 3.4 [B] `mvn verify` green. `WireFormatTest` passes without any change to the JSON files. If a file has to change to make it pass, stop and report — that is a wire-format change.

## 4. Timeouts and payload logging (D5, D6, D3) — unit C

- [x] 4.1 [C] `amqp/AmqpClientChannel.java`:
  - D3: `private static final int PERSISTENT_DELIVERY_MODE = 2;` replaces `DeliveryMode.PERSISTENT.getValue()`; remove the `JmsProperties` import;
  - D5: `requestMap.remove(correlationId)` in a `finally`; an `InterruptedException` is rethrown before the generic `catch (Exception)` so it does not log `error in client`;
  - D7: the logger uses `AmqpClientChannel.class`.
- [x] 4.2 [C] `common/ImportedServiceImpl.java`:
  - D5: wrap `t.get(timeout, TimeUnit.SECONDS)` so that on `TimeoutException` it calls `t.cancel(true)` and rethrows; the WARN in the outer catch names `serviceInterface.getSimpleName()`, the method, the timeout, the elapsed time, and says `cancelled` for a timeout;
  - D6: replace the DEBUG lines at 69, 71 and 74 with one DEBUG line after the reply: `<Interface>.<method> (<n> args) took <ms> ms` (`n` = `args == null ? 0 : args.length`). No values.
- [x] 4.3 [C] Test harness, design D5 "How it is tested": `common-test/loop/LostService.java` (`@RemoteEndpoint(app = "test", name = "lost")`, one method with `@RemotingTimeout(1)`), `common-test/loop/LoopbackConfig.java` (imports `SimpleRemotingCodecConfig`, `SimpleRemotingExecutorConfig`, `RemotingImportService`, `RemotingExportServiceConfig`; stub `ClientChannelProvider` — name `a` → loopback into an `ExportedService` for `new AClass()`, name `lost` → blocks on a never-released latch and counts interrupts; stub `ServerChannelProvider` returning `null`). Proxies come from `ctx.getBean(RemotingImportService.class).importService(X.class).getProxy()`, as in the examples.
- [x] 4.4 [C] `common-test/loop/TimeoutTest.java`: 8 `LostService` calls in parallel each fail within ~1–3 s with an `UndeclaredThrowableException` whose cause is a `TimeoutException`; all 8 stub channels were interrupted (wait up to a few seconds for the count); then `AInterface.add(2, 3)` returns 5 within 2 s. `common-test/loop/PayloadLoggingTest.java`: design D6 "How it is tested" — with `com.mcg` at `FINE`, a loopback `AInterface.echoList(List.of("jane@example.org"))` returns the list, no log record contains `jane@example.org`, and a record names `AInterface` and `echoList`; with `com.mcg.tools.remoting.payload` at `FINEST`, a record on that logger contains it. Restore levels/handlers in `@AfterEach`.
- [x] 4.5 [C] `remoting-impl-amqp/src/test/java/com/mcg/tools/remoting/impl/amqp/AmqpClientChannelTest.java`: design D5 — `java.lang.reflect.Proxy` stubs for `com.rabbitmq.client.Connection` (`createChannel` → channel stub, everything else `null`) and `Channel` (everything `null`); `start(connection)`; `invoke(new byte[]{1})` on a thread; wait until `requestMap` (read reflectively) has one entry; interrupt; join; assert the thread ended with `InterruptedException` and `requestMap` is empty. Then `mvn verify` green.

## 5. Export fixes (D7) — unit D

- [x] 5.1 [D] `common/RemotingImportService.java`: remove `BeanPostProcessor`, `Ordered`, `ApplicationContextAware`, the `applicationContext` field, `setApplicationContext`, `getOrder`, `postProcessBeforeInitialization` and the unused imports. Keep `@Service @Configuration` and `importService` unchanged.
- [x] 5.2 [D] `common/util/CglibHelper.java`: body of `getTargetObject()` becomes the `AopProxyUtils.getSingletonTarget` line from design D7; delete the private helpers. `common/ExportedService.java`: the three fixes from the D7 table (lines 70, 76/81, 86).
- [x] 5.3 [D] `common-test/export/ExportTest.java` as described under the D7 table (nested interface; CGLIB proxy via `ProxyFactory`). Nested fixture types live inside the test class. `mvn verify` green.

## 6. Docs — unit E

- [x] 6.1 [E] `README.md` (keep the existing sections and tone; change or add only this):
  - new "Compatibility": Java 25 or later; Spring Boot 3.5 and 4.0 (one release for both); 0.2.x for older setups;
  - "Codec": delete "Provide an ObjectMapper"; say the codec uses its own Jackson 2 mapper (design D1 settings, condensed) and ignores the application's `ObjectMapper`/`spring.jackson.*`; the wire format is fixed and tested against recorded JSON in `remoting-common/src/test/resources/wire/`, so 0.2.2 and 0.3.0 services interoperate; "Override the Codec" now requires marking the replacement bean `@Primary` (same for a custom `RemotingExecutor`);
  - new "Timeouts": `mcg.remoting.timeout` (seconds, default 10), `@RemotingTimeout`, `mcg.remoting.poolsize` (default 8); a timed-out call is cancelled and frees its pool thread; the caller sees an `UndeclaredThrowableException` caused by `TimeoutException`; late replies are dropped with a warning;
  - new "Logging": DEBUG on `com.mcg` logs interface, method, argument count and duration, no values; `logging.level.com.mcg.tools.remoting.payload=TRACE` logs full request/response bodies — **warning: this logs credentials (JWTs) and personal data**; exception messages from remote implementations are logged as they are;
  - new "Build": `mvn verify` (Boot 3.5); `mvn -Dspringboot.version=4.0.9 verify` (Boot 4);
  - new "Migrating from 0.2.x": Java 25; no `@ConditionalOnMissingBean` on the codec/executor any more (use `@Primary`); the application's `ObjectMapper` is no longer used; payload bodies moved from DEBUG to the TRACE payload logger;
  - fix the config block's indentation if it uses tabs in YAML.

## 7. Finalize the poms and verify both lines (D3, D4) — unit A2

- [x] 7.1 [A2] Delete `common-test/wire/GoldenFileGenerator.java`. In `remoting-common/pom.xml` remove `org.springframework:spring-web` (test) and `org.springframework.boot:spring-boot-autoconfigure`.
- [x] 7.2 [A2] Checks, each must print nothing:
  - `grep -rn 'org.springframework.boot' --include=*.java remoting-api/src remoting-common/src remoting-impl-amqp/src`
  - `grep -rnE 'classgraph|reflections|junit:junit|<artifactId>junit</artifactId>' --include=pom.xml .  | grep -v '/bin/'`
  - `grep -rn 'TypeFactory.defaultInstance\|getCanonicalName\|StringUtils.isEmpty' --include=*.java remoting-common/src/main`
  - `mvn -pl remoting-api,remoting-common dependency:tree -Dscope=runtime | grep 'org.springframework.boot:'`
- [x] 7.3 [A2] On JDK 25: `mvn clean verify` green with the default BOM **and** with `-Dspringboot.version=4.0.9`, same test counts on both (compare the surefire `Tests run:` totals per module). If the Boot 4 run reports zero tests, the surefire pin is the suspect. If an example module fails only on Boot 4 (e.g. a renamed starter), fix it in that example's pom and report it.
- [x] 7.4 [A2] End-to-end run, design D4 "Verification" item 2: throwaway `rabbitmq:4.3.0` container on port 35672; the three runs (0.3.0↔0.3.0 on 3.5.16, 0.3.0↔0.3.0 on 4.0.9, 0.2.2 server ↔ 0.3.0 client on 4.0.9); the 0.2.2 tree goes to a scratch directory outside the repo via `git archive 0.2.2 | tar -x -C …` and is built there with `mvn -DskipTests package`. Check the success lines from D4. The client loop makes 100 000 calls before the timeout cases; let it run until `short: wait one second ... OK!` (or give up after 10 minutes and report). Stop both apps and `docker stop remoting-e2e-rabbit` afterwards, also on failure.

## 8. Prepare the release (do not run it) — unit A2

- [x] 8.1 [A2] `mvn versions:set -DnewVersion=0.3.0-SNAPSHOT -DgenerateBackups=false -DprocessAllModules=true`, so that `release.sh` (which strips `-SNAPSHOT`) produces **0.3.0**. `grep -rn '0.2.3-SNAPSHOT' --include=pom.xml . | grep -v '/bin/'` prints nothing. Then `mvn verify` once more (default BOM). Do not install.
- [x] 8.2 [A2] Report back (no commit):
  - test counts per module on both BOMs, and the 1.1 baseline;
  - the three end-to-end runs: pass/fail, with the relevant client log lines;
  - versions used (Boot 3.5.16 / 4.0.9, Jackson 2 per line, plugins);
  - every file created, changed or deleted;
  - any deviation from design.md, and anything left open.

## 9. After release (platform repo, not these agents)

- [ ] 9.1 Platform: `remoting.version` 0.2.2 → 0.3.0 in `services/pom.xml` while on Boot 3.5, then a rolling deploy. Mixed versions are safe because of the golden-file test and the mixed end-to-end run.
