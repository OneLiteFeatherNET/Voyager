# Spec Delta

## Purpose

The waiting room tells the racer how many racers are missing on the action bar, shows the start countdown on the experience
bar, and no longer uses a boss bar. The start rules themselves do not change.

## MODIFIED Requirements

### Requirement: Waiting racers are told how many are online and how many are needed
**MoSCoW:** Must. WHILE the minimum is not met, THE SYSTEM SHALL show every waiting racer, on the action bar described in
"Lobby action bar while waiting" of `race/lobby-hud`, the number of racers still needed to start, and the number of racers
online and the number needed. THE SYSTEM SHALL NOT show this line on a boss bar.

#### Scenario: Count shown while waiting
- **WHEN** one racer of a minimum of two is online and waiting for the cup "Grand Tour"
- **THEN** the racer's action bar reads that one more racer is needed to start, in the racer's language, and no boss bar is shown

### Requirement: The lobby countdown reads whole seconds of wall-clock time
**MoSCoW:** Should. THE SYSTEM SHALL display the remaining lobby time in whole seconds of wall-clock time, derived from the
phase length as a duration, on the experience level of each waiting racer, so that a 20 second lobby is never shown as 20
minutes or as ticks. The experience bar SHALL drain from full to empty over the lobby, as `race/lobby-hud` specifies.

#### Scenario: Six seconds left
- **WHEN** six seconds of the lobby remain
- **THEN** the racer's experience level reads 6 and the bar is at 6/20 of its full length

### Requirement: A full lobby shortens the start countdown
**MoSCoW:** Must. IF the online racer count reaches the maximum racer count while the first map's lobby is counting down
AND more than the full-lobby countdown length of that lobby remains, THEN THE SYSTEM SHALL shorten the countdown to the
full-lobby countdown length of 10 seconds, and SHALL play the full-lobby sound of `race/lobby-feedback` once. THE SYSTEM
SHALL NOT lengthen a countdown, and SHALL NOT shorten a countdown after the start is committed.

#### Scenario: A full lobby shortens a long countdown
- **WHEN** the sixteenth racer joins a server with a maximum of sixteen while fifteen seconds of the lobby remain
- **THEN** the countdown is shortened to ten seconds from that tick, and the experience level of each waiting racer reads ten

#### Scenario: A short countdown is left alone
- **WHEN** the sixteenth racer joins while six seconds of the lobby remain
- **THEN** the countdown still ends after six seconds, and no full-lobby sound plays

#### Scenario: A commit is not shortened
- **WHEN** the lobby is full with two seconds of the lobby remaining
- **THEN** the countdown is unchanged and the start remains committed

#### Scenario: A racer leaving does not restore the longer countdown
- **WHEN** the lobby was shortened to ten seconds and one racer leaves with nine seconds left
- **THEN** the countdown is cancelled as in the cancel requirement, and when the lobby is full again the full lobby is shortened to ten seconds

### Requirement: Once a cup has started, its per-tick behaviour is unchanged
**MoSCoW:** Must. ONCE a cup has started, THE SYSTEM SHALL play each tick as the cup-session-characterization capability
specifies, and the lobby gate SHALL NOT change the tick order of that capability.

#### Scenario: The golden scenarios still pass
- **WHEN** the cup-session golden master scenarios run after this change
- **THEN** every transcript equals its committed golden file byte for byte, except the two exceptions named here: the yaw and
  pitch recorded for a racer at the GO tick, which `race/arrival-facing` changes, and the action bar lines recorded while the
  lobby phase runs, which `race/lobby-hud` changes; no other line of any transcript differs

## REMOVED Requirements

### Requirement: The room shows the count and the countdown on a boss bar

The lobby boss bar is removed. Its content moves to the action bar and the experience bar, as `race/lobby-hud` specifies.

### Requirement: Without a lobby the boss bar shows in the first map

The lobby boss bar is removed in every case. Where no lobby world is configured, the waiting racer in the first map sees the
action bar and the experience bar described in `race/lobby-hud`, until the start is committed.
