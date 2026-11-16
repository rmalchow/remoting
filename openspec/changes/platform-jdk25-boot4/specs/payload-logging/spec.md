## ADDED Requirements

### Requirement: Payloads are not logged by default
Log output about remote calls SHALL NOT contain request or response bodies, argument values or return values, except on the logger `com.mcg.tools.remoting.payload` at TRACE level. Messages of exceptions raised by the remote implementation are exempt.

#### Scenario: Debug logging enabled
- **WHEN** the `com.mcg` loggers are set to DEBUG and a remote call is made
- **THEN** the log names the interface, method, argument count and duration, and contains no argument or return values

#### Scenario: Payloads explicitly enabled
- **WHEN** the logger `com.mcg.tools.remoting.payload` is set to TRACE
- **THEN** request and response bodies are logged on that logger
