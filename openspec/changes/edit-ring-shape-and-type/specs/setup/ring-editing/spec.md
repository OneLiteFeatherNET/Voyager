# Spec Delta

## Purpose

A builder changes a placed ring's type, radius and shape with `/map ring` subcommands, on the ring the builder looks through,
instead of removing it and placing it again from the same pose. An oval is made and turned with the same commands. Each change
is saved before the reply and can be undone.

## ADDED Requirements

### Requirement: Ring commands act on the ring the look ray crosses
**Priority:** MoSCoW Must

WHEN a builder runs a `/map ring` subcommand in an open map, THE SYSTEM SHALL act on the nearest ring that the builder's look ray
crosses within 32 blocks, as the wand's removal does. IF no ring is in reach, THEN THE SYSTEM SHALL change nothing and SHALL tell the
builder that no ring is in reach.

#### Scenario: Ring in reach
- **WHEN** a builder looking through ring 1 of three rings runs `/map ring type boost`
- **THEN** ring 1 is `BOOST`, the other rings are unchanged, and the file is saved

#### Scenario: No ring in reach
- **WHEN** a builder runs `/map ring points 12` while the look ray crosses no ring within 32 blocks
- **THEN** no ring changes, no file is written, and the builder sees the no-ring message

### Requirement: The type of a ring is set by name
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring type <name>`, THE SYSTEM SHALL set the ring's type to the type of that name, which is one of `STANDARD`,
`BOOST`, `SLOW` and `CHECKPOINT`, matched without regard to case. IF the name is none of them, THEN THE SYSTEM SHALL refuse, list the
names, and change nothing.

#### Scenario: Type set
- **WHEN** a builder runs `/map ring type checkpoint` on a `STANDARD` ring
- **THEN** the ring's type is `CHECKPOINT` and the file holds `CHECKPOINT`

#### Scenario: Unknown type
- **WHEN** a builder runs `/map ring type fast`
- **THEN** the server lists the four names and no file changes

### Requirement: The radius of a ring is set in blocks
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring radius <blocks>` on a circle, THE SYSTEM SHALL set its radius to the value. WHEN a builder runs it on an
oval, THE SYSTEM SHALL set the semi-major radius and keep the semi-minor radius and the major axis. IF the value is below the
semi-minor radius of an oval, THEN THE SYSTEM SHALL refuse and change nothing.

#### Scenario: Circle radius set
- **WHEN** a builder runs `/map ring radius 4` on a circle
- **THEN** the ring is a circle of radius 4

#### Scenario: Oval radius below its minor radius
- **WHEN** a builder runs `/map ring radius 2` on an oval with semi-minor radius 3
- **THEN** the server refuses with a message that names both radii, and the oval keeps its radii

### Requirement: An oval is made or resized with its two axes
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring oval <major> <minor>`, THE SYSTEM SHALL make the ring an oval with those semi-major and semi-minor radii,
or set both radii of an oval. IF the minor radius is greater than the major radius, THEN THE SYSTEM SHALL refuse and change nothing. IF the
two radii are equal, THEN THE SYSTEM SHALL make the ring a circle. An existing oval keeps its major axis, and a circle that becomes an
oval takes the default major axis of the ring.

#### Scenario: Circle becomes an oval
- **WHEN** a builder runs `/map ring oval 6 3` on a circle with normal `(0, 0, 1)`
- **THEN** the ring is an oval with semi-major radius 6, semi-minor radius 3, and a major axis that lies in its plane

#### Scenario: Equal axes make a circle
- **WHEN** a builder runs `/map ring oval 4 4`
- **THEN** the ring is a circle of radius 4

#### Scenario: Minor axis above major
- **WHEN** a builder runs `/map ring oval 3 5`
- **THEN** the server refuses with a message that names both values, and the ring is unchanged

### Requirement: An oval becomes a circle again
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring circle` on an oval, THE SYSTEM SHALL make the ring a circle whose radius is the semi-major radius of the oval,
and SHALL drop the semi-minor radius and the major axis.

#### Scenario: Oval becomes a circle
- **WHEN** a builder runs `/map ring circle` on an oval with semi-major radius 6 and semi-minor radius 3
- **THEN** the ring is a circle of radius 6

### Requirement: The turn of an oval turns its major axis in the ring's plane
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring tilt <degrees>` on an oval, THE SYSTEM SHALL set the oval's major axis to the default major axis of the
ring turned by the given angle about the ring's normal. The turn is absolute, and the command never changes the ring's normal. IF the
ring is a circle, THEN THE SYSTEM SHALL refuse and change nothing.

#### Scenario: Turn of an oval
- **WHEN** a builder runs `/map ring tilt 90` on an oval with normal `(0, 0, 1)`
- **THEN** the major axis of the oval is `(-1, 0, 0)`, counter-clockwise seen from the normal's tip, and its normal is still `(0, 0, 1)`

#### Scenario: Second turn replaces the first
- **WHEN** a builder runs `/map ring tilt 30` and then `/map ring tilt 0` on the same oval
- **THEN** the major axis is the default major axis of the ring

#### Scenario: Turn of a circle
- **WHEN** a builder runs `/map ring tilt 30` on a circle
- **THEN** the server refuses with a message to make an oval first, and the file keeps its bytes

### Requirement: The default major axis of a new oval
**Priority:** MoSCoW Must

THE SYSTEM SHALL take as the default major axis of a ring the projection of the world's up axis into the ring's plane, normalised, and
SHALL take world X when the ring's normal is parallel to the up axis, so that the default axis always lies in the ring's plane.

#### Scenario: Wall ring
- **WHEN** a ring with normal `(0, 0, 1)` becomes an oval
- **THEN** its default major axis is `(0, 1, 0)`

#### Scenario: Flat ring
- **WHEN** a ring with normal `(0, 1, 0)` becomes an oval
- **THEN** its default major axis is `(1, 0, 0)`

### Requirement: The points of a ring are an integer count
**Priority:** MoSCoW Must

WHEN a builder runs `/map ring points <n>`, THE SYSTEM SHALL set the ring's points to `n`, IF `n` is an integer from 0 to 100 inclusive, and
otherwise SHALL refuse and change nothing.

#### Scenario: Points set
- **WHEN** a builder runs `/map ring points 12`
- **THEN** the ring has 12 points

#### Scenario: Points above the bound
- **WHEN** a builder runs `/map ring points 101`
- **THEN** the server refuses with a message that names the bound, and no file changes

### Requirement: Radius and axes stay within bounds
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept a radius or an axis of a ring only from 0.5 to 32 blocks inclusive, and SHALL refuse any other value before the ring
is built, naming the value and the bound.

#### Scenario: Radius below the bound
- **WHEN** a builder runs `/map ring radius 0.49`
- **THEN** the server refuses with a message that names 0.5 as the lower bound, and the ring is unchanged

### Requirement: An edit keeps the ring's centre, normal and index
**Priority:** MoSCoW Must

WHEN a ring command changes a ring, THE SYSTEM SHALL keep the ring's index, its centre and its normal.

#### Scenario: Radius change keeps the position
- **WHEN** a builder runs `/map ring radius 5` on ring 2
- **THEN** ring 2 keeps its index, centre and normal, and only its radius changes

### Requirement: Each change is saved before the reply and can be undone
**Priority:** MoSCoW Must

WHEN a ring command changes a ring, THE SYSTEM SHALL save the draft before it replies, SHALL show the changed previews only if the save has
succeeded, and SHALL record the change so that `/map undo` restores the earlier draft. IF the save fails, THEN THE SYSTEM SHALL keep the
previous draft and tell the builder that the change was not saved.

#### Scenario: Save fails
- **WHEN** a builder runs `/map ring type boost` and the save fails
- **THEN** the draft and the file are as before, and the builder sees the save-failed message

#### Scenario: Change is undone
- **WHEN** a builder runs `/map ring points 12` and then `/map undo`
- **THEN** the ring has its earlier number of points

### Requirement: An edit that changes nothing writes nothing
**Priority:** MoSCoW Should

WHEN a ring command would leave the ring as it is, THE SYSTEM SHALL write no file, SHALL record no change, and SHALL reply that the ring is unchanged.

#### Scenario: Same type
- **WHEN** a builder runs `/map ring type standard` on a `STANDARD` ring
- **THEN** no file is written and the builder sees the unchanged message

### Requirement: Ring commands need the setup permission
**Priority:** MoSCoW Must

THE SYSTEM SHALL run each `/map ring` subcommand only for a builder who holds `voyager.setup.use`, as every other `/map` subcommand does.

#### Scenario: Builder without the node
- **WHEN** a builder without `voyager.setup.use` runs `/map ring type boost`
- **THEN** the builder is told the command is denied, and no file changes

### Requirement: Ring command messages exist in English and German
**Priority:** MoSCoW Must

THE SYSTEM SHALL give each message of the ring commands an English and a German text with the same keys and the same placeholders.

#### Scenario: Message key missing in German
- **WHEN** a message key of the ring commands exists in the English bundle and not in the German bundle
- **THEN** the message bundle parity test fails
