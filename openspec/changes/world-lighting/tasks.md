# Tasks

Commits follow the types the change names: `feat(platform)` for every code change and its tests, `docs` for `docs/`
pages and ADRs, `docs(openspec)` for artifact edits. Each behaviour gets a failing test (Red) before its production code
(Green). Every test follows F.I.R.S.T.: no sleeps, no wall clock, `@TempDir` for every world, a fresh Minestom instance per
test with explicit ticks, and region fixtures written in the test itself, never from `run/`.

## 1. Decisions

- [x] 1.1 Owner accepts ADR-0034 (`docs/decisions/0034-world-lighting.md`) and its Status line changes from Proposed to Accepted (`docs`)
- [x] 1.2 Spike, no commit: read the section light arrays of the shipped `ElytraraceBlueAndRed` region files, read-only. Result: 98.8 % of sections store no light; the change stays in scope (design.md, OQ1). `ElytraLobby` was not read; the owner asked for `ElytraraceBlueAndRed` only.

## 2. platform: marking light-less sections

- [ ] 2.1 Red: `WorldLightingTest` names the behaviours "a loaded chunk keeps its stored light", "a section without stored light is marked for relight", "a section with an all-zero stored array is not marked", and "the boot check computes no light" (`feat(platform)`)
- [ ] 2.2 Green: add `platform.world.WorldLight` (package-private) that marks a light only when it has no stored array (`requiresSend()` is false; `Light.array()` cannot tell absent from all-zero), and wrap the Falco loader in its `ChunkLoader` decorator so the mark runs before the chunk is cached (`feat(platform)`)
- [ ] 2.3 Red: `WorldLightingTest` names the behaviours "a viewer sent a light-less chunk receives computed block light", and "a relit chunk leaves the region file byte-identical after close" (`feat(platform)`)
- [ ] 2.4 Green: no production change expected; if 2.3 fails, fix the cause in `WorldLight` or `TrackingInstance` and record it in design.md (`feat(platform)`)

## 3. platform: the lighting chunk for every opened world

- [ ] 3.1 Red: `MapInstancesWorldOpenerTest` names the behaviours "every instance MapInstances opens uses LightingChunk" and "the lobby world opened through LobbyTransition is lit" (`feat(platform)`)
- [ ] 3.2 Green: `TrackingInstance` sets `setChunkSupplier(LightingChunk::new)` in its constructor, so every world `MapInstances` opens uses it (`feat(platform)`)

## 4. Documentation

- [ ] 4.1 Add `docs/explanation/world-lighting.md` (Diátaxis explanation): where a dark area comes from, which light is stored, which is computed, and what the boot check does not compute (`docs`)
- [ ] 4.2 Update `docs/decisions/0034-world-lighting.md` with the measured numbers from 6.1 and 6.2 (`docs`)

## 5. Verification

- [ ] 5.1 Run `./gradlew :voyager:platform:test :voyager:server:test :voyager:fitness:test` and `./gradlew build`; confirm `CupSessionGoldenMasterTest` and the golden transcripts are byte-identical (no commit)

## 6. Measurement

- [ ] 6.1 On ElytraLobby (8676 chunks) with the owner's copy, record the boot time and the peak heap of the configuration check, with and without the change; record the numbers in ADR-0034 (no commit; needs the operator's world)
- [ ] 6.2 Measure the time to the first light packet of a 289-chunk view (view distance 8, the default) and the heap after it, with and without the change; if the first view exceeds the budget agreed with the owner, record the fallback to `falco-light` in ADR-0034 (no commit)

## 7. Smoke boot and pull request

- [ ] 7.1 Mandatory: run the smoke boot (`./gradlew :voyager:server:validateCatalog` and `./gradlew :voyager:server:runServer` with the lobby world configured), confirm a joining racer sees lit lobby and map chunks, and record the boot time and memory from 6.1. Then open the pull request with the title `feat(platform): light the racetrack and lobby worlds`. The PR description ends with these two lines, in this order:

  https://claude.ai/referral/m5Ak2Sa7aQ
  https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz
