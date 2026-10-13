# Tasks

Commits: `docs(openspec)` for artifact edits, `feat(race)` for every behaviour change and its tests, `test(race)` for the
golden-master regeneration (its own commit, so that a `feat` commit never carries a baseline change), `docs` for the style
guide. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: explicit
ticks or pure inputs, no sleeps, no wall clock, no `GlobalTranslator`, no shared static state.

Depends on `localise-player-messages`: the new and changed keys below must land in both `en_US` and `de_DE` with equal
placeholders, which the parity test enforces. Apply that change first.

Effort notes are for the Haiku implementation agents: `low` for mechanical edits and documents, `medium` for bounded code and
bulk wording, `high` for changes that alter race rules or golden masters. Every agent works in its own worktree from the
up-to-date `origin/main`.

## 1. Decisions

- [ ] 1.1 Owner answers UX-1 to UX-7 in `proposal.md` and `design.md`, and the answers are recorded in design.md (`docs(openspec)`). Effort: low.
- [ ] 1.2 Playtest plan for the sound candidates (UX-5): one ring of each candidate in a test world, recorded in the PR. Effort: low.

## 2. Start cue and sound meanings (effort: medium)

- [ ] 2.1 Red: `RaceFeedbackTimingTest` names "the GO title holds twenty ticks", "the countdown digit holds fifteen ticks and fades five", and "each title fades in with no delay" over the `Title.Times` the feedback builds (`feat(race)`).
- [ ] 2.2 Green: change the GO hold to 20 ticks in `RaceFeedback.go` (`feat(race)`).
- [ ] 2.3 Red: `RaceFeedbackSoundTest` names "a reset sound is not the countdown beat", "a DNF sound is not the countdown beat", "a DNF is quieter than a medal", and "the four sounds are distinct keys" (`feat(race)`).
- [ ] 2.4 Green: replace the reset and DNF sounds with the candidates chosen in 1.2 (`feat(race)`).

## 3. Missed ring (effort: high, race rule)

- [ ] 3.1 Red: `ProgressTrackerTest` gains "passing ring k+1 while k is next reports k as missed", "a normal run reports nothing", and "a missed ring is reported once per run" (`feat(race)`).
- [ ] 3.2 Green: add the pure missed-ring detection to `ProgressTracker.advance` and its result type `ProgressUpdate` in `voyager-race`, with no Minestom import (`feat(race)`). `voyager-fitness` must stay green.
- [ ] 3.3 Red: `RaceFeedbackMissedRingTest` names "a missed ring sends one sound and one warn-coloured action bar line for 1.5 s", and "no cue without a missed ring" (`feat(race)`), using a fake `Player` audience.
- [ ] 3.4 Green: `RaceFeedback.ringMissed` plus the wiring in `RaceHud`/`CupAnnouncer` (`feat(race)`).
- [ ] 3.5 Add `voyager.race.ring.missed` with `<arg:0>` to `en_US` and `de_DE` (`feat(race)`), with a unit test on the `Messages` factory (Red first).

## 4. Results and the next goal (effort: medium)

- [ ] 4.1 Red: `MessagesTest` gains "the next goal names the seconds to the next better medal", "no next goal at the top bracket", and "no next goal for a DNF", using `MedalCountdown.boundaryOf` (`feat(race)`).
- [ ] 4.2 Green: add `voyager.result.map.subtitle.next` and the factory; compute the time in `CupAnnouncer.announceMapScore` (`feat(race)`).
- [ ] 4.3 Red then Green: remove the duplicated breakdown from the title and subtitle, as UX-1 decides; each step behind a test named for the fact it keeps in one place (`feat(race)`).

## 5. Joins and channels (effort: medium)

- [ ] 5.1 Red: `JoinBurstTest` names "one join is sent at once", "joins inside the window become one line", "a join after the window starts a new line", and "the window is counted in ticks" (`feat(race)`), with explicit ticks.
- [ ] 5.2 Green: add the pure `JoinBurst` in `platform.lobby`, fed the tick by `WaitingRoom`, and the key `voyager.lobby.player.joined.many` in both locales (`feat(race)`).
- [ ] 5.3 Red then Green: `WaitingRoom.joined` uses `JoinBurst` (`feat(race)`).
- [ ] 5.4 Red then Green: split `voyager.setup.wand.given` into `.place` and `.remove`, and send them in order from `SetupMessages` (`feat(race)`), with `SetupMessagesTest`.

## 6. Colour and wording (effort: medium, bulk wording)

- [ ] 6.1 Red: `MessageStyleTest` in `voyager-platform` reads the bundles and names "failure keys use the warn tag", "refusal keys use the caution tag", "no bundle value uses a red colour", and "no line has two accents". Each assertion names the key (`feat(race)`). Seeded failing fixtures in test resources prove each check is not vacuous.
- [ ] 6.2 Green: change the colours of the keys listed in the proposal, in both locales, and add palette tags to the setup bundle (`feat(race)`).
- [ ] 6.3 Wording pass for the glossary (racer, player, map, cup, ring, points): changes only the words the glossary names, in both locales (`feat(race)`). The `de_DE` change is reviewed by the owner before merge.
- [ ] 6.4 Regenerate `CupSessionGoldenMasterTest` transcripts for the changed result line and join line, in a separate commit typed `test(race)`. The PR lists every changed transcript line. Effort: medium.

## 7. Documentation (effort: low)

- [ ] 7.1 Write `docs/reference/message-style-guide.md`: voice, glossary, channel rules, colour table, timing limits, sound rules, German rules (`docs`). Diátaxis reference; English.
- [ ] 7.2 Link the guide from `Palette.java` and `Messages.java` class comments (`docs`, a one-line Javadoc change in its own commit under the same type).
- [ ] 7.3 Record the ring-miss rule and the new sounds in `docs/reference/hud.md` (`docs`).

## 8. Verification

- [ ] 8.1 `./gradlew build` (both trees) is green (no commit). Effort: low.
- [ ] 8.2 `openspec validate improve-message-ux --strict` passes (`docs(openspec)`). Effort: low.
- [ ] 8.3 Playtest on a local server with one racer: GO, a missed ring, a reset, a DNF and a result, each recorded with the timing in ticks, in the PR description. Effort: medium.

## 9. Pull request

- [ ] 9.1 Open the pull request. Title: `feat(race): improve message ux`. The body lists the owner's answers to UX-1 to UX-7, the playtest record and the golden-master diff. Its footer is exactly these two lines:

  ```
  https://claude.ai/referral/m5Ak2Sa7aQ
  https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz
  ```

  Commits in the PR carry the trailer `Claude-Session: https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`. Effort: low.
