## ADDED Requirements

### Requirement: Client channels recover without a restart
A client channel SHALL retry its setup after any failure, and a call SHALL succeed once the cause of the failure is gone, without restarting the process.

#### Scenario: Setup fails when the channel is created
- **WHEN** creating the channel, a declare, the bind or the reply consumer fails at lazy creation
- **THEN** the next call or the next connection check sets the channel up again

#### Scenario: Channel cannot be set up during a call
- **WHEN** a call finds its channel not ready and a bounded inline repair fails
- **THEN** the call fails with `CLIENT_CHANNEL_NOT_READY`, and a later call repairs the channel again

#### Scenario: Channel closed or reply consumer cancelled
- **WHEN** the broker closes the channel or its connection, or cancels the reply consumer
- **THEN** the next call or connection check builds a new channel

#### Scenario: Publish on a closed channel
- **WHEN** publishing a request fails because the channel is closed
- **THEN** the request is sent once on a new channel

### Requirement: Concurrent use creates one channel per interface
Concurrent first calls of an imported interface, lazy creation and the connection check SHALL produce exactly one ready channel per interface and one connection per service.

#### Scenario: Burst of first calls
- **WHEN** many threads make the first call of an interface at the same time
- **THEN** all calls succeed and one channel is created

### Requirement: Client lifecycle failures are diagnosable from the log
Every failure in the client channel lifecycle SHALL be logged with its exception and the step at which it happened.

#### Scenario: Setup failure
- **WHEN** setting up a client channel fails
- **THEN** the log names the channel, the step (createChannel, exchangeDeclare, queueDeclare, queueBind, basicConsume) and the exception

#### Scenario: Call failure
- **WHEN** a call fails inside the client channel
- **THEN** the log names the channel, the step (ensure channel ready, publish, wait for reply) and the exception
