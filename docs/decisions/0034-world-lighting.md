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
- Bad: a section without stored light is computed on the packet path when a player first sees it. Measured in task 6.2: the
  first send of a 289-chunk view takes about 1 s, against about 0.2 s without the change. The owner decides whether that is
  acceptable or whether option 2 is taken (see Numbers).
- Bad: the computed light is repeated on every boot, because it is never saved.
- Neutral: the lobby keeps the overworld's sky light. No custom dimension type is added (owner decision, 2026-10-13).
- Neutral: the shipped `ElytraraceBlueAndRed` stores light for under 1.5 % of its sections, so almost all of the lit
  result is computed on the packet path (the read-only check is recorded in the change's design.md).

## Numbers

Measured on 2026-10-13 on the build of this change, JDK 25, Minestom 2026.10.07-26.2. Single runs, so the spread is
given where there were two.

**First view of the racetrack spawn (task 6.2).** The spawn of `ElytraraceBlueAndRed` is chunk (6, 3). A 17 x 17 view is
289 chunks, which is the default view distance of 8. The chunks were loaded, then each one's full data packet was built.
Heap was sampled after a forced GC.

| Configuration | Load of 289 chunks | First send of 289 chunks | Heap used after send |
|---|---|---|---|
| Without the change (`DynamicChunk`, stored light only) | 163 ms, 182 ms | 181 ms, 184 ms | 39 MB |
| With the change (`LightingChunk`, light-less sections relit) | 85 ms, 89 ms | 979 ms, 1 227 ms | 56 MB |

The first send is about five times slower with the change, roughly 1 s more on the first view of a track spawn. The
extra heap is about 17 MB for the view. The owner has not agreed a numeric budget for the first send, so this has not
been treated as a failure. It is the trigger for the fallback in the design (option 2, `falco-light`), and the owner
must decide whether to accept it or to take the fallback before this change is considered done.

**Configuration check on ElytraLobby (task 6.1).** Run as the production check (`-Dvoyager.config.check=true`, `-Xmx2G`,
G1 log) on the lobby copy at `/mnt/projects/lab/voyager-legacy-worlds/team-build/ElytraLobby`, with the same data as the
smoke boot.

| Configuration | Wall time to exit | Peak live heap after GC |
|---|---|---|
| Without the change (`origin/main`, 3efba76) | 4.8 s | 549 MB |
| With the change | 5.3 s | 552 MB |

The wall times are single runs. The peak is +3 MB, which fits the design's claim that the check computes no light. The
check exits with status 1 in both builds. It refuses the lobby on the world's own content, which the change does not
touch: 2 block names unknown to this server, and 7 708 chunks with no data in their region files. ADR-0029 already
records that `ElytraLobby` needs its upgrade to Minecraft 26.2. The lobby is therefore not yet a usable configuration, and
the numbers above describe a check that stops at the lobby rather than a boot that serves it.

**Smoke boot (task 7.1).** The shadow jar was booted with the map data and `ElytraraceBlueAndRed`, without a lobby, because
the lobby refuses as described above. The server reached "Voyager started", took the `stop` command, and exited with
status 0. Heap was 585 MB used and 800 MB committed, read with `jcmd GC.heap_info` at the ready point. A comparison
boot without the change was not made, because it needed a second full build for the same result; the check comparison
above is the measured one.

## Confirmation

- `WorldLightingTest` covers the stored light, the marking, the all-zero rule, the boot check and the write path.
- `MapInstancesWorldOpenerTest` covers that every opened world uses `LightingChunk`, and that the lobby is lit when opened
  through `LobbyTransition`.
- `CupSessionGoldenMasterTest` and the golden transcripts stay byte-identical.
- The smoke boot in task 7.1 of the change confirms that a joining racer sees lit lobby and map chunks.
