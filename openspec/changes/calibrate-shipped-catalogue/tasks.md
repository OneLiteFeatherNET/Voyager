# Tasks

Commit types: `feat(catalog)` for the map and cup content, the calibration records and their tests; `docs` for `docs/`. Each
behaviour gets a failing test (Red) before its data (Green). The shipped-catalogue checks are the tests here, and they read files,
so they stay in the shipped-data test and use no network, no clock and no shared state. The code pull request contains no file under
`openspec/`, and no task is ticked in it.

Stage 0 (no data change) runs first. Stages 1 to 4 do not start until the owner has answered the open questions.

## 0. Playtest set-up (no file change)

- [ ] 0.1 Agree the playtest pool and the method (open question) with the owner, and write it into `docs/` as the calibration method
  (`docs`)
- [ ] 0.2 Record the path length and the reference time of each shipped map from its geometry, so each speed cap can be derived
  (`docs`)

## 1. Red then Green: the notes and the development cup's data

- [ ] 1.1 Red: `ShippedNotesTest` "the legacy maps' notes do not claim all rings are STANDARD" (`feat(catalog)`)
- [ ] 1.2 Green: correct the three stale notes in `palm_beach.json`, `dragon_lobby.json` and `memoria.json` to the ring types the
  files carry (`feat(catalog)`)
- [ ] 1.3 Red: `ShippedCatalogueTest` "the three development-cup maps load with a world and a spawn before `dev_cup` is listed"
  (`feat(catalog)`)
- [ ] 1.4 Green: give `nether_sprint`, `frozen_cathedral` and `skyward_drift` their worlds and spawns, then add `cups/dev_cup.json` in
  difficulty order (`feat(catalog)`)

## 2. Red then Green: the alpha map's rings

- [ ] 2.1 Red: `BlueAndRedRingsTest` "the alpha map has one SLOW ring in a chicane, one bonus ring off the line, and still 35 rings"
  (`feat(catalog)`)
- [ ] 2.2 Red: `BlueAndRedRingsTest` "the BOOST rings stay at indices 0, 18 and 27, and the checkpoints at 7, 15, 23 and 31"
  (`feat(catalog)`)
- [ ] 2.3 Red: `BlueAndRedRingsTest` "narrow rings carry 25 points and every other ring keeps 10" (`feat(catalog)`)
- [ ] 2.4 Green: edit the alpha map's ring `type` and `points`, and its guide points for the chicane, so the tests pass (`feat(catalog)`)

## 3. Red then Green: the per-map rockets and speed caps

- [ ] 3.1 Red: `ShippedMapBoostTest` "each shipped map states `rocketsPerRacer` within its band, and dragon_lobby states 8" (`feat(catalog)`)
- [ ] 3.2 Red: `ShippedMapBoostTest` "each shipped map states `maxSpeedBlocksPerTick` and its notes give the path length and reference time"
  (`feat(catalog)`)
- [ ] 3.3 Red: `ShippedMapBoostTest` "a chain of three BOOST rings on the alpha map never exceeds the stated cap" (`feat(catalog)`)
- [ ] 3.4 Green: set `rocketsPerRacer` and `maxSpeedBlocksPerTick` in each map file, with the derivation in its notes (`feat(catalog)`)

## 4. Calibration of reference times

- [ ] 4.1 Playtest: collect at least ten human runs per map under the agreed method and record the top-quartile median (`docs`; the runs
  themselves are not committed)
- [ ] 4.2 Red: `CalibrationNoteTest` "a calibrated map's notes state a sample count of at least ten, a date and the method"
  (`feat(catalog)`)
- [ ] 4.3 Green: write each calibrated `referenceTimeSeconds` and its record into the notes; a map with fewer than ten runs keeps
  its provisional mark (`feat(catalog)`)

## 5. Replay and verify

- [ ] 5.1 Run the shipped-catalogue checks and `:voyager:fitness:test` through `heavy` with `--max-workers=2` (`feat(catalog)`)
- [ ] 5.2 Replay the cup-session golden transcripts for the alpha map; list each transcript that changes and explain the change in the
  code pull request (`feat(catalog)`)

## 6. Docs

- [ ] 6.1 Write the calibration method and the list of calibrated maps in `docs/reference/`, with the sample counts and dates (`docs`)
- [ ] 6.2 Update the operator page for the per-map rocket and speed fields (`docs`)

## 7. Open the code pull request

- [ ] 7.1 Open the code pull request with the title `feat(catalog): calibrate the shipped catalogue from human runs`, from a branch
  based on the remote main branch that contains the merged proposal and the merged `start-solo-practice` change, with no file under
  `openspec/` (`feat(catalog)`)
