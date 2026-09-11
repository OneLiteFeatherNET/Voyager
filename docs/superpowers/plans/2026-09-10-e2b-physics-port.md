# E2b Physics Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Vanilla's elytra tick into `voyager-physics` as a pure function, and build the harness that replays recorded traces against it and reports which step diverged.

**Architecture:** `voyager-physics` depends only on `voyager-api` and knows nothing of Minestom, the game, or the database. The tick is a `FlightState tick(FlightState, FlightInput, CollisionSpace)`, decomposed into named steps in a fixed order so a trace mismatch names the step rather than only the tick. Block access arrives as a `CollisionSpace` parameter, so the same simulation runs against a recorded world slice in tests and a live instance in production.

**Tech Stack:** Java 25, Gradle 9.5.1, JUnit 6.1.1, AssertJ 3.27.7, Gson 2.14.0 (test scope, for reading fixtures).

**Spec:** `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (§ Physics core)
**Verified reference:** `docs/reference/elytra-physics-26.2.md`
**Companion plan:** `docs/superpowers/plans/2026-09-10-e2a-trace-recorder.md` produces the fixtures. Tasks 1 to 6 here do not depend on it; only Task 7 does.

## Global Constraints

- Java toolchain **25**, `options.release = 25`, UTF-8.
- `voyager-physics` depends on `voyager-api` and nothing else. It must not reference `net.minestom..`, `org.bukkit..`, `jakarta.persistence..`, `org.hibernate..`, `com.google.inject..`, `jakarta.inject..`, or `java.nio.file..` in main sources.
- Base package `net.elytrarace.voyager.physics`.
- **The physics module has no configuration of any kind** — no tuning file, no environment variable, no constructor parameter beyond `FlightState`, `FlightInput` and `CollisionSpace`. A knob here would let a deployment silently break the parity the trace suite exists to prove.
- Every package containing sources carries `package-info.java` with `@NotNullByDefault`.
- Domain exceptions extend `RuntimeException`, end in `*Exception`, and live in an `exception` subpackage.
- Messages are built with `String.formatted`, not concatenation.
- Records validate invariants in the compact constructor.
- Every ArchUnit rule added to `voyager-fitness` declares `allowEmptyShould(false)`.
- Adding this module obliges you to wire it into `voyager-fitness`: `FitnessCoverageTest` fails until the module is both a `testImplementation` dependency and mapped to its package prefix, and until at least one rule names that prefix.

## The three fidelity traps

These are the reason this port is a stage of its own rather than an afternoon of transcription. Each would pass a formula review and fail a trace comparison. Each gets a test whose only job is to keep a later tidy-up from reintroducing it.

**1. The drag constants are `float` literals widened to `double`.** Vanilla ends the tick with `movement.multiply(0.99F, 0.98F, 0.99F)` and `Vec3.multiply` takes three `double` parameters, so the values that actually apply are:

```
(double) 0.99F = 0.9900000095367432
(double) 0.98F = 0.9800000190734863
```

Writing `0.99` and `0.98` introduces a relative error of `9.5e-9` per tick in the one operation that touches every component every tick.

**2. The lean angle is computed in `float`.** `float leanAngle = this.getXRot() * Mth.DEG_TO_RAD` — both operands are `float`, and the result widens only afterwards. `Math.toRadians(pitch)` is a different number.

**3. The trigonometry is mixed, and part of it is a lookup table.** Within the same method:

- `liftForce = Mth.square(Math.cos(leanAngle))` — a true `double` cosine from `java.lang.Math`.
- `convert = moveHorLength * -Mth.sin(leanAngle) * 0.04` — `Mth.sin`, a 65536-entry `float` table.
- `lookAngle = getLookAngle()` → `calculateViewVector` — built entirely from `Mth.cos`/`Mth.sin`, so also table-based.

Reimplementing all of it with `Math.*` — the obvious, tidy choice — silently changes the climb.

## Vanilla source this plan transcribes

Established against the decompiled Minecraft 26.2 sources. Reproduce these exactly; do not simplify.

```java
// net/minecraft/util/Mth.java
public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
private static final float[] SIN = Util.make(new float[65536], sin -> {
    for (int i = 0; i < sin.length; i++) {
        sin[i] = (float) Math.sin(i / 10430.378350470453);
    }
});
public static float sin(final double i) { return SIN[(int)((long)(i * 10430.378350470453) & 65535L)]; }
public static float cos(final double i) { return SIN[(int)((long)(i * 10430.378350470453 + 16384.0) & 65535L)]; }
```

```java
// net/minecraft/world/entity/Entity.java
public final Vec3 calculateViewVector(final float xRot, final float yRot) {
    float realXRot = xRot * Mth.DEG_TO_RAD;
    float realYRot = -yRot * Mth.DEG_TO_RAD;
    float yCos = Mth.cos(realYRot);
    float ySin = Mth.sin(realYRot);
    float xCos = Mth.cos(realXRot);
    float xSin = Mth.sin(realXRot);
    return new Vec3(ySin * xCos, -xSin, yCos * xCos);
}
```

`updateFallFlyingMovement` and `travelFallFlying` are quoted in full in `docs/reference/elytra-physics-26.2.md`. Read them there rather than from memory.

## Scope of the collision port

Vanilla's `Entity.collide` also resolves step-up: it looks for candidate step heights when `maxUpStep() > 0` **and** (`onGroundAfterCollision || onGround()`) **and** a horizontal collision occurred. A gliding entity is airborne, so `onGround()` is false and that branch is unreachable for every tick of a glide except the one that lands — and then only if a horizontal collision happens on the same tick.

**This port therefore implements the axis-separated sweep and not step-up.** The boundary is deliberate, and it is testable rather than assumed: if a landing trace diverges on its final tick, the assumption was wrong and the branch must be ported. Say so in the module's documentation, and do not let a passing suite be read as proof that step-up was implemented.

Two further limits, both inherited from how the fixtures are recorded:

- The world slice records **unit cubes for solid blocks**. Slabs, stairs, fences and other partial shapes are not represented. Recording profiles must use full blocks only.
- Entity-versus-entity collision is not simulated. The recordings contain one entity.

---

### Task 1: The module and Vanilla's trigonometry

**Files:**
- Create: `voyager-physics/build.gradle.kts`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/math/MinecraftMath.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/math/package-info.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/package-info.java`
- Modify: `settings.gradle.kts`
- Modify: `voyager-fitness/build.gradle.kts` and `voyager-fitness/src/test/java/net/elytrarace/fitness/FitnessCoverageTest.java`
- Modify: `voyager-fitness/src/test/java/net/elytrarace/fitness/ApiPurityTest.java`
- Test: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/math/MinecraftMathTest.java`

**Interfaces:**
- Consumes: nothing yet.
- Produces: `MinecraftMath.DEG_TO_RAD` (`float`), `MinecraftMath.sin(double)` and `cos(double)` returning `float`, `MinecraftMath.square(double)`. Every later task uses these instead of `java.lang.Math` wherever Vanilla does.

The tests here are the guard for trap 3. One of them asserts that `MinecraftMath.sin` **differs** from `Math.sin` — that assertion is the entire point, because a port that quietly swapped in `Math.sin` would satisfy every other test in this plan.

- [ ] **Step 1: Write the failing test**

Create `voyager-physics/src/test/java/net/elytrarace/voyager/physics/math/MinecraftMathTest.java`:

```java
package net.elytrarace.voyager.physics.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MinecraftMathTest {

    @Test
    void degToRadIsTheFloatQuotientVanillaUses() {
        assertThat(MinecraftMath.DEG_TO_RAD).isEqualTo((float) (Math.PI / 180.0));
    }

    @Test
    void sinAndCosAreTableLookupsAndNotTheJdkFunctions() {
        // The point of this test: a port that swapped in Math.sin would pass everything else here.
        // 0.3 radians is not a table index boundary, so the quantised value must differ.
        assertThat(MinecraftMath.sin(0.3)).isNotEqualTo((float) Math.sin(0.3));
        assertThat(MinecraftMath.cos(0.3)).isNotEqualTo((float) Math.cos(0.3));
    }

    @Test
    void sinStaysCloseToTheRealFunction() {
        for (double radians = -Math.PI; radians < Math.PI; radians += 0.017) {
            assertThat((double) MinecraftMath.sin(radians))
                    .as("sin(%s)".formatted(radians))
                    .isCloseTo(Math.sin(radians), within(1.0e-3));
        }
    }

    @Test
    void cosStaysCloseToTheRealFunction() {
        for (double radians = -Math.PI; radians < Math.PI; radians += 0.017) {
            assertThat((double) MinecraftMath.cos(radians))
                    .as("cos(%s)".formatted(radians))
                    .isCloseTo(Math.cos(radians), within(1.0e-3));
        }
    }

    @Test
    void reproducesTheTableExactlyAtAnIndexBoundary() {
        // Index 1000 of a 65536-entry table built as sin[i] = (float) Math.sin(i / 10430.378350470453).
        double atIndexOneThousand = 1000 / 10430.378350470453;

        assertThat(MinecraftMath.sin(atIndexOneThousand)).isEqualTo((float) Math.sin(atIndexOneThousand));
    }

    @Test
    void wrapsAroundRatherThanFailingOnLargeAngles() {
        assertThat(MinecraftMath.sin(1000.0)).isBetween(-1.0f, 1.0f);
        assertThat(MinecraftMath.cos(-1000.0)).isBetween(-1.0f, 1.0f);
    }

    @Test
    void cosIsSinShiftedByAQuarterTurn() {
        assertThat(MinecraftMath.cos(0.0)).isEqualTo(MinecraftMath.sin(Math.PI / 2.0));
    }

    @Test
    void squareMultipliesAValueByItself() {
        assertThat(MinecraftMath.square(1.5)).isEqualTo(2.25);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-physics:test`
Expected: FAIL — `Project 'voyager-physics' not found in root project 'Voyager'`.

- [ ] **Step 3: Create the module**

Create `voyager-physics/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
}

dependencies {
    api(project(":voyager-api"))
}
```

Add to `settings.gradle.kts`, after `include("voyager-api")`:

```kotlin
include("voyager-physics")
```

- [ ] **Step 4: Write the implementation**

Create `MinecraftMath.java` as an `abstract` utility with a private constructor and `@ApiStatus.Internal`, transcribing Vanilla exactly:

```java
package net.elytrarace.voyager.physics.math;

import org.jetbrains.annotations.ApiStatus;

/**
 * Vanilla's trigonometry, reproduced exactly.
 *
 * <p>Minecraft does not use {@link Math#sin} for these — it reads a 65536-entry table of
 * {@code float}. The quantisation is part of the behaviour being replicated, not an artefact to be
 * improved away: substituting the JDK functions changes climb behaviour in a way that passes review
 * and fails the trace suite.
 *
 * <p>Transcribed from {@code net/minecraft/util/Mth.java} of Minecraft 26.2.
 */
@ApiStatus.Internal
public abstract class MinecraftMath {

    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private static final double SIN_SCALE = 10430.378350470453;
    private static final double COS_OFFSET = 16384.0;
    private static final int TABLE_MASK = 65535;

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i / SIN_SCALE);
        }
    }

    private MinecraftMath() {
    }

    public static float sin(double radians) {
        return SIN[(int) ((long) (radians * SIN_SCALE) & TABLE_MASK)];
    }

    public static float cos(double radians) {
        return SIN[(int) ((long) (radians * SIN_SCALE + COS_OFFSET) & TABLE_MASK)];
    }

    public static double square(double value) {
        return value * value;
    }
}
```

Add `package-info.java` with `@NotNullByDefault` for both new packages.

- [ ] **Step 5: Add gravity to `FlightInput`**

The simulator's signature is `tick(FlightState, FlightInput, CollisionSpace)` — three parameters, as the design specifies. Gravity is not a constant and cannot be one: Vanilla's lift term is `gravity * (-1.0 + liftForce * 0.75)`, read from `getEffectiveGravity()`, and Slow Falling clamps it to `0.01` while descending. It is therefore a per-tick input, alongside rotation.

E1 shipped `FlightInput` without it. Extend the record in `voyager-api` to
`FlightInput(float yaw, float pitch, boolean fireworkBoostActive, int fireworkTicksRemaining, double gravity)`,
with the compact constructor rejecting a non-finite or non-positive gravity via the existing
`InvalidFlightInputException`. Update `FlightStateTest` accordingly — including its reflection guard,
which must now also assert that `gravity` is `double`.

Run: `./gradlew :voyager-api:test`
Expected: PASS, with the added invariant covered.

- [ ] **Step 6: Wire the module into the fitness contract**

`FitnessCoverageTest` fails until this is done — that is what it is for.

In `voyager-fitness/build.gradle.kts` add `testImplementation(project(":voyager-physics"))`.

In `FitnessCoverageTest`, add `":voyager-physics"` → `"net.elytrarace.voyager.physics"` to `PACKAGE_PREFIX_BY_PROJECT`.

In `ApiPurityTest`, add rules that name `net.elytrarace.voyager.physics..` and forbid the same package list the api module is held to, plus one that forbids any dependency on `net.elytrarace.voyager.race..` or `net.elytrarace.voyager.platform..`. Each with `.allowEmptyShould(false)`.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :voyager-physics:test :voyager-fitness:test`
Expected: PASS. 8 new physics tests; the fitness suite green with the new module mapped and ruled.

- [ ] **Step 8: Prove the coverage rule bit**

Temporarily remove the `":voyager-physics"` entry from `PACKAGE_PREFIX_BY_PROJECT` and run `:voyager-fitness:test`. It must fail naming `voyager-physics`. Restore it and re-run. Do not commit the removal.

- [ ] **Step 9: Commit**

```bash
git add voyager-physics voyager-fitness voyager-api settings.gradle.kts
git commit -m "feat(physics): add the module and Vanilla's trigonometry

Minecraft reads sine from a 65536-entry float table rather than calling Math.sin.
The quantisation is behaviour to replicate, not an artefact to improve away, so
one test asserts the results differ from the JDK functions - a port that quietly
substituted them would pass every other test here."
```

---

### Task 2: The look vector

**Files:**
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/math/ViewVector.java`
- Test: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/math/ViewVectorTest.java`

**Interfaces:**
- Consumes: `MinecraftMath` (Task 1), `Vec3` from `voyager-api`.
- Produces: `ViewVector.of(float pitchDegrees, float yawDegrees)` returning `Vec3`. Task 3's steps call it once per tick.

Note the argument order and the sign: Vanilla's `calculateViewVector(xRot, yRot)` takes pitch first, and negates yaw before converting. Getting either wrong yields a flight that looks plausible and heads the wrong way.

- [ ] **Step 1: Write the failing test**

Create `voyager-physics/src/test/java/net/elytrarace/voyager/physics/math/ViewVectorTest.java`:

```java
package net.elytrarace.voyager.physics.math;

import net.elytrarace.voyager.api.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ViewVectorTest {

    @Test
    void lookingStraightAheadAtZeroYawPointsAlongPositiveZ() {
        Vec3 look = ViewVector.of(0.0f, 0.0f);

        assertThat(look.x()).isCloseTo(0.0, within(1.0e-3));
        assertThat(look.y()).isCloseTo(0.0, within(1.0e-3));
        assertThat(look.z()).isCloseTo(1.0, within(1.0e-3));
    }

    @Test
    void yawOfNinetyPointsAlongNegativeX() {
        Vec3 look = ViewVector.of(0.0f, 90.0f);

        assertThat(look.x()).isCloseTo(-1.0, within(1.0e-3));
        assertThat(look.z()).isCloseTo(0.0, within(1.0e-3));
    }

    @Test
    void negativePitchPointsUpwards() {
        assertThat(ViewVector.of(-45.0f, 0.0f).y()).isGreaterThan(0.0);
    }

    @Test
    void positivePitchPointsDownwards() {
        assertThat(ViewVector.of(45.0f, 0.0f).y()).isLessThan(0.0);
    }

    @Test
    void isApproximatelyUnitLength() {
        for (float pitch = -90.0f; pitch <= 90.0f; pitch += 7.5f) {
            for (float yaw = -180.0f; yaw < 180.0f; yaw += 15.0f) {
                assertThat(ViewVector.of(pitch, yaw).length())
                        .as("pitch=%s yaw=%s".formatted(pitch, yaw))
                        .isCloseTo(1.0, within(1.0e-3));
            }
        }
    }

    @Test
    void straightDownIsAlmostExactlyNegativeY() {
        Vec3 look = ViewVector.of(90.0f, 0.0f);

        assertThat(look.y()).isCloseTo(-1.0, within(1.0e-3));
        assertThat(Math.hypot(look.x(), look.z())).isCloseTo(0.0, within(1.0e-3));
    }

    @Test
    void usesTheTableTrigonometryRatherThanTheJdk() {
        // Same guard as MinecraftMathTest, one level up: a view vector built from Math.* would be
        // close enough to pass every assertion above and still drift against the traces.
        float pitch = 17.0f;
        float leanAngle = pitch * MinecraftMath.DEG_TO_RAD;
        double expectedY = -MinecraftMath.sin(leanAngle);

        assertThat(ViewVector.of(pitch, 0.0f).y()).isEqualTo(expectedY);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-physics:test --tests "*ViewVectorTest"`
Expected: FAIL — `cannot find symbol: class ViewVector`.

- [ ] **Step 3: Write the implementation**

`ViewVector` is a **`public abstract`** utility with a private constructor and `@ApiStatus.Internal` — public because Task 3's `step` package calls it — transcribing `calculateViewVector` including its `float` intermediates:

```java
public abstract class ViewVector {

    private ViewVector() {
    }

    public static Vec3 of(float pitchDegrees, float yawDegrees) {
    float realXRot = pitchDegrees * MinecraftMath.DEG_TO_RAD;
    float realYRot = -yawDegrees * MinecraftMath.DEG_TO_RAD;
    float yCos = MinecraftMath.cos(realYRot);
    float ySin = MinecraftMath.sin(realYRot);
    float xCos = MinecraftMath.cos(realXRot);
    float xSin = MinecraftMath.sin(realXRot);
        return new Vec3(ySin * xCos, -xSin, yCos * xCos);
    }
}
```

Keep every intermediate `float`. The multiplications `ySin * xCos` happen in `float` in Vanilla and only widen when the `Vec3` is constructed.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-physics:test`
Expected: PASS, 15 tests.

- [ ] **Step 5: Commit**

```bash
git add voyager-physics
git commit -m "feat(physics): port Vanilla's view vector

Pitch first, yaw negated before conversion, and every intermediate held in float
until the vector is constructed - the multiplications happen in float in Vanilla."
```

---

### Task 3: The velocity update as an ordered pipeline

**Files:**
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/step/FlightStep.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/step/StepContext.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/step/ElytraStep.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/step/package-info.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/exception/InvalidSimulationStateException.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/exception/package-info.java`
- Test: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/step/ElytraStepTest.java`

**Interfaces:**
- Consumes: `MinecraftMath`, `ViewVector`, `Vec3`.
- Produces: `ElytraStep`, an enum of the ordered steps, each holding a `FlightStep`; and `StepContext`, the per-tick values every step reads. Task 5 runs them in order; Task 7's harness reports which one diverged.

The decomposition is diagnostic, and it earns its keep on the first red trace: a mismatch then reports "the drag step diverges from tick 412" instead of "position wrong at tick 412". Without it, debugging means bisecting a nested formula by hand.

Steps, in Vanilla's order:

| Enum constant | What it does |
|---|---|
| `GRAVITY_AND_LIFT` | `velY += gravity * (-1.0 + liftForce * 0.75)` |
| `DOWNWARD_GLIDE` | when `velY < 0 && lookHorLength > 0`: convert sink into forward motion, scaled by `liftForce` |
| `UPWARD_PITCH_BOOST` | when `leanAngle < 0 && lookHorLength > 0`: trade horizontal speed for climb, vertical term `× 3.2` |
| `DIRECTION_ALIGNMENT` | when `lookHorLength > 0`: interpolate horizontal velocity towards the look direction at rate `0.1` |
| `DRAG` | `multiply(0.99F, 0.98F, 0.99F)` |

`StepContext` is a record carrying what the steps share, computed once per tick before the first step runs: `lookAngle`, `leanAngle` (float), `lookHorLength`, `moveHorLength`, `gravity`, `liftForce`. Note `moveHorLength` and `liftForce` are computed from the velocity **entering the tick**, not recomputed between steps — Vanilla computes them once at the top of `updateFallFlyingMovement`.

`FlightStep` is the injectable unit:

```java
@FunctionalInterface
public interface FlightStep {
    Vec3 apply(Vec3 velocity, StepContext context);
}
```

- [ ] **Step 1: Write the failing test**

Create `voyager-physics/src/test/java/net/elytrarace/voyager/physics/step/ElytraStepTest.java`:

```java
package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.math.MinecraftMath;
import net.elytrarace.voyager.physics.math.ViewVector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElytraStepTest {

    private static final double DEFAULT_GRAVITY = 0.08;

    private static StepContext context(Vec3 velocity, float pitch, float yaw) {
        return StepContext.of(velocity, pitch, yaw, DEFAULT_GRAVITY);
    }

    @Test
    void theStepsRunInVanillaOrder() {
        assertThat(ElytraStep.values()).containsExactly(
                ElytraStep.GRAVITY_AND_LIFT,
                ElytraStep.DOWNWARD_GLIDE,
                ElytraStep.UPWARD_PITCH_BOOST,
                ElytraStep.DIRECTION_ALIGNMENT,
                ElytraStep.DRAG);
    }

    @Test
    void levelFlightLosesTheDocumentedAmountOfAltitudePerTick() {
        // pitch 0 -> liftForce = cos²(0) = 1 -> velY += 0.08 * (-1 + 0.75) = -0.02
        Vec3 result = ElytraStep.GRAVITY_AND_LIFT.step().apply(Vec3.ZERO, context(Vec3.ZERO, 0.0f, 0.0f));

        assertThat(result.y()).isCloseTo(-0.02, within(1.0e-12));
    }

    @Test
    void straightDownLosesFullGravityBecauseLiftVanishes() {
        // pitch 90 -> cos(90°) ~ 0 -> velY += gravity * -1
        Vec3 result = ElytraStep.GRAVITY_AND_LIFT.step().apply(Vec3.ZERO, context(Vec3.ZERO, 90.0f, 0.0f));

        assertThat(result.y()).isCloseTo(-DEFAULT_GRAVITY, within(1.0e-4));
    }

    @Test
    void gravityScalesBothHalvesOfTheLiftTerm() {
        // The term is gravity * (-1 + liftForce * 0.75), not a constant -0.08 plus a constant lift.
        StepContext halfGravity = StepContext.of(Vec3.ZERO, 0.0f, 0.0f, 0.04);

        Vec3 result = ElytraStep.GRAVITY_AND_LIFT.step().apply(Vec3.ZERO, halfGravity);

        assertThat(result.y()).isCloseTo(-0.01, within(1.0e-12));
    }

    @Test
    void downwardGlideConvertsSinkIntoForwardMotion() {
        Vec3 sinking = new Vec3(0.0, -0.5, 0.0);

        Vec3 result = ElytraStep.DOWNWARD_GLIDE.step().apply(sinking, context(sinking, 0.0f, 0.0f));

        assertThat(result.y()).isGreaterThan(sinking.y());
        assertThat(result.z()).isGreaterThan(0.0);
    }

    @Test
    void downwardGlideIsScaledByLiftForce() {
        // At pitch 60 the lift factor is cos²(60°) = 0.25, so the conversion is a quarter as strong
        // as at pitch 0. A port that dropped the liftForce factor produces the same value for both.
        Vec3 sinking = new Vec3(0.0, -0.5, 0.0);

        double atLevel = ElytraStep.DOWNWARD_GLIDE.step().apply(sinking, context(sinking, 0.0f, 0.0f)).y();
        double atSteep = ElytraStep.DOWNWARD_GLIDE.step().apply(sinking, context(sinking, 60.0f, 0.0f)).y();

        assertThat(atSteep - sinking.y()).isLessThan(atLevel - sinking.y());
    }

    @Test
    void downwardGlideDoesNothingWhileRising() {
        Vec3 rising = new Vec3(0.0, 0.5, 0.0);

        assertThat(ElytraStep.DOWNWARD_GLIDE.step().apply(rising, context(rising, 0.0f, 0.0f)))
                .isEqualTo(rising);
    }

    @Test
    void upwardPitchBoostTradesHorizontalSpeedForClimb() {
        Vec3 fast = new Vec3(0.0, 0.0, 1.0);
        StepContext climbing = context(fast, -30.0f, 0.0f);

        Vec3 result = ElytraStep.UPWARD_PITCH_BOOST.step().apply(fast, climbing);

        assertThat(result.y()).isGreaterThan(0.0);
        assertThat(result.z()).isLessThan(fast.z());
    }

    @Test
    void upwardPitchBoostDoesNothingWhilePitchedDown() {
        Vec3 fast = new Vec3(0.0, 0.0, 1.0);

        assertThat(ElytraStep.UPWARD_PITCH_BOOST.step().apply(fast, context(fast, 10.0f, 0.0f)))
                .isEqualTo(fast);
    }

    @Test
    void upwardPitchBoostLiftsByThreePointTwoTimesTheTradedSpeed() {
        Vec3 fast = new Vec3(0.0, 0.0, 1.0);
        StepContext climbing = context(fast, -30.0f, 0.0f);
        float leanAngle = -30.0f * MinecraftMath.DEG_TO_RAD;
        double convert = 1.0 * -MinecraftMath.sin(leanAngle) * 0.04;

        Vec3 result = ElytraStep.UPWARD_PITCH_BOOST.step().apply(fast, climbing);

        assertThat(result.y()).isCloseTo(convert * 3.2, within(1.0e-12));
    }

    @Test
    void directionAlignmentPullsVelocityTowardsTheLookDirection() {
        Vec3 sideways = new Vec3(1.0, 0.0, 0.0);
        StepContext lookingForward = context(sideways, 0.0f, 0.0f);

        Vec3 result = ElytraStep.DIRECTION_ALIGNMENT.step().apply(sideways, lookingForward);

        assertThat(result.x()).isLessThan(sideways.x());
        assertThat(result.z()).isGreaterThan(sideways.z());
        assertThat(result.y()).isEqualTo(sideways.y());
    }

    @Test
    void dragUsesTheWidenedFloatConstantsNotTheDoubleLiterals() {
        // Trap 1. 0.99 and 0.99F are different numbers once widened, and the difference compounds
        // every tick in the one operation that touches every component.
        Vec3 velocity = new Vec3(1.0, 1.0, 1.0);

        Vec3 result = ElytraStep.DRAG.step().apply(velocity, context(velocity, 0.0f, 0.0f));

        assertThat(result.x()).isEqualTo((double) 0.99f);
        assertThat(result.y()).isEqualTo((double) 0.98f);
        assertThat(result.z()).isEqualTo((double) 0.99f);
        assertThat(result.x()).isNotEqualTo(0.99);
        assertThat(result.y()).isNotEqualTo(0.98);
    }

    @Test
    void theLeanAngleIsComputedInFloatNotViaMathToRadians() {
        // Trap 2.
        float pitch = -12.5f;
        StepContext ctx = context(Vec3.ZERO, pitch, 0.0f);

        assertThat(ctx.leanAngle()).isEqualTo(pitch * MinecraftMath.DEG_TO_RAD);
        assertThat((double) ctx.leanAngle()).isNotEqualTo(Math.toRadians(pitch));
    }

    @Test
    void theContextExposesTheLookVectorForTheGivenRotation() {
        StepContext ctx = context(Vec3.ZERO, 20.0f, 45.0f);

        assertThat(ctx.lookAngle()).isEqualTo(ViewVector.of(20.0f, 45.0f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-physics:test --tests "*ElytraStepTest"`
Expected: FAIL — `package net.elytrarace.voyager.physics.step does not exist`.

- [ ] **Step 3: Write the implementation**

`StepContext` is a record of `(Vec3 lookAngle, float leanAngle, double lookHorLength, double moveHorLength, double gravity, double liftForce)` with a static `of(Vec3 velocity, float pitch, float yaw, double gravity)` that computes them exactly as Vanilla does at the top of `updateFallFlyingMovement`:

```java
Vec3 lookAngle = ViewVector.of(pitch, yaw);
float leanAngle = pitch * MinecraftMath.DEG_TO_RAD;
double lookHorLength = Math.sqrt(lookAngle.x() * lookAngle.x() + lookAngle.z() * lookAngle.z());
double moveHorLength = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
double liftForce = MinecraftMath.square(Math.cos(leanAngle));
```

Note two things. `Math.sqrt(x*x + z*z)` and not `Math.hypot` — Vanilla's `Vec3.horizontalDistance()` is the former, and `hypot` is a different, more accurate algorithm that disagrees in roughly one case in nine by a single ulp. And `Math.cos` here, not `MinecraftMath.cos` — Vanilla uses the JDK cosine for the lift term and the table for everything else. The compact constructor rejects a non-finite or non-positive gravity with `InvalidSimulationStateException`, a `final class` extending `RuntimeException` in the module's `exception` subpackage:

```java
package net.elytrarace.voyager.physics.exception;

/** Thrown when the simulation is handed a state it cannot advance. */
public final class InvalidSimulationStateException extends RuntimeException {

    public InvalidSimulationStateException(String message) {
        super(message);
    }
}
```

`ElytraStep` is an enum whose constants each hold a `FlightStep`, in the order given above, with a cached `VALUES` array and a `step()` accessor. Each constant transcribes its Vanilla branch, including the guards `lookHorLength > 0.0` and `leanAngle < 0.0F` — replicate Vanilla's guards exactly, neither adding your own nor omitting one.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-physics:test`
Expected: PASS, 29 tests.

- [ ] **Step 5: Commit**

```bash
git add voyager-physics
git commit -m "feat(physics): decompose the velocity update into ordered steps

The decomposition is diagnostic: a trace mismatch names the step that diverged
rather than only the tick. Three tests exist solely to keep a later tidy-up from
reintroducing the fidelity traps - float lean angle, widened float drag, and the
lift term's dependence on gravity rather than a hardcoded pair of constants."
```

---

### Task 4: Position integration and collision

**Files:**
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/collision/MovementResolver.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/collision/MovementResult.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/collision/package-info.java`
- Test: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/collision/MovementResolverTest.java`

**Interfaces:**
- Consumes: `Vec3`, `Aabb`, `CollisionSpace` from `voyager-api`.
- Produces: `MovementResolver.resolve(Aabb box, Vec3 movement, CollisionSpace space)` returning `MovementResult(Vec3 allowedMovement, boolean horizontalCollision, boolean onGround)`. Task 5 uses it for steps 9 and 10.

This is the part the current implementation omits entirely, and the plausibility check the design specifies cannot exist without it: validating a client means predicting where it should be, which means integrating and colliding.

The sweep is axis-separated in Vanilla's order — Y first, then X, then Z — with each axis resolved against the box already moved by the previous axes. Step-up is deliberately not implemented; see this plan's scope section.

- [ ] **Step 1: Write the failing test**

Create `voyager-physics/src/test/java/net/elytrarace/voyager/physics/collision/MovementResolverTest.java`:

```java
package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MovementResolverTest {

    /** A 0.6 x 1.8 box, the shape of a standing player, with its feet at the given point. */
    private static Aabb boxAt(double x, double y, double z) {
        return new Aabb(new Vec3(x - 0.3, y, z - 0.3), new Vec3(x + 0.3, y + 1.8, z + 0.3));
    }

    private static CollisionSpace floorAtZero() {
        return region -> List.of(new Aabb(new Vec3(-64, -1, -64), new Vec3(64, 0, 64)));
    }

    private static CollisionSpace wallAtXFive() {
        return region -> List.of(new Aabb(new Vec3(5, -64, -64), new Vec3(6, 64, 64)));
    }

    @Test
    void emptySpaceAllowsTheWholeMovement() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(1, -0.5, 2), CollisionSpace.empty());

        assertThat(result.allowedMovement()).isEqualTo(new Vec3(1, -0.5, 2));
        assertThat(result.horizontalCollision()).isFalse();
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void aFloorStopsDownwardMovementExactlyAtItsSurface() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, -2.0, 0), floorAtZero());

        assertThat(result.allowedMovement().y()).isCloseTo(-0.5, within(1.0e-9));
        assertThat(result.onGround()).isTrue();
    }

    @Test
    void landingDoesNotReportAHorizontalCollision() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, -2.0, 0), floorAtZero());

        assertThat(result.horizontalCollision()).isFalse();
    }

    @Test
    void aWallStopsHorizontalMovementAndReportsIt() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(10.0, 0, 0), wallAtXFive());

        assertThat(result.allowedMovement().x()).isCloseTo(4.7, within(1.0e-9));
        assertThat(result.horizontalCollision()).isTrue();
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void anAxisWithoutAnObstacleIsUnaffectedByOneThatHasOne() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(10.0, 0, 3.0), wallAtXFive());

        assertThat(result.allowedMovement().z()).isEqualTo(3.0);
    }

    @Test
    void risingIntoNothingIsNotGrounded() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, 2.0, 0), floorAtZero());

        assertThat(result.allowedMovement().y()).isEqualTo(2.0);
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void aBoxAlreadyRestingOnTheFloorCannotSinkIntoIt() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0, 0), new Vec3(0, -0.5, 0), floorAtZero());

        assertThat(result.allowedMovement().y()).isCloseTo(0.0, within(1.0e-9));
        assertThat(result.onGround()).isTrue();
    }

    @Test
    void zeroMovementResolvesToZeroWithoutQueryingTheSpace() {
        CollisionSpace exploding = region -> {
            throw new AssertionError("the resolver must not query the space for a zero movement");
        };

        assertThat(MovementResolver.resolve(boxAt(0, 10, 0), Vec3.ZERO, exploding).allowedMovement())
                .isEqualTo(Vec3.ZERO);
    }
}
```

**These fixtures alone are not sufficient, and the suite must not stop here.** `floorAtZero` and `wallAtXFive` are infinite planes on their perpendicular axes, so neither the axis order nor the strictness of the overlap test can change any outcome, and nothing above clamps Y while rising. All three of the defects this resolver most plausibly has — sweeping the axes in the wrong order, selecting candidate boxes with non-strict overlap, reporting `onGround` on any Y clamp rather than only a downward one — survive the fixtures above untouched. Add three more, each shaped to discriminate exactly one:

1. **Axis order.** A finite step at `x ∈ [1, 2]`, `y ∈ [0, 1]`. A box with feet at `x = 0.5`, `y = 1.1` moving `(+0.6, -0.3, 0)` resolves to an allowed x of `0.2` under Vanilla's Y-first order and to an allowed y of `-0.1` under X-first. The two orders disagree; assert the first.
2. **Overlap strictness.** The same step, with a box whose maximum x edge sits exactly on the step's minimum x edge — feet at `x = 0.7`, so `x ∈ [0.4, 1.0]` — falling from `y = 1.1` by `-0.3`. Strict overlap lets it fall freely; non-strict catches it on the step.
3. **Ground direction.** A ceiling at `y ∈ [7, 8]` and a box with feet at `y = 5` moving `(0, +2.0, 0)`. Y is clamped, but `onGround` must stay false.

Prove each bites by introducing its defect, observing exactly one red test, and reverting.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-physics:test --tests "*MovementResolverTest"`
Expected: FAIL — `package net.elytrarace.voyager.physics.collision does not exist`.

- [ ] **Step 3: Write the implementation**

`MovementResult` is a record with a compact constructor rejecting a non-finite movement.

`MovementResolver` is an `abstract` utility with a private constructor and `@ApiStatus.Internal`. `resolve` returns immediately for a zero movement without touching the space — Vanilla short-circuits on `movement.lengthSqr() == 0.0` and the test above pins that. Otherwise it queries the space once for the region the box sweeps through (`box` expanded towards `movement`, then expanded by a small epsilon), then resolves Y, X, Z in that order, moving the box between axes.

Per-axis resolution finds, among the boxes that overlap the moving box on the other two axes, the nearest surface in the direction of travel and clamps the movement to it. `horizontalCollision` is true when the X or Z component was clamped; `onGround` is true when the Y component was clamped while moving downwards.

Document in the class javadoc that step-up is not implemented and why the glide path does not reach it.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-physics:test`
Expected: PASS, 37 tests.

- [ ] **Step 5: Commit**

```bash
git add voyager-physics
git commit -m "feat(physics): integrate position and resolve block collision

Vanilla's axis-separated sweep, Y then X then Z, each axis resolved against the
box already moved by the previous ones. Step-up is deliberately absent: its branch
requires being on ground, which a gliding entity is not until the tick it lands."
```

---

### Task 5: The simulator

**Files:**
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/ElytraSimulator.java`
- Create: `voyager-physics/src/main/java/net/elytrarace/voyager/physics/TickTrace.java`
- Test: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/ElytraSimulatorTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1 to 4, plus `FlightState`, `FlightInput`, `CollisionSpace` from `voyager-api`.
- Produces: `ElytraSimulator.tick(FlightState previous, FlightInput input, CollisionSpace space)` returning `FlightState`, and `ElytraSimulator.tickTraced(...)` returning a `TickTrace` carrying the velocity after each step. Task 7's harness calls the traced form; production calls the plain one.

Firework boost is applied here rather than as a pipeline step, because Vanilla applies it outside `updateFallFlyingMovement`: `velocity += look * 0.1 + (look * 1.5 - velocity) * 0.5`, evaluated per axis.

The entity's bounding box is derived from its position — a 0.6 by 1.8 box with its feet at the position — rather than carried in `FlightState`, because it is a constant of the entity, not part of its state.

- [ ] **Step 1: Write the failing test**

Create `voyager-physics/src/test/java/net/elytrarace/voyager/physics/ElytraSimulatorTest.java`:

```java
package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.physics.step.ElytraStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElytraSimulatorTest {

    private static final double DEFAULT_GRAVITY = 0.08;

    private static FlightState gliding(double y, Vec3 velocity) {
        return new FlightState(new Vec3(0, y, 0), velocity, 0.0f, -5.0f, false);
    }

    private static FlightInput level() {
        return new FlightInput(0.0f, -5.0f, false, 0, DEFAULT_GRAVITY);
    }

    private static CollisionSpace floorAtZero() {
        return region -> List.of(new Aabb(new Vec3(-64, -1, -64), new Vec3(64, 0, 64)));
    }

    @Test
    void aLevelGlideLosesAltitudeAndGainsForwardSpeed() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));

        for (int tick = 0; tick < 20; tick++) {
            state = ElytraSimulator.tick(state, level(), CollisionSpace.empty());
        }

        assertThat(state.position().y()).isLessThan(100.0);
        assertThat(state.position().z()).isGreaterThan(0.0);
        assertThat(state.velocity().z()).isGreaterThan(0.5);
    }

    @Test
    void isAPureFunctionOfItsArguments() {
        FlightState before = gliding(100.0, new Vec3(0, 0, 0.5));

        FlightState first = ElytraSimulator.tick(before, level(), CollisionSpace.empty());
        FlightState second = ElytraSimulator.tick(before, level(), CollisionSpace.empty());

        assertThat(first).isEqualTo(second);
        assertThat(before.position()).isEqualTo(new Vec3(0, 100.0, 0));
        assertThat(before.velocity()).isEqualTo(new Vec3(0, 0, 0.5));
    }

    @Test
    void theTracedFormReportsOneVelocityPerStepInOrder() {
        TickTrace trace = ElytraSimulator.tickTraced(
                gliding(100.0, new Vec3(0, 0, 0.5)), level(), CollisionSpace.empty());

        assertThat(trace.velocityAfter()).hasSize(ElytraStep.values().length);
        assertThat(trace.velocityAfter().keySet()).containsExactly(ElytraStep.values());
    }

    @Test
    void theTracedFormAgreesWithThePlainForm() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));

        FlightState plain = ElytraSimulator.tick(state, level(), CollisionSpace.empty());
        TickTrace traced = ElytraSimulator.tickTraced(state, level(), CollisionSpace.empty());

        assertThat(traced.result()).isEqualTo(plain);
    }

    @Test
    void aFireworkBoostAcceleratesTowardsTheLookDirection() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));
        FlightInput boosting = new FlightInput(0.0f, -5.0f, true, 1, DEFAULT_GRAVITY);

        FlightState boosted = ElytraSimulator.tick(state, boosting, CollisionSpace.empty());
        FlightState coasting = ElytraSimulator.tick(state, level(), CollisionSpace.empty());

        assertThat(boosted.velocity().z()).isGreaterThan(coasting.velocity().z());
    }

    @Test
    void aGlideIntoTheGroundStopsAndReportsBeingGrounded() {
        FlightState state = gliding(0.2, new Vec3(0, -1.0, 0.5));

        FlightState landed = ElytraSimulator.tick(state, level(), floorAtZero());

        assertThat(landed.onGround()).isTrue();
        assertThat(landed.position().y()).isEqualTo(0.0);
    }

    @Test
    void theEntityNeverSinksBelowTheFloorAcrossManyTicks() {
        FlightState state = gliding(3.0, new Vec3(0, -0.5, 0.3));

        for (int tick = 0; tick < 60; tick++) {
            state = ElytraSimulator.tick(state, level(), floorAtZero());
            assertThat(state.position().y())
                    .as("tick %s".formatted(tick))
                    .isGreaterThanOrEqualTo(0.0);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-physics:test --tests "*ElytraSimulatorTest"`
Expected: FAIL — `cannot find symbol: class ElytraSimulator`.

- [ ] **Step 3: Write the implementation**

`tick` builds a `StepContext` from the incoming velocity and the input's rotation, applies the firework boost if the input requests one, folds the five `ElytraStep` values in order, resolves the movement with `MovementResolver`, and returns a new `FlightState` at the resolved position with the post-drag velocity and the resolved ground state.

`tickTraced` does the same and additionally records the velocity after each step into a `TickTrace` record.

Both are static on an `abstract` utility class; there is no state to hold and nothing to inject. If a later stage needs a version-specific variant, it becomes a different constants record passed in, not a subclass hierarchy invented now.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-physics:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voyager-physics
git commit -m "feat(physics): assemble the Vanilla elytra tick

A pure function from state and input to state. The traced variant records the
velocity after each step so a failing replay can name the step rather than only
the tick."
```

---

### Task 6: The replay harness

**Files:**
- Create: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/trace/TraceFixture.java`
- Create: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/trace/TraceFixtureLoader.java`
- Create: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/trace/RecordedCollisionSpace.java`
- Create: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/trace/TraceReplay.java`
- Create: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/trace/TraceReplayTest.java`
- Modify: `voyager-physics/build.gradle.kts` — Gson in test scope

**Interfaces:**
- Consumes: `ElytraSimulator.tickTraced`, and the fixture format defined by E2a Task 2.
- Produces: `TraceReplay.replay(TraceFixture)` returning a `ReplayReport` naming the first diverging tick, the velocity error at that tick, and the per-tick and cumulative position error.

**Corrected during execution: there is no per-step attribution, and this plan was wrong to promise one.** A fixture carries one velocity per tick — the value after the last step — while the distances between consecutive steps' outputs are the same order of magnitude as a typical divergence. Measured over 245 realistically shaped cases, a heuristic attribution named a step by perturbation magnitude and direction rather than by cause: correct for velocity errors at or below `1e-3`, noise at `1e-2` and above. The step decomposition keeps its value for interactive debugging through `ElytraSimulator.tickTraced`; what the format cannot support is naming the step automatically. The velocity error must be computed against the **restituted** velocity, since that is what a recorder measures — comparing against the pre-restitution value reports the restitution gap on exactly the collision ticks the landing profiles are built from.

All of this lives in the test source set. Fixture reading is not production behaviour and must not leak into the module's API — an ArchUnit rule already forbids `java.nio.file` in main sources, and this task must not be the reason someone weakens it.

Since fixtures do not exist until E2a completes, this task builds the harness against **synthetic fixtures the test generates**: run the simulator forward, write the result out in the fixture format, replay it, and assert the report is clean. That proves the harness works before it is ever pointed at a real recording, and it makes a later real-trace failure meaningful rather than ambiguous.

- [ ] **Step 1: Write the failing test**

`TraceReplayTest` must cover, at minimum:

- a synthetic fixture generated from the simulator replays with zero divergence;
- a fixture whose recorded position is perturbed at tick `n` produces a report naming tick `n`;
- a fixture whose divergence exceeds the per-tick threshold reports it as a failure, and one below it does not;
- cumulative error is reported separately from per-tick error;
- a replay reports the step whose velocity first diverged, not just the tick;
- an empty or malformed fixture fails with a domain exception rather than a `NullPointerException`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-physics:test --tests "*TraceReplayTest"`
Expected: FAIL — `package net.elytrarace.voyager.physics.trace does not exist`.

- [ ] **Step 3: Write the implementation**

`TraceFixture` mirrors E2a's `TraceFile` on the reading side. Keep the field names identical — they are the file format, and a rename here silently stops reading real fixtures while the synthetic tests keep passing.

`RecordedCollisionSpace` implements `CollisionSpace` over the fixture's `worldSlice`, returning the boxes intersecting the queried region.

`TraceReplay` seeds a `FlightState` from tick 0 of the fixture — position and the recorded internal velocity — then for each subsequent tick feeds the recorded rotation and boost state as `FlightInput`, calls `tickTraced`, and compares the simulated position against the recorded one. It reports the first tick exceeding the per-tick threshold, the step whose velocity diverged most at that tick, and the cumulative position error across the whole replay.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-physics:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add voyager-physics
git commit -m "test(physics): add the trace replay harness

Built and proven against synthetic fixtures the tests generate, so the harness is
known good before it ever meets a real recording - a later red trace then means
the port is wrong, not that the harness might be."
```

---

### Task 7: Real fixtures and threshold calibration

**E2a is complete.** Nine fixtures — not eight; `sustained-turn` was added because the other
eight all fly at yaw 0, where the look vector's x component vanishes and the whole x axis drops out
of the tick. They are committed at `tools/trace-recorder/traces/*.json` and must be **moved** to
`voyager-physics/src/test/resources/traces/`, which is where the spec puts them and where the
consuming tests live.

## What has already been measured

Do not re-derive this. Every fixture was replayed through `ElytraSimulator` as a **one-step
residual** — take the recorded state at k, advance exactly one tick, compare against the recorded
state at k+1 — with gravity read from the metadata, the firework flags read from the fixture, and an
empty `CollisionSpace`:

| Profile | ticks | steps exactly zero | worst residual | deviating steps |
|---|---|---|---|---|
| `steady-glide` | 200 | **199/199** | 0 | — |
| `climb-into-stall` | 200 | **199/199** | 0 | — |
| `sustained-turn` | 200 | 194/199 | 2.6e-03 | 31–34, plus 158 |
| `pitch-extremes` | 200 | 194/199 | 1.9e-03 | 195–198, plus 172 |
| `dive-and-pull-out` | 200 | 188/199 | 2.5e-03 | 163–172, plus 139 |
| `single-boost` | 200 | 175/199 | 2.2e-02 | **29–51**, plus 77 |
| `chained-boosts` | 200 | 169/199 | 2.7e-02 | **19–47**, plus 73 |
| `wall-graze` | 220 | 170/219 | 7.1e-01 | from 170 |
| `landing` | 200 | 137/199 | 7.3e-01 | from 138 |

This already settles Step 2's classification for most of them:

- **Two profiles need no tolerance at all.** Steady flight and the climb into stall are bit-exact
  against real Vanilla. Any threshold that lets them pass by a margin is a threshold that would
  also hide a real defect. Assert exactly zero for these.
- **The boost is a port defect, not a tolerance question.** In `single-boost` the boost is active on
  ticks 30–51 and the deviating steps are 29–51 — exactly the window, not one tick more. The port
  has a boost branch (`applyFireworkBoost`); it does not match Vanilla, and its magnitude is an
  order above everything else. What to reproduce is visible in the recording: during a boost `velZ`
  stands still (1.671612 / 1.671612 / 1.671613) and only falls afterwards, so Vanilla blends toward
  a terminal speed rather than adding an impulse. **Fix the step. Do not widen a threshold.**
- **`wall-graze` and `landing` are not findings yet.** That measurement ran an empty
  `CollisionSpace`, so a 0.7-block deviation at the collision and touchdown ticks is expected. Feed
  a `CollisionSpace` from each fixture's own `worldSlice` — 98 and 196 boxes, coordinates already
  checked against the scripts' `# world:` lines — and measure again before classifying them.
- **Three profiles show short bursts at manoeuvre transitions** (4–10 steps, 2–3e-03), plus a single
  isolated step in almost every profile (77, 73, 158, 139, 172). A contiguous window and a lone
  outlier are different phenomena; classify them separately. `tickTraced` gives the per-step values
  needed to name which `ElytraStep` diverges.

**One methodological point that changes the answer.** A free-running replay measures the starting
condition *and* the formula, and makes a single slip look like a continuous error: the same
`steady-glide` fixture that is bit-exact step by step accumulated 8.2e-02 blocks of drift when
replayed free-running from its spawn point, purely as the decaying echo of one anomalous first
transition that has since been fixed in the recorder. Calibrate a threshold **only** against the
one-step residual. Keep a free-running test as well, with its own separate bound, but never as the
only one — on its own it points at 199 steps where nothing is wrong.

A working harness for both forms is at
`/tmp/claude-1000/-mnt-projects-oss-onelitefeather-Voyager/bf572fec-d4f7-4155-ae01-787f00089f05/scratchpad/tick0check/`
(`Residual.java`, `Replay.java`). It is scratch code, not something to merge, but the numbers above
came out of it and it is the fastest way to reproduce them.

**Files:**
- Create: `voyager-physics/src/test/resources/traces/*.json` — the nine fixtures, moved from `tools/trace-recorder/traces/`
- Delete: `tools/trace-recorder/traces/*.json` — one home for the fixtures, not two
- Create: `voyager-physics/src/test/java/net/elytrarace/voyager/physics/trace/VanillaParityTest.java`
- Modify: `docs/reference/elytra-physics-26.2.md` — record the measured thresholds
- Modify: `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` — replace the assumed thresholds with the measured ones

**Interfaces:**
- Consumes: `TraceReplay` from Task 6, the fixtures from E2a Task 7.
- Produces: the parity suite that gates stage E2, and the thresholds every later claim about parity rests on.

The spec's thresholds — per-tick position deviation under `1e-6` blocks, cumulative drift under `0.01` blocks over 200 ticks — are **stated assumptions, not measurements**. This task is where they are confirmed or corrected against evidence. Correcting them is a legitimate outcome; quietly widening them until the suite passes is not.

- [ ] **Step 1: Copy the fixtures and run them**

Move the nine fixtures into `voyager-physics/src/test/resources/traces/` and delete the originals under `tools/trace-recorder/traces/` — two copies of a fixture is one copy too many, and the recorder does not read them back. Write `VanillaParityTest` as a parameterised test over every fixture on the classpath, so adding a ninth needs no code change.

Run it and record, per profile: the maximum per-tick deviation, the cumulative deviation, and — for any profile that fails — the first diverging tick and step.

- [ ] **Step 2: Read the failures before touching the thresholds**

For each failing profile, decide which of these it is, and say so in the report with evidence:

1. **A port defect.** The step named by the report is wrong. Fix the step, not the threshold.
2. **A recording defect.** The fixture does not show the behaviour its profile claims, or its metadata is wrong. Fix the script and re-record.
3. **A genuine tolerance question.** The port matches Vanilla but floating-point accumulation exceeds an assumed bound.

Only the third justifies changing a threshold, and only with the measured distribution written down beside it.

- [ ] **Step 3: Set the thresholds from measurement**

Set the per-tick and cumulative bounds from the observed values with a stated margin, and record in `docs/reference/elytra-physics-26.2.md`: the measured maximum per profile, the chosen bound, and the margin between them. Update the spec's Trace acceptance section to match, replacing the assumed numbers rather than leaving both.

- [ ] **Step 4: Add the contract tests for the step hierarchy**

Create an abstract contract test that every `FlightStep` must pass: it never returns a non-finite vector for finite input, it is a pure function of its two arguments, and it leaves the vector unchanged when its guard condition is not met. Run all five steps through it.

This is the mechanism the design names for Liskov substitutability — ArchUnit cannot check behavioural substitutability, a contract test can.

- [ ] **Step 5: Run the full suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, with all nine profiles green at the calibrated thresholds — and the two bit-exact profiles green at exactly zero, not within a margin.

- [ ] **Step 6: Commit**

```bash
git add voyager-physics docs
git commit -m "test(physics): calibrate the parity thresholds against real traces

Replaces the design's assumed bounds with measured ones, recording the observed
maximum per profile and the margin taken. Adds the contract test every flight step
must pass, which is the mechanism for substitutability that ArchUnit cannot check."
```

---

## Definition of Done for E2b

- [ ] `voyager-physics` depends only on `voyager-api`, and `voyager-fitness` enforces it.
- [ ] `FitnessCoverageTest` maps the module and at least one rule names its prefix; removing either turns the build red.
- [ ] The three fidelity traps each have a test whose failure would be caused by exactly the tidy-up that reintroduces them.
- [ ] The simulator is a pure function: no state, no configuration, no injection point.
- [ ] The replay harness is proven against synthetic fixtures before meeting a real one.
- [ ] All eight recorded profiles replay within thresholds that were **measured and recorded**, not assumed.
- [ ] Where the port deliberately stops short of Vanilla — step-up, partial block shapes, entity collision — the module says so in its own documentation.
