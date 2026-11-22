# Spec Delta

## Purpose

With a cup rotation the cup a waiting racer will play next is armed once the previous cup's results have lingered and its racers
have been handed to the hub. The lobby shows that cup, not the one the last round played. Without a rotation the lobby shows the same cup it shows today.

## MODIFIED Requirements

### Requirement: The lobby shows the cup that is currently loaded
**MoSCoW:** Must. THE SYSTEM SHALL show the cup the next round will play, which is the cup a waiting racer waits for. WHERE a cup
rotation is configured, that cup is the rotation's armed cup, and THE SYSTEM SHALL show it from the moment it is armed. WHERE no rotation is configured, that cup is the one the catalogue holder currently holds. A pending reload SHALL NOT change
what the lobby shows until a round starts and promotes it; from that round on the lobby shows the promoted cup. A countdown SHALL keep the
cup it began with.

#### Scenario: A reload waits for the next round
- **WHEN** a reload changes the cup "Grand Tour" to "Grand Tour II" while the room is waiting and no round has started, and no rotation is configured
- **THEN** the lobby still displays "Grand Tour", and the next round that starts plays and displays "Grand Tour II"

#### Scenario: The countdown keeps its cup
- **WHEN** a countdown for "Grand Tour" is running and a reload is applied
- **THEN** the lobby displays keep "Grand Tour" until the round starts

#### Scenario: The rotation shows the next cup once the results have lingered
- **WHEN** "alpha_cup" finishes, its results linger, its racers are handed to the hub, and the rotation is "alpha_cup,legacy_cup"
- **THEN** the sidebar, the hologram and the boss bar name "legacy_cup" and its maps in play order, before any racer joins

#### Scenario: The results screen names the cup that just finished
- **WHEN** "alpha_cup" finishes and its results are shown
- **THEN** the results screen names "alpha_cup", and the lobby does not name "legacy_cup" until the results have lingered

#### Scenario: The rotation shows the first cup at boot
- **WHEN** the server boots with the rotation "alpha_cup,legacy_cup" and no state file
- **THEN** the lobby names "alpha_cup" from the first tick on
