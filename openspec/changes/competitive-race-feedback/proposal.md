# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It changes what a racer reads and sees between
two maps and in the result chat after each map: the countdown to the next map, a standings sidebar, and each racer's place,
gap and best time. It does not change scoring, the medals, the statistics store, the start countdown's length, the glide-out
or the spawn freeze (`smooth-map-transitions`), or the cup's end screen (`show-colored-map-and-cup-names`).

**Depends on:**

- `show-colored-map-and-cup-names` (`feat(race)`, proposal merged as #356, code not yet implemented). This change reuses its
  name colours, its six-hue palette, its medal colours (`Palette.GOLD`, `SILVER`, `BRONZE`) and its one-accent rule. It does
  not restate them. Its code PR should land first; if it does not, this change's code PR uses the same `Palette` constants
  and its own `Messages` keys.
- `smooth-map-transitions` (`fix(race)`): the between-maps window this change counts down in is the one that change keeps
  (the END phase and the lobby). The two code PRs touch `CupSession`; the fix lands first.

## Why

After a map the owner could not tell how long it would be until the next one. The END screen runs for eight seconds and the
lobby for twenty more, and during that time nothing on screen counts down, so a racer who finished early in the diamond
time waits without knowing for how long.

The result chat after a map is accurate but flat. It lists the points and the medal, and it says nothing about the other
racers. A racer who finished second on a map does not learn that they were 1.4 seconds behind the winner, and a racer who
set a best time does not learn it. The owner's playtest notes say the result does not make anyone want the next map.

## What Changes

- **Between maps.** From the end of a map until the GO of the next, the boss bar reads "Next map in N s" (German "Nächste Map
  in N s") and counts down. A standings sidebar lists the racers who have finished, with their time and the gap to the leader,
  and the racers who did not finish. In the last three seconds the owner's title and pling play (see open question 1 for a
  collision with the start countdown).
- **Result chat, one more line.** Each racer who finished a map gets a line with their place out of the racers who started
  it and the gap to the racer they are chasing: "Place 2 of 5 · +1.4 s behind <name>", or "Place 1 of 5 · 0.8 s ahead of
  <name>" for the first place.
- **Personal best.** When statistics are on and the cup is ranked, the same line carries the racer's personal best for the map:
  "New best! −2.1 s" when the run beats it, or the gap to it when it does not. When statistics are off, the personal best is
  not shown at all, and nothing is claimed about it.

## Capabilities

### New Capabilities

- `race/between-maps-countdown`: the boss bar countdown to the next GO, the standings sidebar and the last-three-seconds cue,
  and when each is shown.
- `race/map-result-standing`: the place and gap line, the personal best line, and what each shows when statistics are off or a
  place does not exist (a did-not-finish racer).

### Modified Capabilities

- `race-feedback`: "The map result lists its points in chat only" is extended. Chat now holds the breakdown line and the
  standing line, once each.

## Out of scope

- The cup's end screen, its placement titles and the cup standings: `show-colored-map-and-cup-names`, `race/cup-finish`.
  The place in this change is per map; the place in that change is per cup.
- The particle and colour of the guide line: `configurable-guide-particles`.
- Computer racers in the place count. Open question 2.
- Persistence itself. Statistics and the personal best are read from `player-stats`; this change adds no store.
- A pause before the hand-off, a play-again item, a hover breakdown.

## Impact

- `voyager/race`: a pure standings calculation and a pure personal-best comparison (`race/standing`), with no Minestom import.
- `voyager/platform`: `cup/CupAnnouncer` (the standing line), `cup/CupSession` (the between-maps phase, the PB read at GO),
  `hud/RaceHud` (the boss bar title between maps), the sidebar in `lobby/`, `text/Messages` and the German keys.
- `docs/reference/hud.md` and `docs/reference/message-style-guide.md`: the countdown, the sidebar, the new chat line and the
  personal best rule.
