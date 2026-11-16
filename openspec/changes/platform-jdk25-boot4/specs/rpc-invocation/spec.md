## ADDED Requirements

### Requirement: Remote calls time out and release their resources
A remote call SHALL fail with a timeout error after its configured timeout. When it does, the thread performing the call SHALL be released and the pending reply correlation SHALL be removed.

#### Scenario: Reply never arrives
- **WHEN** no reply arrives for a call within its timeout
- **THEN** the caller receives a timeout error, no executor thread stays blocked on that call, and no correlation entry remains

#### Scenario: More lost replies than pool threads
- **WHEN** as many calls as the executor has threads have each timed out without a reply, and another call is then made to a service that answers
- **THEN** that call succeeds within its timeout

#### Scenario: Late reply
- **WHEN** a reply arrives after its call has timed out
- **THEN** it is discarded with a warning
