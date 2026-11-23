# Tasks

Commit type and scope: `feat(race)` for the code and its tests, `docs` for `docs/reference/hud.md`. Each behaviour gets a failing
test (Red) before its production code (Green). Every test follows F.I.R.S.T.: no sleeps, time from an explicit race tick, a fresh
fixture per test, no shared static state.

## 1. Red: the cues

- [ ] 1.1 Red: `CheckpointOrdinalTest` "the ordinal of a checkpoint is its count among the checkpoint rings up to it, from 1"
  (`feat(race)`)
- [ ] 1.2 Red: `RingCueSelectionTest` "a missed ring wins over a checkpoint, boost or slow line due in the same tick, and the
  pre-empted line is not shown later" (`feat(race)`)
- [ ] 1.3 Red: `RingCueSelectionTest` "the flight line is shown when no cue is due" (`feat(race)`)
- [ ] 1.4 Red: `RingSoundTest` "a BOOST ring, a SLOW ring and a CHECKPOINT ring each play their own sample, and none is the ring bell"
  (`feat(race)`)
- [ ] 1.5 Red: `RingSoundTest` "the last ring of a map keeps the chime, whatever its type" (`feat(race)`)
- [ ] 1.6 Red: `RaceHudPointsMarkerTest` "during the flash window the pad shows '+N' in the OK colour, and it is gone after eight ticks"
  (`feat(race)`)
- [ ] 1.7 Red: `RaceHudPointsMarkerTest` "a zero-point ring shows '+0' for the flash window" (`feat(race)`)
- [ ] 1.8 Red: `CheckpointLineTest` "passing a checkpoint shows 'Checkpoint n saved' for 30 ticks, and a reset shows nothing"
  (`feat(race)`)
- [ ] 1.9 Red: `LobbySoundsDistinctTest` "the new samples are not in the lobby sample set" (`feat(race)`)
- [ ] 1.10 Red: run 1.1 to 1.9 and record each failure message in the code pull request (`feat(race)`)

## 2. Green: the cues

- [ ] 2.1 Green: `CheckpointOrdinal` in `voyager/race` `net.elytrarace.voyager.race.flow`, pure, `package-info` @NotNullByDefault
  (`feat(race)`)
- [ ] 2.2 Green: `RingCue` sealed interface and the selection in `platform/hud`, the fixed precedence (`feat(race)`)
- [ ] 2.3 Green: `RaceFeedback.ringSound` takes the ring type; BOOST, SLOW and CHECKPOINT samples are new keys and are kept out of the
  lobby set (`feat(race)`)
- [ ] 2.4 Green: `RaceHud` shows the points marker in the pad, using the points stored beside `lastRingGameTick` (`feat(race)`)
- [ ] 2.5 Green: `CupAnnouncer.report` passes the ring to the cue code (`feat(race)`)
- [ ] 2.6 Green: the new keys in `voyager_en_US` and `voyager_de_DE` with `<arg:N>` placeholders, so the parity test passes
  (`feat(race)`)
- [ ] 2.7 Refactor: confirm 1.1 to 1.9 pass, and that the existing `RaceHud`, `RaceFeedback` and `CupAnnouncer` tests pass without edits
  beyond the signature change (`feat(race)`)

## 3. Verify

- [ ] 3.1 Check the new sound keys against the 26.2 registry and record the result in the code pull request (`feat(race)`)
- [ ] 3.2 Run `./gradlew :voyager:race:test :voyager:platform:test :voyager:fitness:test` and confirm green (`feat(race)`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/hud.md`: the points marker, the boost, slow and checkpoint lines, and their precedence (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `feat(race): show ring points and checkpoint, boost and slow cues in flight`, from the
  implementation branch only, with no file under `openspec/` (`feat(race)`)
