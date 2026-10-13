# message-style Specification

## Purpose
Defines the rules every player-facing line is written and coloured by: the colour each kind of line uses, the single
glossary, the channel for each kind of information, and the reference document that states them for translators and
authors. It is what keeps the next line written by anyone consistent with the ones already in the bundles.

## Requirements

### Requirement: A failure is shown in the warn colour, a refusal in the caution colour
**MoSCoW:** Must. THE SYSTEM SHALL colour a failure line with the `<c-warn>` palette tag, and a refusal that is information
rather than a failure with `<c-caution>`. The keys `voyager.command.reload.failed`, `voyager.command.reload.denied`,
`voyager.command.denied`, `voyager.setup.refused` and `voyager.setup.save.failed` SHALL be warn; `voyager.command.skip.refused`
and `voyager.setup.ring.none` SHALL be caution.

#### Scenario: A reload that fails
- **WHEN** a catalogue reload fails
- **THEN** its line is coloured with the warn tag

#### Scenario: A skip that is not banked
- **WHEN** an operator asks to skip while nothing is racing
- **THEN** its line is coloured with the caution tag, not the dim tag

### Requirement: Each line uses one accent, and the red colour is never used
**MoSCoW:** Must. THE SYSTEM SHALL write each player-facing line with at most one accent palette tag besides the dim
structure tag, and SHALL NOT use a red colour in any bundle value, in line with the course's own red finish.

#### Scenario: A line with two accents
- **WHEN** a bundle value uses two accent tags in one line
- **THEN** the style test fails and names the key

### Requirement: One glossary governs every race line
**MoSCoW:** Must. THE SYSTEM SHALL use racer for a person in a race in every race line, player only for the vanilla join
phrasing, map for one course, cup for the sequence of maps, ring numbered from 1 for one gate, and points written in full
and never abbreviated.

#### Scenario: A race line naming a person
- **WHEN** a race line names a person taking part in a race
- **THEN** the word is racer, not player

### Requirement: The style guide is the reference for authors and translators
**MoSCoW:** Must. THE REPOSITORY SHALL hold `docs/reference/message-style-guide.md`, which states the voice, the glossary,
the channel rules, the colour table, the timing limits, the sound rules and the German rules, and `Palette` and `Messages`
SHALL link to it.

#### Scenario: A new line
- **WHEN** an author adds a line to a bundle
- **THEN** the guide states which channel, colour and wording the line follows

### Requirement: Channels carry information in the way their kind of surface is read
**MoSCoW:** Should. THE SYSTEM SHALL use chat for the record that stays on screen (results, errors, joins), the action bar
for live state (clock, ring counter, missed ring), a title for one moment (GO, medal, reset, cup), the boss bar for
persistent state, and a sound only to confirm an event, never as the sole carrier of information.

#### Scenario: A missed ring
- **WHEN** a ring is missed
- **THEN** the information is in the action bar, and the sound only confirms it
