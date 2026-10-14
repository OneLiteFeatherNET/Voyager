# Delta for architecture/vertical-slices

Adds the computer racer slice to the slice map, and the port that keeps the cup free of it. The existing requirements of
this capability are unchanged.

## ADDED Requirements

### Requirement: Computer racers are a slice in race and in platform
**MoSCoW:** Must. THE SYSTEM SHALL organise the computer racer feature as the slice `com` in two modules: `race.com` in
`voyager-race` for the pure movement, planning and tuning, and `platform.com` in `voyager-platform` for the Minestom entity,
packets and names. The `com` slice SHALL own its ports and adapters in both modules.

#### Scenario: The com slice is in both modules
- **WHEN** the computer racer feature is found in the source tree
- **THEN** its pure types are under `net.elytrarace.voyager.race.com..` and its Minestom types under `net.elytrarace.voyager.platform.com..`

#### Scenario: Its settings value type is in api
- **WHEN** the difficulty enum is needed by the settings and the brain
- **THEN** it lives in `net.elytrarace.voyager.api.race.com` in `voyager-api`

### Requirement: The cup depends on a port and never on the computer racer slice
**MoSCoW:** Must. THE SYSTEM SHALL define the port the cup calls for computer racers in `platform.cup`, and SHALL NOT let
`platform.cup` depend on `platform.com`. The computer racer slice implements the port and depends on `platform.cup`.

#### Scenario: No compile path from the cup to a Minestom entity
- **WHEN** the dependencies of `platform.cup` are listed
- **THEN** none is in `net.elytrarace.voyager.platform.com..`

#### Scenario: The port uses only domain and JDK types
- **WHEN** the signatures of the port are read
- **THEN** they use `UUID`, `Vec3`, `RaceClock`, `MapDefinition`, `Competitor` and `List`, and no Minestom type
