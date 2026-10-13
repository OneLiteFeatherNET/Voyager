# Design

## Context and measured facts

All facts are read from source. The versions are the ones the rebuild resolves: Minestom `2026.10.07-26.2`, falco-anvil
and falco-light `3.0.1`. The sources are the `-sources.jar` files of the release repository and the Minestom sources jar
in the Gradle cache.

1. **Falco already reads stored light.** `FalcoAnvilLoader.decodeSections` reads `SkyLight` and `BlockLight` for each
   section (a 2048-byte array, else absent) and `DecodedSection.applyTo` calls `section.skyLight().set(...)` and
   `section.blockLight().set(...)`. Nothing is discarded at load time. On save, the loader writes both arrays back, but
   nothing in the rebuild saves a world.
2. **Falco builds chunks through the instance's supplier.** `FalcoAnvilLoader` calls
   `instance.getChunkSupplier().createChunk(...)`. So the chunk class is chosen by `setChunkSupplier`, which
   `TrackingInstance` does not set today. The rebuild uses the default `DynamicChunk`.
3. **Minestom sends stored light as it is.** `DynamicChunk.createLightData` sends each section's `array()`. An absent array
   (length 0) goes into the "empty" mask, so the section is sent with no light, which is the dark area.
4. **`LightingChunk` relights only what is marked.** `onLoad` only sets `doneInit`. `onGenerate` runs for generated chunks,
   not for chunks that a loader returns, so it never runs for a Falco chunk. `createLightData` relights a section only
   when `Light.requiresUpdate()` is true, which is `!isValidBorders`. `Light.set(...)` sets `isValidBorders = true`, and
   a fresh light starts with `isValidBorders = true`. `Section.invalidate()` (public) sets it to false. So a section
   with no stored light is never relit until something invalidates it.
5. **`falco-light` recomputes every loaded chunk.** `ChunkLightListener.onLoad` marks each loaded chunk changed, and the
   scheduler then computes its light from the block states on the next tick. Stored light is ignored. The chunk class,
   `FalcoLightingChunk`, extends `FalcoChunk` from `falco-instance`, which is a `compileOnly` dependency of `falco-light`.
   The API carries `@ApiStatus.Experimental`.

## Decisions

### D1. Engine: Minestom `LightingChunk` with a marker for light-less sections (chosen)

Options:

| | A. Minestom `LightingChunk` + mark absent sections (chosen) | B. `falco-light` via `ChunkLightScheduler.supplier()` |
|---|---|---|
| Stored light | Kept, never recomputed | Ignored; every loaded chunk is recomputed |
| Missing light | Relit on first send to a viewer | Relit on the next tick after load |
| Dependencies | None: Minestom already on the classpath | `falco-light`, plus `falco-instance` on the runtime path |
| API maturity | Minestom public API | `@ApiStatus.Experimental` |
| Work at boot | None: loads only flip flags | One pass per loaded chunk on the first tick |
| Block edits | Minestom relights neighbours | Incremental, designed for edits (not needed here) |

Pro of A: the cheapest path that meets "nothing is dark" for stored-light worlds, with no new dependency. Con of A: the
engine computes lazily on the packet path (under the chunk's packet lock), so a player entering unlit area can see a
hitch. Measured in task 6.2. Pro of B: it already covers missing light and handles block edits, which this change does
not need. Con of B: it recomputes what is stored, adds a dependency, and is experimental. B stays the fallback if A's
relight is too slow in the smoke boot.

Chosen: **A**. Rule: a section keeps a stored light array; a section without one is marked for relight. The mark is
applied in `TrackingInstance.retrieveChunk`, after the loader and `onLoad` have run:

```java
// Sketch, not production code. Light-less sections are marked so LightingChunk relights them on send.
for each section in chunk: if (section.skyLight().array().length == 0) section.skyLight().invalidate();
                           if (section.blockLight().array().length == 0) section.blockLight().invalidate();
```

`Light.array()` returns an empty array when no content is set (`UNSET_CONTENT`). An all-zero stored array is still 2048
bytes and counts as stored. `Section.invalidate()` invalidates both lights together, so the sketch marks the whole
section, and the per-kind check decides only whether the section is marked at all.

### D2. Stored light is authoritative and is not recomputed

Vanilla wrote the stored light when it last saved each chunk. The worlds are read-only and never saved by the rebuild, so
the stored light is the light the world was built with. A recompute would change what the owner built. The rule is per
light kind: a section can keep stored sky light and be relit for block light, if only one array is missing. The
implementation marks the section, so the rule holds only if the stored array is also kept; task 2.1 proves it with a test.

### D3. Missing light is computed on demand and never saved

A light-less section is relit the first time a viewer is sent its chunk. The result stays in memory for the life of the
server, as Minestom caches it. Nothing is written back, so every boot recomputes what was missing. The cost is repeated
per boot and per process, which is acceptable for read-only worlds and is a reason to measure it (task 6.2).

### D4. Boot check: no light is computed

`readEveryChunk` only calls `loadChunk(...).join()` and releases each region afterwards. It never sends a chunk, so
`createLightData` never runs and no relight happens. The marking in D1 costs one flag write per light-less section and
allocates nothing. `checkWorld` discards the world after the read. So the boot check computes no light, and its peak
memory is unchanged, because the stored light arrays are already applied today (fact 1). The check's one-world-at-a-time
rule from #297 and `add-lobby-world` is unchanged.

Task 2.1 proves this with a test: after `checkWorld` on a fixture with a light-less section, that section still
reports `requiresUpdate() == true`. A relight would have set it to false.

### D5. No write path

Nothing in this change calls a save. `MapInstances.close()` already documents that it does not save. A test asserts that
the region files are byte-identical after a player is sent a relit chunk.

### D6. No dependency change

`voyager/platform/build.gradle.kts` does not change. `falco-light` is not added. The `voyager-fitness` rules are unchanged,
because `platform` is already covered by `FitnessCoverageTest` and the new code stays in `platform.world`.

## Cost estimate (analytic; to be measured in task 6.2)

The bounds below come from the source and the section count. They are not measured.

- **Sections.** The overworld has 24 sections per chunk (`-64` to `320`). ElytraLobby has 8676 chunks, so that is about
  208 000 sections.
- **Light array size.** One non-uniform light array is 2048 bytes. A uniform array (all 0 or all 15) is shared by the
  engine and costs nothing extra. Each section has two light arrays, sky and block.
- **Boot check (ElytraLobby, held and checked).** Loads are unchanged. Stored light is already applied today. Extra
  computation: none. Extra memory: the per-section flag write, negligible. Expected delta: about zero. The check's peak
  is still one region at a time.
- **Runtime, theoretical ceiling for the whole lobby.** If every section were light-less and relit: about
  208 000 × 2 × 2 KB ≈ 0.85 GB. This is not reached, because only chunks sent to a viewer are relit. It is the bound
  that justifies a view-based policy.
- **Runtime, one viewer.** With a view distance `v`, a viewer is sent `(2v+1)²` chunks. At the default `v = 8` (decided
  above) that is 289 chunks, so about 289 × 24 × 4 KB ≈ 28 MB in the worst case for light-less sections. At `v = 10` it
  would be 441 chunks and about 42 MB. `LightingChunk` also keeps packet-ready
  light copies per chunk, which may roughly double that. Both figures are upper bounds, and the real number depends on
  how much of each chunk lacks light.
- **Runtime, time.** The relight of a section is a flood fill per section. No measurement exists yet. Task 6.2 records
  the time to the first light packet of a 441-chunk view, and the boot time with and without the change, on the
  ElytraLobby world. If the first view hitches, the fallback is option B.

Comparison for the same world: `falco-light` (B) recomputes every loaded chunk on its first tick, including chunks whose
stored light is already correct, so its ceiling is the full loaded set, not the viewed set.

## Architecture

- Dependency direction: `platform.world` depends on Minestom (`LightingChunk`, `Section`, `Light`), which is the adapter
  ring. Nothing in `api` or `race` changes, so the domain stays free of Minestom.
- New type: `platform.world.WorldLight`, package-private, one static method that marks light-less sections. It lives next
  to `TrackingInstance`, because `TrackingInstance` is its only caller.
- `TrackingInstance` sets `setChunkSupplier(LightingChunk::new)` in its constructor and calls `WorldLight` from
  `retrieveChunk`.
- ArchUnit: no new rule is required. A rule that only `platform.world` imports `LightingChunk` is optional (Could).

## Owner decisions and resolved questions

Recorded on 2026-10-13 from the owner's answers to the open questions.

- **Engine (D1).** Minestom's `LightingChunk`, with light-less sections marked for relight. `falco-light` is not used.
- **Setup server (was OQ3).** The setup server lights its authoring worlds too. The premise of the question was wrong:
  `SetupCommands` opens every authoring world through `MapInstances.forWorld` (`voyager/setup/.../SetupCommands.java`), so
  the one change in `MapInstances` covers both servers. No separate setup code path is needed.
- **Sky light in the lobby (was OQ2).** The lobby keeps sky light. No custom dimension type is added.
- **Pre-lighting the spawn (was OQ4).** Not done. Light is computed lazily on the first send of a chunk, and the
  explanation page says so. Pre-lighting the spawn at boot is a possible follow-up, not part of this change.
- **View distance (was OQ5).** The budget uses Minestom's default `minestom.chunk-view-distance` of 8, which is what
  `ServerProperties.CHUNK_VIEW_DISTANCE` reports (2026.10.07). No voyager code overrides it. `Arrivals` caps the view at
  the client's setting, so 8 is the ceiling the budget assumes. A full view at 8 is `17 x 17 = 289` chunks, not the 441 of
  view distance 10 in the cost estimate below.

### OQ1 result: the shipped worlds store almost no light (read-only check, 2026-10-13)

The owner approved reading `run/` read-only. The region NBT of `run/run/worlds/ElytraraceBlueAndRed/region` (20 `.mca`
files, format `root`, not `Level`) was read with a stdlib-only parser, `python3 -I`, and no file was written. Result:

| Measure | Count |
|---|---|
| Chunks | 14 270 |
| Chunks with at least one stored light array | 285 |
| Sections | 343 048 |
| Sections with a stored `SkyLight` (2048 bytes) | 2 277 |
| Sections with a stored `BlockLight` (2048 bytes) | 3 178 |
| Sections with neither array | 338 790 (98.8 %) |

So the premise holds: the world stores almost no light, and the darkness comes from the missing arrays. The change stays in
scope. `ElytraLobby` was not read; the owner asked for `ElytraraceBlueAndRed` only. The run's chunk statuses are mixed
(`full`, `minecraft:full`, `biomes`, `features`, `liquid_carvers`, `structure_starts`), so part of the world is not fully
generated. That is a separate world-data question and is not part of this change.

### Corrections found while reading the sources

- **`Light.array()` cannot tell an absent array from an all-zero one.** `BlockLight` and `SkyLight` return the shared
  empty `UNSET_CONTENT` for both, because `bake` returns `EMPTY_CONTENT` for all-zero content. A length test would mark a
  stored all-zero section, which the spec forbids. The implementation asks `requiresSend()` instead: `set(...)` arms that
  flag and an absent light never has it set. A stored light is re-armed with `set(array())`, the same round trip
  `Section.copy()` uses, so its state is unchanged.
- **Marking after the load is too late.** `InstanceContainer.retrieveChunk` calls `cacheChunk` and `onLoad` before the
  returned future completes, so a chunk can be visible and sent before a mark applied in `thenApply`. The marking therefore
  runs inside a `ChunkLoader` decorator that wraps the Falco loader, before the chunk is cached. The sketch in D1 is
  superseded by this rule.
