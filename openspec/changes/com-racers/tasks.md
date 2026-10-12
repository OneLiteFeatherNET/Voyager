# Tasks

Commits follow the type this change names: `feat(race)` for every code change and its tests, `docs` for `docs/` pages and
the ADR, `docs(openspec)` for artifact edits. Each behaviour gets a failing test (Red) before its production code
(Green), under "Red/Green TDD". Every test follows F.I.R.S.T.: no sleeps, `Clock` injected, a fixed seed, `@TempDir` for
worlds, a fresh Minestom instance per test with explicit ticks.

**Build split and effort.** Each group names the effort for the Haiku implementation agent that does it, as the owner
asked. Every agent that writes files runs with `isolation: "worktree"` and branches from the current `origin/main`. The
groups are ordered: a group starts when the groups it names are merged into this branch. Groups 2 and 4 wait for the
owner's answers in group 0, because the join policy and the settings change the code.

## 0. Owner answers and spikes (no production code)

- [ ] 0.1 Record the owner's answers to OQ1 to OQ10 in design.md, "Owner questions", replacing each recommendation with the decision (`docs(openspec)`). Effort: low (Haiku).
- [ ] 0.2 Owner accepts ADR-0035 and its status line changes from Proposed to Accepted (`docs`). Effort: low (Haiku). The owner decides; the agent only edits the status.
- [ ] 0.3 Spike S1, no commit: read the `steady-glide`, `dive-and-pull-out` and `chained-boosts` recordings in `docs/reference/elytra-physics-26.2.md`, measure the horizontal speed, the yaw and pitch change per tick and the sink rate, and write the measured cap, floor, turn bound and sink bound into design.md D14 (`docs(openspec)`). Effort: medium (Haiku). Groups 2.1 to 2.8 use these numbers; until D14 is measured they use the starting values.
- [ ] 0.4 Spike S2, no commit: on a fresh Minestom test instance, spawn an `Entity` of `EntityType.PLAYER` with a `PlayerMeta`, send `PlayerInfoUpdatePacket` for its tab-list entry, and record whether the entity's metadata and move packets are sent as expected. Write the result in design.md D2 (`docs(openspec)`). If the answer is no, switch the body to D2 option C (armour stand) before group 4. Effort: medium (Haiku). The real-client check is in 10.1.

## 1. api: the difficulty value

- [ ] 1.1 Red: `ComDifficultyTest` names the behaviours "the names EASY, NORMAL and HARD are listed in that order", "byName is case-insensitive", and "an unknown name is empty" (`feat(race)`). Effort: low (Haiku).
- [ ] 1.2 Green: add the enum `ComDifficulty` in `net.elytrarace.voyager.api.race.com` with `VALUES` and `byName` returning `Optional`, and a `package-info.java` with `@NotNullByDefault` (`feat(race)`). Effort: low (Haiku).

## 2. race: the pure brain (voyager-race, `race.com`)

Effort for this whole group: high (Haiku). It is the movement model and the difficulty numbers; the agent reads design.md D1,
D3, D4, D13 and D14 before writing a test. No `System` clock and no unseeded `Random` appear in this group (rule F1).

- [ ] 2.1 Red: `ComPathTest` names the behaviours "the arc length of a straight course is the sum of its segments", "a distance before the start clamps to the first point", "a distance past the end clamps to the last point", and "the tangent at any distance has unit length" (`feat(race)`). Effort: high (Haiku).
- [ ] 2.2 Green: add `ComPath`, built once from a `RacingLine` with cumulative arc lengths and binary search for `pointAt` and `tangentAt` (`feat(race)`). Effort: high (Haiku).
- [ ] 2.3 Red: `ComTuningTest` names the behaviours "EASY is slower and wanders further than NORMAL", "HARD has no miss chance and the reference speed factor", "the speed factor never exceeds one", and "a negative amplitude is refused" (`feat(race)`). Effort: medium (Haiku).
- [ ] 2.4 Green: add `ComTuning.of(ComDifficulty)` with the table in design.md D3, the exceptions `InvalidComTuningException` in `race.com.exception` with its `package-info.java`, and the seeded personal variation of plus or minus 2 per cent (`feat(race)`). Effort: medium (Haiku).
- [ ] 2.5 Red: `ComRosterTest` names the behaviours "the grid fills to the target count", "humans already on the grid reduce the count", "the same seed gives the same seats", "seats are numbered COM 1 to COM n in order", and "a count above 15 is refused" (`feat(race)`). Effort: medium (Haiku).
- [ ] 2.6 Green: add `ComRoster.plan(targetCount, humans, seed)` returning seats, with `InvalidComCountException` in `race.com.exception` (`feat(race)`). Effort: medium (Haiku).
- [ ] 2.7 Red: `ComPilotTest` names the behaviours "one step advances the distance by the speed", "the same seed and tick give the same position", "the speed never exceeds the cap", "the speed changes by no more than the acceleration bound in one tick", "a missed ring is re-crossed through its disc and counted once", "HARD never misses a ring", "a boost takes effect after the reaction delay, with a fake ring effect", "a racer with no progress for 400 ticks is marked did-not-finish", and "a whole lap of a fixture course is run with no server and no clock" (`feat(race)`). The lap test uses `RingPass` and `ProgressTracker` on a fixture `MapDefinition`. Effort: high (Haiku).
- [ ] 2.8 Green: add `ComPilot.step(ComState, ComTuning, ComPath, long tick)` as a pure function returning the next `ComState`, and the records `ComState` and `ComSeat` (`feat(race)`). Effort: high (Haiku).
- [ ] 2.9 Refactor, only if needed, with the java-style skill applied: no behaviour change, tests stay green (`refactor(race)`). Effort: medium (Haiku).

## 3. platform.cup: the port and the cup's calls (voyager-platform)

Effort for this group: medium (Haiku). The cup must not learn about Minestom bodies (rule F2).

- [ ] 3.1 Red: `CupSessionCompetitorTest` drives explicit ticks with a fake `CompetitorField` and names the behaviours "a competitor's run starts at its map's start", "a competitor's position feeds its run and not the human supplier", "a competitor that is not on the course is not advanced", and "dismiss is called at cup end and on abort" (`feat(race)`). Effort: medium (Haiku).
- [ ] 3.2 Green: add `CompetitorField` (the port), `Competitor` (a record of id, position and gliding), and `NoCompetitors` in `platform.cup`, and give `CupSession` the port. Call `prepare` in `mapStarted`, `advance` after the human loop in `raceTick`, and `dismiss` at cup end and abort (`feat(race)`). Effort: medium (Haiku).
- [ ] 3.3 Confirm `CupSessionGoldenMasterTest` and the four other golden transcripts pass unchanged with `NoCompetitors` (no commit, verification) (`feat(race)` on the same commit as 3.2). Effort: low (Haiku).

## 4. platform.com: the Minestom adapter (voyager-platform)

Effort for this group: medium (Haiku). Group 0.4 must be done first; its result decides the body.

- [ ] 4.1 Red: `ComNamesTest` names the behaviours "seats are named COM 1 to COM n", "a name is kept for the life of the cup", and "a human's id is not a computer racer" (`feat(race)`). Effort: low (Haiku).
- [ ] 4.2 Green: add `ComNames` with `nameOf(seat)`, `isComputer(uuid)`, and the seat ids derived from the round seed and the seat index (`feat(race)`). Effort: low (Haiku).
- [ ] 4.3 Red: `ComBodyTest` on a fresh Minestom instance with explicit ticks names the behaviours "a body spawns in its instance at its position", "a teleport moves the body on the next tick", "removal despawns the body", and "the body shows its custom name" (`feat(race)`). If `voyager-platform` has no Minestom testing dependency, the same commit adds it (`feat(race)`). Effort: medium (Haiku).
- [ ] 4.4 Green: add `ComBody`, wrapping `new Entity(EntityType.PLAYER, uuid)` with a `PlayerMeta` and a custom name, or the armour stand fallback if 0.4 said so (`feat(race)`). Effort: medium (Haiku).
- [ ] 4.5 Red: `ComTabListTest` names the behaviours "every human gets the add entry before a body spawns", "a human who joins mid-cup gets the entries of the current bodies", and "dismiss sends the remove entries" (`feat(race)`). Effort: medium (Haiku).
- [ ] 4.6 Green: add `ComTabList` over `PlayerInfoUpdatePacket` and `PlayerInfoRemovePacket`, with the default skin and no textures (`feat(race)`). Effort: medium (Haiku).
- [ ] 4.7 Red: `ComFieldTest` with a fake brain and explicit ticks names the behaviours "prepare returns one id per computer racer", "advance returns positions in seat order after the humans", "dismiss removes every body and entry", "a count of zero creates nothing", and "the path for a map is built once and shared" (`feat(race)`). Effort: medium (Haiku).
- [ ] 4.8 Green: add `ComField implements CompetitorField` over `ComRoster`, `ComPilot`, `ComPath` (cached per map), `ComBody` and `ComTabList` (`feat(race)`). Effort: medium (Haiku).

## 5. server: settings, configuration check and wiring (voyager-server)

Effort for this group: low for wiring and the check; the settings tests are medium (Haiku).

- [ ] 5.1 Red: `ServerSettingsComTest` names the behaviours "an absent count is zero", "a count of 16 is refused and names the range", "a count that is not a number is refused and names the value", "an absent difficulty is NORMAL", "a difficulty is case-insensitive", and "an unknown difficulty is refused and lists the three names" (`feat(race)`). Effort: medium (Haiku).
- [ ] 5.2 Green: add `COM_COUNT_PROPERTY` and `COM_DIFFICULTY_PROPERTY` and the two components to `ServerSettings` with the existing compatibility constructors (`feat(race)`). Effort: low (Haiku).
- [ ] 5.3 Red: `ConfigCheckComTest` names the behaviours "a boot line states the count and difficulty when enabled" and "no line is logged when the count is zero" (`feat(race)`). Effort: low (Haiku).
- [ ] 5.4 Green: add the computer racer lines to `ConfigCheck`, keyed by setting name (`feat(race)`). Effort: low (Haiku).
- [ ] 5.5 Red then green: `CupBeans` wiring test names the behaviours "a count of zero binds NoCompetitors" and "a count above zero binds ComField"; wire it in the composition root only (`feat(race)`). Effort: low (Haiku).

## 6. fitness: the boundary rules (voyager-fitness)

Effort for this group: medium (Haiku). The rules are written first and fail on an empty match until the code exists,
because `allowEmptyShould(false)` makes an empty match a failure.

- [ ] 6.1 Red: `ComBoundaryTest` declares `comBrainIsPure` (F1), `cupDoesNotDependOnComPlatform` (F2), `comAdapterDoesNotSimulateFlight` (F3) and `onlyServerConfigReadsComSettings` (F4), each with `allowEmptyShould(false)` and not frozen. Run it: the rules fail with no matching classes (`feat(race)`). Effort: medium (Haiku).
- [ ] 6.2 Green: the rules pass once groups 2 to 5 are in. Any violation found is fixed in the production code, not by a freeze (`feat(race)`). Effort: low (Haiku).
- [ ] 6.3 Confirm that `SliceBoundaryRulesTest` r4 and r7 match `platform.com` and `race.com` without a change, and that `FitnessCoverageTest` needs no change (verification, no commit). Effort: low (Haiku).

## 7. golden master and the zero-count proof (voyager-server)

- [ ] 7.1 Red: `CupSessionComGoldenTest` names the behaviour "a cup with three computer racers at NORMAL replays the committed transcript", with a fixed clock and round seed. The golden file is absent, so the test fails (`feat(race)`). Effort: medium (Haiku).
- [ ] 7.2 Green: write `voyager/server/src/test/resources/golden/cup-session/com-field.txt` from the harness, and a human reviews the transcript before it is committed (`feat(race)`). Effort: medium (Haiku).
- [ ] 7.3 Verify that `lobby-and-start.txt`, `lobby-world-start.txt`, `racing-boost-disconnect.txt`, `restart-pending-reload.txt` and `skip-finish.txt` are byte-identical to `main` (`git diff --exit-code` on the five files, verification, no commit). Effort: low (Haiku).

## 8. docs (Diátaxis Framework)

Effort for this group: low (Haiku), except 8.4, which is research and takes medium.

- [ ] 8.1 Reference page `docs/reference/com-racers.md`: the two settings, the difficulty table, the lifecycle, the scoring exclusion and the known limits (`docs`). Effort: low (Haiku).
- [ ] 8.2 Update `docs/reference/config-check.md` with `VOYAGER_COM_COUNT` and `VOYAGER_COM_DIFFICULTY` and the boot line (`docs`). Effort: low (Haiku).
- [ ] 8.3 Update `docs/guides/how-to-run-a-playtest.md` with how to turn computer racers on and what to look for (`docs`). Effort: low (Haiku).
- [ ] 8.4 Research note in `docs/research/` on the movement model, the speed bounds from spike 0.3 and the tuning plan (`docs`). Effort: medium (Haiku).
- [ ] 8.5 ADR-0035 status line, "Accepted", after 0.2, and the consequences section updated with the measured numbers (`docs`). Effort: low (Haiku).
- [ ] 8.6 Sync the delta specs into `openspec/specs/` when the change ships with its implementation, with no separate archive commit (`docs(openspec)`). Effort: low (Haiku).

## 9. Verification

- [ ] 9.1 Run `./gradlew build` for both trees, plus `:voyager:race:test`, `:voyager:platform:test`, `:voyager:server:test` and `:voyager:fitness:test`. Confirm the golden transcripts are byte-identical (verification). Effort: low (Haiku).
- [ ] 9.2 Run `openspec validate com-racers --strict` and resolve every finding (verification). Effort: low (Haiku).
- [ ] 9.3 Record the per-tick cost of the field with `VOYAGER_COM_COUNT` at 0 and at 15, from the tick log, in the PR description. The unit tests do not time the brain (F.I.R.S.T.). Effort: low (Haiku).

## 10. Ship

- [ ] 10.1 Smoke boot, mandatory. Run `./gradlew :voyager:server:validateCatalog`, then `VOYAGER_COM_COUNT=5 ./gradlew :voyager:server:runServer` on the playtest server with a real client. Check: the boot line; computer racers appear on the grid at cup start and not before; each has the name `COM n` above its head and a tab-list entry; they move along the line; the table lists them at the finish; they are removed at cup end; the tab list is clean after. Confirm the 0.4 result with a real client (the body is visible, not hidden). Then run with `VOYAGER_COM_COUNT` unset and confirm nothing changed. Record the boot memory, the tick cost and the bandwidth in the PR description. Effort: low (Haiku), but the agent reports the result and does not change code during the boot.
- [ ] 10.2 Open the pull request from this branch under the title `feat(race): add computer racers named COM 1, COM 2 and so on`, with the spike results, the golden result, the smoke-boot result from 10.1 and the tick cost from 9.3 in the description. End the description with exactly two lines: first `https://claude.ai/referral/m5Ak2Sa7aQ`, then `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`. Effort: low (Haiku).
