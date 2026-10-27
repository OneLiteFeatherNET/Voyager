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
| Action bar | live state under the crosshair: the clock and the ring counter, each labelled, and a missed ring | until replaced | `Time 0:02.1 · Rings 0/35`, `Ring 5 missed` |
| Title and subtitle | one moment: GO, the medal, a reset, the cup result | the title's hold time | `GOLD`, `0.8 s faster for Gold` |
| Boss bar | persistent state of a map: map progress, the medal band and the time left in it | until the state changes | `Map 2/4 · Diamond · 12.4 s left` |
| Action bar (lobby) | the waiting count, or the start countdown, while the room waits; the experience bar counts the start down | until the room starts | `Need 1 more player to start`, `Start in 12 s` |
| Sidebar (lobby) | the cup, its maps, the racers online, the status and a footer, one per racer | while the room waits | `Racers 3/16`, `starts in 12 s` |
| Sound | confirmation of an event | not on screen | the ring bell |

Rules:

- A fact appears in one place for its numbers. The map breakdown is in chat; the title and subtitle name the medal and
  the next goal (not the breakdown again).
- A sound confirms an event. It never carries information on its own: the same event is also on screen.
- At most three UI elements are visible to a racer at the same time.
- A missed ring is on the action bar for 1.5 s (thirty ticks), in the warn colour, and replaces the flight line for that
  span only. The clock is back on the next refresh.
- A burst of joins within three seconds (sixty game ticks) is one chat line with the count. A single join is told at once.

### Map result

| Surface | Says |
|---|---|
| Chat | the breakdown: rings, medal points, placement bonus, total |
| Title | the medal word |
| Subtitle | the next goal in seconds, for example `0.8 s faster for Gold`; omitted when no better medal is reachable |

A DNF's subtitle is the rings reached (`23 of 35 rings`) and carries no points. The points stay in chat.

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

Rules (enforced by `MessageStyleTest` in `voyager-fitness`):

- One accent per line. A colour with two jobs has none.
- Failures use `<c-warn>`. Refusals that are information use `<c-caution>`. Neither is `<c-dim>`.
- A line has at most one accent. The dim tag is structure and does not count. A medal word keeps the colour of its
  medal (`<m-gold>`), which is its identity and not a second accent.
- Red is never used, in any form. The course marks its finish in red.
- Every race HUD text colour reads at 4.5 to 1 or more on the dark backdrops `#101010` and `#1E1E1E`. `PaletteContrastTest`
  (`voyager-platform`) checks each colour and names one that fails.

### Medal words

- The boss bar names the medal band in words, with the time left in it: `Diamond · 57.9 s left` in English and
  `Diamant · noch 57.9 s` in German. The phrase takes the band's own colour, and the bar itself is one fixed colour for every
  band, so the title is what names the medal.
- The finish band has no countdown and is named alone: `Finish`, or `Ziel` in German.
- Results keep the capitals medal words (`DIAMOND`, `GOLD`, and so on) in the title and in chat. The boss bar does not use them.

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

Candidates in use (`RaceFeedback`). The owner's playtest chooses the final sounds; each candidate played is listed in the
pull request that changes it.

| Event | Sound | Pitch | Volume | Note |
|---|---|---|---|---|
| Countdown digit | `block.note_block.bass` | 1.0 to 1.4 | 0.8 | the beat, for a digit only |
| Ring passed | `block.note_block.bell` | 1.0 to 1.8 | 0.7 | rises with the ring number |
| Last ring | `ui.toast.challenge_complete` | 1.0 | 0.8 | not a higher bell |
| Reset | `block.note_block.chime` | 0.7 | 0.8 | falling, not the beat |
| DNF | `block.note_block.bit` | 0.6 | 0.6 | quieter than any medal |
| Missed ring | `block.note_block.didgeridoo` | 0.5 | 0.5 | quiet, not the bell or the beat |
| Medal | `ui.toast.challenge_complete`, `entity.player.levelup`, `block.note_block.pling` | as in `RaceFeedback` | 0.9 to 1.0 | none is the countdown beat |

## German

- Use "du". The owner chose it (ADR-0038, decision 5).
- Keep every `<arg:N>` and palette tag, in the same count as the English line. A translation may reorder the arguments
  (by index) and reword the words around them, but may not add or drop one.
- Use the German glossary equivalents from the table above. Keep "Ring", "Cup" and "Map" as they are.
- Race times keep the point as the decimal separator, so that they match the boss bar, the logs and the leaderboards
  (ADR-0038, decision 4).
- Plurals are two keys (`.one` and `.other`). Write both; the parity test requires them.
- Medals: Diamant, Gold, Silber, Bronze, Ziel, DNF. Keep the English abbreviation DNF.

## Where the rules are checked

| Rule | Checked by |
|---|---|
| Every key in both locales, with the same placeholders and tags | `MessageBundleParityTest` (`voyager-fitness`) |
| No `{N}` in any value | the bundle loader, and the parity test |
| No literal player-facing string outside `platform.text` | `ApiPurityTest.onlyTheTextPackageBuildsAUserFacingString` |
| Failure and refusal colours, one accent per line, no red | `MessageStyleTest` (`voyager-fitness`) |
| Channel and timing choices | review against this page; the timing values are asserted in `RaceFeedbackTimingTest` |
