# Message style guide

This page is the reference for every line the game says to a player: the voice, the words, the channel, the colour, the
timing and the sound. It applies to the English bundle and to the German bundle. Translators and authors read it before
they add a key. The rules are enforced where a test can enforce them, and the test is named in each section.

The architecture (keys, bundles, fallback, placeholders) is in [ADR-0038](../decisions/0038-player-message-i18n.md). The
workflow for adding a key is in [How to add a translation](../guides/how-to-add-a-translation.md).

## Voice

- Short and concrete. A line states a fact the racer can act on, or the result of an action they took.
- Address the racer as "you" in English and as "du" in German (see German below).
- No instruction walls. A line that needs more than one sentence is split across channels or lines (for example, the setup
  wand instruction is two chat lines).
- Numbers, not adjectives. "0.8 s faster for Gold", not "a little faster".

## Glossary

| Word | Use | Do not use |
|---|---|---|
| **racer** | a person taking part in a race, in every race line | player, pilot, rider |
| **player** | only the vanilla join phrasing the client also writes | racer, in a vanilla join line |
| **map** | one course, with its rings | level, track, stage |
| **cup** | the sequence of maps a racer plays through | series, round |
| **ring** | one gate, numbered from 1 | checkpoint, gate (in player text) |
| **medal** | Diamond, Gold, Silver, Bronze, Finish, DNF | trophy, badge |
| **points** | written in full | pts, P |

German equivalents are fixed in the German bundle's glossary comment and must match this table.

## Channels

Each kind of information has one channel. Choose the channel before the wording.

| Channel | Carries | Stays on screen | Example |
|---|---|---|---|
| Chat | the record: results, errors, joins, the map breakdown | until the next message | the map result breakdown line |
| Action bar | live state under the crosshair: the clock, the ring counter, a missed ring | until replaced | `Ring 5 missed` |
| Title and subtitle | one moment: GO, the medal, a reset, the cup result | the title's hold time | `GOLD`, `0.8 s faster for Gold` |
| Boss bar | persistent state: map progress, the lobby count | until the state changes | `Map 2/4 · 12.4 s` |
| Sound | confirmation of an event | not on screen | the ring bell |

Rules:

- A fact appears in one place for its numbers. The map breakdown is in chat; the title and subtitle name the medal and
  the next goal (not the breakdown again).
- A sound confirms an event. It never carries information on its own: the same event is also on screen.
- At most three UI elements are visible to a racer at the same time.

## Colour

The palette is defined once, in `Palette.java`, and written in the bundles as tags. No bundle contains a hex code.

| Tag | Role | Used for |
|---|---|---|
| `<c-dim>` | structure | labels, separators, the rest of a line |
| `<c-value>` | a value the racer reads | a time, a count, a name |
| `<c-speed>` | speed, boost, the game's own voice | GO, the cup title |
| `<c-ok>` | something went right | a finish, a ring passed |
| `<c-caution>` | attention, not a failure | the countdown, a refusal that is information |
| `<c-warn>` | a failure, act now | a missed ring, a DNF, a failed command |

Rules (enforced by `MessageStyleTest` once `improve-message-ux` lands):

- One accent per line. A colour with two jobs has none.
- Failures use `<c-warn>`. Refusals that are information use `<c-caution>`. Neither is `<c-dim>`.
- Red is never used, in any form. The course marks its finish in red.

## Timing

| Surface | Limit |
|---|---|
| GO title | held at least 20 ticks (1.0 s) before fade-out |
| Countdown digit | 15 ticks, fade-out 5 ticks (1.0 s in total); each digit gone before the next |
| Other titles | held 1.0 s to 4.0 s, never longer than the moment needs |
| Action bar line | one line, replaced or cleared when the state ends |
| Feedback that acknowledges a ring | within 1 to 2 game ticks of the event (100 ms rule) |

## Sound

- Each meaning has one sound. The countdown beat is for a countdown digit only. The ring bell and the last-ring chime
  are for a ring pass only. A reset, a DNF and a missed ring each have their own sound.
- A loss is quieter than a gain, and informational rather than punishing.
- A sound that plays more than ten times in one race varies in pitch, timing or timbre.

## German

- Use "du", unless the owner has chosen "Sie" (open item 5 of ADR-0038).
- Keep every `<arg:N>` and palette tag, in the same count as the English line. A translation may reorder the arguments
  (by index) and reword the words around them, but may not add or drop one.
- Use the German glossary equivalents from the table above. Keep "Ring", "Cup" and "Map" as they are.
- Race times keep the point as the decimal separator, so that they match the boss bar, the logs and the leaderboards
  (open item 4 of ADR-0038, pending the owner).
- Plurals are two keys (`.one` and `.other`). Write both; the parity test requires them.
- Medals: Diamant, Gold, Silber, Bronze, Ziel, DNF. Keep the English abbreviation DNF.

## Where the rules are checked

| Rule | Checked by |
|---|---|
| Every key in both locales, with the same placeholders and tags | `MessageBundleParityTest` (`voyager-fitness`) |
| No `{N}` in any value | the bundle loader, and the parity test |
| No literal player-facing string outside `platform.text` | `ApiPurityTest.onlyTheTextPackageBuildsAUserFacingString` |
| Failure and refusal colours, one accent per line, no red | `MessageStyleTest` (`voyager-platform`), added by `improve-message-ux` |
| Channel and timing choices | review against this page; the timing values are asserted in `RaceFeedbackTimingTest` |
