# Spec Delta

## Purpose

The wand still only appends and removes rings. The ring commands of `setup/ring-editing` change a ring's type, radius and shape,
so the rule that no ring field may change is replaced by a rule on position: a ring keeps its index and its place in the order.

## REMOVED Requirements

### Requirement: Rings are only appended and removed in this change
**Reason**: `setup/ring-editing` changes a placed ring's type, radius, axes and points. The rule that no field may change is
replaced by the rule below, which keeps the order of the rings.

## ADDED Requirements

### Requirement: Rings are never inserted or reordered
**Priority:** MoSCoW Must

THE SYSTEM SHALL NOT insert a ring between existing rings or reorder rings, and SHALL NOT change the index, the centre or the normal
of a ring through any command or click other than the placement and removal of `setup/ring-placement`.

#### Scenario: Insertion requested
- **WHEN** any command or click would insert a ring at an index below the last
- **THEN** the system has no such operation and the draft is unchanged

#### Scenario: A field edit keeps the order
- **WHEN** a builder runs `/map ring type boost` on ring 1 of three rings
- **THEN** the draft still holds three rings in the same order, each with its index
