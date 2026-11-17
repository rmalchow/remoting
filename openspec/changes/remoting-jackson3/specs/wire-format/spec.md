## ADDED Requirements

### Requirement: Wire format is byte-identical across the Jackson 3 port
The codec SHALL encode every recorded payload to exactly the recorded bytes, and SHALL decode every recorded payload to the same values as the 0.3.x Jackson 2 codec.

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
