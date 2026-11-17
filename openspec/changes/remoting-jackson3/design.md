# Design: remoting codec on Jackson 3

Terms used below:

- **Mapper** — Jackson's converter between Java objects and JSON. In Jackson 2 it is
  `com.fasterxml.jackson.databind.ObjectMapper`, in Jackson 3 `tools.jackson.databind.json.JsonMapper`.
  remoting's codec owns a private one (decision D1 of `platform-jdk25-boot4`), so the host
  application's mapper and its `spring.jackson.*` settings never affect the wire.
- **Feature** — an on/off switch on the mapper, e.g. "write dates as numbers". Jackson 3 changed the
  default of several switches and moved the date-related ones into a new enum, `DateTimeFeature`.
- **Golden file** — a JSON document recorded from a known-good version, which later code must
  reproduce. They live in `remoting-common/src/test/resources/wire/`.
- **Old peer** — a service still running 0.3.x. During a rolling update it talks to 0.4.0 services in
  both directions.

## D1. Which Jackson 3 defaults had to be turned back

Before writing the new codec, a scratch prototype ported the codec with only the settings 0.3.x
already had (dates and durations not as timestamps, unknown properties ignored, view inclusion off)
and ran the 0.2.2 golden files against it. Reading them was fine. Writing differed in two places:

| Value | 0.3.x (Jackson 2) | untuned Jackson 3 |
|---|---|---|
| `java.util.Date` 2026-11-16 12:00 UTC | `"2026-11-16T12:00:00.000+00:00"` | `"2026-11-16T12:00:00.000Z"` |
| `Locale.GERMANY` | `"de_DE"` | `"de-DE"` |

Jackson 2 reads `Z` as a date, so the first difference would have been harmless in practice, but
`"de-DE"` is the IETF language-tag form, which is not what Jackson 2 writes, and a byte-identical
format is the simpler promise. The two fixes:

- `defaultDateFormat(new StdDateFormat().withColonInTimeZone(true).withZeroOffsetAsZ(false))` —
  Jackson 3's own date format class, told to write `+00:00` again.
- A `SimpleModule` registering `ToStringSerializer` for `Locale` — `Locale.toString()` is exactly
  `de_DE`. Reading is unaffected: Jackson 3 still parses `de_DE`.

Further switches are set explicitly although no golden file failed on them, because their Jackson 3
default differs from Jackson 2 and a future fixture could hit them:

- `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` off. Jackson 3 sorts properties by name by default;
  with sorting on, the `Sample` fixture would be written `at, children, color, name, tags, when`
  instead of the declaration order `name, when, at, color, children, tags`. JSON readers don't care
  about order, but byte-identical output does.
- `EnumFeature.READ_ENUMS_USING_TO_STRING` / `WRITE_ENUMS_USING_TO_STRING` off. With them on, the
  fixture `Level.LOW`, whose `toString()` returns `"low"`, would be written as `"low"` instead of `"LOW"`,
  and an old peer could not read it back.
- `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` off. On in Jackson 3: a JSON `null` for an
  `int` field would throw instead of becoming `0` as in Jackson 2.

The `Jdk8Module`, `JavaTimeModule` and `ParameterNamesModule` that 0.3.x registered are part of
Jackson 3's core and need no registration.

Rejected: copying a Jackson 2-compatibility preset from Spring Boot 4 (`spring.jackson.use-jackson2-defaults`).
That is Boot configuration of Boot's mapper; the codec must not depend on Boot, and the preset's
exact contents can change with a Boot release while the wire format must not.

## D2. Golden files: what is recorded, and by whom

The 0.2.2 files (`request-all`, `request-nulls`, `request-headers`, `response-sample`,
`response-void`, `response-error`) stay as they are.

The platform's remoted types were listed by grepping every `@RemoteEndpoint` interface under
`services/` on the platform's `main` branch and following the field types of every DTO they use
(39 types). They use: `java.util.Date` (12 DTOs), `BigDecimal` (`BillingEvent`, `RecurringEvent`),
`Locale` (`MailSpec`), `byte[]` (`Context`), plain enums, `Map<String,…>`, `long`/`Long`, and an `id`
inherited from dbutils' generic `BaseEntityV2<T>`. No `java.time`, `Optional`, records or
non-`String` map keys — but those are where Jackson 3's defaults moved, so they are covered too.

`WireFixtures` gained `WireService.more(More, BigDecimal, long, Optional<String>, Level, Point, Map<Date,String>)`
and a `More` DTO (extending a generic `Entity<String>`) with `BigDecimal`, a `long` beyond 2^53
(9007199254740993, which a `double` cannot hold), `Long` null, `LocalDate`, `LocalDateTime` with
fractional seconds, `OffsetDateTime` at +02:00, `Duration`, an enum overriding `toString`, a record,
a `Map<Long,String>` and a `Map<String,Object>` holding a `Date`, a `BigDecimal`, a large `long` and a
`Locale`.

The two new files `request-more.json` and `response-more.json` were recorded **before** the port, by
`Jackson2RemotingCodec`: a verbatim copy of the 0.3.x codec in test scope. The recording is the
second commit on the branch, the port the third, so the history shows the files were not produced by
the code they test. The recorder (`WireGoldenRecorder`) only runs with `-Dwire.record=true`.

## D3. What the permanent test checks

`WireFormatTest`, for every golden file:

- **encode** — the codec's output equals the file as a JSON tree *and* byte for byte (the byte check
  is new in 0.4.0; the tree check alone would not catch property order).
- **decode** — for the `*-more` files, the values the new codec decodes equal the values
  `Jackson2RemotingCodec` decodes from the same file, including the Java class of every untyped
  value in `More.props` (e.g. `Long` stays `Long`, not `Integer` or `BigInteger`). For the older files,
  decoded values equal the fixtures, as before.

Encoding byte-identically covers "0.4.0 → old peer"; decoding the old peer's files the same way
covers "old peer → 0.4.0". `Jackson2RemotingCodec` stays in the tests as that old peer; it must not
be "fixed".

## D4. Dependencies and versions

- `remoting-common`: compile `tools.jackson.core:jackson-databind`; Jackson 2 databind and its three
  modules in `test` scope for the old peer.
- The root pom imports `tools.jackson:jackson-bom` (property `jackson3.version`, 3.1.7) *after*
  Boot's BOM. Maven lets the first import win, so building with Boot 4 (`-Dspringboot.version=4.0.9`)
  uses Boot 4's own Jackson 3 version, and Boot 3.5, which does not manage Jackson 3, gets 3.1.7. In
  a consuming service, the service's BOM decides, as before.
- Jackson 3 still depends on `com.fasterxml.jackson.core:jackson-annotations` (2.21), the shared
  annotation package. That jar is small and stays.
- `SimpleRemotingCodec.conform(...)` (protected) now declares `throws IOException` only; Jackson 3's
  exceptions are unchecked. A subclass that caught Jackson 2 exception types must be adapted.

## D5. Known behaviour, deliberately unchanged

The codec first reads arguments and return values *untyped* (into `Map`/`List`/`Double`/...), then
converts them to the declared parameter type. Consequences, identical in 0.2.2, 0.3.x and 0.4.0 and
pinned by the tests so a change would be noticed:

- `BigDecimal` goes through a `double`: `1234.50` arrives as `1234.5`; values beyond ~15 significant
  digits lose precision. Relevant for `BillingEvent` amounts, where two decimals fit easily.
- `Optional.empty()` arrives as `null`.
- Same-arity overloads are dispatched to the first method whose parameters the values convert to.

Fixing these changes what a service receives, so it belongs in its own change.

## D6. dbutils

dbutils uses Jackson in one place: `SchemaCheckService` logs an MD5 hash of the schema written as
indented JSON. It moves to Jackson 3 in dbutils 0.4.0 with property sorting off — without that, the
JSON order and so every logged hash would change, which was confirmed by removing the line and seeing
`SchemaCheckServiceTest` fail. A golden `schema.json` recorded on Jackson 2 pins the output.

## Risks

- **A DTO type not covered by the fixtures behaves differently.** Mitigated by covering every type
  the platform uses today; new DTO types should be checked against `Jackson2RemotingCodec` the same way.
- **Jackson 2 databind annotations in a DTO** would be ignored silently. None exist today (grep in D2's
  type set); the README says so.
