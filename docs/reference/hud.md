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

## Reset feedback

When a course reset sends a racer back, `platform.hud.RaceFeedback.reset` shows one title and plays one sound in the tick the reset is detected. The rules that trigger a reset are in [course-reset.md](course-reset.md).

The title names the reason: `voyager.reset.title.outofbounds` or `voyager.reset.title.landed`. The subtitle names the target: `voyager.reset.target.ring` with the one-based number of the last ring passed, or `voyager.reset.target.start`. The title has no fade-in, as every race title does.

The sound is `block.note_block.chime` at pitch 0.7 and volume 0.8, on the master channel. It falls, so a racer who has been sent back hears the difference without reading. It is not the countdown beat (`block.note_block.bass`), and a ring pass plays a rising bell.

## Race boss bar and action bar

The rebuild's race HUD is `RaceHud` (`voyager-platform`, `platform/hud`). It refreshes both lines at 10 Hz and names every value.

| Surface | Shows | Colour | Example (English, `de_DE` in brackets) |
|---|---|---|---|
| Boss bar title | `Map <n>/<count> · <map display name> · <tier phrase>`, the tier phrase being the medal band and the time left in it | The map is dim; the tier phrase takes the band's colour | `Map 1/2 · Blue & Red · Diamond · 57.9 s left` (`Map 1/2 · Blue & Red · Diamant · noch 57.9 s`) |
| Boss bar fill | The fraction of the rings passed | One fixed colour, `GREEN`, for every band | 12 of 35 rings passed: 12/35 |
| Action bar | The race clock and the ring counter, each labelled | The labels are dim; the clock is white; the counter is white, and green for 8 ticks after a ring | `Time 0:02.1 · Rings 0/35` (`Zeit 0:02.1 · Ringe 0/35`) |

The finish band has no countdown, so its tier phrase is the name alone (`Finish`, `Ziel`). Times keep the dot as the decimal
separator in both locales (ADR-0038). Every text colour reads at 4.5 to 1 or more on the dark backdrops (`PaletteContrastTest`).

## Missed ring

A ring is missed when the racer passes the ring after it while the ring is still the next one expected. The rule is in `voyager-race` (`ProgressTracker`), and it is reported once per run: a second crossing of the same pair, or a reset that keeps the progress, reports nothing more. Progression does not change: the racer must still pass the missed ring in order.

The cue, in the tick the skip is detected (`RaceHud.missedRing`):

- a quiet sound, `block.note_block.didgeridoo` at pitch 0.5 and volume 0.5, from `RaceFeedback.missedRingSound`;
- the action bar line `Ring <n> missed` in the warn colour, numbered from one, for 30 ticks (1.5 s). It replaces the flight line for that span, and the clock returns on the first refresh after it.

There is no proximity cue. A near miss needs a distance test against the ring plane that the course data does not yet provide.

## Results and joins

- The map result: the breakdown is in chat. The title is the medal word. The subtitle is the next goal in seconds, for example `0.8 s faster for Gold`, and it is omitted for Diamond. A DNF's subtitle is `23 of 35 rings`.
- A join: the first join of a burst is told at once. Joins after it within 60 game ticks (3 s) are told as one line at the window's close, with the count. The window is counted in ticks by the waiting room.

## Sounds

Each meaning has its own sound, and the countdown beat is for a countdown digit only. The table of candidates, which the owner's playtest tunes, is in [the message style guide](message-style-guide.md#sound).

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

## Lobby displays

The rebuild's lobby shows the cup that waiting racers are about to play. Three displays read one board, `LobbyBoard`, so they
always name the same cup and the same maps in play order (`platform/lobby`, ADR-0033).

| Display | Shown when | Text |
|---|---|---|
| Sidebar | The room is waiting and the racer stands in the place racers wait: the lobby instance, or the first map with no lobby. Hidden at `RUNNING` and when the racer enters a map. | The cup's name as the title, then one line per map, `1. <map>`. Twelve maps at most; a longer cup ends with `+<n> more`. |
| Hologram | `VOYAGER_LOBBY_HOLOGRAM` names a position and a lobby world is set. One text display, spawned at boot, never saved with the lobby. | The same lines as the sidebar, the cup's name on the first line. |
| Boss bar | Waiting and countdown, as before. | `Cup <name> · waiting for racers: <online> of <needed> online`, or `Cup <name> · starts in <n> s`. |

The cup shown is the cup the catalogue currently holds. A reload that is pending does not change it until a round starts and
promotes it. A countdown keeps the cup it began with.

## Related topics

- [ADR-0002: Move HUD state into an ECS component](../decisions/0002-hud-as-ecs-component.md)
- [Register an ECS system](../guides/how-to-register-an-ecs-system.md)
- [Firework boost system](firework-boost.md)
