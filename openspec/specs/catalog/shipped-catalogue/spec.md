# catalog/shipped-catalogue Specification

## Purpose
Defines the content the server boots with by default from the shipped data directory: which cups ship, the map the
playable cup plays, and what the shipped map states (spawn, boost tuning, ring count and ring types, world, clean load).
It is the contract the first closed alpha is judged against. The loader rules stay in `catalog-loading` and
`map-file-format`, and the boot-time cup rules in `cup-validation`; this capability says what the shipped files must
satisfy under those rules.

Requirements are written in EARS form and prioritised with MoSCoW (Must, Should, Could, Won't). A Must that is not met
fails the alpha.

## Requirements

### Requirement: Exactly one cup ships, named alpha_cup
Priority: Must.

THE shipped catalogue SHALL provide exactly one cup, named `alpha_cup`, and SHALL NOT ship `test_cup` or any other cup file.

#### Scenario: the catalogue lists one cup
- **WHEN** the shipped data directory is loaded through the catalogue loader
- **THEN** the snapshot lists exactly one cup, named `alpha_cup`

#### Scenario: no other cup file is shipped
- **WHEN** the shipped `cups/` directory is listed
- **THEN** it contains `alpha_cup.json` and no other cup file

### Requirement: The alpha cup plays the one shipped map
Priority: Must.

THE `alpha_cup` cup SHALL name exactly one map in its `mapNames`, `elytraraceblueandred`, and that name SHALL resolve to
the shipped map file.

#### Scenario: the rotation is the one map
- **WHEN** `alpha_cup` is resolved from the shipped catalogue
- **THEN** its maps are `elytraraceblueandred` and nothing else

#### Scenario: a map name that resolves to nothing is refused
- **WHEN** a map name of `alpha_cup` has no shipped map file
- **THEN** the catalogue reports a problem naming the cup and the missing map, and the shipped catalogue does not load cleanly

### Requirement: A server with no cup named plays the alpha cup
Priority: Must.

WHILE the shipped catalogue holds exactly one cup, WHEN the server boots from the shipped data directory without a cup
name, THE server SHALL select `alpha_cup` and SHALL NOT refuse boot as ambiguous.

#### Scenario: boot without a cup name
- **WHEN** the server boots from the shipped data directory without a cup name and without `-Pcup` or `VOYAGER_CUP`
- **THEN** it selects `alpha_cup` and does not refuse boot as ambiguous

### Requirement: The shipped map has at least eight rings in sequence
Priority: Must.

THE shipped map `elytraraceblueandred` SHALL have at least eight rings, and its ring indices SHALL be `0` to `n-1` in file
order, with no gaps.

#### Scenario: the ring minimum is met
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** it has 35 rings, indexed from 0 to 34

#### Scenario: a map with fewer than eight rings fails the check
- **WHEN** a map of `alpha_cup` holds seven rings
- **THEN** the shipped-catalogue check fails and names the map and its ring count

### Requirement: The shipped map carries at least two BOOST rings
Priority: Must.

THE shipped map `elytraraceblueandred` SHALL carry at least two rings of type `BOOST`, so that the boost ring effect is
exercised by the alpha.

#### Scenario: the boost rings are present
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** at least two of its rings have type `BOOST`

#### Scenario: the boost rings are the chosen ones
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** its `BOOST` rings are at indices 0, 18 and 27, and every other ring is `STANDARD`

### Requirement: A BOOST ring is not the final ring and starts a straight segment
Priority: Should.

WHERE a ring is of type `BOOST`, THE ring SHALL NOT be the final ring of its map, and the segment from that ring to the
next SHALL have no guide point in its gap, so that the segment is straight.

#### Scenario: the final ring is not a boost ring
- **WHEN** `elytraraceblueandred` is loaded
- **THEN** ring 34, the final ring, is `STANDARD`

#### Scenario: each boost ring starts a guide-free segment
- **WHEN** the guide points of `elytraraceblueandred` are checked against each boost ring's gap
- **THEN** no guide point has an order index strictly between `100 * i` and `100 * (i + 1)` for each boost ring `i`

### Requirement: The shipped catalogue loads without any problem
Priority: Must.

WHEN the shipped data directory is loaded through the catalogue loader, THE loader SHALL report no problem for any cup or
map file.

#### Scenario: a clean load
- **WHEN** the shipped data directory is loaded
- **THEN** the loader reports no problem for any cup or map file

### Requirement: The shipped map states its spawn point and where it came from
Priority: Must.

THE shipped map SHALL state an explicit spawn point with `x`, `y` and `z`, and its `notes` SHALL name `level.dat` as the
source of that spawn, so that a placeholder cannot pass as a measured value.

#### Scenario: a spawn is stated and sourced
- **WHEN** `elytraraceblueandred.json` is read
- **THEN** it contains a `spawn` with `x`, `y` and `z`, and its `notes` name `level.dat` as the source of the spawn

### Requirement: The shipped map's boost pair is consistent
Priority: Must.

THE shipped map SHALL carry a `boostConfig` whose burn duration is shorter than its cooldown, both in ticks.

#### Scenario: the boost pair is consistent
- **WHEN** the shipped map's boost configuration is loaded
- **THEN** its burn duration is 30 ticks, less than its cooldown of 40 ticks

### Requirement: Seeded values are marked provisional in the file
Priority: Should.

THE shipped map's `notes` SHALL mark its reference time, its per-ring points, its guide-line values and its BOOST ring
choice as provisional, so that the balancing pass and the playtest can tell them from measured values.

#### Scenario: the BOOST choice is marked
- **WHEN** the shipped map's notes are read
- **THEN** the notes name rings 0, 18 and 27 as provisional BOOST rings and say they are to be tuned after the playtest

### Requirement: The shipped map keeps its geometry
Priority: Must.

THE change SHALL alter only the ring types of `elytraraceblueandred`; ring centres, normals, radii, points and guide points
SHALL stay as committed, so that the racing line and its measured budget are unchanged.

#### Scenario: the ring centres are unchanged
- **WHEN** `elytraraceblueandred` is loaded after this change
- **THEN** every ring centre equals its committed value and the racing-line test over the course still passes unchanged

### Requirement: The validation check finds the alpha world
Priority: Must.

WHEN `validateCatalog` runs against a worlds directory that holds `ElytraraceBlueAndRed`, THE check SHALL exit with code 0
and report the world as present; IF the world folder is missing, THEN the check SHALL exit with code 1 and name the
missing world directory.

#### Scenario: the world is present
- **WHEN** `validateCatalog -PworldsPath=<dir>` runs and `<dir>/ElytraraceBlueAndRed` holds region data
- **THEN** it exits with code 0 and reports the world present

#### Scenario: the world is missing
- **WHEN** `validateCatalog` runs and `ElytraraceBlueAndRed` is not under the worlds path
- **THEN** it exits with code 1 and names the missing world directory

### Requirement: The three converted maps are not shipped
Priority: Must.

THE shipped data directory SHALL NOT contain `nether_sprint`, `frozen_cathedral` or `skyward_drift` map files until their
worlds exist under the worlds path.

#### Scenario: the converted maps stay out
- **WHEN** the shipped `maps/` directory is listed
- **THEN** it contains `elytraraceblueandred.json` and none of the three converted map files
