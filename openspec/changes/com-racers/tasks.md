# Tasks

Commits follow the type this change names: `feat(race)` for every code change and its tests, `docs` for `docs/` pages and
the ADR, `docs(openspec)` for artifact edits. Each behaviour gets a failing test (Red) before its production code
(Green), under "Red/Green TDD". Every test follows F.I.R.S.T.: no sleeps, `Clock` injected, a fixed seed, `@TempDir` for
worlds, a fresh Minestom instance per test with explicit ticks.

**Build split and effort.** Each group names the effort for the Haiku implementation agent that does it, as the owner
asked. Every agent that writes files runs with `isolation: "worktree"` and branches from the current `origin/main`. The
groups are ordered: a group starts when the groups it names are merged into this branch. Group 0 records the owner's
decisions, which the join policy and the settings in groups 2 and 4 follow.

## 0. Owner answers and spikes (no production code)

- [x] 0.1 Record the owner's answers to OQ1 to OQ10 in design.md, "Owner decisions" (`docs(openspec)`). Effort: low (Haiku).
- [x] 0.2 ADR-0035 stays Proposed (owner decision). No status change. Effort: low (Haiku).
- [x] 0.3 Spike S1: measured the speed, yaw, pitch and sink of the recordings into design.md D14. Result: cap 1.7 blocks/tick (kept), floor 0.70 blocks/tick (was 0.8), yaw 0.91 deg/tick, pitch 7.23 deg/tick, sink 0.134 blocks/tick. Effort: medium (Haiku).
- [x] 0.4 Spike S2 recorded as an owner playtest item in design.md D2 and "Owner playtest" (`docs(openspec)`). No spike test. The primary approach (PLAYER entity with a tab-list entry) is built; the armour stand (D2 option C) is the fallback if the playtest shows the body hidden. Effort: low (Haiku).

## 1. api: the difficulty value

- [x] 1.1 Red: `ComDifficultyTest` names the behaviours "the names EASY, MEDIUM and HARD are listed in that order", "byName is case-insensitive", and "an unknown name is empty" (`feat(race)`). Effort: low (Haiku).
- [x] 1.2 Green: add the enum `ComDifficulty` in `net.elytrarace.voyager.api.race.com` with `VALUES` and `byName` returning `Optional`, and a `package-info.java` with `@NotNullByDefault` (`feat(race)`). Effort: low (Haiku).

## 2. race: the pure brain (voyager-race, `race.com`)

Effort for this whole group: high (Haiku). It is the movement model and the difficulty numbers; the agent reads design.md D1,
D3, D4, D13 and D14 before writing a test. No `System` clock and no unseeded `Random` appear in this group (rule F1).

- [x] 2.1 Red: `ComPathTest` names the behaviours "the arc length of a straight course is the sum of its segments", "a distance before the start clamps to the first point", "a distance past the end clamps to the last point", and "the tangent at any distance has unit length" (`feat(race)`). Effort: high (Haiku).
- [x] 2.2 Green: add `ComPath`, built once from a `RacingLine` with cumulative arc lengths and binary search for `pointAt` and `tangentAt` (`feat(race)`). Effort: high (Haiku).
- [x] 2.3 Red: `ComTuningTest` names the behaviours "EASY is slower and wanders further than MEDIUM", "HARD has no miss chance and the reference speed factor", "the speed factor never exceeds one", and "a negative amplitude is refused" (`feat(race)`). Effort: medium (Haiku).
- [x] 2.4 Green: add `ComTuning.of(ComDifficulty)` with the table in design.md D3, the exceptions `InvalidComTuningException` in `race.com.exception` with its `package-info.java`, and the seeded personal variation of plus or minus 2 per cent (`feat(race)`). Effort: medium (Haiku).
- [x] 2.5 Red: `ComRosterTest` names the behaviours "the grid fills to the target count", "humans already on the grid reduce the count", "the same seed gives the same seats", "seats are numbered COM 1 to COM n in order", and "a count above 15 is refused" (`feat(race)`). Effort: medium (Haiku).
- [x] 2.6 Green: add `ComRoster.plan(targetCount, humans, seed)` returning seats, with `InvalidComCountException` in `race.com.exception` (`feat(race)`). Effort: medium (Haiku).
- [x] 2.7 Red: `ComPilotTest` names the behaviours "one step advances the distance by the speed", "the same seed and tick give the same position", "the speed never exceeds the cap", "the speed changes by no more than the acceleration bound in one tick", "a missed ring is re-crossed through its disc and counted once", "HARD never misses a ring", "a boost takes effect after the reaction delay, with a fake ring effect", "a racer with no progress for 400 ticks is marked did-not-finish", and "a whole lap of a fixture course is run with no server and no clock" (`feat(race)`). The lap test uses `RingPass` and `ProgressTracker` on a fixture `MapDefinition`. Effort: high (Haiku).
- [x] 2.8 Green: add `ComPilot.step(ComState, ComTuning, ComPath, long tick)` as a pure function returning the next `ComState`, and the records `ComState` and `ComSeat` (`feat(race)`). Effort: high (Haiku).
- [x] 2.9 Refactor, only if needed, with the java-style skill applied: no behaviour change, tests stay green (`refactor(race)`). Effort: medium (Haiku).

## 3. platform.cup: the port and the cup's calls (voyager-platform)

Effort for this group: medium (Haiku). The cup must not learn about Minestom bodies (rule F2).

- [x] 3.1 Red: `CupSessionCompetitorTest` drives explicit ticks with a fake `CompetitorField` and names the behaviours "a competitor's run starts at its map's start", "a competitor's position feeds its run and not the human supplier", "a competitor that is not on the course is not advanced", and "dismiss is called at cup end and on abort" (`feat(race)`). Effort: medium (Haiku).
- [x] 3.2 Green: add `CompetitorField` (the port), `Competitor` (a record of id, position and gliding), and `NoCompetitors` in `platform.cup`, and give `CupSession` the port. Call `prepare` in `mapStarted`, `advance` after the human loop in `raceTick`, and `dismiss` at cup end and abort (`feat(race)`). Effort: medium (Haiku).
- [x] 3.3 Confirm `CupSessionGoldenMasterTest` and the four other golden transcripts pass unchanged with `NoCompetitors` (no commit, verification) (`feat(race)` on the same commit as 3.2). Effort: low (Haiku).

## 4. platform.com: the Minestom adapter (voyager-platform)

Effort for this group: medium (Haiku). The body is the primary PLAYER entity (D2). S2 is an owner playtest item, not a gate for this group.

- [x] 4.1 Red: `ComNamesTest` names the behaviours "seats are named COM 1 to COM n", "a name is kept for the life of the cup", and "a human's id is not a computer racer" (`feat(race)`). Effort: low (Haiku).
- [x] 4.2 Green: add `ComNames` with `nameOf(seat)`, `isComputer(uuid)`, and the seat ids derived from the round seed and the seat index (`feat(race)`). Effort: low (Haiku).
- [x] 4.3 Red: `ComBodyTest` on a fresh Minestom instance with explicit ticks names the behaviours "a body spawns in its instance at its position", "a teleport moves the body on the next tick", "removal despawns the body", and "the body shows its custom name" (`feat(race)`). If `voyager-platform` has no Minestom testing dependency, the same commit adds it (`feat(race)`). Effort: medium (Haiku).
- [x] 4.4 Green: add `ComBody`, wrapping `new Entity(EntityType.PLAYER, uuid)` with a `PlayerMeta` and a custom name (`feat(race)`). The armour stand fallback is used only if the owner playtest finds the body hidden. Effort: medium (Haiku).
- [x] 4.5 Red: `ComTabListTest` names the behaviours "every human gets the add entry before a body spawns", "a human who joins mid-cup gets the entries of the current bodies", and "dismiss sends the remove entries" (`feat(race)`). Effort: medium (Haiku).
- [x] 4.6 Green: add `ComTabList` over `PlayerInfoUpdatePacket` and `PlayerInfoRemovePacket`, with the default skin and no textures (`feat(race)`). Effort: medium (Haiku).
- [x] 4.7 Red: `ComFieldTest` with a fake brain and explicit ticks names the behaviours "prepare returns one id per computer racer", "advance returns positions in seat order after the humans", "dismiss removes every body and entry", "a count of zero creates nothing", and "the path for a map is built once and shared" (`feat(race)`). Effort: medium (Haiku).
- [x] 4.8 Green: add `ComField implements CompetitorField` over `ComRoster`, `ComPilot`, `ComPath` (cached per map), `ComBody` and `ComTabList` (`feat(race)`). Effort: medium (Haiku).

## 5. server: settings, configuration check and wiring (voyager-server)

Effort for this group: low for wiring and the check; the settings tests are medium (Haiku).

- [x] 5.1 Red: `ServerSettingsComTest` names the behaviours "an absent count is zero", "a count of 16 is refused and names the range", "a count that is not a number is refused and names the value", "an absent difficulty is MEDIUM", "a difficulty is case-insensitive", and "an unknown difficulty is refused and lists the three names" (`feat(race)`). Effort: medium (Haiku).
- [x] 5.2 Green: add `COM_COUNT_PROPERTY` and `COM_DIFFICULTY_PROPERTY` and the two components to `ServerSettings` with the existing compatibility constructors (`feat(race)`). Effort: low (Haiku).
- [x] 5.3 Red: `ConfigCheckComTest` names the behaviours "a boot line states the count and difficulty when enabled" and "no line is logged when the count is zero" (`feat(race)`). Effort: low (Haiku).
- [x] 5.4 Green: add the computer racer lines to `ConfigCheck`, keyed by setting name (`feat(race)`). Effort: low (Haiku).
- [x] 5.5 Red then green: `CupBeans` wiring test names the behaviours "a count of zero binds NoCompetitors" and "a count above zero binds ComField"; wire it in the composition root only (`feat(race)`). Effort: low (Haiku).

## 6. fitness: the boundary rules (voyager-fitness)

Effort for this group: medium (Haiku). The rules are written first and fail on an empty match until the code exists,
because `allowEmptyShould(false)` makes an empty match a failure.

- [x] 6.1 Red: `ComBoundaryTest` declares `comBrainIsPure` (F1), `cupDoesNotDependOnComPlatform` (F2), `comAdapterDoesNotSimulateFlight` (F3) and `onlyServerConfigReadsComSettings` (F4), each with `allowEmptyShould(false)` and not frozen. Run it: the rules fail with no matching classes (`feat(race)`). Effort: medium (Haiku).
- [x] 6.2 Green: the rules pass once groups 2 to 5 are in. Any violation found is fixed in the production code, not by a freeze (`feat(race)`). Effort: low (Haiku).
- [x] 6.3 Confirm that `SliceBoundaryRulesTest` r4 and r7 match `platform.com` and `race.com` without a change, and that `FitnessCoverageTest` needs no change (verification, no commit). Effort: low (Haiku).

## 7. golden master and the zero-count proof (voyager-server)

- [x] 7.1 Red: `CupSessionComGoldenTest` names the behaviour "a cup with three computer racers at MEDIUM replays the committed transcript", with a fixed clock and round seed. The golden file is absent, so the test fails (`feat(race)`). Effort: medium (Haiku).
- [x] 7.2 Green: write `voyager/server/src/test/resources/golden/cup-session/com-field.txt` from the harness, and a human reviews the transcript before it is committed (`feat(race)`). Effort: medium (Haiku).
- [x] 7.3 Verify that `lobby-and-start.txt`, `lobby-world-start.txt`, `racing-boost-disconnect.txt`, `restart-pending-reload.txt` and `skip-finish.txt` are byte-identical to `main` (`git diff --exit-code` on the five files, verification, no commit). Effort: low (Haiku).

## 7b. Fixes from the golden review

Effort for this group: medium (Haiku) for each task. Each task is written Red first (its test fails on the code of the
previous commit), then Green. The golden transcript `com-field.txt` is regenerated once, after 7b.4, in its own commit
(`feat(race)`), and the other five transcripts must stay byte-identical.

- [x] 7b.1 Red then green: a finished computer racer's body stays where it finished and does not fall after the finish (`feat(race)`). Effort: medium (Haiku).
- [x] 7b.2 Red then green: a computer racer's first position is its seat's grid position, the map's spawn, not a point on the line at the height of the first ring (`feat(race)`). Effort: medium (Haiku).
- [x] 7b.3 Red then green: `ComField.prepare` accepts a one-ring map with no guide points whose spawn is on the ring, and builds a straight line through the ring centre (`feat(race)`). Effort: medium (Haiku).
- [x] 7b.4 Red then green: the round seed is read from the injected `Clock` when a cup's first map starts, not when the server boots (`feat(race)`). Effort: medium (Haiku).

## 7c. Results and chat

Effort for this group: medium (Haiku). Computer racers are never in `CupRound`, never stored and never scored (D6, OQ2).
They are listed in the results as `COM n` with their time and no points, and each finish gives one chat line (OQ9).

- [x] 7c.1 Red: a test that a finished computer racer appears in the ranking table as `COM n` with its time, placed among
  humans by time, and gets no points and no placement bonus (`feat(race)`). Green: the table includes computer racers by
  reading the competitor port's results through the `CupSession`, not through `platform.com` directly (`feat(race)`).
  Effort: medium (Haiku).
- [x] 7c.2 Red: a test that a finished computer racer produces exactly one chat line per language and no line per ring
  (`feat(race)`). Green: one finish line through the existing message path, with bundle keys in both `voyager_en_US` and
  `voyager_de_DE` (`feat(race)`). Effort: medium (Haiku).
- [x] 7c.3 Verify (no commit): the existing scoring and the golden transcripts are unchanged except `com-field.txt`, which
  changes only where the chat or table rows change. Regenerate `com-field.txt` only if it changes, and read the diff.
  Effort: low (Haiku).

## 8. docs (Diátaxis Framework)

Effort for this group: low (Haiku), except 8.4, which is research and takes medium.

- [ ] 8.1 Reference page `docs/reference/com-racers.md`: the two settings, the difficulty table, the lifecycle, the scoring exclusion and the known limits (`docs`). Effort: low (Haiku).
- [ ] 8.2 Update `docs/reference/config-check.md` with `VOYAGER_COM_COUNT` and `VOYAGER_COM_DIFFICULTY` and the boot line (`docs`). Effort: low (Haiku).
- [ ] 8.3 Update `docs/guides/how-to-run-a-playtest.md` with how to turn computer racers on and what to look for (`docs`). Effort: low (Haiku).
- [ ] 8.4 Research note in `docs/research/` on the movement model, the speed bounds from spike 0.3 and the tuning plan (`docs`). Effort: medium (Haiku).
- [ ] 8.5 ADR-0035 stays Proposed. Consequences section updated with the measured numbers from D14 when they exist (`docs`). Effort: low (Haiku).
- [ ] 8.6 Delta specs are synced by the archive step after merge (`openspec archive com-racers --yes`, apply-rules "Archive"), not by a task here. No separate sync commit. Effort: none.

## 9. Verification

- [ ] 9.1 Run `./gradlew build` for both trees, plus `:voyager:race:test`, `:voyager:platform:test`, `:voyager:server:test` and `:voyager:fitness:test`. Confirm the golden transcripts are byte-identical (verification). Effort: low (Haiku).
- [ ] 9.2 Run `openspec validate com-racers --strict` and resolve every finding (verification). Effort: low (Haiku).
- [ ] 9.3 Record the per-tick cost of the field with `VOYAGER_COM_COUNT` at 0 and at 15, from the tick log, in the PR description. The unit tests do not time the brain (F.I.R.S.T.). Effort: low (Haiku).

## 10. Ship

- [ ] 10.1 Smoke boot, mandatory. Run `./gradlew :voyager:server:validateCatalog`, then the apply-rules smoke procedure twice: with `VOYAGER_COM_COUNT=0` and with `VOYAGER_COM_COUNT=3` (shadow JAR in a scratch dir; wait for "Successfully enabled", "Listening on" and "Voyager started"; send `stop`; expect a clean exit). Quote those lines in the PR description. The real-client check is not part of this task: it is the owner playtest in design.md, "Owner playtest". Record the boot memory, the tick cost and the bandwidth in the PR description. Effort: low (Haiku); the agent reports the result and does not change code during the boot.
- [ ] 10.2 Open the pull request from this branch under the title `feat(race): add computer racers named COM 1, COM 2 and so on`, with the spike results, the golden result, the smoke-boot result from 10.1 and the tick cost from 9.3 in the description. End the description with exactly two lines: first `https://claude.ai/referral/m5Ak2Sa7aQ`, then `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`. Effort: low (Haiku).
