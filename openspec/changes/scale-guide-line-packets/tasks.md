# Tasks

Commit type and scope: `perf(race)` for the code and its tests, `docs` for `docs/reference/`. Each behaviour gets a failing test (Red)
before its production code (Green). Every test follows F.I.R.S.T.: a `MapDefinition` fixture per test, `Vec3` positions fixed in the
test, no clock, no shared static state.

This change does not start until the owner has decided the "no gap" exception and the hard-cap question (see proposal.md).

## 1. Red: the packets and the selection

- [ ] 1.1 Red: `PrebuiltLineTest` "the packets of a map are built once, and a second refresh reuses the same instances" (`perf(race)`)
- [ ] 1.2 Red: `PrebuiltLineTest` "two racers on the same tick are sent packets from the same instances, filtered by reach"
  (`perf(race)`)
- [ ] 1.3 Red: `LevelOfDetailTest` "a point 40 blocks away is sent on every refresh" (`perf(race)`)
- [ ] 1.4 Red: `LevelOfDetailTest` "a point 100 blocks away is sent on alternate refreshes only" (`perf(race)`)
- [ ] 1.5 Red: `LevelOfDetailTest` "a point at exactly 60 blocks is on the full rate" (`perf(race)`)
- [ ] 1.6 Red: `HideLineTest` "a hidden racer receives no racing-line particle, and other racers still do" (`perf(race)`)
- [ ] 1.7 Red: `HideLineTest` "a racer who has not changed the toggle sees the line" (`perf(race)`)
- [ ] 1.8 Red: `BudgetTest` "the shipped course at sixteen racers stays within 30,000 packets a second in total and 2,000 per racer"
  (`perf(race)`)
- [ ] 1.9 Red: run 1.1 to 1.8 and record each failure message in the code pull request (`perf(race)`)

## 2. Green: the packets and the selection

- [ ] 2.1 Green: `PrebuiltLine` in `platform/render`, built once per map from the stretch and the particle (`perf(race)`)
- [ ] 2.2 Green: `GuideLineRenderer.render` selects from `PrebuiltLine` by reach and by level of detail (`perf(race)`)
- [ ] 2.3 Green: the hide set in `CupSession`, passed to the renderer and cleared when the racer leaves (`perf(race)`)
- [ ] 2.4 Green: the hide toggle in the command or item layer chosen in design, with the keys in `voyager_en_US` and
  `voyager_de_DE` (`perf(race)`)
- [ ] 2.5 Refactor: confirm 1.1 to 1.8 pass, and that the existing `GuideLineRendererTest` passes without edits (`perf(race)`)

## 3. Verify

- [ ] 3.1 Measure palm_beach at sixteen racers before and after, and record the packet counts in the code pull request
  (`perf(race)`)
- [ ] 3.2 Run `./gradlew :voyager:platform:test :voyager:fitness:test` and confirm green (`perf(race)`)

## 4. Docs

- [ ] 4.1 Update `docs/reference/map-and-cup-files.md` and `docs/reference/configuration.md` with the level of detail, the hide
  toggle and the budget (`docs`)

## 5. Pull request

- [ ] 5.1 Open the code pull request titled `perf(race): send fewer racing-line packets and let racers hide the line` from the
  implementation branch, with no file under `openspec/` (`perf(race)`)
