# Spec Delta

## Purpose

A draft edit changes the fields the edit names and keeps every other field. Until this change, a ring placement, a ring
removal and a spawn set rebuilt the draft without its display name and authors, so the next save wrote a map without them.

## ADDED Requirements

### Requirement: Every draft edit keeps the display name and the authors
**Priority:** MoSCoW Must

WHEN a ring is appended to a draft, a ring is removed from a draft, or a spawn is set on a draft, THE SYSTEM SHALL produce
the edited draft with the same `displayName` and the same `authors` as the draft it edits, and SHALL change no other field
except the rings or the spawn that the edit names.

#### Scenario: Appended ring keeps the name and the authors
- **WHEN** a ring is appended to a draft whose display name is "Blue & Red" and which has two authors
- **THEN** the edited draft has the display name "Blue & Red" and the same two authors, and the appended ring is the last ring

#### Scenario: Removed ring keeps the name and the authors
- **WHEN** ring 1 of three is removed from a draft whose display name is "Blue & Red" and which has two authors
- **THEN** the edited draft has the same display name and the same two authors, and its rings are numbered 0 and 1

#### Scenario: Spawn set keeps the name and the authors
- **WHEN** a spawn is set on a draft whose display name is "Blue & Red" and which has two authors
- **THEN** the edited draft has the new spawn, the same display name and the same two authors

#### Scenario: Draft without a name or authors stays without them
- **WHEN** a ring is appended to a draft that has no display name and no authors
- **THEN** the edited draft has no display name and no authors

### Requirement: A saved draft keeps the name and the authors after a ring edit
**Priority:** MoSCoW Must

WHEN a ring is placed on a map whose display name and authors are set, and the draft is saved and then loaded again,
THE SYSTEM SHALL load the same display name and the same authors that the map had before the edit.

#### Scenario: Ring appended and saved
- **WHEN** a map with the display name "Blue & Red" and two authors gets a ring, the draft is saved, and the file is loaded again
- **THEN** the loaded draft has the display name "Blue & Red" and both authors, and holds the new ring
