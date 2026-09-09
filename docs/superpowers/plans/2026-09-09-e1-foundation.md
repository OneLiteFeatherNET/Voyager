# E1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the build foundation, the pure `voyager-api` module, and an architecture-test module that provably sees every new module — so no later epic can add code that escapes the rules.

**Architecture:** A `buildSrc` convention plugin declares the Java 25 toolchain and the common test stack exactly once. `voyager-api` holds implementation-free types with invariants enforced in compact constructors; `Vec3` rejecting non-finite values is what makes Minestom's NaN-velocity poisoning unrepresentable later. `voyager-fitness` is a test-only module depending on every other new module, with a meta-test that fails when a module is added and not wired into it.

**Tech Stack:** Gradle 9.5.1 (Kotlin DSL, programmatic version catalog in `settings.gradle.kts`), Java 25, JUnit 6.1.1, AssertJ 3.27.7, ArchUnit 1.4.2, JetBrains Annotations 26.1.0.

**Spec:** `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`

## Global Constraints

- Java toolchain **25**, `options.release = 25`, source encoding **UTF-8**, for every new module.
- Base package `net.elytrarace`. New modules are named `voyager-*` and live in directories of the same name at the repository root.
- `voyager-api` contains **interfaces, records, enums and exceptions only** — no implementation, no I/O. It must not reference `net.minestom..`, `org.bukkit..`, `jakarta.persistence..`, `org.hibernate..`, `com.google.inject..`, `jakarta.inject..`, or `java.nio.file..`.
- Every package that contains sources has a `package-info.java` carrying `@NotNullByDefault` (`org.jetbrains.annotations.NotNullByDefault`).
- Domain exceptions extend `RuntimeException`, are named `*Exception`, and use domain-specific names — never `IllegalArgumentException` or `IllegalStateException` for domain invariants.
- Records validate invariants in the compact constructor.
- Version catalog stays programmatic in `settings.gradle.kts`. `gradle/libs.versions.toml` is not used.
- ArchUnit rules are declared with `.allowEmptyShould(false)`. A rule that passes because it matched nothing is a defect.
- The old tree (`server/`, `plugins/*`, `shared/*`) is untouched by this epic and must keep building.

## Known risk carried by this epic

`shared/database` already uses the package `net.elytrarace.api.database`, and `voyager-api` introduces `net.elytrarace.api`. These are not a split package (no class lives in `net.elytrarace.api` itself on the old side) and the two modules never share a classpath, because `voyager-fitness` depends only on `voyager-*` modules. The collision disappears at E7. If a JPMS module descriptor is ever added before then, revisit this.

---

### Task 1: Build foundation and the `voyager-api` module

**Files:**
- Create: `buildSrc/build.gradle.kts`
- Create: `buildSrc/settings.gradle.kts`
- Create: `buildSrc/src/main/kotlin/voyager.java-conventions.gradle.kts`
- Create: `voyager-api/build.gradle.kts`
- Create: `voyager-api/src/main/java/net/elytrarace/api/package-info.java`
- Create: `voyager-api/src/test/java/net/elytrarace/api/ToolchainConventionTest.java`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: nothing.
- Produces: the plugin id `voyager.java-conventions`, applied by every later module; the Gradle project `:voyager-api`; catalog aliases `junit-bom`, `junit-jupiter`, `assertj`.

- [ ] **Step 1: Write the failing test**

The test proves the convention plugin actually produced Java 25 bytecode, by reading the class-file major version out of a compiled class. Major version 69 is Java 25.

Create `voyager-api/src/test/java/net/elytrarace/api/ToolchainConventionTest.java`:

```java
package net.elytrarace.api;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ToolchainConventionTest {

    private static final int JAVA_25_CLASS_FILE_MAJOR = 69;

    @Test
    void classesAreCompiledForJava25() throws Exception {
        String resource = "/" + ToolchainConventionTest.class.getName().replace('.', '/') + ".class";
        try (InputStream in = ToolchainConventionTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("compiled class file must be on the test classpath").isNotNull();
            DataInputStream data = new DataInputStream(in);
            assertThat(data.readInt()).as("class file magic").isEqualTo(0xCAFEBABE);
            data.readUnsignedShort(); // minor version, unused
            assertThat(data.readUnsignedShort())
                    .as("class file major version — 69 is Java 25")
                    .isEqualTo(JAVA_25_CLASS_FILE_MAJOR);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-api:test`
Expected: FAIL — `Project 'voyager-api' not found in root project 'Voyager'`. The module does not exist yet.

- [ ] **Step 3: Create the buildSrc convention plugin**

Create `buildSrc/settings.gradle.kts`:

```kotlin
rootProject.name = "buildSrc"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Create `buildSrc/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}
```

Create `buildSrc/src/main/kotlin/voyager.java-conventions.gradle.kts`:

```kotlin
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    compileOnly(libs.findLibrary("jetbrains-annotations").orElseThrow())
    testImplementation(platform(libs.findLibrary("junit-bom").orElseThrow()))
    testImplementation(libs.findLibrary("junit-jupiter").orElseThrow())
    testImplementation(libs.findLibrary("assertj").orElseThrow())
}
```

The `VersionCatalogsExtension` lookup is required: the generated `libs` accessor is not available inside a precompiled script plugin.

- [ ] **Step 4: Add the catalog aliases and the module to settings**

In `settings.gradle.kts`, inside the `create("libs")` block, after the `archunit.junit5` library line, add:

```kotlin
            version("junit", "6.1.1")
            version("assertj", "3.27.7")
            library("junit.bom", "org.junit", "junit-bom").versionRef("junit")
            library("junit.jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
            library("assertj", "org.assertj", "assertj-core").versionRef("assertj")
```

At the end of `settings.gradle.kts`, after the existing `include("server")` line, add:

```kotlin

// Greenfield rebuild — see docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md
include("voyager-api")
```

- [ ] **Step 5: Create the module**

Create `voyager-api/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/package-info.java`:

```java
@NotNullByDefault
package net.elytrarace.api;

import org.jetbrains.annotations.NotNullByDefault;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :voyager-api:test`
Expected: PASS, 1 test.

- [ ] **Step 7: Verify the old tree still builds**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL. The convention plugin must not have changed any existing module.

- [ ] **Step 8: Commit**

```bash
git add buildSrc settings.gradle.kts voyager-api
git commit -m "build: add buildSrc conventions and the voyager-api module

Declares the Java 25 toolchain, --release 25, UTF-8 and the common test stack
once instead of repeating them per module. Adds voyager-api as the first module
of the greenfield tree, proven to compile to class-file major version 69."
```

---

### Task 2: `Vec3` — the type that cannot hold NaN

**Files:**
- Create: `voyager-api/src/main/java/net/elytrarace/api/math/Vec3.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/math/NonFiniteVectorException.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/math/package-info.java`
- Test: `voyager-api/src/test/java/net/elytrarace/api/math/Vec3Test.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Vec3(double x, double y, double z)` with `Vec3.ZERO`, `plus(Vec3)`, `minus(Vec3)`, `scale(double)`, `length()`, `lengthSquared()`; `NonFiniteVectorException extends RuntimeException`. Every later module uses `Vec3` as its position and velocity carrier.

- [ ] **Step 1: Write the failing test**

Create `voyager-api/src/test/java/net/elytrarace/api/math/Vec3Test.java`:

```java
package net.elytrarace.api.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class Vec3Test {

    @Test
    void acceptsFiniteComponents() {
        Vec3 vec = new Vec3(1.5, -2.25, 3.0);

        assertThat(vec.x()).isEqualTo(1.5);
        assertThat(vec.y()).isEqualTo(-2.25);
        assertThat(vec.z()).isEqualTo(3.0);
    }

    @Test
    void rejectsNaNInAnyComponent() {
        assertThatThrownBy(() -> new Vec3(Double.NaN, 0, 0)).isInstanceOf(NonFiniteVectorException.class);
        assertThatThrownBy(() -> new Vec3(0, Double.NaN, 0)).isInstanceOf(NonFiniteVectorException.class);
        assertThatThrownBy(() -> new Vec3(0, 0, Double.NaN)).isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void rejectsInfinityInAnyComponent() {
        assertThatThrownBy(() -> new Vec3(Double.POSITIVE_INFINITY, 0, 0))
                .isInstanceOf(NonFiniteVectorException.class);
        assertThatThrownBy(() -> new Vec3(0, Double.NEGATIVE_INFINITY, 0))
                .isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void arithmeticThatOverflowsToInfinityIsRejectedAtConstruction() {
        Vec3 huge = new Vec3(Double.MAX_VALUE, 0, 0);

        assertThatThrownBy(() -> huge.scale(2.0))
                .as("overflow must not silently produce an infinite vector")
                .isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void addsAndSubtractsComponentwise() {
        Vec3 a = new Vec3(1, 2, 3);
        Vec3 b = new Vec3(0.5, -1, 2);

        assertThat(a.plus(b)).isEqualTo(new Vec3(1.5, 1, 5));
        assertThat(a.minus(b)).isEqualTo(new Vec3(0.5, 3, 1));
    }

    @Test
    void computesLength() {
        Vec3 vec = new Vec3(3, 4, 0);

        assertThat(vec.lengthSquared()).isEqualTo(25.0);
        assertThat(vec.length()).isCloseTo(5.0, within(1e-12));
    }

    @Test
    void exposesAZeroConstant() {
        assertThat(Vec3.ZERO).isEqualTo(new Vec3(0, 0, 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-api:test --tests "net.elytrarace.api.math.Vec3Test"`
Expected: FAIL — compilation error, `package net.elytrarace.api.math does not exist`.

- [ ] **Step 3: Write the implementation**

Create `voyager-api/src/main/java/net/elytrarace/api/math/NonFiniteVectorException.java`:

```java
package net.elytrarace.api.math;

/**
 * Thrown when a vector component is NaN or infinite.
 *
 * <p>This exists so a non-finite value can never reach the platform layer. Minestom permanently
 * disables a player's velocity channel once a NaN is passed to {@code setVelocity}, including for
 * every later valid call, so the invariant is enforced at construction rather than at the boundary.
 */
public final class NonFiniteVectorException extends RuntimeException {

    public NonFiniteVectorException(double x, double y, double z) {
        super("vector components must be finite, was (" + x + ", " + y + ", " + z + ")");
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/math/Vec3.java`:

```java
package net.elytrarace.api.math;

/**
 * An immutable three-dimensional vector in world space, in blocks.
 *
 * <p>Components are always finite. Construction of a vector holding NaN or infinity fails.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new NonFiniteVectorException(x, y, z);
        }
    }

    public Vec3 plus(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 minus(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/math/package-info.java`:

```java
@NotNullByDefault
package net.elytrarace.api.math;

import org.jetbrains.annotations.NotNullByDefault;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-api:test --tests "net.elytrarace.api.math.Vec3Test"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add voyager-api/src
git commit -m "feat(api): add Vec3 with a finiteness invariant

Components are validated in the compact constructor, so a non-finite vector
cannot be constructed and therefore cannot reach setVelocity. Minestom issue
1335 disables a player's velocity channel permanently after a single NaN."
```

---

### Task 3: `Aabb` and the `CollisionSpace` port

**Files:**
- Create: `voyager-api/src/main/java/net/elytrarace/api/math/Aabb.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/math/InvalidBoundingBoxException.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/physics/CollisionSpace.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/physics/package-info.java`
- Test: `voyager-api/src/test/java/net/elytrarace/api/math/AabbTest.java`
- Test: `voyager-api/src/test/java/net/elytrarace/api/physics/CollisionSpaceTest.java`

**Interfaces:**
- Consumes: `Vec3` from Task 2.
- Produces: `Aabb(Vec3 min, Vec3 max)` with `intersects(Aabb)` and `expand(double)`; `CollisionSpace` with `List<Aabb> boxesIntersecting(Aabb region)` and the static factory `CollisionSpace.empty()`. E2's physics tick takes a `CollisionSpace` as its third parameter.

- [ ] **Step 1: Write the failing tests**

Create `voyager-api/src/test/java/net/elytrarace/api/math/AabbTest.java`:

```java
package net.elytrarace.api.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AabbTest {

    private static Aabb box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new Aabb(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    @Test
    void rejectsInvertedBounds() {
        assertThatThrownBy(() -> box(1, 0, 0, 0, 1, 1)).isInstanceOf(InvalidBoundingBoxException.class);
        assertThatThrownBy(() -> box(0, 1, 0, 1, 0, 1)).isInstanceOf(InvalidBoundingBoxException.class);
        assertThatThrownBy(() -> box(0, 0, 1, 1, 1, 0)).isInstanceOf(InvalidBoundingBoxException.class);
    }

    @Test
    void acceptsDegenerateBoundsWhereMinEqualsMax() {
        assertThat(box(1, 1, 1, 1, 1, 1)).isNotNull();
    }

    @Test
    void detectsOverlap() {
        assertThat(box(0, 0, 0, 2, 2, 2).intersects(box(1, 1, 1, 3, 3, 3))).isTrue();
    }

    @Test
    void treatsTouchingFacesAsIntersecting() {
        assertThat(box(0, 0, 0, 1, 1, 1).intersects(box(1, 0, 0, 2, 1, 1))).isTrue();
    }

    @Test
    void detectsSeparationOnASingleAxis() {
        assertThat(box(0, 0, 0, 1, 1, 1).intersects(box(2, 0, 0, 3, 1, 1))).isFalse();
    }

    @Test
    void expandsSymmetrically() {
        assertThat(box(0, 0, 0, 1, 1, 1).expand(0.5)).isEqualTo(box(-0.5, -0.5, -0.5, 1.5, 1.5, 1.5));
    }
}
```

Create `voyager-api/src/test/java/net/elytrarace/api/physics/CollisionSpaceTest.java`:

```java
package net.elytrarace.api.physics;

import net.elytrarace.api.math.Aabb;
import net.elytrarace.api.math.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollisionSpaceTest {

    private static final Aabb UNIT = new Aabb(Vec3.ZERO, new Vec3(1, 1, 1));

    @Test
    void emptySpaceReturnsNoBoxes() {
        assertThat(CollisionSpace.empty().boxesIntersecting(UNIT)).isEmpty();
    }

    @Test
    void isAFunctionalInterfaceSoTestsCanSupplyAWorld() {
        CollisionSpace floor = region -> List.of(new Aabb(new Vec3(-8, -1, -8), new Vec3(8, 0, 8)));

        assertThat(floor.boxesIntersecting(UNIT)).hasSize(1);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :voyager-api:test --tests "net.elytrarace.api.math.AabbTest" --tests "net.elytrarace.api.physics.CollisionSpaceTest"`
Expected: FAIL — compilation errors, `cannot find symbol: class Aabb` and `package net.elytrarace.api.physics does not exist`.

- [ ] **Step 3: Write the implementation**

Create `voyager-api/src/main/java/net/elytrarace/api/math/InvalidBoundingBoxException.java`:

```java
package net.elytrarace.api.math;

/** Thrown when a bounding box is constructed with a minimum corner that exceeds its maximum. */
public final class InvalidBoundingBoxException extends RuntimeException {

    public InvalidBoundingBoxException(Vec3 min, Vec3 max) {
        super("bounding box minimum must not exceed its maximum on any axis, was " + min + " to " + max);
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/math/Aabb.java`:

```java
package net.elytrarace.api.math;

/** An immutable axis-aligned bounding box in world space, in blocks. */
public record Aabb(Vec3 min, Vec3 max) {

    public Aabb {
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new InvalidBoundingBoxException(min, max);
        }
    }

    /** Touching faces count as intersecting, matching how Vanilla resolves block collision. */
    public boolean intersects(Aabb other) {
        return min.x() <= other.max.x() && max.x() >= other.min.x()
                && min.y() <= other.max.y() && max.y() >= other.min.y()
                && min.z() <= other.max.z() && max.z() >= other.min.z();
    }

    public Aabb expand(double amount) {
        Vec3 delta = new Vec3(amount, amount, amount);
        return new Aabb(min.minus(delta), max.plus(delta));
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/physics/CollisionSpace.java`:

```java
package net.elytrarace.api.physics;

import net.elytrarace.api.math.Aabb;

import java.util.List;

/**
 * Read-only block-collision access, defined from the consumer's need rather than the provider's
 * capability.
 *
 * <p>The physics simulation is handed a collision space; it never looks one up. That is what keeps
 * the physics module free of any server dependency and lets the trace suite supply a recorded world
 * slice instead of a live instance.
 */
@FunctionalInterface
public interface CollisionSpace {

    /** Returns every solid box intersecting the region, in unspecified order. */
    List<Aabb> boxesIntersecting(Aabb region);

    /** A space containing nothing, for tests that only exercise free flight. */
    static CollisionSpace empty() {
        return region -> List.of();
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/physics/package-info.java`:

```java
@NotNullByDefault
package net.elytrarace.api.physics;

import org.jetbrains.annotations.NotNullByDefault;
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :voyager-api:test`
Expected: PASS, 15 tests total.

- [ ] **Step 5: Commit**

```bash
git add voyager-api/src
git commit -m "feat(api): add Aabb and the CollisionSpace port

CollisionSpace is the narrow query the physics simulation is handed instead of a
world reference, so the simulation stays a pure function and the trace suite can
supply a recorded world slice."
```

---

### Task 4: `FlightState` and `FlightInput` with Vanilla numeric fidelity

**Files:**
- Create: `voyager-api/src/main/java/net/elytrarace/api/physics/FlightState.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/physics/FlightInput.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/physics/NonFiniteRotationException.java`
- Create: `voyager-api/src/main/java/net/elytrarace/api/physics/InvalidFlightInputException.java`
- Test: `voyager-api/src/test/java/net/elytrarace/api/physics/FlightStateTest.java`

**Interfaces:**
- Consumes: `Vec3` from Task 2.
- Produces: `FlightState(Vec3 position, Vec3 velocity, float yaw, float pitch, boolean onGround)` and `FlightInput(float yaw, float pitch, boolean fireworkBoostActive, int fireworkTicksRemaining)`. E2's simulator signature is `FlightState tick(FlightState previous, FlightInput input, CollisionSpace space)`.

The rotation components are `float` and the position and velocity components are `double`, because Vanilla holds them that way. Computing rotation in `double` because it is "more accurate" produces reproducible drift against the traces. One test guards this at the type level so a later refactor cannot widen them silently.

- [ ] **Step 1: Write the failing test**

Create `voyager-api/src/test/java/net/elytrarace/api/physics/FlightStateTest.java`:

```java
package net.elytrarace.api.physics;

import net.elytrarace.api.math.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightStateTest {

    private static Class<?> componentType(Class<?> record, String name) {
        return Arrays.stream(record.getRecordComponents())
                .filter(component -> component.getName().equals(name))
                .map(RecordComponent::getType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no record component named " + name));
    }

    @Test
    void rotationIsFloatAndPositionIsDoubleToMatchVanilla() {
        assertThat(componentType(FlightState.class, "yaw")).isEqualTo(float.class);
        assertThat(componentType(FlightState.class, "pitch")).isEqualTo(float.class);
        assertThat(componentType(FlightInput.class, "yaw")).isEqualTo(float.class);
        assertThat(componentType(FlightInput.class, "pitch")).isEqualTo(float.class);
        assertThat(componentType(FlightState.class, "position")).isEqualTo(Vec3.class);
        assertThat(componentType(FlightState.class, "velocity")).isEqualTo(Vec3.class);
    }

    @Test
    void holdsAFiniteState() {
        FlightState state = new FlightState(Vec3.ZERO, new Vec3(0, -0.08, 0), 90.0f, -12.5f, false);

        assertThat(state.velocity().y()).isEqualTo(-0.08);
        assertThat(state.pitch()).isEqualTo(-12.5f);
        assertThat(state.onGround()).isFalse();
    }

    @Test
    void rejectsNonFiniteRotation() {
        assertThatThrownBy(() -> new FlightState(Vec3.ZERO, Vec3.ZERO, Float.NaN, 0f, false))
                .isInstanceOf(NonFiniteRotationException.class);
        assertThatThrownBy(() -> new FlightInput(0f, Float.POSITIVE_INFINITY, false, 0))
                .isInstanceOf(NonFiniteRotationException.class);
    }

    @Test
    void rejectsNegativeFireworkTicks() {
        assertThatThrownBy(() -> new FlightInput(0f, 0f, true, -1))
                .isInstanceOf(InvalidFlightInputException.class);
    }

    @Test
    void acceptsAnInactiveBoostWithZeroTicks() {
        assertThat(new FlightInput(0f, 0f, false, 0).fireworkBoostActive()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-api:test --tests "net.elytrarace.api.physics.FlightStateTest"`
Expected: FAIL — compilation error, `cannot find symbol: class FlightState`.

- [ ] **Step 3: Write the implementation**

Create `voyager-api/src/main/java/net/elytrarace/api/physics/NonFiniteRotationException.java`:

```java
package net.elytrarace.api.physics;

/** Thrown when a yaw or pitch value is NaN or infinite. */
public final class NonFiniteRotationException extends RuntimeException {

    public NonFiniteRotationException(float yaw, float pitch) {
        super("rotation must be finite, was yaw=" + yaw + " pitch=" + pitch);
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/physics/InvalidFlightInputException.java`:

```java
package net.elytrarace.api.physics;

/** Thrown when flight input violates an invariant that no client can legitimately produce. */
public final class InvalidFlightInputException extends RuntimeException {

    public InvalidFlightInputException(String message) {
        super(message);
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/physics/FlightState.java`:

```java
package net.elytrarace.api.physics;

import net.elytrarace.api.math.Vec3;

/**
 * The complete state of one gliding entity at a tick boundary.
 *
 * <p>Rotation is {@code float} and position and velocity are {@code double}, mirroring Vanilla's own
 * numeric types. Widening rotation to {@code double} looks harmless and produces reproducible drift
 * against the recorded traces.
 */
public record FlightState(Vec3 position, Vec3 velocity, float yaw, float pitch, boolean onGround) {

    public FlightState {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new NonFiniteRotationException(yaw, pitch);
        }
    }
}
```

Create `voyager-api/src/main/java/net/elytrarace/api/physics/FlightInput.java`:

```java
package net.elytrarace.api.physics;

/** The per-tick input driving a simulated glide. */
public record FlightInput(float yaw, float pitch, boolean fireworkBoostActive, int fireworkTicksRemaining) {

    public FlightInput {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new NonFiniteRotationException(yaw, pitch);
        }
        if (fireworkTicksRemaining < 0) {
            throw new InvalidFlightInputException(
                    "fireworkTicksRemaining must be >= 0, was " + fireworkTicksRemaining);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-api:test`
Expected: PASS, 20 tests total.

- [ ] **Step 5: Commit**

```bash
git add voyager-api/src
git commit -m "feat(api): add FlightState and FlightInput

Rotation stays float and position stays double, mirroring Vanilla's numeric
types. A reflection test guards the component types so a later refactor cannot
widen them and drift against the traces."
```

---

### Task 5: `voyager-fitness` and the API purity rules

**Files:**
- Create: `voyager-fitness/build.gradle.kts`
- Create: `voyager-fitness/src/test/java/net/elytrarace/fitness/ApiPurityTest.java`
- Create: `voyager-fitness/src/test/java/net/elytrarace/fitness/DesignRuleTest.java`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: the `:voyager-api` project from Task 1.
- Produces: the Gradle project `:voyager-fitness`. Every later epic adds its module as a dependency here and its rules to this module.

- [ ] **Step 1: Write the failing test**

Create `voyager-fitness/src/test/java/net/elytrarace/fitness/ApiPurityTest.java`:

```java
package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * voyager-api is the module every other module depends on, so anything it drags in is dragged in
 * everywhere. These rules are the reason the dependency direction in the module graph holds.
 */
@AnalyzeClasses(packages = "net.elytrarace", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiPurityTest {

    @ArchTest
    static final ArchRule apiDoesNotDependOnMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.minestom..")
                    .because("Minestom types belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("ports live in the api module, ORM technology does not")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnADiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "jakarta.inject..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotPerformFileIo =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..")
                    .because("voyager-api declares configuration types and never loads them")
                    .allowEmptyShould(false);
}
```

Create `voyager-fitness/src/test/java/net/elytrarace/fitness/DesignRuleTest.java`:

```java
package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "net.elytrarace", importOptions = ImportOption.DoNotIncludeTests.class)
class DesignRuleTest {

    // ArchUnit 1.4.2 has beInterfaces() and beEnums() but no beRecords(), so the record case is
    // expressed through JavaClass.isRecord() directly.
    private static final ArchCondition<JavaClass> BE_A_RECORD_AN_INTERFACE_OR_AN_ENUM =
            new ArchCondition<>("be a record, an interface or an enum") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    if (!item.isRecord() && !item.isInterface() && !item.isEnum()) {
                        events.add(SimpleConditionEvent.violated(item,
                                item.getName() + " is neither a record, an interface nor an enum"));
                    }
                }
            };

    @ArchTest
    static final ArchRule exceptionsAreUncheckedAndDomainNamed =
            classes().that().haveSimpleNameEndingWith("Exception").and().areNotInterfaces()
                    .should().beAssignableTo(RuntimeException.class)
                    .because("rule 9 — domain exceptions are unchecked")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiTypesAreRecordsInterfacesOrEnums =
            classes().that().resideInAPackage("net.elytrarace.api..")
                    .and().areTopLevelClasses()
                    .and().haveSimpleNameNotEndingWith("Exception")
                    .and().haveSimpleNameNotEndingWith("package-info")
                    .should(BE_A_RECORD_AN_INTERFACE_OR_AN_ENUM)
                    .because("voyager-api carries interfaces, records, enums and exceptions only")
                    .allowEmptyShould(false);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-fitness:test`
Expected: FAIL — `Project 'voyager-fitness' not found in root project 'Voyager'`.

- [ ] **Step 3: Create the module**

Create `voyager-fitness/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
}

// This module exists to give ArchUnit a classpath containing every module of the rebuild.
// The audit of the tree being replaced found architecture rules that silently checked nothing,
// because they ran on a classpath that did not contain the modules they named.
dependencies {
    testImplementation(project(":voyager-api"))
    testImplementation(libs.archunit.junit5)
}
```

In `settings.gradle.kts`, after `include("voyager-api")`, add:

```kotlin
include("voyager-fitness")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-fitness:test`
Expected: PASS, 7 rules evaluated (5 purity, 2 design), none empty.

- [ ] **Step 5: Prove the rules actually bite**

Temporarily add this field to `voyager-api/src/main/java/net/elytrarace/api/math/Vec3.java`:

```java
    private static final java.nio.file.Path SMOKE_TEST = java.nio.file.Path.of(".");
```

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.ApiPurityTest"`
Expected: FAIL — `apiDoesNotPerformFileIo` reports the violation in `Vec3`.

Then remove the field again and re-run:

Run: `./gradlew :voyager-fitness:test`
Expected: PASS.

Do not commit the temporary field.

- [ ] **Step 6: Commit**

```bash
git add voyager-fitness settings.gradle.kts
git commit -m "test(fitness): add the architecture test module and API purity rules

voyager-fitness depends on every module of the rebuild so ArchUnit imports the
whole tree. Every rule declares allowEmptyShould(false): a rule that passes
because it matched nothing is the defect this module exists to prevent."
```

---

### Task 6: Enforce `@NotNullByDefault` on every package

**Files:**
- Modify: `voyager-fitness/build.gradle.kts`
- Create: `voyager-fitness/src/test/java/net/elytrarace/fitness/NullabilityConventionTest.java`

**Interfaces:**
- Consumes: the `:voyager-fitness` project from Task 5.
- Produces: the system property `voyager.sourceRoots`, a path-separated list of the `src/main/java` directories of every `voyager-*` module. Later epics get this enforcement for free by existing.

ArchUnit cannot see a `package-info.java` that carries only a source-retained annotation, so this rule walks the source tree instead. The build supplies the roots, so the rule extends to new modules automatically.

- [ ] **Step 1: Write the failing test**

Create `voyager-fitness/src/test/java/net/elytrarace/fitness/NullabilityConventionTest.java`:

```java
package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Rule 5 — nullability is the exception, not the default. */
class NullabilityConventionTest {

    @Test
    void everyPackageWithSourcesDeclaresNotNullByDefault() throws IOException {
        List<Path> roots = Arrays.stream(System.getProperty("voyager.sourceRoots", "").split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();

        assertThat(roots)
                .as("the build must supply voyager.sourceRoots; an empty list would pass vacuously")
                .isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> directories = Files.walk(root)) {
                directories.filter(Files::isDirectory).forEach(directory -> {
                    if (!containsSources(directory)) {
                        return;
                    }
                    Path packageInfo = directory.resolve("package-info.java");
                    if (!Files.exists(packageInfo)) {
                        offenders.add(root.relativize(directory) + " — no package-info.java");
                        return;
                    }
                    if (!readString(packageInfo).contains("@NotNullByDefault")) {
                        offenders.add(root.relativize(directory) + " — package-info.java lacks @NotNullByDefault");
                    }
                });
            }
        }

        assertThat(offenders).as("packages violating rule 5").isEmpty();
    }

    private static boolean containsSources(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".java") && !name.equals("package-info.java");
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.NullabilityConventionTest"`
Expected: FAIL — `the build must supply voyager.sourceRoots` — the property is not configured yet.

- [ ] **Step 3: Supply the source roots from the build**

Append to `voyager-fitness/build.gradle.kts`:

```kotlin
tasks.test {
    // Only the rebuild's modules. The tree being replaced does not satisfy this rule and is
    // deliberately not held to it; it is deleted at E7.
    systemProperty(
        "voyager.sourceRoots",
        rootProject.subprojects
            .filter { it.name.startsWith("voyager-") }
            .map { it.projectDir.resolve("src/main/java") }
            .filter { it.isDirectory }
            .joinToString(File.pathSeparator) { it.absolutePath }
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.NullabilityConventionTest"`
Expected: PASS. Tasks 1 to 4 already created a `package-info.java` for each of the three API packages.

- [ ] **Step 5: Prove the rule bites**

Temporarily rename one package-info file:

```bash
mv voyager-api/src/main/java/net/elytrarace/api/math/package-info.java \
   voyager-api/src/main/java/net/elytrarace/api/math/package-info.java.bak
```

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.NullabilityConventionTest"`
Expected: FAIL, listing `net/elytrarace/api/math — no package-info.java`.

Restore it:

```bash
mv voyager-api/src/main/java/net/elytrarace/api/math/package-info.java.bak \
   voyager-api/src/main/java/net/elytrarace/api/math/package-info.java
```

Run: `./gradlew :voyager-fitness:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add voyager-fitness
git commit -m "test(fitness): enforce @NotNullByDefault on every package

The rule walks the source roots the build supplies, because a source-retained
annotation on package-info is not visible to ArchUnit. New modules are covered
automatically by being named voyager-*."
```

---

### Task 7: The meta-rule — the fitness module must see every module

**Files:**
- Modify: `voyager-fitness/build.gradle.kts`
- Create: `voyager-fitness/src/test/java/net/elytrarace/fitness/FitnessCoverageTest.java`

**Interfaces:**
- Consumes: the `:voyager-fitness` project from Task 5.
- Produces: the system properties `voyager.allModules` and `voyager.fitnessDependencies`. This is the task that makes every later epic's rules unskippable.

This is the direct fix for the defect the audit found: the previous architecture suite ran on a classpath that did not contain four of the modules whose rules it declared, so those rules never ran and never failed.

- [ ] **Step 1: Write the failing test**

Create `voyager-fitness/src/test/java/net/elytrarace/fitness/FitnessCoverageTest.java`:

```java
package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture rules only constrain what is on the classpath. In the tree being replaced, rules were
 * declared for four modules that the test classpath never contained, so they passed without ever
 * evaluating anything. This test fails the build when a module is added and not wired in here.
 */
class FitnessCoverageTest {

    private static Set<String> property(String key) {
        return Arrays.stream(System.getProperty(key, "").split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toSet());
    }

    @Test
    void everyModuleOfTheRebuildIsOnTheFitnessClasspath() {
        Set<String> modules = property("voyager.allModules");
        Set<String> dependencies = property("voyager.fitnessDependencies");

        assertThat(modules).as("the build must supply voyager.allModules").isNotEmpty();
        assertThat(dependencies)
                .as("voyager-fitness must depend on every voyager-* module; add the missing ones to "
                        + "voyager-fitness/build.gradle.kts, then add their architecture rules")
                .containsExactlyInAnyOrderElementsOf(modules);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.FitnessCoverageTest"`
Expected: FAIL — `the build must supply voyager.allModules`.

- [ ] **Step 3: Supply both module lists from the build**

Append to the existing `tasks.test { ... }` block in `voyager-fitness/build.gradle.kts`:

```kotlin
    systemProperty(
        "voyager.allModules",
        rootProject.subprojects
            .filter { it.name.startsWith("voyager-") && it.name != project.name }
            .joinToString(",") { it.name }
    )

    systemProperty(
        "voyager.fitnessDependencies",
        configurations.testImplementation.get().dependencies
            .filterIsInstance<ProjectDependency>()
            .joinToString(",") { it.name }
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.FitnessCoverageTest"`
Expected: PASS. At this point the only module is `voyager-api`, and `voyager-fitness` depends on it.

- [ ] **Step 5: Prove the rule bites**

Temporarily add a module. Create `voyager-scratch/build.gradle.kts`:

```kotlin
plugins {
    id("voyager.java-conventions")
}
```

Add `include("voyager-scratch")` to `settings.gradle.kts`.

Run: `./gradlew :voyager-fitness:test --tests "net.elytrarace.fitness.FitnessCoverageTest"`
Expected: FAIL — the assertion reports `voyager-scratch` as missing from the fitness dependencies.

Remove the directory and the `include` line again:

```bash
rm -rf voyager-scratch
```

Remove `include("voyager-scratch")` from `settings.gradle.kts`.

Run: `./gradlew :voyager-fitness:test`
Expected: PASS.

- [ ] **Step 6: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — the new modules and the old tree both build and test green.

- [ ] **Step 7: Commit**

```bash
git add voyager-fitness
git commit -m "test(fitness): fail the build when a module escapes the architecture tests

Compares the set of voyager-* subprojects against the fitness module's own
project dependencies. The suite being replaced declared rules for four modules
its classpath never contained, so those rules evaluated nothing and passed."
```

---

### Task 8: Rewrite `CLAUDE.md`

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: everything the previous tasks established.
- Produces: the file every contributor and every agent reads first.

Decision D12 supersedes the current file rather than amending it. It is demonstrably wrong today: it names a `shared/phase` module that does not exist, a Minestom version that disagrees with `settings.gradle.kts`, and build commands for tests that were never written. It must describe both trees during coexistence, because both are in the repository until E7.

- [ ] **Step 1: Verify the claims before writing them down**

Run each and record the answer; the rewrite must match reality rather than intent:

```bash
grep -n 'version("minestom"' settings.gradle.kts
grep -n 'include(' settings.gradle.kts
ls plugins/game/src/test 2>/dev/null || echo "plugins/game has no tests"
ls docs/decisions/
```

- [ ] **Step 2: Replace the Project Overview and Module Structure sections**

Replace everything from the `## Project Overview` heading down to, but not including, `## Architecture` with:

```markdown
## Project Overview

Voyager is a Minecraft elytra racing minigame — fly through cups of maps, each map has rings that
give points. Multi-module Java project built with Gradle 9.5.1.

**The repository currently contains two trees.** The greenfield rebuild is being built alongside the
tree it replaces; both build, both test, and `main` stays green throughout. The design that governs
the rebuild is `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, and it — not this
file — is the authority for the design rules until the rebuild lands.

### The rebuild (`voyager-*`)

| Module | Contents |
|---|---|
| `voyager-api` | Interfaces, records, enums, exceptions. No implementation, no I/O. |
| `voyager-fitness` | Test-only. ArchUnit over every `voyager-*` module. |

Further modules — `voyager-physics`, `voyager-race`, `voyager-persistence`, `voyager-platform`,
`voyager-server`, `voyager-setup` — arrive in later stages. See the spec's delivery plan.

### The tree being replaced

`server`, `plugins/game`, `plugins/setup`, `shared/common`, `shared/conversation-api`,
`shared/database`, `shared/spline`. Do not add features here. It is deleted in one cut once the
rebuild reaches a flyable build with a green Vanilla trace suite.

## Key Decisions

- **Java**: 25 for every `voyager-*` module. Vanilla itself requires Java 25 since Minecraft 26.1.
- **Target**: Minecraft 26.2, Minestom `2026.08.28-26.2`. Mojang moved to calendar versioning in
  2026; there is no 1.22, it became 26.1.
- **Dependency injection**: `io.airlift:guice:10`, with DI annotations confined to the composition
  roots.
- **Commits**: Conventional Commits, no Co-Author line beyond the configured attribution.
- **Version Catalog**: declared programmatically in `settings.gradle.kts`. Do not add
  `gradle/libs.versions.toml`.

## Build Commands

```bash
./gradlew build                    # Everything, both trees
./gradlew :voyager-api:test        # The rebuild's API module
./gradlew :voyager-fitness:test    # Architecture rules over the whole rebuild
./gradlew :server:build            # The tree being replaced
```

Tests use JUnit 6 with AssertJ. Architecture rules live in `voyager-fitness` and are declared with
`allowEmptyShould(false)` — a rule that passes because it matched nothing is a defect, not a pass.
```

- [ ] **Step 3: Correct the ArchUnit and module-isolation section**

Find the `### ArchUnit Enforcement` line and replace that line and its following paragraph with:

```markdown
### ArchUnit Enforcement

Rules for the rebuild live in `voyager-fitness/src/test/java/net/elytrarace/fitness/`. That module
depends on every `voyager-*` module, and `FitnessCoverageTest` fails the build if a module is added
without being wired in — the previous suite declared rules for four modules its classpath never
contained, so they never ran.
```

- [ ] **Step 4: Verify no stale references remain**

Run:

```bash
grep -n "shared/phase\|2026.03.25\|ElytraRaceTest\|0002-elytra-flight-client-authority" CLAUDE.md
```

Expected: no output. If any line matches, remove or correct it.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: rewrite CLAUDE.md for the two-tree state

Per decision D12 the file is superseded rather than amended. It named a
shared/phase module that does not exist, a Minestom version disagreeing with
settings.gradle.kts, and build commands for tests that were never written.

It now describes both trees explicitly and points at the design spec as the
authority for the rebuild's rules."
```

---

## Definition of Done for E1

- [ ] `./gradlew build` is green with both trees present.
- [ ] `voyager-fitness` imports every `voyager-*` module, and `FitnessCoverageTest` fails if one is missing.
- [ ] No ArchUnit rule in `voyager-fitness` uses `allowEmptyShould(true)`.
- [ ] Every package in `voyager-api` has a `package-info.java` with `@NotNullByDefault`.
- [ ] `Vec3` cannot be constructed holding NaN or infinity, and the test proving it is green.
- [ ] `FlightState` and `FlightInput` carry `float` rotation and `Vec3` position, guarded by a test.
- [ ] The Java 25 toolchain is declared once in `buildSrc`, not per module.
- [ ] `CLAUDE.md` contains no reference to `shared/phase`, to Minestom `2026.03.25`, or to
      `ElytraRaceTest`.
