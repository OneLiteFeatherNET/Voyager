# Proposal: improve-message-ux

**Conventional Commits type and scope:** `feat(race)`. It changes what a racer sees and hears during and after a race
(channels, timing, sounds, one new feedback signal, wording of the results). It adds no German text of its own; the new
and changed lines are written in `en_US` and go through `localise-player-messages` (`feat(i18n)`), which ships first.

**Vertical slices touched:** race feedback in `voyager-platform` (`hud.RaceFeedback`, `hud.RaceHud`, `cup.CupAnnouncer`,
`lobby.WaitingRoom`), message wording in `platform.text` (`Messages`, `Palette`), and the race progress rule in
`voyager-race` (`progress.ProgressTracker`) for the missed-ring signal. Out of scope: physics, catalogue format, setup
wording beyond the wand lines, and persistence.

**Decision altered:** none of the greenfield design spec's numbered decisions. Every change in this proposal follows the
existing palette (ADR-level decision not needed). If the missed-ring detection changes the race rules, the design says so.

## Why

The owner asked for better player UX in general. The inventory and the UX review (Pulse and Glint house rules, applied
to every line) found these, with file references in `design.md`:

1. The GO title is the shortest title in the game: 0.75 s in total, against 1.0 s for each countdown digit.
2. The reset sound and the DNF sound are both the countdown beat at a different pitch, so a reset and a DNF sound like a
   countdown.
3. A missed ring gives no feedback at all. The palette and `RaceFeedback` both reserve it for later.
4. A map result appears three times: a chat line, a title and a subtitle. It never says what the racer should aim for.
5. Every racer is told every join in chat, so a lobby of eight adds eight lines in a few seconds.
6. Failures use the dim colour, so "Reload failed" and "You may not reload" read as hints.
7. The setup wand instruction is one chat line of about 170 characters, a text wall that Pulse's rules forbid.
8. Terminology drifts: "racer" and "player", "points" and "pts", "joined the game" against "Waiting for racers".
9. Personal best is reserved in the palette (`PERSONAL_BEST`) but cannot be earned until `voyager-persistence` exists.

## What changes

- GO title on screen for at least one second (proposal: 20 ticks of hold, 5 ticks of fade-out, 1.25 s in total).
- Distinct sounds for reset and DNF, unlike the countdown beat and the ring bell, chosen by playtest from candidates.
- A missed-ring cue: one quiet sound and one line in the action bar, emitted once per missed ring.
- The map result subtitle carries the points and the next goal ("0.8 s faster for Gold") when a better medal is reachable.
  Medals are time brackets (`MedalBrackets.classify(elapsed, reference)`), so the goal is a time, computed from
  `MedalCountdown.boundaryOf(tier, ...)`, not a ring count.
- Join lines coalesce: joins within a three-second window (60 game ticks, counted, not timed by the wall clock) become
  one line with the count.
- Failure lines switch from `<c-dim>` to `<c-warn>`.
- The setup wand instruction becomes two lines.
- A message style guide in `docs/reference/message-style-guide.md`: voice, glossary, channel rules, colour rules, timing
  limits, and the German rules that the translators follow.

## Out of scope

- Personal-best feedback (waits for persistence). The key and colour stay reserved and unused.
- New sounds beyond the candidates named in the design, and any music.
- Changes to the boss bar, the flight HUD or the lobby boss bar layout, except the wording of their lines.
- Spectator and social features.

## Owner decisions (recorded 2026-10-13)

- UX-1 Map result: chat holds the only breakdown (points and rings). The title shows the medal word. The subtitle
  shows the next goal in seconds, or nothing when no better medal is reachable.
- UX-2 Missed ring: a ring skipped by passing the next one. No near-miss proximity cue in this change.
- UX-3 Join window: 3 s (60 game ticks). Joins inside it coalesce into one line.
- UX-4 Colour: failures use `<c-warn>` (WARN), refusals use `<c-caution>` (CAUTION).
- UX-5 Sounds: the candidates in `design.md` D2 and D3 are the starting point. Final tuning happens in the owner's playtest,
  and the PR lists the candidates played.
- UX-6 Terminology: "racer" and "points" everywhere in race lines. The join line becomes "joined the race".
- UX-7 Times keep the "." decimal separator in both locales (one decimal, as today).
- German tone: informal "du".
- Console (log) lines stay English.
- Minestom's built-in command syntax errors stay as Minestom writes them. Every error text that Voyager's own code sends is
  translated.
- The GO title holds at least 1.0 s (20 ticks), the same as a countdown digit.
- The setup wand instruction is split into short lines (placement, then removal).
