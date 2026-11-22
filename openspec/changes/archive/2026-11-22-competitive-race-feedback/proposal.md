# Proposal

**Type and scope:** `feat(race)`. Ships as one code change under this title. It changes what a racer reads and sees between
two maps and in the result chat after each map: the countdown to the next map, a standings sidebar, and each racer's place,
gap and best time. It does not change scoring, the medals, the statistics store, the start countdown, the glide-out or the
placement (`smooth-map-transitions`), or the cup's end screen (`show-colored-map-and-cup-names`).

**Depends on:**

- `show-colored-map-and-cup-names` (`feat(race)`, proposal merged as #356, code not yet implemented). This change reuses its name
  colours, its six-hue palette, its medal colours and its one-accent rule. It does not restate them.
- `smooth-map-transitions` (`fix(race)`). Racers stand in the next map while the wait runs, so the countdown and the standings
  must work there. That change's code lands first, or the two are merged together.

## Why

After a map the owner could not tell how long it would be until the next one. Nothing on screen counts down while the END screen
and the next lobby run, so a racer who finished early in the diamond time waits without knowing for how long.

The result chat after a map is accurate but flat. It lists the points and the medal, and it says nothing about the other racers.
A racer who finished second does not learn they were 1.4 seconds behind the winner, and a racer who set a best time does not
learn it.

## What Changes

- **Countdown.** From the end of a map until the next map's GO, the boss bar reads "Next map in N s" (German "Nächste Map in N s")
  and counts down. The boss bar stays the only countdown. The start countdown's title and beat are unchanged, and no cue is
  added on top of them.
- **Standings sidebar.** Between the maps the sidebar lists the racers who played the map, finishers by time with the gap to the
  leader, then those who did not finish. A racer who arrives in the next map early sees the same sidebar live, with the racers
  still on the previous map marked as racing, until the phase ends.
- **Computer racers count.** Computer racers count in "Place n of m" and in the gaps, as players do. A computer racer has no
  line of its own.
- **Place line.** Each racer who finished a map gets a chat line with their place out of the racers who started it and the gap to
  the racer they are chasing: "Place 2 of 5 · +1.4 s behind <name>", or "Place 1 of 5 · 0.8 s ahead of <name>".
- **Personal best.** When statistics are on and the cup is ranked, a human racer's line carries their personal best for the map:
  "New best! −2.1 s", or the gap to it. When statistics are off, no best is shown and nothing is claimed about one. No
  session-only best is kept.

## Capabilities

### New Capabilities

- `race/between-maps-countdown`: the boss bar countdown to the next GO, its title while a racer is already in the next map, and
  the standings sidebar and when it is shown.
- `race/map-result-standing`: the place and gap line, the personal best clause, the ranked and statistics rules, and what a
  computer racer contributes.

### Modified Capabilities

- `race-feedback`: "The map result lists its points in chat only" is extended. Chat holds the breakdown line and the place line,
  once each.

## Out of scope

- The cup's end screen, placement titles and cup standings: `show-colored-map-and-cup-names`. The place here is per map.
- The guide line's particle and colour: `configurable-guide-particles`.
- Persistence itself. The personal best is read from `player-stats`; this change adds no store.
- A session-only best when statistics are off. The owner declined it.
- A pause before the hand-off, a play-again item, a hover breakdown.
- A pling or any extra cue in the last three seconds. The owner declined it.

## Impact

- `voyager/race`: a pure standings calculation and a pure personal-best comparison (`race/standing`), no Minestom import.
- `voyager/platform`: `cup/CupAnnouncer` (the place line), `cup/CupSession` (the wait, the personal best read at GO), `hud/RaceHud`
  (the boss bar title), the sidebar in `lobby/`, `text/Messages` and the German keys.
- `docs/reference/hud.md` and `docs/reference/message-style-guide.md`: the countdown, the sidebar, the place line and the
  personal-best rule.
