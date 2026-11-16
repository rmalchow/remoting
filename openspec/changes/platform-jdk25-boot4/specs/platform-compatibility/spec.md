## ADDED Requirements

### Requirement: One release spans the supported framework lines
Each release SHALL build, pass its tests and run unchanged against every Spring Boot line listed as supported in the README, on Java 25. The build SHALL verify each listed line.

#### Scenario: Supported lines
- **WHEN** the build runs with the Spring Boot BOM of any supported line selected
- **THEN** compilation and all tests succeed

#### Scenario: Java 25
- **WHEN** the build and tests run on JDK 25
- **THEN** they succeed without additional JVM flags such as `--add-opens`

### Requirement: No dependency on Spring Boot classes
Library modules SHALL NOT reference classes from Spring Boot packages (`org.springframework.boot..`) in their code. They MAY rely on beans that the host application's Spring Boot configuration provides.

#### Scenario: JMS auto-configuration absent
- **WHEN** remoting runs in an application whose classpath lacks Spring Boot's JMS auto-configuration classes
- **THEN** sending and receiving remote calls works
