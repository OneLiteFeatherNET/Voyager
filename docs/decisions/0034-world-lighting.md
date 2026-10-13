# ADR-0034: Worlds are lit by Minestom's LightingChunk, and stored light is kept

## Status

Accepted

Accepted by the project owner on 2026-10-13, with the approval of the change `world-lighting` that implements it.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided that the lobby world and every map world use a real light engine,
  so nothing is dark.
- Drafted with the change `world-lighting`. The engine and the boot-check rule are in its design.md, D1 to D4.

## Context and problem statement

The rebuild loads its worlds through Falco (`falco-anvil` 3.0.1, via `aonyx-bom` 0.8.9). Falco reads the stored
`SkyLight` and `BlockLight` arrays of each section and applies them to the chunk, but the rebuild uses Minestom's default
`DynamicChunk`, which sends each section's light as stored. A section with no stored array is sent with no light, and
the client draws it dark. The owner requires that no area of the lobby or of any map is dark.

Minestom ships a light engine, `LightingChunk`. It does not light a chunk that a loader returns, because it relights only
the sections whose light is marked for update, and a section that a loader has filled is not marked. So adopting it
alone does not remove the dark area.

Falco ships a second engine, `falco-light`, which recomputes the light of every loaded chunk from its block states.

## Decision drivers

- No dark area in the lobby or in any map, for the worlds as they are stored.
- Stored light is what the world was built with, and it must not be replaced by a recompute.
- The worlds are read-only and never saved, so a recomputed light is never written back.
- The boot configuration check reads every chunk of a world, one world at a time (#297), and must not gain time or memory
  from lighting.
- No new experimental dependency on the platform module.

## Considered options

1. **Minestom `LightingChunk` for every instance, with light-less sections marked for relight (chosen).** Stored light is
   kept. A section without stored light is relit the first time a viewer is sent its chunk. No dependency is added.
2. **`falco-light` via `ChunkLightScheduler.supplier()`.** It recomputes every loaded chunk on its first tick, ignoring
   stored light. It needs `falco-instance` on the runtime path, and its API is marked experimental.
3. **Keep `DynamicChunk` and compute light in the platform.** Duplicates the engine that Minestom already ships and
   maintains.
4. **Do nothing.** The lobby and the maps stay dark where no light is stored. Rejected by the owner.

## Decision outcome

Chosen option: **1**, with these rules.

- Every instance that `MapInstances` opens uses `LightingChunk`. That covers the lobby world, every map world and the
  authoring worlds of the setup server, which open through `MapInstances` too.
- A section keeps its stored `SkyLight` and `BlockLight` arrays. A light with no stored array is marked for relight, and
  only that kind. The test is `requiresSend()`, not the array length, because an all-zero stored array reads back as
  empty too.
- The marking runs in a `ChunkLoader` decorator before the chunk is cached, so no viewer can be sent an unmarked chunk.
- Light is computed on the first send to a viewer and is never written to a region file.
- The configuration check computes no light, because it loads chunks but never sends them.

## Consequences

- Good: the stored light of the worlds is used as it was saved, and nothing is recomputed that does not need to be.
- Good: no dependency is added, and the engine is Minestom's, which the rebuild already runs on.
- Good: the boot check's time and peak memory stay where they are, because stored light is already applied today.
- Bad: a section without stored light is computed on the packet path when a player first sees it, which may cause a
  hitch. Measured in the change's task 6.2. If it is too slow, option 2 is the fallback.
- Bad: the computed light is repeated on every boot, because it is never saved.
- Neutral: the lobby keeps the overworld's sky light. No custom dimension type is added (owner decision, 2026-10-13).
- Neutral: the shipped `ElytraraceBlueAndRed` stores light for under 1.5 % of its sections, so almost all of the lit
  result is computed on the packet path (the read-only check is recorded in the change's design.md).

## Numbers

Not measured yet. The design gives analytic bounds: about zero extra work at boot, and at most roughly 28 MB for a
289-chunk view (view distance 8, the default) of light-less sections, before packet caches. The measured boot time, the boot memory and the time to the
first light packet of a view are recorded here by tasks 6.1 and 6.2 of the change. Lighting for the spawn is not
pre-computed (owner decision, 2026-10-13); the first send of each chunk pays the cost.

## Confirmation

- `WorldLightingTest` covers the stored light, the marking, the all-zero rule, the boot check and the write path.
- `MapInstancesWorldOpenerTest` covers that every opened world uses `LightingChunk`, and that the lobby is lit when opened
  through `LobbyTransition`.
- `CupSessionGoldenMasterTest` and the golden transcripts stay byte-identical.
- The smoke boot in task 7.1 of the change confirms that a joining racer sees lit lobby and map chunks.
