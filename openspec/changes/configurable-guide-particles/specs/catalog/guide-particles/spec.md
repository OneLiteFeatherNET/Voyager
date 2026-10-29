## ADDED Requirements

### Requirement: Every shipped map names its guide particle
**MoSCoW:** Must. THE SYSTEM SHALL ship every map in the shipped catalogue with an explicit `particle` in its `guideLine`, one of
`END_ROD` or `DUST`. WHEN the particle is `DUST`, THE SYSTEM SHALL also ship a `dustColor` for the map.

#### Scenario: A shipped map names both fields
- **WHEN** the shipped catalogue is read
- **THEN** each map's `guideLine` names a `particle`, and each `DUST` map names a `dustColor`

#### Scenario: A shipped map with no particle
- **WHEN** a shipped map's `guideLine` names no `particle`
- **THEN** the shipped-catalogue test fails and names the map

### Requirement: palm_beach is drawn in deep violet, and the other shipped maps in white
**MoSCoW:** Must. THE SYSTEM SHALL ship the map `elytrarace:palm_beach` with particle `DUST` and colour `#7A1FB8`, and SHALL ship
`elytrarace:dragon_lobby`, `elytrarace:blue_and_red` and `elytrarace:memoria` with particle `END_ROD`.

#### Scenario: palm_beach
- **WHEN** the shipped `elytrarace:palm_beach` is loaded
- **THEN** its line is drawn as `DUST` in `#7A1FB8`

#### Scenario: The other shipped maps
- **WHEN** a shipped map other than `elytrarace:palm_beach` is loaded
- **THEN** its line is drawn as `END_ROD`

### Requirement: A map that names no particle is drawn as END_ROD
**MoSCoW:** Must. WHEN a map's `guideLine` names no `particle`, THE SYSTEM SHALL draw its line as `END_ROD`, as
`catalog/map-file-format` states. This default is not changed by this change.

#### Scenario: A map without the field
- **WHEN** a map that names no particle is loaded
- **THEN** its line is drawn as `END_ROD`, and its other fields load as before

### Requirement: Saving a loaded map keeps its guide particle and colour
**MoSCoW:** Should. WHEN the setup server saves a map it loaded, THE SYSTEM SHALL write the map's `particle` and, for `DUST`, its
`dustColor`, unchanged.

#### Scenario: A save with DUST
- **WHEN** a map that names `DUST` and `#7A1FB8` is loaded and saved by the setup server
- **THEN** the saved file names `DUST` and `#7A1FB8`

### Requirement: palm_beach's DUST line is an accepted exception to the per-racer packet cap
**MoSCoW:** Must. THE SYSTEM SHALL accept, for `elytrarace:palm_beach` only, a per-racer packet count above the limit of
`race/racing-line-display`, as the owner decided on 2026-10-29, and SHALL keep the total within its 30,000 limit.

#### Scenario: palm_beach at full race size
- **WHEN** sixteen racers are spread along palm_beach, each in FULL mode, with the 160-block reach applied
- **THEN** the total particle packets per second do not exceed 30,000, and no racer is sent more than 4,000 per second.
  The measured figures on 2026-10-28 were 29,800 in total and 3,690 for the worst racer, pinned by a test so they cannot
  get worse without a new decision. END_ROD would send the worst racer about 2,460 (from the refresh ratio, not measured),
  so the excess is not caused by DUST alone.

#### Scenario: The exception does not extend to the shipped course
- **WHEN** sixteen racers are spread along `elytrarace:blue_and_red`, each in FULL mode
- **THEN** no racer is sent more than 2,000 packets per second, as `race/racing-line-display` requires
