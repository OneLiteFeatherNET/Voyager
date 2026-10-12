# Tasks

Commits: `feat(race)` for every code change with its tests, `docs` for `docs/` pages and ADRs, `docs(openspec)` for
artifact edits. Each behaviour gets a failing test (Red) before its production code (Green). Every test follows F.I.R.S.T.:
no sleeps, `Clock` injected where time is read, `@TempDir` for files, a fresh Minestom instance per test with explicit
ticks, no shared static state.

## 1. Decisions and owner answers (human checkpoints)

- [ ] 1.1 Owner answers the six open questions in design.md and they are recorded under a "Owner decisions" heading (`docs(openspec)`)
- [ ] 1.2 Owner accepts ADR-0032; its Status changes from Proposed to Accepted (`docs`)
- [ ] 1.3 Owner accepts the amendment of the design spec, D8. Tasks 9.4 waits for this; code tasks do not

## 2. api: the format records

- [ ] 2.1 Red: `BoostConfigTest` names the behaviours "a boost config without the new fields keeps 64 rockets and no cap", "rockets per racer must be between 1 and 64", and "a cap must be finite and greater than zero" (`feat(race)`)
- [ ] 2.2 Green: add `rocketsPerRacer` and `maxSpeedBlocksPerTick` (nullable, absent means no cap) to `BoostConfig`, keep the two-argument constructor, add the refusal factories to `InvalidBoostConfigException` (`feat(race)`)
- [ ] 2.3 Red: `RingEffectTuningTest` names "the default tuning is 1.5 for BOOST and 0.5 for SLOW", and "a negative or non-finite factor is refused" (`feat(race)`)
- [ ] 2.4 Green: add `RingEffectTuning(boostMultiplier, slowMultiplier)` with `DEFAULT` in `net.elytrarace.voyager.api.race.effect`, and `InvalidRingEffectException` in `api.race.effect.exception` with its `package-info.java` (`feat(race)`)
- [ ] 2.5 Red: `MapDefinitionTest` names "a map without ring effects gets the default tuning" and "the seven-argument constructor still builds a map" (`feat(race)`)
- [ ] 2.6 Green: add the `ringEffects` component to `MapDefinition` and the delegating seven-argument constructor (`feat(race)`)

## 3. race: the per-map registry and the speed cap

- [ ] 3.1 Red: `RingEffectRegistryTest` names "a registry built from a tuning multiplies by that tuning" and "create() keeps 1.5 and 0.5" (`feat(race)`)
- [ ] 3.2 Green: `RingEffectRegistry.create(RingEffectTuning)`; `DefaultRingEffectRegistry` takes the tuning; rewrite the class javadoc that says the mapping is fixed, with the new caller named (`feat(race)`)
- [ ] 3.3 Red: `SpeedCapTest` names "a velocity under the cap is unchanged", "a velocity over the cap keeps its direction and has the cap's length", "no cap leaves the velocity unchanged", and "the zero velocity is unchanged" (`feat(race)`)
- [ ] 3.4 Green: `SpeedCap` as a pure function in `race.effect` (`feat(race)`)
- [ ] 3.5 Red: `RingEffectSequenceTest` names "BOOST then SLOW leaves three quarters of the speed", "two BOOST rings give 2.25 times unless the cap applies", and "effects compose in crossing order" (`feat(race)`)
- [ ] 3.6 Green: the sequence step: fold the crossed rings' effects in order, then apply `SpeedCap` once (`feat(race)`)

## 4. platform: the catalogue format

- [ ] 4.1 Red: `MapDefinitionAdapterTest` names "a file without the new fields loads with today's values", "rocketsPerRacer of 65 is refused and the message names the field", "a file with ringEffects loads the factors", and "a version 2 file with the new fields loads" (`feat(race)`)
- [ ] 4.2 Green: optional reads for the new fields in `MapDefinitionAdapter` (through `JsonFields`, which gets optional accessors) (`feat(race)`)
- [ ] 4.3 Red: `MapDraftJsonRoundTripTest` names "a draft keeps the new fields through a save and a load" and "a default value is not written to the file" (`feat(race)`)
- [ ] 4.4 Green: `MapDraftAdapter`, `MapDraftJsonWriter` and `DraftEditor` carry the new fields; the writer omits defaults (`feat(race)`)
- [ ] 4.5 Red: `FileSchemaTest` names "the map schema accepts ringEffects and the optional boost fields" and "the map schema refuses rocketsPerRacer of 0" (`feat(race)`)
- [ ] 4.6 Green: update `voyager/platform/src/main/resources/schema/map.schema.json` with the optional fields and their descriptions; keep `schemaVersion` capped at 1 (`feat(race)`)

## 5. platform: rockets

- [ ] 5.1 Red: `RacersTest` names "a map's count is the stack a racer is given" and "a reset gives the map's count again"; `RocketsTest` names "the item's flight duration follows the burn: 30 ticks gives 3, 10 gives 1, 50 gives 3" (`feat(race)`)
- [ ] 5.2 Green: `Racers.equip(player, count)` replaces the constant `ROCKETS`; `Rockets.item` derives the flight duration; rewrite the `RocketsTest` assertion that tied the duration to a constant (`feat(race)`)

## 6. platform: ring effects in the cup

- [ ] 6.1 Red: `CupSessionRingEffectTest`, with explicit ticks, names "a BOOST ring multiplies the velocity the client is sent by the map's factor", "the shadow simulation holds the same velocity as the client after the ring", "a SLOW ring after a BOOST ring on the next tick composes in order", "a grounded racer crossing a ring gets no effect", "a racer reset on the tick of a ring gets no effect", "a finished racer gets no effect", and "a capped ring writes the cap" (`feat(race)`)
- [ ] 6.2 Green: the ring step in `CupSession.raceTick` after `runs.advance`, using the registry built at `enterMap` from the map's `ringEffects`; the reset check; the cap; one `VelocityExit.send` per tick (`feat(race)`)
- [ ] 6.3 Red: `FlightTickDriverTest` names "scaleVelocity changes the velocity the next tick starts from" and "scaleVelocity for a player with no simulated state is refused" (`feat(race)`)
- [ ] 6.4 Green: the `scaleVelocity` seam on `FlightTickDriver` and `FlightTracker`, package-private to `platform.flight` (`feat(race)`)
- [ ] 6.5 Green (no new test): update the javadoc of `VelocityExit`, `Racers` and `FlightTickDriver` from "not built yet" to the built state, and keep the list of external forces at two (`feat(race)`)

## 7. platform: visibility

- [ ] 7.1 Red: `CupSessionMapStartLogTest` captures the log through the test appender and names "the map-start line states burn, cooldown, rockets, cap and both factors" (`feat(race)`)
- [ ] 7.2 Green: extend the map-start log line in `CupSession.enterMap` (`feat(race)`)
- [ ] 7.3 Golden: update the map-start line in the five transcripts under `voyager/server/src/test/resources/golden/cup-session/`. Each diff is reviewed line by line and nothing else in them changes; the test never writes goldens (`feat(race)`)

## 8. server: wiring and the shipped map

- [ ] 8.1 Red: `ShippedCatalogueTest` names "the shipped map races with default ring effects and no speed cap" (`feat(race)`)
- [ ] 8.2 Green: no new bean is expected; confirm `CupBeans` needs no change and `TickPipelineOrderTest` still passes (`feat(race)`)
- [ ] 8.3 Run `./gradlew :voyager:server:validateCatalog` and record its output in the PR description

## 9. Documentation

- [ ] 9.1 Add a reference page under `docs/reference/` for the map file's boost fields and `ringEffects`: type, bounds, default and an example. The first sentence states the cap rule (`docs`)
- [ ] 9.2 Link the reference page from ADR-0032's "More Information" section (`docs`)
- [ ] 9.3 Update ADR-0032's Status after task 1.2 (`docs`)
- [ ] 9.4 After task 1.3: amend the design spec rows (c) and (d) and the paragraph "(c) is source, not configuration", lines 818-828 of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (`docs`)

## 10. Verification

- [ ] 10.1 `./gradlew build` (both trees) is green, and `./gradlew :voyager:fitness:test` passes with no new rule needed
- [ ] 10.2 Playtest checkpoint with the owner: the shipped map's BOOST rings are flown, the feel is recorded, and the maximum shadow speed from the new log is read (design D6, option C). Any decision to add a hard cap goes back to the owner (human-in-the-loop)
- [ ] 10.3 Confirm the golden diffs are only the map-start line (task 7.3)

## 11. Ship

- [ ] 11.1 Commits follow the types above: `feat(race)` for code, `docs` for pages and ADRs, `docs(openspec)` for artifacts. The archive commit is `docs(openspec): archive per-map-boost`, separate from the implementation
- [ ] 11.2 Open the pull request with the title `feat(race): tune boost and ring effects per map`. Its body ends with the two footer lines: `https://claude.ai/referral/m5Ak2Sa7aQ` and then `https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz`
