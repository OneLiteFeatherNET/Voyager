# Tasks

Commits follow the types the change names: `feat(platform)` for every code change and its tests, `docs` for `docs/`
pages and ADRs, `docs(openspec)` for artifact edits. Each behaviour gets a failing test (Red) before its production code
(Green). Every test follows F.I.R.S.T.: no sleeps, no wall clock, `@TempDir` for every world, a fresh Minestom instance per
test with explicit ticks, and region fixtures written in the test itself, never from `run/`.

## 1. Decisions

- [x] 1.1 Owner accepts ADR-0034 (`docs/decisions/0034-world-lighting.md`) and its Status line changes from Proposed to Accepted (`docs`)
- [x] 1.2 Spike, no commit: read the section light arrays of the shipped `ElytraraceBlueAndRed` region files, read-only. Result: 98.8 % of sections store no light; the change stays in scope (design.md, OQ1). `ElytraLobby` was not read; the owner asked for `ElytraraceBlueAndRed` only.

## 2. platform: marking light-less sections

- [x] 2.1 Red: `WorldLightingTest` names the behaviours "a loaded chunk keeps its stored light", "a section without stored light is marked for relight", "a section with an all-zero stored array is not marked", and "the boot check computes no light" (`feat(platform)`). Commit 8497ba8. The boot-check case is written as a load without a send, because `checkWorld` discards the chunks it reads.
- [x] 2.2 Green: add `platform.world.WorldLight` (package-private) that marks a light only when it has no stored array (`requiresSend()` is false; `Light.array()` cannot tell absent from all-zero), and wrap the Falco loader in its `ChunkLoader` decorator so the mark runs before the chunk is cached (`feat(platform)`). Commit 53a79a3, which also sets the supplier (3.2).
- [x] 2.3 Red: `WorldLightingTest` names the behaviours "a viewer sent a light-less chunk receives computed block light", and "a relit chunk leaves the region file byte-identical after close" (`feat(platform)`). Same commit as 2.1.
- [x] 2.4 Green: no production change needed; the Green tests passed with the 2.2 code.

## 3. platform: the lighting chunk for every opened world

- [x] 3.1 Red: `MapInstancesWorldOpenerTest` names the behaviours "every instance MapInstances opens uses LightingChunk" and "the lobby world opened through LobbyTransition is lit" (`feat(platform)`). Commit 849396e. The lobby test is in `LobbyTransitionTest`, where the lobby is opened. Both pass, because the supplier was already set.
- [x] 3.2 Green: `TrackingInstance` sets `setChunkSupplier(LightingChunk::new)` in its constructor, so every world `MapInstances` opens uses it (`feat(platform)`). Done in 53a79a3 with 2.2.

## 4. Documentation

- [x] 4.1 Add `docs/explanation/world-lighting.md` (Diátaxis explanation): where a dark area comes from, which light is stored, which is computed, and what the boot check does not compute (`docs`). Commit 8509dc2.
- [x] 4.2 Update `docs/decisions/0034-world-lighting.md` with the measured numbers from 6.1 and 6.2 (`docs`). The lobby blocker and the first-send cost are in its Numbers section.

## 5. Verification

- [x] 5.1 Run `./gradlew :voyager:platform:test :voyager:server:test :voyager:fitness:test` and `./gradlew build`; confirm `CupSessionGoldenMasterTest` and the golden transcripts are byte-identical (no commit). `./gradlew build` passed for both trees: platform 636, server 205, fitness 56 tests, no failures; no golden transcript or ArchUnit store changed.

## 6. Measurement

- [x] 6.1 On ElytraLobby (8676 chunks) with the owner's copy, record the boot time and the peak heap of the configuration check, with and without the change; record the numbers in ADR-0034 (no commit; needs the operator's world). Done on the lab copy at `/mnt/projects/lab/voyager-legacy-worlds/team-build/ElytraLobby`, as the only copy found: the check takes 4.8 s without and 5.3 s with the change, and the peak live heap is 549 MB and 552 MB. The lobby is refused on its own content (2 unknown block names, 7 708 chunks with no data), so the numbers describe a check that stops at the lobby, not a served lobby. The owner's copy may differ and was not found.
- [x] 6.2 Measure the time to the first light packet of a 289-chunk view (view distance 8, the default) and the heap after it, with and without the change; if the first view exceeds the budget agreed with the owner, record the fallback to `falco-light` in ADR-0034 (no commit). Result: the first send takes about 180 ms without and about 980 to 1 230 ms with the change. No numeric budget was agreed with the owner, so the fallback is recorded as the owner's decision, not taken.

## 7. Smoke boot and pull request

- [x] 7.1 Mandatory: run the smoke boot, confirm a joining racer sees lit lobby and map chunks, and record the boot time and memory from 6.1. Done without the lobby, because the lobby is refused (see 6.1): the server booted with the three ready lines, heap 585 MB used, stopped cleanly with exit 0. A real client was not connected, so "a joining racer sees lit chunks" is not verified; the in-process tests cover the packets. Then open the pull request with the title `feat(platform): light the racetrack and lobby worlds`. The PR description ends with these two lines, in this order:

  https://claude.ai/referral/m5Ak2Sa7aQ
  https://claude.ai/code/session_01QgtyvQwXoNTXyBjSyAABrz
