# Tasks

Commit type and scope: `feat(catalog)` for the shipped data and its tests, `docs` for the `docs/` pages. Each behaviour gets a
failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.: the shipped catalogue is read from the
resources directory in a fresh fixture, with no clock, no network and no shared static state. Task checkboxes are ticked in
the follow-up pull request, never in the code pull request.

This change starts after `add-catalogue-keys-and-authors` has landed, because both edit the shipped map files. Its code pull
request is rebased on that one.

## 1. Red: the shipped maps name their particle

- [ ] 1.1 Red: `ShippedGuideParticleTest` "every shipped map names its particle explicitly, and every DUST map names a colour"
  (`feat(catalog)`); it fails today, because no shipped map names a particle
- [ ] 1.2 Red: `MapDraftWriterGuideLineTest` "saving a loaded map keeps its particle and its dust colour" (`feat(catalog)`); it
  may pass today, because `MapDraftJsonWriter` already writes both fields. If it passes, it stays as a regression pin, and the
  code pull request says so
- [ ] 1.3 Red: run 1.1 and 1.2 and record the failure messages in the code pull request (`feat(catalog)`)

## 2. Green: the shipped data

- [ ] 2.1 Green: in each shipped map file, set `particle` and, for `DUST`, `dustColor`, as the owner decided in open question 2
  of the design; leave `lookAheadRings`, `particleSpacing` and the points unchanged (`feat(catalog)`)
- [ ] 2.2 Green: 1.1 passes, and 1.2 passes or stays as a pin (`feat(catalog)`)
- [ ] 2.3 Refactor: confirm that the golden masters that print the guide line are unchanged, or list each changed one with its
  reason in the code pull request (`feat(catalog)`)

## 3. Docs

- [ ] 3.1 Write the evaluation record under `docs/research/`: the method, the candidate table, the TRAIL rejection, the cost
  note, and the playtest result (`docs`)
- [ ] 3.2 Update the map designer's reference under `docs/reference/` with the `particle` and `dustColor` options and the
  recommendation for bright and dark maps (`docs`)

## 4. Checks

- [ ] 4.1 Run `./gradlew :voyager:platform:test :voyager:server:test :voyager:fitness:test` (`feat(catalog)`)
- [ ] 4.2 Playtest each shipped map: draw the line as END_ROD and as DUST in the chosen colour, from the spawn, at ring 1 and at
  mid-course, and record the owner's verdict for each in `docs/research/` (`feat(catalog)`)
- [ ] 4.3 Check the racing line's packet budget for the shipped course with DUST, since DUST redraws more often than END_ROD
  (`feat(catalog)`)
- [ ] 4.4 Run `./gradlew build` for both trees, and smoke-boot the server shadow JAR with LuckPerms as the apply rules require,
  quoting the start lines in the code pull request (`feat(catalog)`)

## 5. Ship

- [ ] 5.1 Open the code pull request under the title `feat(catalog): name a guide particle and colour for each shipped map`.
  Write the description as the racer sees the line: the same course, drawn in a colour that can be seen on its ground. Include the
  Red messages (1.3), the playtest verdicts (4.2), the smoke lines (4.4) and the list of changed golden transcripts. Do not add a
  session link (`feat(catalog)`)
