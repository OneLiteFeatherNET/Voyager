# Tasks

Every behaviour is written as a failing test first (Red), then made to pass (Green). The shipped-catalogue test reads only
`src/main/resources`, writes nothing, injects no clock and keeps no shared state, as `CommittedMapDataTest` does.
F.I.R.S.T. applies: no sleeps, no system-time reads, no order dependence.

## 0. Owner decisions (recorded)

- [x] 0.1 Owner: the alpha starts with `elytraraceblueandred` only. The three converted maps stay out until their worlds
  exist. Verify: design.md, Context and Decision 1 record it.
- [x] 0.2 Owner: replace `test_cup` with `alpha_cup` as the only shipped cup. Verify: design.md, Decision 2 records it.
- [x] 0.3 Owner: reset after landing or out of bounds goes to the last passed ring, so no checkpoint rings are needed.
  Verify: design.md, Decision 7 records it.
- [x] 0.4 Owner: give the map 2 to 4 BOOST rings, chosen from geometry and marked provisional. Verify: design.md,
  Decision 3 records the rule and the result (rings 0, 18, 27); Decision 5 records the provisional marking.

## 1. Shipped catalogue test (Red)

- [ ] 1.1 Red: add `ShippedCatalogueTest` in `voyager-server/src/test/java/net/elytrarace/voyager/server/`, one behaviour per
  test: `theShippedCatalogueHoldsExactlyOneCupNamedAlphaCup`, `theAlphaCupPlaysOnlyTheElytraraceBlueAndRedMap`,
  `theShippedMapHasAtLeastEightRingsIndexedFromZero`, `theShippedMapCarriesAtLeastTwoBoostRings`,
  `theShippedCatalogueLoadsWithoutAnyProblem`. Each loads `src/main/resources` through `CatalogLoader.load`, as
  `CommittedMapDataTest` does. Verify: `./gradlew :voyager-server:test --tests '*ShippedCatalogueTest*'` runs and fails,
  each test for the reason named (`test_cup` is the only cup, zero BOOST rings), not because of a compile error.

## 2. Ship the data (Green)

- [ ] 2.1 Green: add `voyager-server/src/main/resources/cups/alpha_cup.json` (mode `RACE`, `mapNames` `[elytraraceblueandred]`,
  notes recording the display name "Alpha Cup" and the one-map rotation), and delete `cups/test_cup.json`. Verify:
  `cups/` lists only `alpha_cup.json`.
- [ ] 2.2 Green: set the type of rings 0, 18 and 27 of `maps/elytraraceblueandred.json` to `BOOST`, and add a note that
  names them as provisional BOOST rings, the straight-segment rule that chose them, and "tune after playtest". Ring centres,
  normals, radii, points and guide points are not touched. Verify: `git diff` on that file changes only the three `type`
  values and the notes array.
- [ ] 2.3 Green: update the fixtures that name the shipped cup: the `ShippedCatalogue` helper copies `alpha_cup.json`, and
  `ConfigCheckRunTest`, `VoyagerGraphTest`, `TickPipelineOrderTest` and `VoyagerStartupTest` pass `alpha_cup` as the played
  cup. Update `CommittedMapDataTest`: its ring-type assertion becomes "ring 0, 18 and 27 are BOOST, the rest STANDARD" and its
  cup assertion becomes `alpha_cup`. `CupBootValidationTest` and `BootRefusalTest` keep their own `test_cup` fixtures.
  Verify: `./gradlew :voyager-server:test --tests '*ShippedCatalogueTest*'` passes, then `./gradlew :voyager-server:test` passes.
- [ ] 2.4 Check the golden masters: confirm `CupSessionGoldenMasterTest` builds its catalogue in memory and reads no shipped
  file, and that no golden file changes. Verify: `git diff --stat -- voyager-server/src/test/resources/golden` is empty. If a
  golden file would change, stop and report; do not regenerate.

## 3. Verify the worlds and the build

- [ ] 3.1 Run the catalogue check with no `-Pcup`, pointing at the main checkout's worlds:
  `./gradlew :voyager-server:validateCatalog -PworldsPath=/mnt/projects/oss/onelitefeather/Voyager/run/run/worlds`. Verify:
  exit 0; the output names `alpha_cup` as the played cup and `ElytraraceBlueAndRed` as present. Record the output in task
  4.1. If the deep world check cannot find the world, report it.
- [ ] 3.2 Run `./gradlew build` for both trees. Verify: exit 0; record the test count.
- [ ] 3.3 Run `CI=true ./gradlew :voyager-fitness:test`. Verify: exit 0.

## 4. Document the content

- [ ] 4.1 Add a section to `docs/migration/status.md` that names the shipped cup `alpha_cup` and its one map, the BOOST rings
  0, 18 and 27 with their segment lengths (96.99, 87.42, 72.95 blocks, provisional), the `validateCatalog` command with
  `-PworldsPath` and its output from 3.1, the dropped `maxSpeedBlocksPerTick`, and a follow-up note that the three converted
  maps (`nether_sprint` 11 rings, `frozen_cathedral` 12, `skyward_drift` 9) are converted-ready and blocked only on their
  worlds and `level.dat` spawns. No data file is added for them. Verify: the section names `alpha_cup`, `elytraraceblueandred`,
  and the three ring counts; `grep -n test_cup docs/migration/status.md` shows only a removal note.
- [ ] 4.2 Check `docs/reference/map-and-cup-files.md` for stale examples. A search found no `test_cup` or
  `elytraraceblueandred` in it. Verify: `grep -n 'test_cup' docs/reference/map-and-cup-files.md` finds nothing; record "no
  change needed" in the commit body if so.

## 5. Playtest (owner-run)

- [ ] 5.1 Two players fly `alpha_cup` end to end on the owner's worlds: every ring reachable, the three BOOST rings give the
  speed boost, no crash, no world-load failure. Verify: the playtest notes (date, players, result, any ring issue as its own
  follow-up) are appended to `docs/migration/status.md`. This is owner work; the agent does not mark it done.

## 6. Commit and pull request

- [ ] 6.1 Commit in this order, each under one type and each ending with a blank line and the session trailer:
  `docs(openspec): propose add-alpha-cup-content` (this planning directory, done first),
  `feat(content): ship the alpha cup with one playable map` (groups 1 and 2), and
  `docs(migration): record the alpha cup content` (group 4). Verify: `git log --oneline -3` shows the three subjects, and
  neither carries a Co-Author line.
- [ ] 6.2 Record the pull request title `feat(content): ship the alpha cup with one playable map`. The owner merges locally, so
  the agent opens no pull request; the title is the squash message that lands on main. Verify: the title is recorded in the
  change's final report.
