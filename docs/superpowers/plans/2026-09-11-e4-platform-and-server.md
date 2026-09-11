# E4 Platform and Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first flyable build — `voyager-platform` translating between the domain and Minestom, `voyager-server` wiring it together, and a player able to fly a race on a real server.

**Architecture:** `voyager-platform` is the only module that imports `net.minestom.*`. It makes no game decisions; it translates. `voyager-server` is the composition root and the only place that knows how the pieces are assembled.

**Tech Stack:** Java 25, Minestom `2026.08.28-26.2`, Xerus for phase ticking, `io.airlift:guice:10` confined to the composition root.

**Spec:** `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, sections "Platform layer" and "Setup".

**Spike:** `scratchpad/e4-platform-spike.md` — every Minestom API name in this plan was confirmed against the real `minestom-2026.08.28-26.2` jar, by decompilation, `javap`, and a runtime probe against the live block registry. Four of the spec's platform statements were wrong before that spike and have been corrected in the spec itself.

## Global Constraints

- Java 25. No Kotlin. Packages under `net.elytrarace.voyager.platform..` and `net.elytrarace.voyager.server..`.
- **`voyager-platform` is the only module allowed to import `net.minestom.*`.** `voyager-server` may import it for bootstrap, but game logic must not. `voyager-api`, `voyager-physics` and `voyager-race` must stay free of it — `ApiPurityTest` enforces this and gains rules for the two new modules in Task 1.
- Build exception messages with `"...%s...".formatted(x)`, never `+`.
- Domain exceptions live in an `exception` subpackage with its own `package-info.java`; every package with sources declares `@NotNullByDefault`.
- Commits follow Conventional Commits. **No Co-Author or session trailer** — a repository hook rejects them.
- Every task proves its tests bite by mutation: apply, run, confirm red, revert, **confirm by content hash**. `git checkout -- <file>` is a silent no-op on an untracked file and `git diff` on one is always empty, so stage the baseline first.

## What the spike settled, so nobody re-derives it

| | |
|---|---|
| Collision candidates | Minestom has **no** region query at all. Its own collision is a private swept-AABB routine that collects nothing and nests X outside Z — the opposite of Vanilla. We iterate ourselves and therefore control the order completely. |
| Building blocks | `ChunkCache(Instance, Chunk, Block)` implements `Block.Getter`; `Block.collisionShape()` returns a `Shape`; `ShapeImpl.boundingBoxes()` gives the boxes — **but only on the record, not on the `Shape` interface**, so a cast is needed. Every block's runtime type is `ShapeImpl`, confirmed by probe. |
| Flight signals | `isFlyingWithElytra()` exists. The client action is **`START_FLYING_ELYTRA`**, not `..._WITH_ELYTRA`. `PlayerStopFlyingWithElytraEvent` fires at exactly one place — `Player.refreshOnGround`, under `if (onGround && isFlyingWithElytra())` — so **it is the `isOnGround()` construction**, and 26.2 has **no signal at all for a flight that ends in the air**. |
| Sneak events | `PlayerStartSneakingEvent` and `PlayerStopSneakingEvent` are gone. Replacement: `PlayerInputEvent` with `Player.inputs().shift()`. |
| Velocity | `Entity#setVelocity(Vec)`, unit **blocks per second** — the factor 20 still holds, measured. Compute it against `ServerFlag.SERVER_TICKS_PER_SECOND`, not a literal `20`. |
| The three "hazards" | All three issues are closed, two of them describing something other than the spec claimed. See the spec's corrected section; the only live one is #1880 (auto-sync), and it probably does not reach a player at all because line 696 is `sendPacketToViewers` and a `Player` is not its own viewer. **Derived, not measured — Task 7 measures it.** |

---

## Task 1: Two modules, under the purity rules

**Files:**
- Create: `voyager-platform/build.gradle.kts`, `voyager-server/build.gradle.kts`
- Create: `voyager-platform/src/main/java/net/elytrarace/voyager/platform/package-info.java` and a `PlatformCore` placeholder
- Create: the same pair under `voyager-server/…/server/`
- Modify: `settings.gradle.kts`, `voyager-fitness/build.gradle.kts`
- Modify: `voyager-fitness/src/test/java/net/elytrarace/fitness/{FitnessCoverageTest,ApiPurityTest,DesignRuleTest}.java`

**Interfaces:**
- Produces: two buildable, fitness-covered modules every later task adds to.

`voyager-race` shipped without a single module purity rule — `ApiPurityTest` carried five rules for `voyager-api` and six for `voyager-physics` and none for it, so the spec's ban on Xerus in the domain was enforced by nothing until the final review caught it. **Do not repeat that here.** These two modules are the ones where a stray import does the most damage, because they are the only ones with a platform to reach for.

Both need `` `java-library` `` alongside `voyager.java-conventions` if they expose an `api` configuration — the convention applies plain `java`, which has no `api`. Copy the shape from `voyager-physics/build.gradle.kts` rather than from memory; that mistake has now been made twice.

- [ ] **Step 1: Create both modules and register them**

`voyager-platform` depends on `voyager-api`, `voyager-physics` and `voyager-race`, plus Minestom and Xerus. `voyager-server` depends on all of them plus Guice. Add both to `settings.gradle.kts` beside the existing `voyager-*` entries, and to `voyager-fitness`'s `testImplementation` list.

Add the Minestom 26.2 coordinate to the catalogue in `settings.gradle.kts`. **The existing `minestom` version there is `2026.04.13-1.21.11` and belongs to the tree being replaced — do not change it.** Add a second, separately named version for the rebuild, and pin it inline in the module rather than adding a second catalogue alias for the same artifact: that was tried for `paper-api` and reverted, because Renovate matches coordinates rather than alias names and would then see two pins of one artifact.

- [ ] **Step 2: Extend the fitness rules, and prove each is non-vacuous**

`FitnessCoverageTest`'s `PACKAGE_PREFIX_BY_PROJECT` gains both modules. `ApiPurityTest` gains:

- `voyager-api`, `voyager-physics` and `voyager-race` must not depend on `net.minestom..`, `net.theevilreaper.xerus..` or `voyager-platform`.
- `voyager-platform` must not depend on `voyager-server`.
- `voyager-server` is the only module allowed to depend on `com.google.inject..` / `io.airlift..`.

Each rule carries `allowEmptyShould(false)`, and **each must be shown non-vacuous individually** — add a violation, watch that one rule go red while the others stay green, revert, confirm by hash. Proving only the first is not proving the set; that distinction was made explicit in E3's final review.

- [ ] **Step 3: Build, then commit**

Run: `./gradlew build`. Expected: green, with both new modules present and covered.

---

## Task 2: The boundary — `Vec3` ↔ `Vec`, and the single velocity exit

**Files:**
- Create: `voyager-platform/.../platform/convert/Vectors.java`
- Create: `voyager-platform/.../platform/convert/VelocityExit.java`
- Test: the matching test classes

**Interfaces:**
- Produces: `Vectors.toMinestom(Vec3)`, `Vectors.toDomain(Point)`, and `VelocityExit.send(Player, Vec3)`. Every later task goes through these; nothing else converts.

`Vec3` cannot hold a non-finite value — its compact constructor rejects them. The boundary asserts finiteness **again** on the way out, because a value arriving from Minestom has not been through that constructor.

The unit is **blocks per second**: multiply by `ServerFlag.SERVER_TICKS_PER_SECOND`, not by a literal `20`. The literal is right today and the flag is configurable through `-Dminestom.tps`.

**Nobody sets velocity during normal flight.** The client flies; the server simulates silently alongside. `VelocityExit` exists for the three external forces only — firework boost, ring `BOOST`/`SLOW`, out-of-bounds reset — and that restriction is the reason it is one class: a single place to look when a velocity appears that should not have.

- [ ] **Step 1: Write the failing tests**

Cover, at minimum: a round trip through both conversions returns the original components exactly; a non-finite component on the way out throws rather than reaching Minestom; the per-second conversion is exact for a velocity whose three components are **mutually distinct and not all positive** — `(0.75, -0.32, 1.6)` — because a factor cannot be told from a component swap on `(1, 1, 1)`.

- [ ] **Steps 2 to 5: fail, implement, pass, prove by mutation**

Mutations to catch: drop the finiteness assertion; multiply instead of divide (or vice versa) on one direction; apply the factor to two components and not the third; use a literal `20` where the flag says otherwise (set the flag in the test).

---

## Task 3: `MinestomCollisionSpace` — reproducing Vanilla's candidate order

**Files:**
- Create: `voyager-platform/.../platform/collision/MinestomCollisionSpace.java`
- Create: `voyager-platform/.../platform/collision/BlockShapes.java` — the one place that casts `Shape` to `ShapeImpl`
- Test: `MinestomCollisionSpaceTest`

**Interfaces:**
- Consumes: `CollisionSpace` from `voyager-api`.
- Produces: the implementation the simulator runs against on a live server.

This is the task where precision is most easily lost, and the one where a mistake is hardest to see. **Four things have to be reproduced, not one:**

1. Bounds `floor(min − 1e-7) − 1 .. floor(max + 1e-7) + 1` on each axis. Minestom's own `BoundingBox.getBlocks()` iterates `floor(min)..floor(max)` with neither the tolerance nor the ±1 margin, so its candidate set is a different one.
2. Order `for z { for y { for x } }` — X fastest. Minestom's `PointIterator.next()` already does exactly this, so the iteration order can be borrowed even though the bounds cannot.
3. The cell-class filter: corners never, faces only for a block with a large collision shape, edges only for a moving piston.
4. The intersection test, before a candidate is emitted.

**Minestom has no `hasLargeCollisionShape`** — zero grep hits across all sources. But an exact
translation exists and should be used rather than an approximation: Vanilla's own formula is in the
decompile at `BlockBehaviour.Cache` — a shape is large when `min(axis) < 0.0 || max(axis) > 1.0` on
any axis — and that transcribes directly onto `relativeStart()`/`relativeEnd()`.

An earlier draft of this plan proposed Minestom's own tall-block heuristic, `relativeEnd().y() > 1`.
**That is wrong and was measured to be wrong:** `piston_head` is large on X or Z depending on
facing, and `piston_head[facing=up]` is large by reaching *below* its cell, with
`relativeStart().y() == -0.25`. Four tests catch the difference.

`ShapeImpl.boundingBoxes()` exists only on the record, not on the `Shape` interface. Every block's runtime type is `ShapeImpl`, confirmed by probe, but it is still an implementation type: put the cast behind one method in `BlockShapes` so a Minestom upgrade breaks in one place instead of twenty.

- [ ] **Step 1: Do not assume E2's traces prove anything about order**

Seven of E2's nine fixtures carry an empty world slice, so the candidate path never runs in them. The two that do touch blocks were replayed against a list built `for x { for y { for z } }` — the reverse nesting — and matched exactly anyway, which shows the order did not matter *for that geometry*. **"The physics is bit-exact, so the order is right" is a false inference.** Write the tests as if nothing about order were known.

- [ ] **Step 2: Write the failing tests**

Against a fake `Block.Getter`, not a live server. Cover: the candidate list for a known region comes back in `for z { for y { for x } }` order and the test asserts the **sequence**, not the set; a block whose box leaves the unit cell is included from a face cell while a normal block is not; a corner cell never contributes; the ±1 margin catches a block the naive bounds would miss; a multi-box block (oak stairs — two boxes, confirmed) yields both boxes.

Give the fixture blocks at coordinates that are **not** symmetric in x/z and that straddle zero, so a transposed loop or a sign error cannot pass.

- [ ] **Steps 3 to 5: implement, pass, prove by mutation**

Mutations: swap the z and x loops; drop the ±1 margin; drop the `1e-7` tolerance from the bounds; include corner cells; treat every block as large; return the boxes of a multi-box block in reverse.

- [ ] **Step 6: Measure whether flattening shapes to boxes matters**

Vanilla's `Shapes.collide` iterates whole `VoxelShape`s and short-circuits once per shape; `CollisionSpace` hands back `Aabb`s, so a two-box block becomes two short-circuit checks rather than one. On a map of full blocks this is inert — one box per block. With stairs, slabs or fences in a flight path it may not be.

**This is derived, not measured, and it stays that way until this step.** Write a test in `voyager-physics` that drives `MovementResolver` at a stair-shaped obstacle twice — once with the boxes flattened as today, once grouped per shape — and report whether any trajectory differs. If none does, record that and close the question. If one does, stop and say so: changing `CollisionSpace` is an API change reaching `voyager-api`, `voyager-physics`, the replay harness and the fixture format, and it is a decision, not a fix.

---

## Task 4: Flight detection, including the ending Minestom will not tell us about

**Files:**
- Create: `voyager-platform/.../platform/flight/FlightTracker.java`
- Test: `FlightTrackerTest`

**Interfaces:**
- Produces: a per-player flight state the tick driver reads — started, still flying, ended — and the reason it ended.

Use the real signals: `isFlyingWithElytra()` on the meta, `PlayerStopFlyingWithElytraEvent`, and the client action **`START_FLYING_ELYTRA`**.

**The hard part is what does not exist.** `PlayerStopFlyingWithElytraEvent` fires only from `Player.refreshOnGround`, under `if (onGround && isFlyingWithElytra())`. A flight that ends in the air — the player closes the elytra, or the server clears the flag — produces no event in 26.2. `setFlyingWithElytra(false)` appears nowhere else in the 1499 sources.

So the tracker polls the meta each tick and derives the transition itself, and the event is a confirmation rather than the source of truth. Say that in the class javadoc with the file and condition, because the next person to read this will reach for the event first, exactly as the spec did.

Any flight-entry logic that depended on sneak is gone: `PlayerStartSneakingEvent` and `PlayerStopSneakingEvent` no longer exist. Use `PlayerInputEvent` with `Player.inputs().shift()` if sneak is needed at all.

- [ ] **Steps 1 to 5**

Tests against a fake player-meta source, not a live server: flight starts when the flag turns on; ends on the event; **ends without an event when the flag turns off in the air**, which is the case the spec did not plan for; a flag that flickers within one tick is not two flights.

Mutations: trust the event alone; trust the flag alone without the event; treat a missing transition as a continuing flight.

---

## Task 5: The tick driver

**Files:**
- Create: `voyager-platform/.../platform/tick/FlightTickDriver.java`
- Create: `voyager-platform/.../platform/tick/XerusPhaseDriver.java`
- Test: both

**Interfaces:**
- Consumes: `ElytraSimulator`, `FlightTracker`, `MinestomCollisionSpace`, `RaceStateMachine`.
- Produces: the per-tick loop that advances every flying player's simulated state and the race's phase.

`voyager-race` owns which transitions exist; this module owns ticking, timers and delays, driven by Xerus. That split is why a whole race is playable in a JUnit run, and it is the reason Xerus must not appear in `voyager-race` — Task 1's purity rule enforces it.

**Order within a tick is load-bearing** and the recorder learned it the expensive way: rotation is written, then a world tick passes, then the state is sampled. A driver that samples before the tick records an input that never took effect, and nothing downstream can tell.

**E3 left a known gap here** and it belongs to this task: `RaceState.inPhase()` lags the movement clock by one tick — entering `GAME` gives `inPhase = 0`, and the last `GAME` tick is played while it reads one step short. A driver using it as the race clock records every finish one tick early. Decide here what the race clock is, name it in the type, and pin it with a test.

---

## Task 6: `RaceRun` — the type E3 deliberately left for this stage

**Files:**
- Create: `voyager-race/.../race/run/RaceRun.java` and `RaceRunTest`
- Modify: the platform driver to use it

**Interfaces:**
- Produces: `RaceRun` — one player's run as a record, advanced by a pure `tick(...)`.

E3's playthrough harness had to invent about forty lines of mutable per-player state — progress, previous position, finish tick, velocity, and what elapsed time to hand a DNF — and its final review named this the multiplier on three smaller findings. Every driver would otherwise rewrite it identically, and the first of those drivers is Task 5.

It subsumes, and this task closes, all three:

- `MapScore.completionTime()` for a `DNF` is a value no reader should trust; `Optional<Duration>` says it in the type, as `CupScore.bestTime` already does.
- `PlacementBonus.award` carries no racer identity, so the caller aligns a parallel list by hand — the one place a mis-pairing yields a plausible wrong answer instead of an exception.
- The race clock from Task 5.

This lives in `voyager-race`, not the platform: it is domain, and it must stay playable without a server.

---

## Task 7: Worlds, instances, and the map-to-map transition

**Files:**
- Create: `voyager-platform/.../platform/world/InstanceManager.java`
- Test: what can be tested without a server; the rest is the acceptance procedure in Task 10

**The cup's map transition is where Minestom issue #2017 lands** — players falling below the map on an instance switch. It is closed as completed but **without a framework fix**: the reporter solved it with a chunk check and confirmed teleports, and both mechanisms are present in 26.2 (`Player.setInstance` waits for the surrounding chunks; teleport confirmation is wired in `PacketListenerManager.java:168`). Use them deliberately rather than assuming the framework does.

**Also measure #1880 here** (the auto-sync tick that resets velocity, which #2267 was closed as a duplicate of and which is still open). The spike's reading is that it probably does not reach a player at all, because the relevant line is `sendPacketToViewers` and a `Player` is not its own viewer — but that is derived, not measured. Measure it: set a velocity, let the sync interval pass, read it back.

---

## Task 8: The plausibility check, in log-only mode

**Files:**
- Create: `voyager-platform/.../platform/check/PlausibilityCheck.java`
- Test: `PlausibilityCheckTest`

Each tick yields a prediction; the client reports a position. The comparison is **not per-tick and absolute** but against an error budget over a window: short-term deviation is normal — packet batching, latency, rotation quantisation — and only accumulated error over the window counts.

On breach the run is **invalidated and the player is not moved**: points are discarded, the event is logged, the flight continues undisturbed. Rubber-banding in a flight game destroys the feel the entire physics effort exists to protect.

**For v1 it only logs.** It is armed once real runs show how the error distributes for clean players. GrimAC — the strongest existing server-side Vanilla movement replication — is known to drift on high-speed elytra manoeuvres, which is exactly the regime a racer occupies, and thresholds set theoretically would invalidate the best pilots' runs.

Note also that bit-exactness with a client is unachievable in principle: `Math.cos` differs by one ulp between CPU architectures, so a client on another architecture computes a slightly different flight. That is the reason the check is windowed rather than exact, and it belongs in the class javadoc.

---

## Task 9: `voyager-server` — the composition root

**Files:**
- Create: the Guice modules, the bootstrap, the commands

Guice annotations are confined to this module. Nothing in `voyager-api`, `voyager-physics`, `voyager-race` or `voyager-platform` carries an injection annotation — Task 1's purity rule enforces it.

**All seven of `voyager-race`'s production entry points are `@ApiStatus.Internal`**, inherited from E2's conventions. This module has to call them. Decide once: either the annotation comes off the types a composition root legitimately uses, or this module documents why it ignores it. Do not leave both.

---

## Task 10: Fly it

A written acceptance procedure and a real run, in the shape E2a's recorder procedure took: which server build, how to start it, what a good result looks like, and what to do when it is not.

A player joins, a race starts, they fly through rings, score, the cup advances to the second map, and the race ends. **The cup advancing is the assertion** — the tree being replaced loads map two and never runs it, and E3's state machine is the first that does.

Record what the plausibility check logged during the run. That is the data the threshold will be set from later, and this is the first chance to collect it.

---

## Open, and to be decided in this stage rather than assumed

**The firework boost input is a boolean and Vanilla applies one impulse per rocket.** `FlightInput.fireworkBoostActive` cannot express two rockets, and the difference is measured — steady `velZ` 1.686 with two against 1.672 with one. `chained-boosts` is therefore velocity-asserted on only 170 of its 199 ticks. Fixing it spans `voyager-api`, `ElytraSimulator`, the recorder's sample, a `formatVersion` bump and a re-record. It is the last known gap in the physics parity, and chained boosts is how players actually fly a race.

**`Vec3` equality and negative zero.** `(0.75, -0.32, 1.6).scale(0.0)` is `(0.0, -0.0, 0.0)` — numerically zero, but not `equals(Vec3.ZERO)`, because a record compares doubles bitwise. `voyager-physics`' `0.003` deadzone drives components to exactly zero, so any code asking whether a velocity *equals* `ZERO` rather than testing its magnitude can disagree with itself by a sign bit. Nothing shipped depends on it yet; this is the stage where something might.
