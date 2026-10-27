# Spec Delta

## Purpose

The lobby gate keeps its tick order. One recorded value changes: the facing a racer has at the GO tick, which
`race/arrival-facing` fixes. Every other line of the cup-session transcript stays as it is.

## MODIFIED Requirements

### Requirement: Once a cup has started, its per-tick behaviour is unchanged
**MoSCoW:** Must. ONCE a cup has started, THE SYSTEM SHALL play each tick as the cup-session-characterization capability
specifies, and the lobby gate SHALL NOT change the tick order of that capability.

#### Scenario: The golden scenarios still pass
- **WHEN** the cup-session golden master scenarios run after this change
- **THEN** every transcript equals its committed golden file byte for byte, except the yaw and pitch recorded for a racer at
  the GO tick, which `race/arrival-facing` changes to face ring 0; no other line of any transcript differs
