## MODIFIED Requirements

### Requirement: A Golden Master pins each cup scenario tick by tick
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep four Golden Master scenarios for a running cup: lobby and start, racing with a boost and a
disconnect, skip and map and cup finish, and restart with a pending catalogue. For every tick of a scenario, the
transcript SHALL record the full `describe()` text, the chat and title messages each racer received in order, and
the log lines the cup wrote. The transcript SHALL equal the committed golden file for that scenario.

#### Scenario: Transcript matches the committed golden file
- **WHEN** the lobby-and-start scenario runs against the production code
- **THEN** its transcript equals `voyager/server/src/test/resources/golden/cup-session/lobby-and-start.txt` line for line

#### Scenario: A changed message fails the scenario
- **WHEN** one message string of the cup is changed by one character
- **THEN** every scenario that delivers that message fails and names the first differing tick and line
