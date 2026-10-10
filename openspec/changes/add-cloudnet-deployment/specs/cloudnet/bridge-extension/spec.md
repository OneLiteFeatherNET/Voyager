# Spec Delta

## Purpose

The Minestom extension that lets the CloudNet bridge answer permission questions for Voyager players through the same
PermissionPolicy as commands. Without it, CloudNet's maintenance bypass and task-level permission checks read a permission
level that is always zero on a Voyager server, and reject every player, staff included.

## ADDED Requirements

### Requirement: Only the bridge module references CloudNet
**Priority:** MoSCoW Must

THE SYSTEM SHALL confine every reference to a `eu.cloudnetservice` type to the module `voyager-cloudnet-bridge`. THE SYSTEM
SHALL declare each such dependency as compileOnly in that module, and no other module SHALL depend on it.

#### Scenario: Composition root imports a CloudNet type
- **WHEN** a class in `voyager-server` or `voyager-setup` references `eu.cloudnetservice`
- **THEN** the fitness rule `cloudNetIsConfinedToBridge` fails and names the class

### Requirement: The bridge is a Minestom extension that needs CloudNet_Bridge
**Priority:** MoSCoW Must

THE SYSTEM SHALL package the bridge as a Minestom extension whose generated `extension.json` declares the dependency
`CloudNet_Bridge`, so that the extension loads after the CloudNet bridge and shares its classloader hierarchy.

#### Scenario: Extension descriptor
- **WHEN** the bridge module is built
- **THEN** its `extension.json` lists `CloudNet_Bridge` among its dependencies

### Requirement: CloudNet asks the player's pointer for each permission
**Priority:** MoSCoW Must

WHEN CloudNet asks whether a player holds a permission, THE SYSTEM SHALL answer by reading Adventure's
`PermissionChecker.POINTER` from that player. THE SYSTEM SHALL have that pointer resolve through the PermissionPolicy
port of `voyager-api`.

#### Scenario: Maintenance bypass for a granted player
- **WHEN** a player holds the permission that CloudNet's maintenance check asks for, as granted by the LuckPerms adapter
- **THEN** CloudNet's check passes for that player

#### Scenario: Player without the grant
- **WHEN** a player does not hold the requested permission
- **THEN** CloudNet's check fails for that player

### Requirement: Every Voyager player carries the pointer
**Priority:** MoSCoW Must

THE SYSTEM SHALL create every player of the game and setup servers from a Player subclass that supplies the
`PermissionChecker.POINTER`, registered through the connection manager's player provider before the port binds. Without
that pointer every permission check answers false.

#### Scenario: Player pointer is present
- **WHEN** a player joins the game server
- **THEN** `player.getOrDefault(PermissionChecker.POINTER, ...)` returns the player's checker, not the default

### Requirement: The bridge is skipped when CloudNet_Bridge is missing
**Priority:** MoSCoW Should

IF the extension's dependency `CloudNet_Bridge` is missing, THEN THE SYSTEM SHALL skip the extension, log one line that names
the missing dependency, and start the server normally.

#### Scenario: Local run without CloudNet
- **WHEN** the server starts from a directory without `extensions/`
- **THEN** the server starts, and the log names the skipped extension

### Requirement: Only JDK and voyager-api types cross the extension boundary
**Priority:** MoSCoW Must

THE SYSTEM SHALL pass across the extension boundary only JDK types and types declared in `voyager-api`. THE SYSTEM SHALL NOT
pass a `voyager-platform` or composition-root type to the bridge.

#### Scenario: Platform type in the bridge
- **WHEN** a class in `net.elytrarace.voyager.cloudnet.bridge..` imports `net.elytrarace.voyager.platform..`
- **THEN** the fitness rule `bridgeDependsOnlyOnApi` fails and names the class

### Requirement: Both roots start the extension bootstrap before binding
**Priority:** MoSCoW Must

THE SYSTEM SHALL start the Minestom extension bootstrap of the `net.onelitefeather:minestom-extensions` library in both
composition roots, after the game is wired and before the port binds, and SHALL bind the host and port through that
bootstrap.

#### Scenario: Bootstrap starts before listening
- **WHEN** the game server starts
- **THEN** the extension bootstrap starts before the server reports that it is listening
