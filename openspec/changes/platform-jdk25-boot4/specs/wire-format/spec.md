## ADDED Requirements

### Requirement: Wire format is owned by remoting
The codec SHALL serialise and deserialise RPC payloads with a mapper that remoting configures itself. The JSON produced for a given payload SHALL NOT depend on any JSON mapper bean or `spring.jackson.*` setting of the host application.

#### Scenario: Host application defines its own mapper
- **WHEN** the host application context contains an `ObjectMapper` or `JsonMapper` bean with non-default settings
- **THEN** the codec encodes a payload to the same JSON as in a context without that bean

#### Scenario: Host application on a newer framework line
- **WHEN** the same payload is encoded by remoting running in a Spring Boot 3.5 application and in a Spring Boot 4 application
- **THEN** both encodings are equal as JSON trees

### Requirement: Wire format is stable across releases
A release SHALL encode payloads to the same JSON as the previous release, and SHALL decode JSON produced by the previous release. Changes to the format SHALL be made only in a change that declares them.

#### Scenario: Golden payloads
- **WHEN** the recorded requests and responses in the test resources, including an error response and a request with headers, are encoded by the current codec
- **THEN** the output equals the recorded JSON as a JSON tree

#### Scenario: Dates
- **WHEN** a payload contains a `java.util.Date` or a `java.time` value
- **THEN** it is written as an ISO-8601 string, not as a number

#### Scenario: Previous release's payloads
- **WHEN** the recorded requests and responses are decoded by the current codec
- **THEN** the decoded arguments and return values equal the values they were recorded from

### Requirement: Readers tolerate unknown properties
Decoding SHALL ignore properties that the target type does not declare.

#### Scenario: Newer sender adds a field
- **WHEN** a reply contains a property that the caller's DTO class does not have
- **THEN** the call succeeds and the property is ignored
