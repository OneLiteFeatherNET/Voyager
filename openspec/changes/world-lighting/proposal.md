# Proposal

**Conventional Commits:** `feat(platform)`. Squash-merge PR title: `feat(platform): light the racetrack and lobby worlds`.

**Owner decision (2026-10-12):** the lobby world and every map world use a real light engine, so nothing is dark.

**Slices touched:** `platform/world` (the instance and chunk loading in `MapInstances` and `TrackingInstance`). The lobby
world is opened through the same `MapInstances` path by `LobbyTransition` (change `add-lobby-world`), so it gets the same
lighting without a second code path. No `api`, `race`, `cup` or `catalog` code changes.

**Records:** ADR-0034 (`docs/decisions/0034-world-lighting.md`, Proposed) records the engine choice.

**Depends on:** nothing merged. It is independent of `add-lobby-world`, which only needs to open its world through
`MapInstances`, as it already does.

## Why

The rebuild loads every world through Falco (`falco-anvil` 3.0.1, via `aonyx-bom` 0.8.9). Falco reads the stored
`SkyLight` and `BlockLight` arrays of each section and hands them to the chunk. The rebuild then uses Minestom's default
`DynamicChunk`, which forwards those arrays to the client as they are. A section with no stored array is sent with no
light, and the client draws it dark. The owner wants no dark area on any map or in the lobby.

Minestom ships a light engine, `LightingChunk`, but it does not light a chunk that a loader hands it. Its `onLoad` only
marks the chunk ready. Its relight condition, `Light.requiresUpdate()`, is `!isValidBorders`, and a fresh light starts
with `isValidBorders = true`. A section with no stored light therefore stays unlit even under `LightingChunk`, unless
something invalidates it. Adopting the supplier alone would not fix the dark area, so the change has to mark the missing
sections.

## What Changes

- **Engine.** Every instance `MapInstances` opens is built with Minestom's `LightingChunk` (chosen in ADR-0034). Falco's
  `falco-light` is not added (see the design, D1).
- **Stored light is kept.** A section that carries a stored `SkyLight` or `BlockLight` array keeps it. It is not
  recomputed on load.
- **Missing light is computed on demand.** A section with no stored array of a kind is marked for relight when its chunk
  loads. The light is computed the first time a viewer is sent that chunk, and it is never written to disk.
- **Boot check stays cheap.** `MapInstances.readEveryChunk` and `checkWorld` load chunks but never send them, so they
  compute no light. The check's time and memory stay within the bounds in the design.
- **Setup server.** The authoring worlds of `voyager-setup` open through `MapInstances.forWorld` in `SetupCommands`, so
  the same change lights them (owner decision, 2026-10-13). No setup code changes.
- **No save path.** Nothing in this change writes light to a region file. The worlds stay read-only and are never saved.

**BREAKING:** none. No setting, catalogue file or map format changes.

Out of scope:
- `falco-light` and its `falco-instance` dependency.
- A dimension type without sky light for the lobby. This is an open question for the owner (design, OQ2).
- Pre-lighting the spawn area at boot. The light is computed lazily by default (design, OQ4).
- Any change to the race, cup or scoring behaviour, or to the golden transcripts.
- Reading the shipped worlds under `run/` beyond the read-only check of `ElytraraceBlueAndRed` (design, OQ1 result).
