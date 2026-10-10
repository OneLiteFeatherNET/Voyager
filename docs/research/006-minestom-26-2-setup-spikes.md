# Minestom 26.2 Setup Spikes: Display Transform, Input Events, Void World

**Authors:** Voyager Development Team | **Date:** 2026-10-10 | **Status:** Final | **Version:** 1.0

**Research input for:** `introduce-voyager-setup` tasks 1.1 to 1.3 (blocked tasks 6.3, 7.6 and 8.1)
**Prior work:** [005](005-simpler-map-and-cup-setup.md), section 5.1 (target workflow, steps 3 and 5)

## Abstract

The first slice of `voyager-setup` needs three answers from Minestom `2026.08.28-26.2` before any production code
is written: which API sets a display entity's transform, which events a wand gesture raises, and what a void world
must contain for `MapInstances` to accept it. Each answer is backed by a runnable check in `voyager-setup/src/spike`
(run with `./gradlew :voyager-setup:spike`) or, for the event order, by reading the Minestom sources shipped with the
same version. All three checks pass. Two points stay open until a client is connected (task 10.2): the order in which
a block right-click raises its two events, and the visual result of the transform order.

## 1. Methodology

- **Version under test.** `net.minestom:minestom:2026.08.28-26.2` and `net.minestom:testing:2026.08.28-26.2`, the pins
  of `voyager-platform` and `voyager-setup`. The sources jar of the same version was read for the event order.
- **Spike harness.** A separate source set `src/spike` with its own `spike` Gradle task. The `test` task does not run it,
  so the spikes do not slow the unit suite, and they can be removed without touching production code.
- **Environment.** Minestom's `Env` (JUnit extension `@EnvTest`), one fresh environment per test, ticks driven by
  `env.tick()`, no sleeps, `@TempDir` for files. This follows the F.I.R.S.T. rules of `openspec/config.yaml`.
- **Limit.** The spikes run without a network client. Gesture-to-packet behaviour of the Minecraft client is taken from
  the packet listeners of the server, not observed. The client-side claims are marked as such below.

## 2. Spike 1.1: Display Transformation API

**Question.** Which API sets rotation and scale of a display entity on 26.2, and does the value survive a tick?

**Result.** The transform is held by the entity metadata, not by an entity method. A display entity is created with
`new Entity(EntityType.BLOCK_DISPLAY)` and `setInstance(instance, pos)`; its metadata is `BlockDisplayMeta`, read with
`entity.getEntityMeta()`. The setters are on `AbstractDisplayMeta`:

| Setter | Getter | Type | Meaning |
|---|---|---|---|
| `setTranslation(Point)` | `getTranslation()` | `Point` | translation of the display |
| `setLeftRotation(float[])` | `getLeftRotation()` | quaternion `x, y, z, w` | rotation applied after scale |
| `setRightRotation(float[])` | `getRightRotation()` | quaternion `x, y, z, w` | rotation applied before scale |
| `setScale(Vec)` | `getScale()` | `Vec` | scale per axis |
| `setBlockState(Block)` | `getBlockStateId()` | `Block` | the block shown (`BlockDisplayMeta`) |

The spike sets all of them on a `GLASS` block display, ticks the environment once and reads every value back. All
values are equal after the tick. The stored values are the ones given; no normalisation happens in the metadata.

**Transform order.** Minecraft applies a display transform as translation, then left rotation, then scale, then right
rotation, with the block model spanning the unit cube from the origin. This is the vanilla convention and it is not
observed on a client here. The preview adapter therefore computes its translation from the ring normal with the same
order, and task 10.2 confirms the disc is centred on the ring.

**Decision unblocked.** Task 8.2 uses `setScale`, `setLeftRotation` and `setTranslation` on a `BlockDisplay` per
ring. The quaternion comes from the pure `RingOrientation` (task 5.2), so the adapter only copies numbers.

## 3. Spike 1.2: Input Events

**Question.** Which events fire for a right-click with an item, a left-click on air and a left-click on a block; and
can an item tag identify the wand?

**Wand identity.** Confirmed by test (`WandIdentitySpikeTest`). A `CUSTOM_DATA` component with a `voyager` string key
marks the wand. A plain item of the same material has no such component, so the tag separates the two. The tag survives
`ItemStack.with(...)`, which is how the wand is given to the builder.

**Event order (from the listener sources of 26.2).**

| Gesture | Packet | Event raised | Cancelling it does |
|---|---|---|---|
| Right-click in air with the wand | `ClientUseItemPacket` | `PlayerUseItemEvent` | Use is refused and the inventory is resynchronised. |
| Right-click on a block | `ClientPlayerBlockPlacementPacket` | `PlayerBlockInteractEvent`, then `PlayerUseItemOnBlockEvent`, then `PlayerBlockPlaceEvent` | Cancelling the interaction blocks item use on the block; cancelling the placement keeps the block from appearing. |
| Left-click on air | `ClientAnimationPacket` | `PlayerHandAnimationEvent` | Suppresses the swing animation only. |
| Left-click on a block | `ClientAnimationPacket`, then `ClientPlayerActionPacket` (`STARTED_DIGGING`) | `PlayerHandAnimationEvent`, then `PlayerStartDiggingEvent` | Cancelling the start prevents digging. |
| Block broken (instant or finished) | `ClientPlayerActionPacket` | `PlayerBlockBreakEvent` | `InstanceContainer.breakBlock` returns false, so the block stays. |

**Right-click on a block raises two events (client behaviour, to confirm in 10.2).** The server handles
`UseItemOn` and `UseItem` as separate packets. A vanilla client sends `UseItem` after `UseItemOn` when the block
interaction passes, which is the case for a wand. The same click can therefore raise `PlayerBlockInteractEvent` and
`PlayerUseItemEvent`. The wand handler listens to both. The block interaction is cancelled, so no block is placed, and a
right-click whose pose repeats the last ring's centre and normal is the same click arriving twice: it places nothing. The
dedupe is by pose, not by time, so it needs no clock and a test that moves the builder between clicks sees one ring per
click.

**Left-click.** `PlayerHandAnimationEvent` is raised for every left-click, on air and on a block. Removal is bound to it,
so one left-click removes at most one ring. The digging events are cancelled in a setup world, which keeps terrain
unchanged (task 7.7, spec `setup/ring-placement`).

**Decisions unblocked.** Task 7.6 binds right-click to `PlayerUseItemEvent` and `PlayerBlockInteractEvent` (one ring per
click, by pose), left-click to `PlayerHandAnimationEvent`, and block edits to the cancel events above. Item identity uses
the `CUSTOM_DATA` tag.

## 4. Spike 1.3: Void World

**Question.** What is the minimum content of a template directory that `MapInstances` accepts?

**Result.** Confirmed by test (`VoidWorldSpikeTest`):

- A world directory with `region/r.0.0.mca` of 8192 zero bytes is accepted. `MapInstances.forWorld` returns an instance,
  `readEveryChunk` completes without an exception, and `healthOf` produces its report.
- A world directory with an empty `region/` folder is refused with `UnknownWorldException` before any chunk is read,
  because `WorldFolders.holdsRegionData` requires a `.mca` file.
- `level.dat` is not needed for the loader to open the world. The spike does not add one, and none is read.
- `WorldHealth.isSound()` also requires at least one loaded chunk. An empty void has none until something reads it, so a
  freshly copied void world is not "sound" in the health report until its chunks load. The configuration check
  (`ConfigCheck`) reads every chunk, so it is unaffected; the setup server reads only what the builder stands in.

**Minimum template.** `templates/void-world/region/r.0.0.mca`, an 8192-byte file of zeros. Task 6.3 copies this directory
to `<worldsPath>/<id>`.

## 5. Consequences for the Plan

- Tasks 6.3, 7.6 and 8.1 are unblocked, with the choices above recorded as their basis.
- Task 8.1 names `setScale`, `setLeftRotation` and `setTranslation` as the transform API.
- The client-side points (the double event on a block right-click; the transform order) are the first items of the
  manual boot check (task 10.2). If the double event is not confirmed, the pose dedupe stays as a guard and costs nothing.

## 6. Reproduction

```bash
./gradlew :voyager-setup:spike --offline
```

The three test classes are `DisplayTransformSpikeTest`, `WandIdentitySpikeTest` and `VoidWorldSpikeTest`, in
`voyager-setup/src/spike/java/net/elytrarace/voyager/setup/spike/`.
