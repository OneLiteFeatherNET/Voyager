# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It adds one chat line per human racer after the
cup's final standings, naming the seconds to the next medal on that racer's weakest map. It does not add a button, a timer,
a play-again item, or anything to the end screen's titles.

**Vertical slice:** `race` (the cup round's finish, `race/cup`) and its presentation in `platform/cup` (`CupAnnouncer`).

## Why

After the last map the cup's end is a flat hand-off. The final standings are written to chat (`voyager/platform/src/main/java/net/elytrarace/voyager/platform/cup/CupAnnouncer.java:205-240`,
`announceCupResult`), the end screen lingers for 20 s (`voyager/race/src/main/java/net/elytrarace/voyager/race/flow/RaceTimings.java:54`,
`endAfterLastMap`), and the racer is handed off to the hub. Nothing in that sequence says what to do next.

The game already has the numbers for a goal. Each map's result subtitle names the next goal in seconds
(`openspec/specs/race-feedback/spec.md`, "The map result names the next goal in seconds, when one is reachable"), and
`MedalCountdown.nextGoal` computes it (`voyager/race/src/main/java/net/elytrarace/voyager/race/scoring/MedalCountdown.java:89`).
The cup standings add none of it. The cup-finish spec also forbids adding anything to the end screen
(`openspec/specs/race/cup-finish/spec.md`, "Nothing in the end screen is added beyond the named elements", lines 109-115).

## What Changes

- **One chat line per human racer**, sent after the standings block. It names the map where the racer's weakest medal is, and
  the seconds they would need to save to reach the next medal there: "Next medal: 4.2 s faster on <map> for Gold."
- **Only when that map is in the armed rotation**, so the racer can actually play it next. A racer whose weakest map is not in
  the rotation gets no line.
- **No button, no timer, no extra title.** The end screen's named elements are unchanged; the line is chat only.
- **MODIFIED requirement.** "Nothing in the end screen is added beyond the named elements" is amended to say that the chat line
  is not an end-screen element, and that the line is the only addition. This is an owner decision, because the requirement was
  written on purpose.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `race/cup-finish`: "Nothing in the end screen is added beyond the named elements" is amended for one chat line per human racer.

## Trade-offs

### Pros

- Turns a flat end into a goal, using numbers the game already computes.
- No pressure: no timer, no button, no pop-up; a racer who reads nothing loses nothing.
- Small: one chat line, no new screen.

### Cons

- It amends a deliberate "nothing added" requirement, so the owner must agree the boundary is chat, not the end screen.
- Not actionable when the weakest map is not in the rotation, so the line is absent for some racers.
- The per-map times are not in the cup standings today. `CupScore` (`voyager/race/src/main/java/net/elytrarace/voyager/race/scoring/CupScore.java:15`)
  holds only totals, the best time and the rings; the weakest map needs each racer's per-map medal and time, which must be kept for the round.

## Out of scope

- The end screen's titles, the standings colours and the celebration (`show-colored-map-and-cup-names`).
- A play-again item or any action in the chat line.
- Showing the goal for every map; the line names one.
- The cup's statistics (`player-stats`).

## Open questions

- Owner: amend the end-screen requirement for a chat line, or keep the end flat and drop this change?
- Owner: when several maps tie for weakest, which is named? Proposed: the lowest-ranked medal, then the earliest in the rotation.

## Impact

- `voyager/race`: a pure `WeakestMap` selection over per-map results for a racer, in `race/cup` (no Minestom import).
- `voyager/platform`: `cup/CupAnnouncer` (one line after the standings), `cup/CupSession` (the per-map results kept for the round).
- `voyager/server`: keys in `voyager_en_US` and `voyager_de_DE`.
- `docs/reference/message-style-guide.md` for the new line.
