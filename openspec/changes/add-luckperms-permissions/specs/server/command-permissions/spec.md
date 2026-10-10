# Spec Delta

## Purpose

How Voyager decides whether a sender may run a gated command or action. Both composition roots ask one port, the
PermissionPolicy, so the rule is written once. LuckPerms answers when it is present. A level-based fallback answers
when it is not, and it never grants a player more than operator level 4 gives.

## ADDED Requirements

### Requirement: Gated actions ask one permission port
**Priority:** MoSCoW Must

WHEN a command or listener decides whether a sender may run a gated action, THE SYSTEM SHALL ask the PermissionPolicy
port declared in voyager-api. THE SYSTEM SHALL NOT call LuckPerms or read a player's operator level from a command class.

#### Scenario: Command asks the port
- **WHEN** a player runs `/race reload`
- **THEN** the command asks PermissionPolicy whether that player holds `voyager.command.race.reload`

#### Scenario: Command class names LuckPerms
- **WHEN** a class outside the LuckPerms adapter package references a `net.luckperms` type
- **THEN** the fitness rule `luckPermsIsConfinedToItsAdapter` fails and names the class

### Requirement: The port also answers node names Voyager does not own
**Priority:** MoSCoW Should

THE SYSTEM SHALL offer the port in a form that takes a free-form node name, for an external caller that asks about a name
Voyager does not own. THE SYSTEM SHALL answer that form with the same policy rules as the enum form. Voyager's own gated
actions SHALL use the enum form.

#### Scenario: External name in fallback mode
- **WHEN** a level-4 player is asked about `cloudnet.bridge.maintenance` and LuckPerms is absent
- **THEN** the answer is allowed, by the same level rule as any other node

#### Scenario: Voyager's gate uses the enum form
- **WHEN** a command gates an action on one of the five Voyager nodes
- **THEN** it calls the enum form of the port

### Requirement: The permission node list is fixed
**Priority:** MoSCoW Must

THE SYSTEM SHALL define every permission node as a constant of the PermissionNode enum in voyager-api. THE SYSTEM SHALL
name exactly these nodes: voyager.command.race.reload, voyager.command.race.start, voyager.command.race.skip,
voyager.command.stop and voyager.setup.use.

#### Scenario: Node list is checked
- **WHEN** the unit test lists the PermissionNode constants
- **THEN** the list equals the five node names above, and each value starts with `voyager.`

### Requirement: LuckPerms answers when it is present
**Priority:** MoSCoW Must

WHEN LuckPerms is on the class path and has started, THE SYSTEM SHALL answer a player's node from that player's cached
LuckPerms permission data. IF LuckPerms holds no user data for the player, THEN THE SYSTEM SHALL deny the node.

#### Scenario: Granted node
- **WHEN** a player's LuckPerms user holds `voyager.command.race.reload` as true
- **THEN** the policy allows that player to run `/race reload`

#### Scenario: Player without LuckPerms data
- **WHEN** LuckPerms has no user for the player's UUID
- **THEN** the policy denies every node for that player

### Requirement: Fallback grants by operator level when LuckPerms is absent
**Priority:** MoSCoW Must

WHEN LuckPerms is absent from the class path, THE SYSTEM SHALL allow the console every node. THE SYSTEM SHALL allow a
player every node only when the player's operator level is 4, and SHALL deny every node to a player below level 4. THE
SYSTEM SHALL log one WARN line at startup that names the fallback.

#### Scenario: Operator level 4 player in fallback mode
- **WHEN** LuckPerms is absent and a player has operator level 4
- **THEN** the player may run `/race reload` and `/race skip` in dev mode

#### Scenario: Operator level 3 player in fallback mode
- **WHEN** LuckPerms is absent and a player has operator level 3
- **THEN** the player is denied `/race reload` and receives the denial message

#### Scenario: Console in fallback mode
- **WHEN** LuckPerms is absent and the console runs `/race reload`
- **THEN** the reload is accepted

### Requirement: A failed LuckPerms start refuses to listen
**Priority:** MoSCoW Should

IF LuckPerms is on the class path but fails to start, THEN THE SYSTEM SHALL log the reason, exit with a non-zero status
and not bind the network port. THE SYSTEM SHALL NOT fall back to the level-based policy in that case.

#### Scenario: LuckPerms present but broken
- **WHEN** the loader class is present and its start throws
- **THEN** the server exits with a non-zero status before it binds the port

### Requirement: A denied command is reported, not hidden
**Priority:** MoSCoW Should

WHEN a sender lacks a node for a gated subcommand, THE SYSTEM SHALL send the denial message to the sender. THE SYSTEM
SHALL NOT report the subcommand as unknown.

#### Scenario: Denied reload
- **WHEN** a player without `voyager.command.race.reload` runs `/race reload`
- **THEN** the player receives the denial message and no reload is requested
