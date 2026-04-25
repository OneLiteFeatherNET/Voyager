---
name: create-phase
description: Scaffold a new game phase for the Minestom server. Use when extending the game flow (e.g., Countdown, Results, Intermission).
---

# Create Game Phase

Add a new phase to the Minestom server game flow following project conventions.

## Input

- Phase name (e.g., "Countdown", "Results", "Intermission")
- Phase position in the sequence relative to the existing flow (Lobby -> Game -> End)
- What happens in `onStart()` and `onFinish()`
- Whether the phase is time-based (`TimedPhase`) or tick-driven (`TickingPhase`)

## Decision: Which base class to extend?

| Base class | When to use |
|---|---|
| `TimedPhase` | Phase counts down or up over a fixed duration and auto-finishes (e.g., Lobby, End) |
| `TickingPhase` | Phase runs every tick indefinitely until code calls `finish()` (e.g., Game) |

Both live in `net.theevilreaper.xerus.api.phase`. Read `MinestomLobbyPhase` for a `TimedPhase` example and `MinestomGamePhase` for a `TickingPhase` example before writing new code.

## Steps

### 1. Read existing phases to understand the pattern

Read at least one of these before writing any code:

- `/mnt/projects/oss/onelitefeather/Voyager/server/src/main/java/net/elytrarace/server/phase/MinestomLobbyPhase.java` — `TimedPhase` example (countdown, actionbar UI)
- `/mnt/projects/oss/onelitefeather/Voyager/server/src/main/java/net/elytrarace/server/phase/MinestomEndPhase.java` — `TimedPhase` example (results display, server stop)
- `/mnt/projects/oss/onelitefeather/Voyager/server/src/main/java/net/elytrarace/server/phase/MinestomGamePhase.java` — `TickingPhase` example (ECS loop, manual finish condition)

Also read `GamePhaseFactory` before registering:

- `/mnt/projects/oss/onelitefeather/Voyager/server/src/main/java/net/elytrarace/server/phase/GamePhaseFactory.java`

### 2. Create the phase file

Path: `server/src/main/java/net/elytrarace/server/phase/Minestom{Name}Phase.java`

**TimedPhase template** (countdown that auto-finishes):

```java
package net.elytrarace.server.phase;

import net.minestom.server.utils.time.TimeUnit;
import net.theevilreaper.xerus.api.phase.TickDirection;
import net.theevilreaper.xerus.api.phase.TimedPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// One-line comment here only if the phase purpose is non-obvious.
public final class Minestom{Name}Phase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(Minestom{Name}Phase.class);
    private static final int DEFAULT_{NAME}_TICKS = /* sensible default */;

    private final int durationTicks;
    private final Runnable onFinishCallback;
    private boolean finishing = false;

    public Minestom{Name}Phase() {
        this(DEFAULT_{NAME}_TICKS, null);
    }

    public Minestom{Name}Phase(int durationTicks, Runnable onFinishCallback) {
        super("{name}", TimeUnit.SERVER_TICK, 20);
        this.durationTicks = durationTicks;
        this.onFinishCallback = onFinishCallback;
        setEndTicks(0);
        setCurrentTicks(durationTicks);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void onStart() {
        finishing = false;
        setCurrentTicks(durationTicks);
        super.onStart();
        LOGGER.info("{Name} phase started — duration {} ticks", durationTicks);
    }

    @Override
    public void onUpdate() {
        PhaseUiHelper.broadcastTimeActionBar("phase.{name}.time", getCurrentTicks());
    }

    @Override
    public void finish() {
        if (finishing) return;
        finishing = true;
        super.finish();
    }

    @Override
    protected void onFinish() {
        LOGGER.info("{Name} phase finished");
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }
}
```

**TickingPhase template** (runs every tick, finishes on a condition):

```java
package net.elytrarace.server.phase;

import net.minestom.server.utils.time.TimeUnit;
import net.theevilreaper.xerus.api.phase.TickingPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// One-line comment here only if the phase purpose is non-obvious.
public final class Minestom{Name}Phase extends TickingPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(Minestom{Name}Phase.class);

    private final Runnable onFinishCallback;
    private boolean finishing = false;

    public Minestom{Name}Phase(Runnable onFinishCallback) {
        super("{name}", TimeUnit.SERVER_TICK, 1);
        this.onFinishCallback = onFinishCallback;
    }

    @Override
    public void onStart() {
        finishing = false;
        super.onStart();
        LOGGER.info("{Name} phase started");
    }

    @Override
    public void onUpdate() {
        // Per-tick logic here. Call finish() when the exit condition is met.
        if (/* exit condition */) {
            finish();
        }
    }

    @Override
    public void finish() {
        if (finishing) return;
        finishing = true;
        LOGGER.info("{Name} phase finished");
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
        super.finish();
    }
}
```

### 3. Register the phase in GamePhaseFactory

Open `/mnt/projects/oss/onelitefeather/Voyager/server/src/main/java/net/elytrarace/server/phase/GamePhaseFactory.java`.

Add the new phase to the `List.of(...)` in the appropriate position inside `createGamePhases(...)`. Preserve the existing phase order and add overloaded factory methods if the new phase requires additional constructor arguments.

Example — inserting a `Countdown` phase between Lobby and Game:

```java
var lobby = new MinestomLobbyPhase(120, onMapSwitch);
var countdown = new MinestomCountdownPhase(60, /* callback */);  // new
var game = new MinestomGamePhase(entityManager,
        MinestomGamePhase.DEFAULT_RACE_DURATION_TICKS, onGamePhaseFinished);
var end = new MinestomEndPhase(100, entityManager, gameResultPersistence);
return new LinearPhaseSeries<>("game-phases", List.of(lobby, countdown, game, end));
```

Update the `@param` Javadoc of the factory method to document any new callback parameter.

### 4. Write JUnit 5 tests

Path: `server/src/test/java/net/elytrarace/server/phase/Minestom{Name}PhaseTest.java`

Minimum test coverage:

```java
package net.elytrarace.server.phase;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Minestom{Name}PhaseTest {

    @Test
    void phaseNameIsCorrect() {
        var phase = new Minestom{Name}Phase();
        assertThat(phase.getName()).isEqualTo("{name}");
    }

    @Test
    void onStartCallbackIsInvokedOnFinish() {
        var[] called = {false};
        var phase = new Minestom{Name}Phase(/* ticks */, () -> called[0] = true);
        phase.onStart();
        phase.finish();
        assertThat(called[0]).isTrue();
    }

    @Test
    void finishIsIdempotent() {
        var[] count = {0};
        var phase = new Minestom{Name}Phase(/* ticks */, () -> count[0]++);
        phase.onStart();
        phase.finish();
        phase.finish();
        assertThat(count[0]).isEqualTo(1);
    }
}
```

Also update `GamePhaseFactoryTest` to assert the new phase appears in the series at the correct index.

### 5. Build and verify

Run `/build` and confirm there are no compilation errors or test failures.

## Rules

- Class naming is always `Minestom{Name}Phase` — no exceptions.
- Package is always `net.elytrarace.server.phase`.
- Never import `org.bukkit.*` — this is a Minestom server module.
- The `finishing` guard (`if (finishing) return;`) is mandatory in every `finish()` override to prevent double-execution during phase transitions.
- Use `PhaseUiHelper.broadcastTimeActionBar(key, ticks)` for actionbar countdowns — do not call `player.sendActionBar(...)` directly in phase code.
- User-facing strings use `Component.translatable("phase.{name}.something")` — never hardcoded English text.
- Every new phase must be registered in `GamePhaseFactory` before the skill is considered done.
- Add a class-level Javadoc comment only when the phase purpose is not obvious from its name and the template comment above.

## Output

- `server/src/main/java/net/elytrarace/server/phase/Minestom{Name}Phase.java`
- Updated `server/src/main/java/net/elytrarace/server/phase/GamePhaseFactory.java`
- `server/src/test/java/net/elytrarace/server/phase/Minestom{Name}PhaseTest.java`
- Updated `server/src/test/java/net/elytrarace/server/phase/GamePhaseFactoryTest.java`
