# cloudnet/hub-handoff Specification

## Purpose
How the server sends a racer to the network hub through the CloudNet bridge without naming a CloudNet type. The server asks a
port in `voyager-api`. The bridge extension installs the port's implementation when it starts. Without the bridge the server
disconnects the racer with a translated message, which is the local-development path.

Depends on `add-cloudnet-deployment`, which creates the `voyager-cloudnet-bridge` module and its `cloudnet/bridge-extension`
capability.

## Requirements

### Requirement: A handoff port in voyager-api names the hub move
**MoSCoW:** Must. THE SYSTEM SHALL declare in `voyager-api`, in the package `net.elytrarace.voyager.api.handoff`, an interface
`PlayerHandoff` with the operation `toHub(UUID playerId)`. THE SYSTEM SHALL declare `HandoffUnavailableException` in the package
`net.elytrarace.voyager.api.handoff.exception`, and THE SYSTEM SHALL make it the only exception that `toHub` throws.

#### Scenario: The port has no implementation in the api module
- **WHEN** the api module is built
- **THEN** `net.elytrarace.voyager.api.handoff` holds no class that does I/O, and it depends only on the JDK and on `voyager-api`

### Requirement: The installed handoff is held in one slot in voyager-api
**MoSCoW:** Must. THE SYSTEM SHALL hold at most one `PlayerHandoff` in `PlayerHandoffs`, a holder in `voyager-api`. `install`
SHALL replace the held handoff, `uninstall` SHALL clear it, and `current()` SHALL return it as an `Optional`.

#### Scenario: Empty holder
- **WHEN** nothing has been installed
- **THEN** `current()` is empty

#### Scenario: A later install replaces the earlier one
- **WHEN** two handoffs are installed in turn
- **THEN** `current()` returns the second

### Requirement: The bridge installs the CloudNet handoff when it starts
**MoSCoW:** Must. WHEN `VoyagerCloudNetExtension` initializes, THE SYSTEM SHALL install a `PlayerHandoff` in `PlayerHandoffs` whose
`toHub` asks CloudNet to connect the player to the best matching fallback through `PlayerManager.playerExecutor(uuid)`
and `connectToFallback()`.

#### Scenario: A racer is sent to the fallback
- **WHEN** the cup ends and the handoff is called for a racer on a node with CloudNet_Bridge
- **THEN** the racer's CloudNet player executor is asked to connect to the fallback, and the racer is not kicked

#### Scenario: A CloudNet failure is refused
- **WHEN** the CloudNet call throws
- **THEN** `toHub` throws `HandoffUnavailableException`, and the failure is not swallowed

### Requirement: The local fallback disconnects the racer with a translated message
**MoSCoW:** Must. IF no `PlayerHandoff` is installed, OR IF the installed handoff throws `HandoffUnavailableException`, THEN THE
SYSTEM SHALL disconnect the racer with the translated message `voyager.handoff.disconnect`, and SHALL log a warning that names the
racer when the handoff was refused.

#### Scenario: Local run without CloudNet
- **WHEN** the server runs without `extensions/` and a cup ends
- **THEN** each racer is disconnected with the translated message, and no racer is moved to a lobby world

#### Scenario: Refused handoff
- **WHEN** the installed handoff throws for one racer
- **THEN** that racer is disconnected with the same message and a warning names the racer

### Requirement: Only the bridge installs a handoff
**MoSCoW:** Must. THE SYSTEM SHALL call `PlayerHandoffs.install` only from classes of `net.elytrarace.voyager.cloudnet.bridge..`. The
fitness rule that enforces this SHALL use `allowEmptyShould(false)`.

#### Scenario: Platform installs a handoff
- **WHEN** a class outside the bridge package calls `PlayerHandoffs.install`
- **THEN** the fitness rule `onlyTheBridgeInstallsAPlayerHandoff` fails and names the class

### Requirement: The bridge names only the handoff package among Voyager types
**MoSCoW:** Must. THE SYSTEM SHALL let classes of `net.elytrarace.voyager.cloudnet.bridge..` depend on Voyager types only in the
package `net.elytrarace.voyager.api.handoff..`, and on the JDK, Minestom, Adventure, CloudNet, the annotations and their own package.

#### Scenario: Bridge reads a race type
- **WHEN** a bridge class imports `net.elytrarace.voyager.api.race..`
- **THEN** the fitness rule `bridgeDependsOnlyOnJdkAndLibraries` fails and names the class

### Requirement: The bridge jar does not carry the api
**MoSCoW:** Must. THE SYSTEM SHALL build `voyager-cloudnet-bridge` with `voyager-api` as compileOnly, and the build SHALL fail when the
bridge jar contains an entry under `net/elytrarace/voyager/`.

#### Scenario: Api bundled into the bridge
- **WHEN** the bridge jar contains `net/elytrarace/voyager/api/handoff/PlayerHandoffs.class`
- **THEN** the build task `verifyNoVoyagerApiInBridgeJar` fails

### Requirement: The handoff is verified on a node before it ships
**MoSCoW:** Should. THE SYSTEM SHALL have two spike results recorded in the change's design.md before the handoff is enabled on the
owner's network: that the server's `PlayerHandoffs` class is the same class inside the extension, and what a racer sees when
`connectToFallback` runs with and without a configured fallback.

#### Scenario: Spike recorded
- **WHEN** the handoff is proposed for the network
- **THEN** design.md records the class identity and the fallback behaviour from the node, not from the source alone
