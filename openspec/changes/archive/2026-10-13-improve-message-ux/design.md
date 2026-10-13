# Design: improve-message-ux

## Context and method

The review applied two house rule sets to every player-facing line and every sound, read from
`.claude/agents/voyager-game-psychologist.md` (Pulse) and `.claude/agents/voyager-junior-frontend.md` (Glint):

- Glint: at most three UI elements visible at once; one consistent colour scheme; senior review before merge.
- Pulse: feedback under 100 ms for gameplay-critical moments; anti-habituation (a sound played more than ten times in a race
  varies in pitch, timing or timbre); losses are quieter and informational, never punishing; one clear next goal at the
  end of a race; no text walls; numbers, not adjectives.
- The palette (`Palette.java`): one accent per line; red is never used (the course's own finish line is red); magenta
  (`WARN`) means "act now".

Where a rule conflicts with the current code, the proposal names the conflict. Where a choice belongs to the owner, it is in
the owner list at the end. Every numeric claim below was read from the source at `origin/main` (6635220).

## Findings (file references, main at 6635220)

| # | Finding | Where | Rule |
|---|---|---|---|
| F1 | GO title holds 10 ticks plus 5 fade-out, 0.75 s; each digit 15 + 5 ticks, 1.0 s | `RaceFeedback.go` (line ~148–158), `RaceFeedback.countdown` (~124–131) | Pulse: the start cue must be the clearest moment |
| F2 | Reset sound is `block.note_block.bass` at pitch 0.6; countdown beat is the same sample at 1.2–1.4 | `RaceFeedback.resetSound` (~196–198) | Pulse: sounds must be distinct by meaning |
| F3 | DNF sound is the same bass sample at pitch 0.5 | `RaceFeedback.medalSound` (~185) | Pulse: a loss is quieter and informational, not a countdown |
| F4 | Missed ring: no signal. `RaceFeedback` names it as future work | `RaceFeedback` class note (~line 37); `Palette.WARN` doc (~51–57) | Pulse: near-miss awareness and "next time" motivation |
| F5 | Map result: chat line, title, subtitle, all carrying the same numbers | `CupAnnouncer.announceMapScore` (~79–92) | Glint: at most three elements; one place per fact |
| F6 | No next goal after a map. Medals are time brackets, so the goal is seconds | `Messages.mapResultSubtitle`, `MedalCountdown.boundaryOf` | Pulse: "leave with unfinished business" |
| F7 | Every racer receives every join line in chat | `WaitingRoom.joined` (~133–140) | Pulse: no spam; numbers over noise |
| F8 | Failure lines are dim | `voyager_en_US.properties` lines 76–78 (`reload.failed`, `reload.denied`, `command.denied`); setup `setup.refused`, `setup.save.failed` (no colour at all) | Palette: a colour has one job; failures need the act-now accent |
| F9 | Setup wand line is about 170 characters in one chat line | `voyager_setup_en_US.properties`, `voyager.setup.wand.given` | Pulse: no text walls |
| F10 | Terminology: "racer" in the lobby and results, "player" in the join line, "points" in full in one place and "pts" nowhere, "map" against "course" | `voyager_en_US.properties` lobby keys; `voyager.lobby.player.join` | Consistent terminology (style guide) |
| F11 | Personal best reserved (`Palette.PERSONAL_BEST`), no source of it until persistence | `Palette.java` (~72–79) | Pulse: "one more race" needs a visible personal goal; blocked |
| F12 | Setup bundle has no palette tags at all; its lines are plain | `voyager_setup_en_US.properties` | Consistency: one palette across both products |

Strengths to keep, because they meet the rules already: the ring bell rises with the ring index and the last ring has its
own sound (`RaceFeedback.ringSound`); the countdown beat rises by digit, so the three are heard as a run; the action bar is
cleared when the map ends; the boss bar drops its countdown when no medal is left to lose.

## Decisions proposed (owner answers in the list at the end)

### D1 GO and countdown timing (F1)

- GO title: hold 20 ticks (1.0 s), fade-out 5 ticks, total 1.25 s. The subtitle "Jump, then deploy your elytra" stays for the same
  span. Reason: the GO is the one moment the racer must see, and the countdown digits already hold for 1.0 s.
- Countdown digits: unchanged (15 + 5 ticks, 1.0 s each). The last digit is then followed by the GO for 1.25 s, so the
  screen is never empty at the start.
- Test: `RaceFeedbackTimingTest` (new), a pure test over the `Title.Times` values the feedback builds. It takes no clock.

### D2 Reset and DNF sounds (F2, F3)

- Keep the countdown beat for the countdown only.
- Reset: candidate `block.note_block.chime` at pitch 0.7 (quiet, falling), replacing the bass beat. Playtest decides.
- DNF: candidate `block.note_block.bit` at pitch 0.6, quieter (volume 0.6), replacing the bass beat at 0.5.
- Test: a `RaceFeedbackSoundTest` asserts that the reset, DNF, countdown and ring sounds are four distinct `Key`s, and that
  DNF volume is below the medal volume. These are asserted on the constants, not on played audio.

### D3 Missed-ring cue (F4)

- Definition (UX-2, proposal): a ring is missed when the racer passes ring `k+1` while ring `k` is still the next expected
  ring. `ProgressTracker.advance` already holds the next ring index, so the detection goes there as a pure function on
  `RingProgress` and returns the skipped ring's index. The race slice owns the rule; the platform only plays the cue.
- Cue: one quiet sound (candidate `block.note_block.didgeridoo` at 0.5, not the ring bell, not the countdown beat) and
  one action bar line for 1.5 s: `Ring <n> missed`, in `<c-warn>`, which replaces the flight HUD for that span only, so
  the clock is still visible on the next refresh. Emitted once per missed ring, never repeated for the same ring.
- Limit: no near-miss proximity cue in this change (Pulse's "whoosh within two blocks"); it needs a distance test against
  the ring plane that the course data does not yet provide. Recorded as a follow-up.
- Tests: `ProgressTrackerTest` gains "passing ring k+1 while k is next reports k as missed", "a normal run reports nothing",
  and "a ring is reported once". The platform cue is tested through `RaceFeedback` with a fake `Player` audience.

### D4 Map result: one place per fact, and a next goal (F5, F6)

- Chat: the breakdown line (`voyager.result.map.medal`) is the only place the points and rings are listed (UX-1).
- Title: the medal word, unchanged.
- Subtitle: the next goal in seconds, `voyager.result.map.subtitle.next` ("<arg:0> s faster for <arg:1>", with the medal word
  as `<arg:1>`), computed from `MedalCountdown.boundaryOf(nextTier, reference, brackets)` minus the racer's completion time,
  one decimal (UX-7). When no better medal is reachable (Diamond) the subtitle is omitted. A DNF's subtitle is
  `voyager.result.map.subtitle.dnf` reduced to the progress fact `<rings> of <count> rings`, with no points.
- Test: `MessagesTest` gains "the next goal names the seconds to the next better medal", "no next goal at the top
  bracket", "a DNF subtitle names no points", and "the points are not in the title or subtitle".

### D5 Join coalescing (F7)

- Joins within a window of 60 game ticks (three seconds, counted by the cup's tick, not the wall clock) are one line:
  `voyager.lobby.player.joined.many` ("3 racers joined (8/12)"). The first join in an empty window is still sent at once,
  so a single join is never delayed.
- The coalescing state is a pure class (`JoinBurst`, in `platform.lobby`) fed the tick by `WaitingRoom`. No scheduler.
- Test: `JoinBurstTest` names "one join is sent at once", "joins inside the window become one line", "a join after the
  window starts a new line", and "the window is counted in ticks". It uses explicit ticks.

### D6 Failure colour (F8)

- Failures (`reload.failed`, `reload.denied`, `command.denied`, setup `refused`, setup `save.failed`): `<c-warn>` (magenta).
- Refusals that are information, not failures (`skip.refused`, `setup.ring.none`): `<c-caution>` (amber), not dim.
- Setup lines gain palette tags (F12), following the same table.
- Enforced by the message-style rules in the style guide and by a test (`MessageStyleTest`, in `voyager-platform`) that reads
  the bundles and asserts each failure key uses `c-warn`. Named keys, not pattern matching on text.

### D7 Setup wand instruction (F9)

- `voyager.setup.wand.given` is split into two keys read in order: `…wand.given.place` (right-click places a ring where you
  stand, looking along the flight) and `…wand.given.remove` (left-click removes the ring you look through). Two chat lines.
- The factory sends both lines in order. Test: `SetupMessagesTest` asserts two keys and their order.

### D8 Terminology (F10)

- Glossary in the style guide: **racer** is the person in a race, in every race line; **player** only in the vanilla join
  line the client also writes ("joined the game"). **Map** is one course; **cup** is the sequence of maps; **ring** is one
  gate, numbered from 1; **medal** is one of Diamond, Gold, Silver, Bronze, Finish, DNF. **Points** in full, never "pts".
- Applied by a wording pass over the two bundles: "Waiting for racers" stays; "joined the game" becomes "joined the race"
  in the race's own line. The owner's answer to UX-6 is "joined the race", so no line keeps "joined the game".

### D9 Personal best (F11)

- Not in this change. The palette key stays reserved; no message uses it. Revisit with `voyager-persistence`.

### D10 Style guide

- `docs/reference/message-style-guide.md` (English, Diátaxis reference): voice, glossary (D8), channel rules (below), colour
  table (palette roles and the failure rule of D6), timing limits (title hold at least 1.0 s for a start cue, at most 3.0 s
  otherwise; subtitle one line; action bar at most one line), sound rules (distinct per meaning, losses quieter, anti-
  habituation), and the German rules for translators (`du` (owner decision); keep placeholders; no English word
  where a German one is standard).
- Channel rules: chat is the record that stays on screen (results, errors, joins); the action bar is live state under the
  crosshair (clock, ring counter, missed ring); a title is one moment (GO, medal, reset, cup); the boss bar is persistent
  state (map progress, lobby count); a sound confirms an event and never carries information on its own.

## Dependency direction and module of each new type

- `RaceFeedbackSoundTest`, `RaceFeedbackTimingTest`, `JoinBurst`, `JoinBurstTest`, `MessageStyleTest`, `SetupMessagesTest`:
  `voyager-platform`.
- `ProgressTracker` change (the missed-ring rule): `voyager-race` (pure; no Minestom).
- The new keys and factories: `voyager-platform` `platform.text` (only place that names a key).
- No new `voyager-api` type. The pure rules stay in `voyager-race` and `platform`; `voyager-fitness` keeps passing (no
  Minestom import in `voyager-race`).
- No new ADR: the choices are presentation rules, recorded in the style guide. The missed-ring definition is the skipped ring
  (UX-2), which is a race rule inside `voyager-race` and needs no ADR. A near-miss cue would need one.

## Golden masters

`CupSessionGoldenMasterTest` records keys and arguments. The missed-ring cue and the join line are new messages and will
appear in transcripts only in scenarios that produce them. The map result subtitle gains an argument, which changes the
transcript of every map result. That is a deliberate baseline change: task 6.2 regenerates the transcripts in its own commit,
typed `test(race)`, and the PR names each changed line. The change is not to be merged with an unexplained golden diff.

## Test plan (F.I.R.S.T.)

Every new test names one behaviour, uses explicit ticks or pure inputs, and touches no clock, no sleep, no shared static
state, and no `GlobalTranslator`. Parity and key tests from `localise-player-messages` run after each wording change.

## Owner decisions (recorded 2026-10-13)

The owner answered every question in `proposal.md`. The answers below replace the earlier proposals wherever the two differ.

- UX-1 Map result (D4): chat is the only place for the points and rings breakdown. The title is the medal word. The subtitle
  is the next goal in seconds only (`0.8 s faster for Gold`). When no better medal is reachable (Diamond), the subtitle is
  omitted. For a DNF the subtitle is the progress fact `<rings> of <count> rings`, with no points.
- UX-2 Missed ring (D3): a skipped ring, as proposed. No near-miss cue.
- UX-3 Join window (D5): 60 game ticks (3 s), coalescing on.
- UX-4 Colour (D6): `<c-warn>` for failures, `<c-caution>` for refusals.
- UX-5 Sounds (D2, D3, the medal row of the table below): the candidates are chosen so that each meaning has its own sound
  and none is the countdown beat. Final tuning in the owner's playtest; the PR lists each candidate that was played.
- UX-6 Join wording (D8): "joined the race". The racer is a racer in every race line.
- UX-7 Times (D4 and every race time): one decimal, `.` as the separator in both locales.

| Event | Sound (candidate) | Pitch, volume | Against the countdown beat |
|---|---|---|---|
| Countdown digit | `block.note_block.bass` (unchanged) | 1.0 to 1.4 | the beat itself |
| Ring hit | `block.note_block.bell`, rising (unchanged) | 1.0 to 1.8, 0.7 | distinct sample |
| Last ring | `ui.toast.challenge_complete` (unchanged) | 1.0, 0.8 | distinct sample |
| Reset | `block.note_block.chime` | 0.7, 0.8 | distinct sample, falling |
| DNF | `block.note_block.bit` | 0.6, 0.6 | distinct sample, quieter |
| Missed ring | `block.note_block.didgeridoo` | 0.5, 0.5 | distinct sample, quiet |
| Medal (Diamond, Gold, Silver, Bronze, Finish) | unchanged (`ui.toast.challenge_complete`, `entity.player.levelup`, `block.note_block.pling`) | as now | distinct samples |

Medal sounds are the candidates kept from `main`: none is the countdown beat, so the sound rule holds without a change. The
playtest may replace them; each candidate played is listed in the PR.

- Console (log) lines stay English.
- Minestom's built-in command syntax errors stay as Minestom writes them. Every error text that Voyager's own code sends is
  translated in both locales.
- Setup wand: two short lines (D7), placement first.
- GO title: 20 ticks of hold, as D1.
- The German tone is informal "du" (matches ADR-0038 decision 5 and the style guide).
- Cover the new lobby board, sidebar, hologram and handoff texts (from `f000b6b`) in the wording pass (task 6.5): glossary,
  one accent per line, failure colour where they report a failure.
- The golden masters are regenerated only in the `test(race)` commit, and the PR lists each changed transcript.
