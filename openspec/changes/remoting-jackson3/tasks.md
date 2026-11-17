# Tasks: remoting 0.4.0 (Jackson 3)

## 1. Record before porting
- [x] 1.1 Extend `WireFixtures` with the platform's DTO types and the types whose Jackson 3 defaults differ (design D2)
- [x] 1.2 Add `Jackson2RemotingCodec`, a verbatim copy of the 0.3.x codec, in test scope
- [x] 1.3 Record `request-more.json` / `response-more.json` with it (`WireGoldenRecorder`, `-Dwire.record=true`)
- [x] 1.4 Byte-identical and old-peer decode checks in `WireFormatTest`; green on the Jackson 2 codec

## 2. Port
- [x] 2.1 `remoting-common` compile dependency: `tools.jackson.core:jackson-databind`; Jackson 2 test scope only
- [x] 2.2 Root pom imports `tools.jackson:jackson-bom` after Boot's BOM (design D4)
- [x] 2.3 `SimpleRemotingCodec` on `JsonMapper` with the settings from design D1
- [x] 2.4 `mvn verify` green on Boot 3.5 (default) and `-Dspringboot.version=4.0.9`

## 3. Docs and dbutils
- [x] 3.1 README: Compatibility, Codec, "Migrating from 0.3.x"
- [x] 3.2 dbutils 0.4.0: `SchemaCheckService` on Jackson 3, sorting off, golden `schema.json` (design D6)

## 3a. BigDecimal as string (owner decision, design D7)
- [x] 3a.1 Serializer writing `BigDecimal.toPlainString()` as a JSON string
- [x] 3a.2 Record `*-more-040.json` with the current codec; test that they differ from the 0.3.x files only in `BigDecimal` values
- [x] 3a.3 Tests for 0.4.0 → 0.4.0, 0.4.0 → 0.3.x (typed and untyped), 0.3.x → 0.4.0, large values, plain notation
- [x] 3a.4 README and spec

## 3b. Property order of classes with extra constructors (AuthIdentity, design D1)
- [x] 3b.1 Reproduce; constructor-shape fixtures recorded with the 0.3.x codec (`*-shapes.json`)
- [x] 3b.2 `Jackson2CreatorRule` in the codec; build with `-parameters`

## 4. Verification in the platform's dev stack (platform session)
- [ ] 4.1 Services on 0.4.0-SNAPSHOT start and serve calls
- [ ] 4.2 Mixed deploy 0.3.x ↔ 0.4.0, both directions, including `ContextService.setPicture(byte[])` and Billing (BigDecimal with scale, e.g. price `1234.50`, and Date)
- [ ] 4.3 Release 0.4.0 (owner)
