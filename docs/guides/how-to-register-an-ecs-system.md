# Register an ECS system

Use this guide when you add a new system to the game server's ECS. It covers the component contract, the registration call, and the ordering rules that existing systems rely on.

## Prerequisites

Before you begin:

- You can build and run the `server` module (`./gradlew :server:build`)
- You understand the ECS roles: `Entity` (container), `Component` (data), `System` (per-tick logic)
- Your behaviour owns mutable per-entity state that must live on the entity graph, not in an ad-hoc map

## Create the component

The component holds the data your system reads and writes. Implement the marker interface `net.elytrarace.common.ecs.Component`.

```java
package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;

public class ExampleComponent implements Component {

    private int ticksRemaining;

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
    }
}
```

If the component is mutated from the Netty event thread as well as the tick thread, use `AtomicBoolean`, `AtomicInteger`, or `volatile` fields. `FireworkBoostComponent` is the canonical example.

## Create the system

Implement `net.elytrarace.common.ecs.System`. Declare every component your system reads or writes in `getRequiredComponents()` — the `EntityManager` filters entities for you before calling `process`.

```java
package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ExampleComponent;

import java.util.Set;

public class ExampleSystem implements net.elytrarace.common.ecs.System {

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(ExampleComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var example = entity.getComponent(ExampleComponent.class);
        if (example.getTicksRemaining() > 0) {
            example.setTicksRemaining(example.getTicksRemaining() - 1);
        }
    }
}
```

## Attach the component to player entities

If the system runs for every player, add the component in `GameEntityFactory.createPlayerEntity`:

```java
public static Entity createPlayerEntity(Player player) {
    Entity entity = new Entity();
    entity.addComponent(new PlayerRefComponent(player.getUuid(), player));
    entity.addComponent(new ElytraFlightComponent());
    entity.addComponent(new FireworkBoostComponent());
    entity.addComponent(new HudComponent(player));
    entity.addComponent(new ExampleComponent());
    // ...
    return entity;
}
```

Standard per-player components attached by `createPlayerEntity` are:

- `PlayerRefComponent` — identity and `Player` reference
- `ElytraFlightComponent` — flight state and velocity
- `FireworkBoostComponent` — boost intent, cooldown, per-map boost config
- `HudComponent` — boss bar, actionbar, titles, ring-pass feedback
- `RingTrackerComponent` — which rings the player has passed
- `ScoreComponent` — ring points for the active map
- `RingEffectComponent` — active ring-triggered effects

If your system needs to reach one of these, declare it in `getRequiredComponents()` and read it via `entity.getComponent(...)` rather than injecting a side channel through the constructor.

If the system runs for the single game entity instead, add it in `GameEntityFactory.createGameEntity`.

## Register the system

Register systems in `GameOrchestrator.startGame()`. Order matters. The `EntityManager` calls systems in registration order each tick.

```java
entityManager.addSystem(new ElytraPhysicsSystem());
entityManager.addSystem(new FireworkBoostSystem());
entityManager.addSystem(new ExampleSystem());
entityManager.addSystem(new RingCollisionSystem(entityManager));
entityManager.addSystem(new OutOfBoundsSystem(entityManager, playerService));
```

The existing ordering rules are:

- `ElytraPhysicsSystem` runs first so every later system reads up-to-date velocity and position
- Input-driven systems (for example `FireworkBoostSystem`) run before collision systems so their velocity changes take effect in the same tick
- `RingCollisionSystem` runs before `OutOfBoundsSystem` so a player who crosses a ring on the same tick as the boundary still scores the ring

Place your system at the point in the pipeline that matches what it reads and what it writes.

## Push per-map configuration

If the system needs per-map configuration, update each entity's component when a new map loads. `GameOrchestrator.loadNextMap()` already iterates entities for `FireworkBoostComponent`:

```java
for (Entity entity : entityManager.getEntities()) {
    var boostComp = entity.getComponent(FireworkBoostComponent.class);
    if (boostComp != null) {
        boostComp.setBoostConfig(mapDef.boostConfig());
    }
}
```

Do not hold map-specific state inside the system itself. The system must be pure per-tick logic over component data.

## Verify

Build the server module and start it:

```shell
$ ./gradlew :server:build
$ java -jar server/build/libs/*.jar
```

On game start, the log lists every registered system indirectly through `GameOrchestrator`:

```plaintext
Starting game for cup 'demo-cup' with 3 maps
Game started with 1 players
```

Join the server and trigger the behaviour your system implements. Confirm the component state changes as expected and that no `NullPointerException` is thrown — a missing `addComponent` call in `GameEntityFactory` is the most common cause.

## Related topics

- [Firework boost system](../reference/firework-boost.md)
- [HUD component](../reference/hud.md)
- [ADR-0001: Move firework boost logic into the ECS](../decisions/0001-firework-boost-in-ecs.md)
- [ADR-0002: Move HUD state into an ECS component](../decisions/0002-hud-as-ecs-component.md)
