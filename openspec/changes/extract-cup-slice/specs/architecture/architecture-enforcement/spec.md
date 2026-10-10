# Spec Delta

This delta builds on the version of `architecture/architecture-enforcement` that `freeze-slice-boundary-violations`
writes. Its MODIFIED and RENAMED blocks start from that version of each requirement, and apply the change of
`extract-cup-slice` to it. Requirements that this change does not touch are not repeated.

## RENAMED Requirements

- FROM: `### Requirement: The server's game package holds no Minestom adapter`
- TO: `### Requirement: Server holds no Minestom adapter outside the composition root`

## MODIFIED Requirements

### Requirement: Server holds no scoring or cup logic once the move is complete
**Priority:** MoSCoW Must

THE SYSTEM SHALL keep a rule that no class in `..server..` depends on `net.elytrarace.voyager.race.scoring..` or
`net.elytrarace.voyager.race.cup..`, so that scoring and cup logic cannot grow back into the composition root. The rule SHALL
be declared with `allowEmptyShould(false)`, and SHALL be plain: it has no frozen baseline, because `extract-cup-slice` closed
migration items 1 to 3 and moved the cup flow out of the server.

#### Scenario: Scoring import in server after the move
- **WHEN** a class in `..server..` imports `race.scoring` after the move is complete
- **THEN** the rule fails and names the class

#### Scenario: Cup type imported in server
- **WHEN** a class in `..server..` imports `race.cup`
- **THEN** the rule fails and names the class

#### Scenario: Scoring import in server before the move
- **WHEN** a class in `..server..` that is not in the baseline imports `race.scoring`
- **THEN** the rule fails and names the class. The baseline of this rule is empty once this change is merged, so the same check applies to every class in `..server..`, not only to those the move left behind.

### Requirement: Server holds no Minestom adapter outside the composition root
**Priority:** MoSCoW Must

THE SYSTEM SHALL forbid classes of `net.elytrarace.voyager.server..` that are outside the composition-root packages
`server.inject` and `server.config`, outside the command package `server.command`, and other than the bootstrap class
`VoyagerServer`, from depending on `net.minestom..`. The rule SHALL be declared with `allowEmptyShould(false)`, and SHALL be
plain. The Minestom adapters that `server.game` held are in `voyager-platform` once `extract-cup-slice` is merged.

#### Scenario: Minestom type in a server adapter class
- **WHEN** a class in `net.elytrarace.voyager.server..` outside those packages and not `VoyagerServer` imports `net.minestom..`
- **THEN** the rule fails and names the class

#### Scenario: Minestom type in the game package
- **WHEN** a class in `net.elytrarace.voyager.server..` imports `net.minestom..`, including a class that is placed in the former `server.game` package
- **THEN** the rule fails and names the class. The former package `server.game` is deleted by this change, so the rule covers the whole server module outside its composition root.

#### Scenario: Composition root and command may use Minestom
- **WHEN** a class in `server.inject`, `server.config`, `server.command`, or `VoyagerServer` imports `net.minestom..`
- **THEN** the rule does not fail

### Requirement: A fixed violation leaves the baseline in the same change
**Priority:** MoSCoW Must

WHEN a developer fixes a violation recorded in the baseline, THE SYSTEM SHALL remove that violation from the store during a local
fitness test run, and the fix and the shrunk store SHALL be committed together. A store entry SHALL NOT be added by hand, and
SHALL NOT be added by a refreeze run, without an owner-approved migration item recorded in the design of this change. A fitness
test SHALL fail when a store entry names the source of a migration item that the change closed, so that a closed violation cannot
return to the baseline unnoticed.

#### Scenario: Local run after a fix
- **WHEN** a developer fixes a recorded violation and runs the fitness tests locally
- **THEN** the store file no longer contains that violation and the test passes

#### Scenario: Store entry added without an item
- **WHEN** a store change adds an entry that no migration item of the design of this change names
- **THEN** the change is rejected in review until the owner approves an item for it

#### Scenario: Closed item re-appears in the store
- **WHEN** a store entry names the source of a migration item that a change closed
- **THEN** the fitness test fails and names the entry

#### Scenario: Store shrinks by the closed items
- **WHEN** a change closes migration items and its store is refreshed
- **THEN** the entry count drops by exactly the number of entries those items held, and no open item's entry is removed
