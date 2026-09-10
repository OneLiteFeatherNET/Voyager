# E2a Vanilla Trace Recorder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce reproducible, scripted trace fixtures of Vanilla elytra flight that the physics port can be replayed against, without a Minecraft client and without implementing the network protocol.

**Architecture:** A Paper 26.2 plugin drives a gliding non-player entity. `updateFallFlyingMovement` runs for any `LivingEntity` — `aiStep()` calls `travel(input)`, whose fall-flying branch integrates position and resolves collision server-side — and it ignores the `input` parameter entirely, so mob AI cannot perturb the glide. Only rotation matters, and the plugin sets it per tick from a script. Each tick it records position, the entity's real internal `getDeltaMovement()`, rotation, ground state and boost state into a JSON fixture.

**Tech Stack:** Java 25, Gradle 9.5.1, Paper API `26.2.build.123-stable`, Gson, JUnit 6.1.1, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (§ Physics core → Trace acceptance)

## Global Constraints

- Java toolchain **25**, `options.release = 25`, UTF-8. Paper 26.2 requires Java 25, matching the rebuild.
- The recorder module is named `tools:trace-recorder`, **not** `voyager-*`. `voyager-fitness`'s `FitnessCoverageTest` requires every `voyager-*` module with production sources to be mapped to a package prefix and named by a rule; the recorder is throwaway tooling and must stay outside that contract.
- Base package for the recorder: `net.elytrarace.tools.recorder`.
- Messages are built with `String.formatted`, not concatenation.
- Domain exceptions extend `RuntimeException`, end in `*Exception`, and live in an `exception` subpackage.
- The rebuild's modules (`voyager-*`), the tree being replaced (`server/`, `plugins/*`, `shared/*`) and `buildSrc` are **not** modified by this plan, except for adding the new module to `settings.gradle.kts`.
- Every class that can be tested without a running server must be. Bukkit-touching classes stay thin adapters over tested cores.

## What this does and does not prove

These fixtures validate **the flight formula**: our port of `updateFallFlyingMovement` against Vanilla's, tick for tick, including position integration and block collision.

They do **not** validate the client-server path. A player's glide is client-authoritative, and the design's plausibility check compares a client's reported positions against a prediction. That end-to-end question belongs to the differential test the design defers to a later stage. Do not claim more than the fixtures support, in code comments or in documentation.

## Verified facts this plan rests on

Established against the decompiled Minecraft 26.2 source and Paper's own patch set before this plan was written. Do not re-derive them; do verify any that a task's behaviour contradicts.

| Fact | Where |
|---|---|
| `travel()` dispatches to `travelFallFlying(input)` when `isFallFlying()`, with no player gating | `LivingEntity.java:2432-2440` |
| `aiStep()` calls `travel(input)` when `canSimulateMovement() && isEffectiveAi()` | `LivingEntity.java:3133-3136` |
| `travelFallFlying` calls `move(MoverType.SELF, …)`, so position and collision are resolved server-side | `LivingEntity.java:2583-2597` |
| `updateFallFlyingMovement` never reads `input`; only rotation, velocity and gravity affect the result | `LivingEntity.java:2604-2628` |
| Paper patches exactly one of the four elytra methods — `stopFallFlying`, adding a cancellable event. The movement math is untouched | `paper-server/patches/sources/net/minecraft/world/entity/LivingEntity.java.patch` |
| Drag is `multiply(0.99F, 0.98F, 0.99F)`; the floats widen to `0.9900000095367432` / `0.9800000190734863` | `docs/reference/elytra-physics-26.2.md` |

---

### Task 1: Spike — prove a scripted gliding entity actually glides

**This task is a time-boxed probe with a decision gate, not production code.** Everything it produces is throwaway and is deleted at the end of the task. Its output is an answer recorded in the report, plus a go/no-go.

**Files:**
- Create (throwaway): `tools/trace-recorder/**` — minimal plugin
- Modify: `settings.gradle.kts` — add `include("tools:trace-recorder")`

**The question:** can a Paper plugin make a non-player `LivingEntity` glide server-side under scripted rotation, with AI not interfering, and read back a per-tick position and velocity that changes the way elytra flight should?

**Known risk this probe exists to settle:** `aiStep()` gates the `travel()` call on `canSimulateMovement() && isEffectiveAi()`. Disabling AI the obvious way (`setAI(false)`) sets the NoAI flag and may make `isEffectiveAi()` false, which would stop the glide entirely. Paper's `Mob#setAware(false)` is the other candidate and may behave differently. There is also a question of ordering: mob AI may overwrite rotation within the same tick after the plugin sets it.

- [ ] **Step 1: Stand up the minimal plugin**

Create `tools/trace-recorder/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
}

paper {
    main = "net.elytrarace.tools.recorder.RecorderPlugin"
    apiVersion = "1.21"
    authors = listOf("Voyager")
}
```

Add to `settings.gradle.kts` after the greenfield includes:

```kotlin
include("tools:trace-recorder")
```

**The command must be runnable from the server console**, so the spike needs no Minecraft client and no human pilot. That means spawning at fixed coordinates in a loaded world rather than at the sender's location — a `ConsoleCommandSender` has no location.

Write a plugin whose `onEnable` registers one command, `/probe`, that:
1. spawns a `Zombie` in the main world at a fixed, high, empty coordinate (for example `0, 200, 0`), loading the chunk first,
2. equips an `ELYTRA` in the chest slot, makes it invulnerable, silent and persistent,
3. calls `setGliding(true)`,
4. schedules a repeating task at every tick for 100 ticks that sets rotation to a fixed pitch of `-5f` and yaw `0f`, then logs tick index, `getLocation()` and `getVelocity()`,
5. removes the entity afterwards.

- [ ] **Step 2: Run it and read the log**

Download a Paper 26.2 server (`https://fill.papermc.io/v3/projects/paper` lists 26.2; the v2 API is sunset), accept the EULA, drop the shadow jar in `plugins/`, start it, and run `/probe` **on the server console** — no client, no player.

Run the server headless with a generous timeout; first start generates a world and takes a while. Feed the command to its standard input, and read the answers from `logs/latest.log`.

Record verbatim in the report: the first ten and last ten logged lines.

- [ ] **Step 3: Answer the four questions**

Write each answer into the report with the evidence that settles it:

1. **Does it glide?** Does `y` decrease slowly with `x`/`z` advancing, rather than falling straight down? A straight vertical drop means `travelFallFlying` was never reached.
2. **Does AI interfere with rotation?** Log the entity's rotation at the *start* of the next tick and compare it to what was set. If it differs, the plugin must set rotation later in the tick or the mob must be made unaware.
3. **Which AI switch works?** Try `setAI(false)` and Paper's `setAware(false)` separately. Report which one keeps gliding and which one stops it.
4. **Is velocity readable and non-trivial?** Does `getVelocity()` return the internal delta movement, changing per tick in the shape the formula predicts — roughly `-0.08 + cos²(pitch)·0.06` on the vertical axis before drag?

- [ ] **Step 4: Decide, and delete the probe**

State a go/no-go in the report:

- **Go** — the entity glides under scripted rotation and velocity is readable. Task 2 onward proceeds. Record which AI switch to use and where in the tick to set rotation.
- **No-go** — say precisely what failed. The fallback is a headless protocol client, which is a substantially larger build and needs its own plan.

Then delete the throwaway module and revert the `settings.gradle.kts` line:

```bash
rm -rf tools/trace-recorder
```

Remove `include("tools:trace-recorder")` from `settings.gradle.kts`.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — the repository is back to its pre-spike state.

- [ ] **Step 5: Commit the findings only**

The probe's code is gone; its answers are not. Commit the report file.

```bash
git add docs/research/
git commit -m "docs: record the gliding-entity spike findings"
```

---

### Task 2: The trace format

**Files:**
- Create: `tools/trace-recorder/build.gradle.kts`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/TraceFile.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/TraceTick.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/TraceMetadata.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/BlockBox.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/package-info.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/package-info.java`
- Modify: `settings.gradle.kts`
- Test: `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/format/TraceFileTest.java`

**Interfaces:**
- Consumes: nothing. Pure records, no Bukkit.
- Produces: the on-disk fixture contract that E2b's replay harness reads. Field names are the file format — renaming one later invalidates every recorded fixture.

The format carries the entity's real internal velocity rather than only positions, because a mob's glide is server-simulated and that value is available. A replay can therefore be seeded exactly instead of reconstructing the first velocity from a position delta.

- [ ] **Step 1: Write the failing test**

Create `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/format/TraceFileTest.java`:

```java
package net.elytrarace.tools.recorder.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceFileTest {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static TraceTick tick(int index, double y) {
        return new TraceTick(index, 0.0, y, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0);
    }

    private static TraceMetadata metadata() {
        return new TraceMetadata("26.2", "steady-glide", 0.08, 1, List.of());
    }

    @Test
    void roundTripsThroughJsonUnchanged() {
        TraceFile original = new TraceFile(metadata(), List.of(tick(0, 100.0), tick(1, 99.92)));

        TraceFile restored = GSON.fromJson(GSON.toJson(original), TraceFile.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void rejectsAnEmptyTickList() {
        assertThatThrownBy(() -> new TraceFile(metadata(), List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsTicksThatAreNotConsecutiveFromZero() {
        assertThatThrownBy(() -> new TraceFile(metadata(), List.of(tick(0, 100.0), tick(2, 99.0))))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsNonFiniteSamples() {
        // The construction must sit inside the lambda: TraceTick validates in its own compact
        // constructor, so building it outside would throw before the assertion runs and the test
        // would be permanently red against a correct implementation.
        assertThatThrownBy(() -> new TraceTick(0, 0.0, Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0f, 0.0f, false, false, 0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANonPositiveGravity() {
        assertThatThrownBy(() -> new TraceMetadata("26.2", "steady-glide", 0.0, 1, List.of()))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void keepsTheTickListImmutable() {
        TraceFile file = new TraceFile(metadata(), List.of(tick(0, 100.0)));

        assertThatThrownBy(() -> file.ticks().add(tick(1, 99.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :tools:trace-recorder:test`
Expected: FAIL — `Project 'tools' not found in root project 'Voyager'`.

- [ ] **Step 3: Create the module and the format**

Create `tools/trace-recorder/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
}
```

Add to `settings.gradle.kts`, after `include("voyager-fitness")`:

```kotlin

// Tooling that is not part of the rebuild's module graph.
include("tools:trace-recorder")
```

Create `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/exception/InvalidTraceException.java`:

```java
package net.elytrarace.tools.recorder.format.exception;

/** Thrown when a recorded trace violates an invariant that makes it unusable as a fixture. */
public final class InvalidTraceException extends RuntimeException {

    public InvalidTraceException(String message) {
        super(message);
    }
}
```

Create `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/format/exception/package-info.java`:

```java
@NotNullByDefault
package net.elytrarace.tools.recorder.format.exception;

import org.jetbrains.annotations.NotNullByDefault;
```

Create `TraceTick.java`:

```java
package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/**
 * One tick of a recorded glide, sampled after the entity has been ticked.
 *
 * <p>Position and velocity are {@code double} and rotation is {@code float}, mirroring Vanilla's own
 * numeric types. Velocity is the entity's real internal delta movement, not a position difference.
 */
public record TraceTick(
        int index,
        double posX, double posY, double posZ,
        double velX, double velY, double velZ,
        float yaw, float pitch,
        boolean onGround,
        boolean fireworkBoostActive,
        int fireworkTicksRemaining) {

    public TraceTick {
        if (index < 0) {
            throw new InvalidTraceException("tick index must be >= 0, was %s".formatted(index));
        }
        if (!Double.isFinite(posX) || !Double.isFinite(posY) || !Double.isFinite(posZ)
                || !Double.isFinite(velX) || !Double.isFinite(velY) || !Double.isFinite(velZ)) {
            throw new InvalidTraceException(
                    "tick %s carries a non-finite sample: pos=(%s, %s, %s) vel=(%s, %s, %s)"
                            .formatted(index, posX, posY, posZ, velX, velY, velZ));
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new InvalidTraceException(
                    "tick %s carries a non-finite rotation: yaw=%s pitch=%s".formatted(index, yaw, pitch));
        }
        if (fireworkTicksRemaining < 0) {
            throw new InvalidTraceException(
                    "tick %s has a negative firework tick count: %s".formatted(index, fireworkTicksRemaining));
        }
    }
}
```

Create `BlockBox.java`:

```java
package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

/** One solid collision box from the recorded world slice, in world coordinates. */
public record BlockBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new InvalidTraceException(
                    "block box minimum exceeds its maximum: (%s, %s, %s) to (%s, %s, %s)"
                            .formatted(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }
}
```

Create `TraceMetadata.java`:

```java
package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

import java.util.List;

/**
 * What a replay needs to know about a recording beyond the samples themselves.
 *
 * <p>{@code gravity} is the entity's effective gravity attribute at record time. Vanilla's lift term
 * is {@code gravity * (-1.0 + cos²(pitch) * 0.75)}, so a replay that assumes the default silently
 * diverges whenever the recording used a different value.
 */
public record TraceMetadata(
        String minecraftVersion,
        String profile,
        double gravity,
        int formatVersion,
        List<BlockBox> worldSlice) {

    public TraceMetadata {
        if (minecraftVersion.isBlank()) {
            throw new InvalidTraceException("minecraftVersion must not be blank");
        }
        if (profile.isBlank()) {
            throw new InvalidTraceException("profile must not be blank");
        }
        if (!Double.isFinite(gravity) || gravity <= 0.0) {
            throw new InvalidTraceException("gravity must be finite and > 0, was %s".formatted(gravity));
        }
        if (formatVersion < 1) {
            throw new InvalidTraceException("formatVersion must be >= 1, was %s".formatted(formatVersion));
        }
        worldSlice = List.copyOf(worldSlice);
    }
}
```

Create `TraceFile.java`:

```java
package net.elytrarace.tools.recorder.format;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;

import java.util.List;

/** A complete recording: what was flown, in what world, and every sampled tick in order. */
public record TraceFile(TraceMetadata metadata, List<TraceTick> ticks) {

    public TraceFile {
        if (ticks.isEmpty()) {
            throw new InvalidTraceException("a trace must contain at least one tick");
        }
        for (int i = 0; i < ticks.size(); i++) {
            if (ticks.get(i).index() != i) {
                throw new InvalidTraceException(
                        "tick indices must be consecutive from zero; position %s holds index %s"
                                .formatted(i, ticks.get(i).index()));
            }
        }
        ticks = List.copyOf(ticks);
    }
}
```

Create `package-info.java` for `…recorder` and `…recorder.format`, each with `@NotNullByDefault`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :tools:trace-recorder:test`
Expected: PASS, 6 tests.

- [ ] **Step 5: Verify the rest of the repository is unaffected**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. In particular `:voyager-fitness:test` must still pass — the new module is not named `voyager-*` and must not be pulled into the coverage contract.

- [ ] **Step 6: Commit**

```bash
git add tools settings.gradle.kts
git commit -m "feat(recorder): add the trace fixture format

Carries the entity's real internal velocity rather than only positions, so a
replay is seeded exactly instead of reconstructing the first velocity from a
position delta. Gravity is recorded because Vanilla's lift term scales with it."
```

---

### Task 3: The flight script

**Files:**
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/script/FlightScript.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/script/ScriptedInput.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/script/FlightScriptParser.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/script/package-info.java`
- Test: `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/script/FlightScriptParserTest.java`

**Interfaces:**
- Consumes: `InvalidTraceException` from Task 2.
- Produces: `FlightScript.inputAt(int tick)` returning `ScriptedInput(float yaw, float pitch, boolean igniteFirework)`, and `FlightScript.durationTicks()`. Task 5's harness reads exactly these.

A script is what makes a recording reproducible: the same script recorded twice must produce the same fixture. Scripts are held as text so a profile can be reviewed in a pull request.

Format — one directive per line, blank lines and `#` comments ignored:

```
# steady-glide
hold 200 yaw=0 pitch=-5
```

```
# dive-and-pull-out
hold 40  yaw=0 pitch=-5
ramp 20  yaw=0 pitch=-5..40
hold 60  yaw=0 pitch=40
ramp 20  yaw=0 pitch=40..-10
hold 60  yaw=0 pitch=-10
```

```
# chained-boosts
hold 20  yaw=0 pitch=-5
boost
hold 60  yaw=0 pitch=-5
boost
hold 120 yaw=0 pitch=-5
```

`hold <ticks> yaw=<f> pitch=<f>` holds a rotation. `ramp <ticks> yaw=<f> pitch=<from>..<to>` interpolates linearly across the span, inclusive of the start value and reaching the end value on the final tick. `boost` occupies one tick and requests a firework ignition; it inherits the previous directive's rotation.

- [ ] **Step 1: Write the failing test**

Create `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/script/FlightScriptParserTest.java`:

```java
package net.elytrarace.tools.recorder.script;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FlightScriptParserTest {

    @Test
    void holdKeepsRotationForTheWholeSpan() {
        FlightScript script = FlightScriptParser.parse("hold 3 yaw=90 pitch=-5");

        assertThat(script.durationTicks()).isEqualTo(3);
        for (int tick = 0; tick < 3; tick++) {
            assertThat(script.inputAt(tick).yaw()).isEqualTo(90.0f);
            assertThat(script.inputAt(tick).pitch()).isEqualTo(-5.0f);
            assertThat(script.inputAt(tick).igniteFirework()).isFalse();
        }
    }

    @Test
    void rampInterpolatesFromStartToEndInclusive() {
        FlightScript script = FlightScriptParser.parse("ramp 5 yaw=0 pitch=0..40");

        assertThat(script.inputAt(0).pitch()).isEqualTo(0.0f);
        assertThat(script.inputAt(4).pitch()).isEqualTo(40.0f);
        assertThat(script.inputAt(2).pitch()).isCloseTo(20.0f, within(1e-4f));
    }

    @Test
    void rampInterpolatesInFloatNotDouble() {
        // The tolerance in the test above cannot see the difference; this one is the guard.
        // Computing in double and narrowing at the end yields 13.333333f here instead.
        FlightScript script = FlightScriptParser.parse("ramp 4 yaw=0 pitch=0..40");

        assertThat(script.inputAt(1).pitch()).isEqualTo(13.333334f);
    }

    @Test
    void boostOccupiesOneTickAndInheritsThePreviousRotation() {
        FlightScript script = FlightScriptParser.parse("""
                hold 2 yaw=0 pitch=-5
                boost
                hold 2 yaw=0 pitch=-5
                """);

        assertThat(script.durationTicks()).isEqualTo(5);
        assertThat(script.inputAt(2).igniteFirework()).isTrue();
        assertThat(script.inputAt(2).pitch()).isEqualTo(-5.0f);
        assertThat(script.inputAt(3).igniteFirework()).isFalse();
    }

    @Test
    void ignoresBlankLinesAndComments() {
        FlightScript script = FlightScriptParser.parse("""
                # a comment

                hold 2 yaw=0 pitch=-5
                """);

        assertThat(script.durationTicks()).isEqualTo(2);
    }

    @Test
    void rejectsABoostBeforeAnyRotationIsEstablished() {
        assertThatThrownBy(() -> FlightScriptParser.parse("boost"))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsAnUnknownDirective() {
        assertThatThrownBy(() -> FlightScriptParser.parse("loop 3 yaw=0 pitch=0"))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsANonPositiveSpan() {
        assertThatThrownBy(() -> FlightScriptParser.parse("hold 0 yaw=0 pitch=-5"))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void rejectsAskingForATickBeyondTheScript() {
        FlightScript script = FlightScriptParser.parse("hold 2 yaw=0 pitch=-5");

        assertThatThrownBy(() -> script.inputAt(2)).isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void aRampOfOneTickYieldsItsEndValue() {
        FlightScript script = FlightScriptParser.parse("ramp 1 yaw=0 pitch=10..20");

        assertThat(script.inputAt(0).pitch()).isEqualTo(20.0f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :tools:trace-recorder:test --tests "*FlightScriptParserTest"`
Expected: FAIL — `package net.elytrarace.tools.recorder.script does not exist`.

- [ ] **Step 3: Write the implementation**

`ScriptedInput` is a record of `(float yaw, float pitch, boolean igniteFirework)` whose compact constructor rejects non-finite rotation with `InvalidTraceException`.

`FlightScript` holds `List<ScriptedInput>`, exposes `durationTicks()` and `inputAt(int)`, and throws `InvalidTraceException` for an index outside the script. Its constructor copies the list.

`FlightScriptParser` is an `abstract` utility with a private constructor and `@ApiStatus.Internal`, with one static `parse(String)`. It walks lines, skips blanks and `#` comments, and expands each directive into per-tick `ScriptedInput` values:

- `hold <n> yaw=<f> pitch=<f>` — `n` copies of the same input; `n` must be `>= 1`.
- `ramp <n> yaw=<f> pitch=<a>..<b>` — for `n == 1`, one input at `b`; otherwise input `i` carries `a + (b - a) * i / (n - 1)`, so index `0` is exactly `a` and index `n-1` is exactly `b`. The same applies to a yaw range if one is given.
- `boost` — one input reusing the last emitted rotation, with `igniteFirework` true. Fails if nothing has been emitted yet.

Note the ramp arithmetic is computed in `float`, matching how rotation is held everywhere else in this project.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :tools:trace-recorder:test`
Expected: PASS, 15 tests.

- [ ] **Step 5: Commit**

```bash
git add tools
git commit -m "feat(recorder): add the flight script format and parser

A script is what makes a recording reproducible - the same script recorded twice
produces the same fixture - and holding it as reviewable text means a profile can
be argued about in a pull request rather than reflown."
```

---

### Task 4: The world slice

**Files:**
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/world/SolidBlockSource.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/world/WorldSliceCollector.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/world/package-info.java`
- Test: `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/world/WorldSliceCollectorTest.java`

**Interfaces:**
- Consumes: `TraceTick` and `BlockBox` from Task 2.
- Produces: `WorldSliceCollector.collect(List<TraceTick>, double radius, SolidBlockSource)` returning `List<BlockBox>`. Task 5 passes a Bukkit-backed `SolidBlockSource`; the tests pass a fake.

Two of the eight profiles — the wall graze and the landing — only mean anything if the replay can resolve the same collisions the recording did. The slice is captured from the flown path rather than as a fixed region so a fixture stays small and self-contained.

`SolidBlockSource` is the seam that keeps this testable without a server:

```java
@FunctionalInterface
public interface SolidBlockSource {
    boolean isSolid(int x, int y, int z);
}
```

- [ ] **Step 1: Write the failing test**

Create `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/world/WorldSliceCollectorTest.java`:

```java
package net.elytrarace.tools.recorder.world;

import net.elytrarace.tools.recorder.format.BlockBox;
import net.elytrarace.tools.recorder.format.TraceTick;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorldSliceCollectorTest {

    private static TraceTick at(int index, double x, double y, double z) {
        return new TraceTick(index, x, y, z, 0, 0, 0, 0f, 0f, false, false, 0);
    }

    /** A floor filling y == 0, unbounded horizontally. */
    private static final SolidBlockSource FLOOR = (x, y, z) -> y == 0;

    private static final SolidBlockSource EMPTY = (x, y, z) -> false;

    @Test
    void collectsNothingFromAnEmptyWorld() {
        assertThat(WorldSliceCollector.collect(List.of(at(0, 0, 5, 0)), 2.0, EMPTY)).isEmpty();
    }

    @Test
    void collectsTheFloorBlocksWithinTheRadius() {
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, 0.5, 1.5, 0.5)), 1.0, FLOOR);

        // The window is named block by block, not counted. A count alone cannot tell
        // [-1, 1] from [0, 2] -- both are three columns per axis -- and the floor is
        // unbounded horizontally, so a shifted or asymmetric scan range would still
        // find nine solid blocks and still report y == 0 for every one of them.
        assertThat(slice).containsExactlyInAnyOrder(
                new BlockBox(-1, 0, -1, 0, 1, 0), new BlockBox(-1, 0, 0, 0, 1, 1), new BlockBox(-1, 0, 1, 0, 1, 2),
                new BlockBox(0, 0, -1, 1, 1, 0), new BlockBox(0, 0, 0, 1, 1, 1), new BlockBox(0, 0, 1, 1, 1, 2),
                new BlockBox(1, 0, -1, 2, 1, 0), new BlockBox(1, 0, 0, 2, 1, 1), new BlockBox(1, 0, 1, 2, 1, 2));
    }

    @Test
    void emitsUnitCubesAtBlockCoordinates() {
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, 0.5, 0.5, 0.5)), 0.0, FLOOR);

        assertThat(slice).containsExactly(new BlockBox(0, 0, 0, 1, 1, 1));
    }

    @Test
    void deduplicatesBlocksSharedBetweenTicks() {
        List<TraceTick> path = List.of(at(0, 0.5, 1.5, 0.5), at(1, 0.6, 1.5, 0.5));

        List<BlockBox> slice = WorldSliceCollector.collect(path, 0.0, FLOOR);

        assertThat(slice).containsExactly(new BlockBox(0, 0, 0, 1, 1, 1));
    }

    @Test
    void coversEveryTickOfThePath() {
        List<TraceTick> path = List.of(at(0, 0.5, 1.5, 0.5), at(1, 8.5, 1.5, 0.5));

        List<BlockBox> slice = WorldSliceCollector.collect(path, 0.0, FLOOR);

        assertThat(slice).containsExactlyInAnyOrder(
                new BlockBox(0, 0, 0, 1, 1, 1),
                new BlockBox(8, 0, 0, 9, 1, 1));
    }

    @Test
    void flooringNegativePositionsRoundsDownRatherThanTowardsZero() {
        // Every other test sits at a positive coordinate, where Math.floor and an int
        // cast agree. They disagree below zero: (int) -0.5 is 0, Math.floor(-0.5) is -1.
        // Recordings fly through negative coordinates, so the wrong one shifts the whole
        // slice by a block on that side of the origin and the replay resolves the wrong
        // collisions.
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, -0.5, 0.5, -3.25)), 0.0, FLOOR);

        assertThat(slice).containsExactly(new BlockBox(-1, 0, -4, 0, 1, -3));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :tools:trace-recorder:test --tests "*WorldSliceCollectorTest"`
Expected: FAIL — `package net.elytrarace.tools.recorder.world does not exist`.

- [ ] **Step 3: Write the implementation**

`WorldSliceCollector` is an `abstract` utility with a private constructor and `@ApiStatus.Internal`. `collect` walks every tick, floors its position to block coordinates, scans the cube of blocks within `ceil(radius)` on each axis, asks the `SolidBlockSource`, and emits a unit `BlockBox` per solid block. Results are deduplicated by block coordinate and returned as an unmodifiable list.

Note the coordinate mapping the tests pin: a position of `0.5` floors to block `0`, whose box spans `0.0` to `1.0`. A radius of `0.0` therefore yields exactly the column the entity is over. Flooring is `Math.floor`, never an `int` cast -- they agree only above zero, and a recording crosses the origin.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :tools:trace-recorder:test`
Expected: PASS -- the module's whole suite, with the six new tests among them.

- [ ] **Step 5: Commit**

```bash
git add tools
git commit -m "feat(recorder): capture the world slice along the flown path

The wall-graze and landing profiles only mean anything if a replay resolves the
same collisions the recording did. Slicing along the path rather than over a
fixed region keeps a fixture small and self-contained."
```

---

### Task 5: The recording harness

**Files:**
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/capture/TraceCollector.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/capture/GliderSample.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/capture/package-info.java`
- Test: `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/capture/TraceCollectorTest.java`

**Interfaces:**
- Consumes: `FlightScript` (Task 3), `TraceTick`/`TraceFile`/`TraceMetadata` (Task 2), `WorldSliceCollector` (Task 4).
- Produces: `TraceCollector.record(GliderSample)` and `TraceCollector.finish(SolidBlockSource, double radius)` returning a `TraceFile`. Task 6's Bukkit listener calls exactly these.

This task holds all the recording logic and touches no Bukkit type. `GliderSample` is a plain record of what one tick's observation carries — position, velocity, rotation, ground state — so the collector can be driven from a test without a server. Task 6 is the thin adapter that fills it from a live entity.

- [ ] **Step 1: Write the failing test**

Create `tools/trace-recorder/src/test/java/net/elytrarace/tools/recorder/capture/TraceCollectorTest.java`:

```java
package net.elytrarace.tools.recorder.capture;

import net.elytrarace.tools.recorder.format.TraceFile;
import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import net.elytrarace.tools.recorder.script.FlightScript;
import net.elytrarace.tools.recorder.script.FlightScriptParser;
import net.elytrarace.tools.recorder.world.SolidBlockSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceCollectorTest {

    private static final SolidBlockSource EMPTY = (x, y, z) -> false;

    private static TraceCollector collector(String script) {
        return new TraceCollector("26.2", "test-profile", 0.08, FlightScriptParser.parse(script));
    }

    private static GliderSample sample(double y) {
        return new GliderSample(0.0, y, 0.0, 0.0, -0.08, 0.0, 0.0f, -5.0f, false, false, 0);
    }

    @Test
    void producesOneTickPerRecordedSample() {
        TraceCollector collector = collector("hold 3 yaw=0 pitch=-5");
        collector.record(sample(100.0));
        collector.record(sample(99.9));
        collector.record(sample(99.8));

        TraceFile file = collector.finish(EMPTY, 1.0);

        assertThat(file.ticks()).hasSize(3);
        assertThat(file.ticks().get(2).posY()).isEqualTo(99.8);
    }

    @Test
    void numbersTicksConsecutivelyFromZero() {
        TraceCollector collector = collector("hold 2 yaw=0 pitch=-5");
        collector.record(sample(100.0));
        collector.record(sample(99.9));

        assertThat(collector.finish(EMPTY, 1.0).ticks()).extracting("index").containsExactly(0, 1);
    }

    @Test
    void carriesTheMetadataItWasBuiltWith() {
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(sample(100.0));

        assertThat(collector.finish(EMPTY, 1.0).metadata().gravity()).isEqualTo(0.08);
        assertThat(collector.finish(EMPTY, 1.0).metadata().profile()).isEqualTo("test-profile");
    }

    @Test
    void refusesMoreSamplesThanTheScriptHasTicks() {
        TraceCollector collector = collector("hold 1 yaw=0 pitch=-5");
        collector.record(sample(100.0));

        assertThatThrownBy(() -> collector.record(sample(99.9)))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void refusesToFinishBeforeTheScriptIsComplete() {
        TraceCollector collector = collector("hold 3 yaw=0 pitch=-5");
        collector.record(sample(100.0));

        assertThatThrownBy(() -> collector.finish(EMPTY, 1.0))
                .isInstanceOf(InvalidTraceException.class);
    }

    @Test
    void reportsWhetherItIsComplete() {
        TraceCollector collector = collector("hold 2 yaw=0 pitch=-5");

        assertThat(collector.isComplete()).isFalse();
        collector.record(sample(100.0));
        collector.record(sample(99.9));
        assertThat(collector.isComplete()).isTrue();
    }

    @Test
    void exposesTheScriptedInputForTheNextTick() {
        TraceCollector collector = collector("hold 1 yaw=42 pitch=-7");

        assertThat(collector.nextInput().yaw()).isEqualTo(42.0f);
        assertThat(collector.nextInput().pitch()).isEqualTo(-7.0f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :tools:trace-recorder:test --tests "*TraceCollectorTest"`
Expected: FAIL — `package net.elytrarace.tools.recorder.capture does not exist`.

- [ ] **Step 3: Write the implementation**

`GliderSample` is a record mirroring `TraceTick` minus the index, with the same finiteness invariants.

`TraceCollector` is a mutable class — it accumulates during a recording, which is the one place in this module where a record does not fit. It holds the metadata fields, the script, and a growing `List<TraceTick>`. `record(GliderSample)` appends with the next index and throws `InvalidTraceException` past the script's duration. `nextInput()` returns the scripted input for the tick about to be recorded. `isComplete()` compares recorded count to `durationTicks()`. `finish(SolidBlockSource, double)` throws unless complete, collects the world slice from the accumulated ticks, and returns a `TraceFile`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :tools:trace-recorder:test`
Expected: PASS, 27 tests.

- [ ] **Step 5: Commit**

```bash
git add tools
git commit -m "feat(recorder): add the server-free recording core

Every recording rule lives here and is unit tested; the Bukkit side stays a thin
adapter that fills a GliderSample per tick."
```

---

### Task 6: The plugin

**Files:**
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/RecorderPlugin.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/bukkit/GliderRunner.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/bukkit/BukkitSolidBlockSource.java`
- Create: `tools/trace-recorder/src/main/java/net/elytrarace/tools/recorder/bukkit/package-info.java`
- Modify: `tools/trace-recorder/build.gradle.kts`
- Modify: `settings.gradle.kts` — Paper repository and API alias

**Interfaces:**
- Consumes: `TraceCollector`, `FlightScriptParser`, `SolidBlockSource`.
- Produces: the command `/record <profile>`, which reads `scripts/<profile>.txt` from the plugin's data folder and writes `traces/<profile>.json`.

This is the only Bukkit-facing code in the module and it must stay thin: spawn, drive, sample, hand off. Anything with a decision in it belongs in Task 5.

Use the AI switch and the rotation timing the Task 1 spike established — do not re-derive them, and if the spike's finding turns out wrong, say so rather than working around it silently.

**Two things in this task are unverified and must be checked, not assumed.** The existing `plugins/setup/build.gradle.kts` uses plugin-yml's `paper { }` block with `apiVersion = "1.21"` and a `serverDependencies { }` block, but nothing in this repository uses its `commands { }` DSL, and no plugin here targets 26.2. Before relying on either: confirm the `commands` DSL exists in plugin-yml 0.6.0, and confirm what `apiVersion` a 26.2 Paper server accepts. If the DSL differs, declare the command in code instead; if `apiVersion` must change, say so in the report rather than guessing a value that makes the plugin silently refuse to load.

- [ ] **Step 1: Add the Paper dependency**

In `settings.gradle.kts`, inside the catalog:

```kotlin
            version("paper-api", "26.2.build.123-stable")
            library("minecraft.paper.api", "io.papermc.paper", "paper-api").versionRef("paper-api")
```

In `tools/trace-recorder/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.minecraft.paper.api)
    implementation("com.google.code.gson:gson:2.14.0")
}

paper {
    main = "net.elytrarace.tools.recorder.RecorderPlugin"
    apiVersion = "1.21"
    authors = listOf("Voyager")
    commands {
        register("record") {
            description = "Fly a scripted profile and write its trace fixture"
            usage = "/record <profile>"
        }
    }
}
```

- [ ] **Step 2: Write the plugin**

`RecorderPlugin` registers the `/record` command. On execution it:

1. reads `scripts/<profile>.txt` from `getDataFolder()`, failing with a clear message if absent;
2. parses it into a `FlightScript`;
3. builds a `TraceCollector` with the Minecraft version from `Bukkit.getMinecraftVersion()`, the profile name, and the spawned entity's effective gravity attribute;
4. hands both to a `GliderRunner`.

`GliderRunner` spawns the entity above the sender, equips an elytra, makes it invulnerable, silent and persistent, applies the AI switch from the spike, sets `setGliding(true)`, and schedules a per-tick task that:

- reads `collector.nextInput()`, applies its rotation to the entity, ignites a firework if requested,
- lets the tick pass,
- samples the entity into a `GliderSample` and calls `collector.record(...)`,
- when `collector.isComplete()`, calls `finish(...)` with a `BukkitSolidBlockSource` and a radius of 3, writes the JSON under `traces/<profile>.json`, removes the entity and reports the path to the sender.

`BukkitSolidBlockSource` wraps a `World` and answers `isSolid` from `getBlockAt(x, y, z).getType().isSolid()`.

**Ordering matters and the spike settled it:** rotation must be applied at the point in the tick the spike identified, and the sample must be taken after the entity has ticked, not before. Getting this backwards produces a trace offset by one tick, which replays as a constant drift and is easy to misread as a formula error.

- [ ] **Step 3: Build the plugin jar**

Run: `./gradlew :tools:trace-recorder:shadowJar`
Expected: BUILD SUCCESSFUL, a jar under `tools/trace-recorder/build/libs/`.

- [ ] **Step 4: Record one profile end to end**

Start a Paper 26.2 server with the plugin, place `scripts/steady-glide.txt` containing `hold 200 yaw=0 pitch=-5`, join, run `/record steady-glide`.

Verify by reading the produced JSON, not by trusting the command's output:

- 200 ticks, indices 0 to 199;
- `posY` decreases monotonically and `posX` or `posZ` advances;
- `velY` sits near the value the formula predicts for a steady glide rather than near `-0.08` per tick of free fall;
- metadata carries `26.2` and the gravity actually read from the entity.

Paste the first three and last three ticks into the report.

- [ ] **Step 5: Commit**

```bash
git add tools settings.gradle.kts
git commit -m "feat(recorder): drive a scripted glide and write the fixture

The Bukkit side spawns, drives and samples; every decision lives in the tested
core. Rotation is applied before the tick and the sample taken after it - the
other order produces a trace offset by one tick, which replays as constant drift."
```

---

### Task 7: The eight profiles and the procedure

**Files:**
- Create: `tools/trace-recorder/scripts/steady-glide.txt`
- Create: `tools/trace-recorder/scripts/climb-into-stall.txt`
- Create: `tools/trace-recorder/scripts/dive-and-pull-out.txt`
- Create: `tools/trace-recorder/scripts/single-boost.txt`
- Create: `tools/trace-recorder/scripts/chained-boosts.txt`
- Create: `tools/trace-recorder/scripts/pitch-extremes.txt`
- Create: `tools/trace-recorder/scripts/wall-graze.txt`
- Create: `tools/trace-recorder/scripts/landing.txt`
- Create: `docs/guides/how-to-record-a-trace.md`

**Interfaces:**
- Consumes: the script format from Task 3 and the plugin from Task 6.
- Produces: eight fixtures under `tools/trace-recorder/traces/`, which E2b copies into `voyager-physics/src/test/resources/traces/`.

Each script targets a behaviour the port could get wrong in a way steady flight would hide.

| Profile | What it exercises | What a wrong port does |
|---|---|---|
| `steady-glide` | The whole tick under stable conditions | Drift accumulates visibly over 200 ticks |
| `climb-into-stall` | The upward-pitch term and the loss of airspeed | Wrong `3.2` factor or wrong sign shows here first |
| `dive-and-pull-out` | The downward-glide conversion, including its `cos²` factor | A missing lift factor reads as too little speed gained in the dive |
| `single-boost` | Firework boost applied once | Wrong blend or strength |
| `chained-boosts` | Boost applied while a previous burn is still active | Boosts that add instead of blend |
| `pitch-extremes` | Pitch at ±90°, where `lookHorLength` approaches zero | A port that adds its own guard where Vanilla has one, or omits Vanilla's |
| `wall-graze` | Horizontal collision and the speed lost to it | Collision handled at the wrong point in the tick |
| `landing` | Ground contact ending the glide | `onGround` handling and the end of fall flying |

- [ ] **Step 1: Write the eight scripts**

Write each as a `.txt` in the script format, with a leading `#` comment naming the profile and one line saying what it is for. Keep every profile at or under 250 ticks — long enough for drift to show, short enough to read a fixture by eye.

`wall-graze` and `landing` need terrain. Document in each script's comment what the world must contain, and place a `# world:` line stating it, for example `# world: a stone wall at x=40, flat ground at y=64`.

- [ ] **Step 2: Record all eight**

Run each through `/record` on the same server and world. Verify each fixture the way Task 6 Step 4 verified `steady-glide`, and additionally check that the profile does what its name says — the stall profile must show horizontal speed collapsing, the wall graze must show a discontinuity in horizontal speed, the landing must end with `onGround` true.

A profile that does not show its behaviour is a broken script, not a broken port. Fix the script and re-record.

- [ ] **Step 3: Write the procedure**

Create `docs/guides/how-to-record-a-trace.md` covering: which Paper build to use and where to get it, how to build and install the plugin, the world each profile needs, how to run a recording, how to tell a good fixture from a broken one, and when to re-record — specifically, that a Minecraft version change invalidates every fixture and that the scripts, not the recordings, are the source of truth.

State plainly what the fixtures prove and what they do not, repeating the boundary from this plan's header: they validate the formula, not the client-server path.

- [ ] **Step 4: Commit**

```bash
git add tools docs/guides
git commit -m "feat(recorder): add the eight flight profiles and the recording procedure

Each profile targets a behaviour the port could get wrong in a way steady flight
would hide. The scripts are the source of truth - a version bump invalidates the
recordings, not the profiles."
```

---

## Definition of Done for E2a

- [ ] The spike's four questions are answered in writing, with a recorded go/no-go.
- [ ] `./gradlew build` is green with the new module present, and `:voyager-fitness:test` still passes — the recorder is outside the `voyager-*` coverage contract.
- [ ] Every class holding a decision is unit tested without a server; Bukkit-facing code is a thin adapter.
- [ ] Eight fixtures exist, each verified to show the behaviour its name claims.
- [ ] `docs/guides/how-to-record-a-trace.md` lets someone else reproduce all eight without asking anyone.
- [ ] The fixtures' limits are stated where a reader will meet them: they validate the formula, not the client-server path.
