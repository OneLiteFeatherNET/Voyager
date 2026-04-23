# HUD component

This reference documents `HudComponent`, the ECS component that owns all in-game HUD state for one player: the cup-progress boss bar, actionbar text, title overlays, and ring-pass feedback.

## HudComponent

Attached to every player entity by `GameEntityFactory.createPlayerEntity`. All methods run on the tick thread (ECS systems or map-load callbacks that run on the Minestom scheduler thread), so the component holds no concurrent data structures.

Fully qualified name: `net.elytrarace.server.ecs.component.HudComponent`

### Fields

| Field | Type | Description |
|---|---|---|
| `player` | `Player` | The Minestom player that this component renders HUD elements for. Set in the constructor and never reassigned. |
| `cupProgressBar` | `BossBar` | The currently shown cup-progress boss bar, or `null` if none is shown. Replaced on every `showCupProgress` call. |

### Constructor

#### `HudComponent(Player player)`

Creates a HUD component bound to one player.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `player` | `Player` | The Minestom player that owns this HUD |

### Methods

#### `updateActionbar(double speedBlocksPerSec, int currentPoints)`

Sends the speed and score actionbar line. Called from `ScoreDisplaySystem` every 4 ticks while the player is flying.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `speedBlocksPerSec` | `double` | Current speed in blocks per second. Displayed with one decimal place. |
| `currentPoints` | `int` | Accumulated ring points for the player |

**Returns:** `void`

**Example:**

```java
var hud = entity.getComponent(HudComponent.class);
hud.updateActionbar(42.7, 180);
```

The rendered line is:

```plaintext
Speed: 42.7 m/s | Points: 180
```

#### `showCupProgress(String cupName, int currentMap, int totalMaps)`

Shows or replaces the cup-progress boss bar. The progress ratio is `currentMap / totalMaps`. If a boss bar is already shown, it is hidden first.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `cupName` | `String` | The current cup's display name |
| `currentMap` | `int` | 1-based index of the active map in the cup |
| `totalMaps` | `int` | Total number of maps in the cup |

**Returns:** `void`

#### `showMapTitle(String mapName)`

Shows the map name as a title overlay. Timing: 500 ms fade in, 2 s stay, 500 ms fade out. The map name is rendered in gold.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `mapName` | `String` | The map name to display |

**Returns:** `void`

#### `showCountdown(int seconds)`

Shows a countdown title. Numbers are color-coded: green for 3, yellow for 2, red for 1. When `seconds` is 0, the title shows `GO!` in bold red. Timing: no fade in, 900 ms stay, 100 ms fade out.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `seconds` | `int` | Remaining seconds. `0` renders `GO!`. |

**Returns:** `void`

#### `showRingPassed(int points)`

Shows ring-pass feedback: a bold green `+N` actionbar line plus the experience-orb pickup sound at pitch 1.5. Called by `RingCollisionSystem` when a player passes a ring.

**Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `points` | `int` | Points awarded for the ring. Prefixed with `+` in the actionbar. |

**Returns:** `void`

#### `cleanup()`

Hides the cup-progress boss bar if one is shown and clears the internal reference. Call this before removing the entity, so the boss bar does not linger on the player's screen.

**Returns:** `void`

## Thread model

`HudComponent` is written only from the tick thread. The Minestom scheduler runs `GameOrchestrator.loadNextMap()` and all ECS `process()` calls on the tick thread, so there is no cross-thread access to synchronise.

This contrasts with `FireworkBoostComponent`, which is written from both the Netty thread (intent signal) and the tick thread (cooldown and impulse). `HudComponent` has no Netty-side writer, so no `AtomicBoolean` or `volatile` field is needed.

## Call sites

| Method | Caller | When |
|---|---|---|
| `updateActionbar` | `ScoreDisplaySystem.process` | Every 4 ticks while the player is flying, and only when displayed values change |
| `showCupProgress` | `GameOrchestrator.loadNextMap` | Once per entity on every map load |
| `showMapTitle` | `GameOrchestrator.loadNextMap` | Once per entity on every map load |
| `showCountdown` | Lobby phase (reserved; no current caller) | When a countdown tick fires |
| `showRingPassed` | `RingCollisionSystem.process` | When a passthrough is detected on a ring |
| `cleanup` | `GameOrchestrator.restartGame`, `GameOrchestrator.cleanup` | Before removing a player entity, and when the orchestrator tears down |

## Related topics

- [ADR-0002: Move HUD state into an ECS component](../decisions/0002-hud-as-ecs-component.md)
- [Register an ECS system](../guides/how-to-register-an-ecs-system.md)
- [Firework boost system](firework-boost.md)
