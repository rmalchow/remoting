# Move the remoting codec to Jackson 3 (release 0.4.0)

## Why

remoting is the library the platform services (`mcg-automation`) use to call each other over
RabbitMQ. A caller invokes a Java interface such as `ContextService.setPicture(byte[])`; remoting
turns that call into a JSON message, sends it, and the receiving service turns the JSON back into a
call on its real implementation. The class that does the JSON part is the *codec*,
`SimpleRemotingCodec` in `remoting-common`.

Up to 0.3.x the codec uses **Jackson 2**, the JSON library under the Java package
`com.fasterxml.jackson`. The platform has meanwhile moved to Spring Boot 4.1, which uses
**Jackson 3** for its REST endpoints. Jackson 3 is a new major version under a new package
(`tools.jackson`), so the two versions are separate libraries that can sit side by side in one
application, and today they do: every service carries Jackson 3 for REST *and* Jackson 2 for
remoting, dbutils' schema check, and its session/JWT JSON. Two copies of a large library cost
memory, and the platform notices that most in the `ui-app`, which runs with a 300 MB heap limit.

The owner asked for remoting (and dbutils) to move to Jackson 3, as long as services on 0.3.x and
services on the new release can keep talking to each other during a rolling update.

## What changes

- `SimpleRemotingCodec` builds its private mapper with Jackson 3 (`tools.jackson.databind.json.JsonMapper`)
  instead of Jackson 2.
- The mapper is configured so that the JSON it writes is **byte-identical** to what 0.3.x writes, and
  so that it reads what 0.3.x writes into the same values. Concretely, for the payload
  `fire("hello", 7)` both versions put exactly these bytes on the wire:

  ```json
  {"headers":{"A-Value":"AAAA"},"methodName":"fire","params":["hello",7]}
  ```

  and for a `java.util.Date` argument both write `"2026-11-16T12:00:00.000+00:00"`, although an
  untuned Jackson 3 would write `"2026-11-16T12:00:00.000Z"` (see design D1 for every such difference).
- `remoting-common`'s compile dependencies change from Jackson 2 (`jackson-databind`,
  `jackson-datatype-jdk8`, `jackson-datatype-jsr310`, `jackson-module-parameter-names`) to Jackson 3's
  single `tools.jackson.core:jackson-databind`. Jackson 2 remains a **test** dependency only.
- New golden files (recorded JSON documents the codec must reproduce, see design D2) cover the types
  the platform's DTOs actually use, plus the `java.time` types and other cases where Jackson 3's
  defaults differ.
- **`BigDecimal` goes on the wire as a JSON string** (`"1234.50"` instead of `1234.50`), so that it
  arrives exactly. This is the one deliberate wire-format change; see "BigDecimal" below.
- Version 0.4.0. No API, annotation, configuration or RabbitMQ topology change.

## What does not change

- The public API: `RemotingCodec`, `RemotingRequest`, `RemotingResponse` and `RemotingInterceptor`
  use only `Object`, `Map`, `List` and `byte[]`; none of them mentions a Jackson type.
- DTO annotations from `com.fasterxml.jackson.annotation` (`@JsonIgnore`, `@JsonProperty`,
  `@JsonFormat`, ...). Jackson 3 deliberately kept that package and reads the same annotations.
- Known quirks of 0.2.x/0.3.x stay as they are (see design D5): `Optional.empty()` arrives as `null`,
  overloads with the same parameter count are ambiguous.

## BigDecimal

Found while recording the golden files: a `BigDecimal` sent through remoting does not arrive as sent.
`BillingEvent.price = 1234.50` arrives as `1234.5`, and a value with more than about 15 significant
digits arrives rounded. The cause is how the receiving codec reads a message: it first reads every
argument *without* knowing its declared type (a JSON number becomes a `Double`), and only then converts
that to the declared `BigDecimal`. The double in between loses the scale and the precision.

The owner decided: "a bigdecimal should _probably_ be strings and then coerced on receipt." So 0.4.0
writes `"1234.50"`. A string passes the untyped step unchanged and is converted exactly into the
declared type, by 0.4.0 receivers and — checked with the 0.3.x codec — by 0.3.x receivers too. A JSON
number from a 0.3.x sender is still accepted, with the old precision. The full table of who gets what
is in design D7.

Consequence beyond typed targets: a `BigDecimal` put into an untyped `Object` or `Map<String,Object>`
(e.g. `MailSpec.params`) now arrives as the `String` `"1234.50"` instead of the `Double` `1234.5`. A grep
of the platform found no such use today.

## Impact

- **Services:** bump `remoting.version` to 0.4.0. A service that used Jackson 2 classes *only because
  remoting brought them in* has to declare them itself. Nothing else.
- **Mixed deploys:** a 0.3.x and a 0.4.0 service exchange identical bytes, in both directions, except
  for `BigDecimal` values, which both sides read correctly (design D7).
- **Memory:** remoting stops pulling Jackson 2 into a service. Whether Jackson 2 actually leaves a
  service depends on its other users (dbutils' schema check moves in the same step; the platform's
  own session/JWT JSON is the platform's work).
- **Risk:** a DTO that uses Jackson 2 *databind* annotations (`@JsonSerialize`, `@JsonDeserialize`,
  `@JsonNaming`), a custom Jackson 2 serializer, or a `JsonNode` would be silently mapped differently,
  because Jackson 3 does not see those classes. A grep of the platform (2026-11-17) found none in the
  types reachable from any `@RemoteEndpoint` interface.

## Rejected

- **Two artifacts, a Jackson 2 and a Jackson 3 variant of `remoting-common`.** The codec is one
  class; the other modules don't touch JSON. Two artifacts would double the release and test work to
  serve services that can simply stay on 0.3.x.
- **Let Jackson 3 use its own defaults and accept a new wire format.** Services are updated one at a
  time; during the rollout a 0.3.x caller would receive dates and locales it may fail to read
  (`"de-DE"` is not what Jackson 2 writes for a `Locale`). Keeping the bytes identical removes the
  question entirely.
- **Fix `BigDecimal` on the receiving side only** (read JSON numbers untyped as `BigDecimal`, Jackson's
  `USE_BIG_DECIMAL_FOR_FLOATS`). It would fix messages from old senders too, but every floating-point
  number in an untyped `Map<String,Object>` would then change from `Double` to `BigDecimal`, also for
  values that were `double`s at the sender — a wider change than the string form, which affects only
  values that were `BigDecimal`s.
- **Bundle this with the 0.3.1 startup fix.** Kept apart on purpose, so a problem seen in the dev
  stack can be pinned on one change.
