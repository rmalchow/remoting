## ADDED Requirements

### Requirement: Wire format is byte-identical across the Jackson 3 port
The codec SHALL encode every recorded payload to exactly the recorded bytes, and SHALL decode every recorded payload to the same values as the 0.3.x Jackson 2 codec. The only permitted difference from the 0.3.x encoding is the representation of `BigDecimal` values.

#### Scenario: Encoding
- **WHEN** a payload from the wire fixtures is encoded
- **THEN** the output equals the recorded golden file byte for byte

#### Scenario: Decoding a 0.3.x payload
- **WHEN** a golden file recorded with the 0.3.x codec is decoded
- **THEN** every argument and return value equals, including its Java class, what the 0.3.x codec decodes from the same file

#### Scenario: Dates, locales and enums
- **WHEN** a payload contains a `java.util.Date`, a `Locale` or an enum whose `toString()` differs from its name
- **THEN** the date is written with a numeric UTC offset (`+00:00`), the locale in `Locale.toString()` form, and the enum by its name

#### Scenario: Property order
- **WHEN** a DTO is encoded
- **THEN** its properties appear in the order the 0.3.x codec writes them, not sorted alphabetically

### Requirement: BigDecimal values arrive exactly
The codec SHALL write a `BigDecimal` as a JSON string holding its plain (non-exponent) representation with its scale, and SHALL accept both a JSON string and a JSON number for a `BigDecimal` target.

#### Scenario: Typed target, current receiver
- **WHEN** a `BigDecimal` `1234.50` is sent as an argument, a return value or a DTO field to a `BigDecimal` target
- **THEN** the receiver gets `1234.50` with scale 2

#### Scenario: Typed target, 0.3.x receiver
- **WHEN** a 0.3.x codec receives the string form for a `BigDecimal` target
- **THEN** it gets the exact value with its scale

#### Scenario: Untyped target
- **WHEN** a `BigDecimal` is sent inside an `Object` or `Map<String,Object>`
- **THEN** the receiver gets the plain string, e.g. `"1234.50"`

#### Scenario: Number from a 0.3.x sender
- **WHEN** a JSON number arrives for a `BigDecimal` target
- **THEN** it is accepted and converted as in 0.3.x
