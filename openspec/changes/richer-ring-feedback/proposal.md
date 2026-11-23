# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It changes what a racer sees and hears at the
moment a ring is passed: the points the ring gave, the checkpoint save, and the boost and slow rings. It does not change
scoring, the placement bonus, the map result chat, or the wording of the missed-ring line.

**Vertical slice:** `race` (ring pass and checkpoint facts from `race/flow` and `RaceRun`) and its presentation in
`platform/hud` and `platform/cup`.

## Why

A ring pass is acknowledged by a bell and by a colour change, and nothing else. In
`voyager/platform/src/main/java/net/elytrarace/voyager/platform/hud/RaceFeedback.java:107-110` the bell is the whole
audio cue, and `hud/RaceHud.java:57` and `hud/RaceHud.java:217-220` turn the ring counter green for `RING_FLASH_TICKS = 8`,
400 ms. The counter is the only visual cue, and it is colour alone.

The points a ring is worth are never shown in flight. The action bar carries the clock and the ring count
(`voyager/server/src/main/resources/voyager_en_US.properties:21`), and the race-feedback spec says the in-race subtitle has no
points (`openspec/specs/race-feedback/spec.md:51`, `:63`). A racer learns what a ring was worth only in the chat after the map.

Two further gaps sit in the same code path. `RaceFeedback.ringSound(ringIndex, ringCount)`
(`RaceFeedback.java:119-124`) takes no ring type, although `Ring` carries one
(`voyager/api/src/main/java/net/elytrarace/voyager/api/race/Ring.java:14`, `RingType.java:10-12`). A BOOST or SLOW ring
therefore sounds like a plain ring. Finally, the tutorial promises that "Passing a checkpoint ring saves your place"
(`voyager_en_US.properties:76`), yet passing a `RingType.CHECKPOINT` plays the plain ring bell and shows no save message.

## What Changes

- **Points marker.** For the flash window after a ring, the action bar shows "+N" (N = the ring's points) in the fixed-width
  pad that already holds the ring counter, in the palette's OK colour. The counter's green and the ring sound stay as they
  are, so the cue is no longer colour alone.
- **BOOST and SLOW.** A BOOST or SLOW ring plays its own sample instead of the ring bell and shows a 1.5 s action-bar line,
  "Boost" or "Slow", in the pattern of the missed-ring line (`RaceHud.missedRing`, `RaceHud.java:132`).
- **Checkpoint.** Passing a `CHECKPOINT` ring shows "Checkpoint N saved" for 1.5 s in the action bar and plays one checkpoint
  sample. N is the checkpoint's ordinal in the map, read from the race state.
- **Precedence.** When lines compete for the action bar, the missed-ring line wins, then the checkpoint or boost line, then
  the flight HUD. A pre-empted line is dropped, not queued.
- **Sound set.** The new samples stay out of the lobby's sample set (`RaceFeedback.sampleKeysLobbyMustNotShare`,
  `RaceFeedback.java:95-101`), which `LobbySoundsDistinctTest` reads.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race/race-hud`: "The action bar labels its clock and its ring count" gains the points marker and the precedence of the
  action-bar lines.
- `race-feedback`: "Each sound means one thing, and no two meanings share a sound" gains the BOOST, SLOW and checkpoint samples;
  "A missed ring is signalled once, in the action bar and by sound" gains the precedence rule.

## Trade-offs

### Pros

- The ring cue no longer depends on colour alone, which matters to racers who do not separate the green from the grey.
- The reward is immediate, at the moment of the pass, and uses numbers the game already has.
- Reuses the tested action-bar line pattern (`missedRing`) and the flash timing; no new channel.

### Cons

- `RaceHud` flight-HUD arguments change, so the golden transcripts that record the action bar change with them.
- The action bar gets crowded: a missed ring, a checkpoint line and a points marker can overlap within a second.
- The new sound samples must be checked against the lobby set, and their 26.2 registry entries are not verified yet.

## Out of scope

- Scoring, medals and the placement bonus (`weight-time-in-score`).
- The end-of-map chat and subtitle (`race-feedback`, "The map result lists its points in chat only", unchanged).
- The guide line's particle and colour (`configurable-guide-particles`).
- A per-racer switch for these cues, and any particle effect on the ring.

## Open questions

- Owner: should a zero-point ring show "+0"? Proposed: show it, so a racer is not left guessing whether the pass counted.
- Owner: may the checkpoint line (1.5 s) sit beside the missed-ring line without a queue, or should a checkpoint wait its turn?

## Impact

- `voyager/race`: no rule changes; reads `Ring.points()`, `Ring.type()` and the checkpoint ordinal from `RaceRun`.
- `voyager/platform`: `hud/RaceFeedback` (`ringSound` takes the `RingType`; the BOOST, SLOW and checkpoint cues), `hud/RaceHud`
  (the points marker, the new lines and their precedence), `cup/CupAnnouncer` (the call at `CupAnnouncer.java:80`).
- `voyager/server`: `voyager_en_US` and `voyager_de_DE` keys with `<arg:N>` placeholders.
- `docs/reference/hud.md` for the marker, the lines and their precedence.
