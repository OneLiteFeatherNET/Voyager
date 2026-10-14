# Design

Status: approved on 2026-10-13. Section 10 records the owner's answers, which govern where the text above differs from them.
Every file and class named here is part of this change.

## 1. Owner decisions (2026-10-12)

The owner's two decisions are in `proposal.md`. The answers to the questions in section 10 are recorded there by task 1.1.

## 2. Investigation

Everything below was read from the repository and from the cached artifacts named in each line. None of it was run on a
node.

### 2.1 CloudNet RC17: how a player is sent to the fallback

- The bridge API (`bridge-api` 4.0.0-RC17, sources jar in the Gradle cache) has `PlayerManager.playerExecutor(UUID)`,
  which returns a `PlayerExecutor`. The bridge's javadoc says the manager is obtained with
  `ServiceRegistry.defaultInstance(PlayerManager.class)` (`eu.cloudnetservice.driver.registry.ServiceRegistry`, driver-api
  RC17).
- `PlayerExecutor` has four ways to move a player: `connect(String service)`, `connectSelecting(ServerSelectorType)`,
  `connectToGroup(group, selector)`, `connectToTask(task, selector)`. The one that matches "the network hub or fallback" is
  **`connectToFallback()`**, which is documented as "the best matching fallback". It returns `void`, so a failure is not
  reported to the caller. Which fallback is chosen comes from the proxy's `ProxyFallbackConfiguration` (`ProxyFallback`
  entries in the bridge configuration), so the operator's proxy groups decide the target.
- `PlayerExecutor.kick(Component)` exists. It is the hard kick the owner rejected for the normal path.
- **Cygnus has no precedent for this call.** Its only CloudNet code is `bridge/.../CygnusBridgePermissionExtension.java`,
  which registers a permission checker with the same `ServiceRegistry` pattern as this change's bridge. A grep of the
  Cygnus tree finds no `connectToFallback`, `connectTo*` or `PlayerManager` use. The RC17 API is the reference.

### 2.2 The extension class-loader boundary (fork 2.2.0)

Read from `minestom-extensions-2.2.0-sources.jar` (`net.onelitefeather:minestom-extensions`, the version the roots pin):

- `ExtensionClassLoader` is created with `super("Ext_" + name, urls, MinecraftServer.class.getClassLoader())`. The
  parent is the application class loader, which holds the root's fat jar when the server runs as `java -jar`.
- `ExtensionClassLoader.loadClass` calls `super.loadClass(name, resolve)` first, which is parent-first delegation. Only when
  the parent cannot find the class does it ask its child loaders, which are the loaders of the extensions this one depends on
  (`addChild`). `CloudNet_Bridge` is therefore visible from the bridge's loader, as the bridge depends on it.
- Consequence: a `voyager-api` class loaded by the application loader is the same `Class` object inside the extension. A
  static field in that class is one shared value. Cygnus's extension says the same thing in its class comment ("Minestom and
  Adventure come from the application classloader above").
- This was read from source, not observed. Task 2.1 is a node spike that proves the identity before any code depends on it.

### 2.3 Where the cup ends

- `RaceStateMachine.advanceEnd` sets `cupFinished` after the `END` phase of the last map has lasted `RaceTimings.endAfterLastMap`
  (20 s in production). `XerusPhaseDriver.onUpdate` calls `finish()` on that state.
- `CupSession.advancePhase` announces the cup result on the tick `cupFinished` first becomes true (`announcer.announceCupResult`).
  The standings therefore appear at the same tick the cup is finished, and nothing in the current code holds the racers after
  that. A handoff on that tick would take players away before they can read the standings. The handoff needs a linger after
  the announcement. This is the reason for `RESULTS_LINGER` in decision D4.
- `WaitingRoom.tick` today returns racers to the lobby on the tick `running && !session.running() && session.cupFinished()`. That is the
  branch this change replaces.

### 2.4 The lobby HUD now

- `WaitingRoom` (platform/lobby) decides start, cancel, abort and hold each tick, and calls `announce` for the boss bar.
- `LobbyHud` (platform/lobby) owns one `BossBar` per racer and shows it only when the racer is in the lobby instance (or in the
  first map when no lobby exists).
- `Messages` (platform/text) builds the lobby lines. The boss bar keys are `voyager.lobby.bossbar.waiting` and
  `voyager.lobby.bossbar.countdown` (in `voyager/server/src/main/resources/voyager_en_US.properties`). The join line is
  `voyager.lobby.player.join`.
- `RaceHud` (platform/hud) leaves the sidebar off deliberately, so a race shows no scoreboard. A lobby sidebar must be hidden the
  moment a racer enters a map.

### 2.5 Minestom

- The Minestom jar in the cache (`net.minestom:minestom:2026.10.07-26.2`) has `net.minestom.server.scoreboard.Sidebar` and
  `net.minestom.server.entity.metadata.display.TextDisplayMeta` (with `Alignment`). Both exist; the hologram and the sidebar use
  them.

### 2.6 The catalogue at start

- `CatalogHolder.promoteForNewRound()` promotes a pending catalogue to current at a round start. The owner's reload rule
  (`voyager.command.reload.pending`) says changed cups play from the next round. The lobby shows `CatalogHolder.current()`
  (decision D7, owner answer Q6): a pending reload is not shown until a round promotes it, and a countdown keeps its cup.

## 3. The rendezvous: how the extension gives the server a handoff

The server cannot name a CloudNet type (`CloudNetBoundaryTest`). The extension cannot be called by the server directly. So a
value must cross the extension boundary. The options:

| Option | How | Verdict |
|---|---|---|
| A. Typed port in `voyager-api`, held in a static slot there, installed by the bridge | `PlayerHandoff` and `PlayerHandoffs` in `api.handoff`. The bridge calls `PlayerHandoffs.install(...)`; the platform calls `PlayerHandoffs.current()`. Works because of parent-first delegation (section 2.2). | **Chosen.** Typed, testable with a fake, one static slot, the bridge names exactly one Voyager package. Needs the owner to amend the greenfield design paragraph and ADR-0026. |
| B. JDK-only rendezvous | The root stores a `java.util.function.Consumer<UUID>` in a JDK-owned place (for example `System.getProperties()`) and the bridge reads it back. The bridge names no Voyager type. | Keeps the greenfield rule exactly. Untyped, stringly keyed, and nothing checks it. Kept as the fallback if the owner rejects A. |
| C. `ServiceLoader` from the extension's loader | The bridge jar ships `META-INF/services/…PlayerHandoff`. The root finds the extension with `MinecraftServer.getExtensionManager().getExtension("VoyagerCloudNetPermissions")` and loads the service with that extension's class loader. | Needs the root to know an extension name and reach its loader. The service interface still has to be in `voyager-api`, so it is A with more moving parts. Rejected. |
| D. Minestom event | The root calls `MinecraftServer.getGlobalEventHandler().call(event)`, and the bridge handles it. | The event type has to be named by both sides, which again means a shared Voyager type, and it is an event the server would fire at cup end. Rejected. |

Decision D1: option A, confirmed by the owner (Q1). The bridge names exactly one Voyager package, `net.elytrarace.voyager.api.handoff`,
and the fitness rule in section 6 enforces it.

## 4. Types, modules and the dependency direction

```
          voyager-server (composition root)
            ServerSettings, ConfigCheck, VoyagerServer (boot: hologram)
                 |                                  |
                 v                                  v
   voyager-platform                        voyager-cloudnet-bridge
   platform.handoff, platform.lobby,       (extension, child loader; compileOnly CloudNet,
   platform.text, platform.cup             compileOnly voyager-api; never bundled)
                 |                                  |
                 +------------> voyager-api <-------+
                                api.handoff: PlayerHandoff, PlayerHandoffs,
                                             exception.HandoffUnavailableException
```

| New type | Module | Package | Notes |
|---|---|---|---|
| `PlayerHandoff` (interface) | api | `net.elytrarace.voyager.api.handoff` | `void toHub(UUID playerId) throws HandoffUnavailableException`. Pure. |
| `PlayerHandoffs` (holder) | api | `net.elytrarace.voyager.api.handoff` | Static slot for one `PlayerHandoff`: `install`, `uninstall`, `current()` returning `Optional`. An `AtomicReference`. It is an exception to the api rule "no implementation", and the design rule 3 sealed-registry shape does not fit a single slot. Confirmed by the owner (section 10, Q1). |
| `HandoffUnavailableException` | api | `net.elytrarace.voyager.api.handoff.exception` | Extends `RuntimeException`, `Exception` suffix (design rule 9); has its own `package-info.java`. |
| `HubHandoff` | platform | `net.elytrarace.voyager.platform.handoff` | Sends each racer: through `PlayerHandoffs.current()` when present; otherwise, or on a refusal, disconnects with `voyager.handoff.disconnect`. |
| `LobbyBoard` | platform | `net.elytrarace.voyager.platform.lobby` | Pure view model: the cup name and the ordered map lines for a cup, plus the count line. Shared by the sidebar and the hologram. |
| `LobbySidebar` | platform | `net.elytrarace.voyager.platform.lobby` | One Minestom `Sidebar` per racer, shown while the room waits and removed when a racer enters a map. |
| `LobbyHologram` | platform | `net.elytrarace.voyager.platform.lobby` | Spawns and updates one `TextDisplay` at the configured position in the lobby instance. |
| `CloudNetPlayerHandoff` | cloudnet-bridge | `net.elytrarace.voyager.cloudnet.bridge` | Implements `PlayerHandoff` through a `FallbackConnector` seam, a `Consumer<UUID>`-shaped functional interface in the bridge package. Maps any failure to `HandoffUnavailableException`. |
| `FallbackConnector` | cloudnet-bridge | `net.elytrarace.voyager.cloudnet.bridge` | The seam. Its production body (inside `VoyagerCloudNetExtension`) calls `ServiceRegistry.registry().defaultInstance(PlayerManager.class).playerExecutor(uuid).connectToFallback()`. Tests use a fake. |
| `ServerSettings` component | server | `net.elytrarace.voyager.server.config` | Reads `VOYAGER_LOBBY_HOLOGRAM`, parsed like `VOYAGER_LOBBY_SPAWN`. |
| `InvalidHologramException` | server | `net.elytrarace.voyager.server.config.exception` | Its own `package-info.java` as the others have. |

The bridge's `FallbackConnector` is a bridge-package type. The platform never names it. The platform names only
`PlayerHandoff` and `HandoffUnavailableException`, so the extension and the platform meet only in `api.handoff`.

## 5. Decisions

- **D1. The port is in `voyager-api` and installed by the bridge.** See section 3.
- **D2. Local fallback is a disconnect.** With no installed handoff, `HubHandoff` disconnects the racer with `voyager.handoff.disconnect`
  (translated, English). This is also the refusal path (Q2: no retry).
- **D3. Everyone online at the handoff tick is handed off.** Racers who join during the linger are handed off with the rest,
  because the owner said "every player". Racers who join after the handoff start a new lobby as before.
- **D4. `RESULTS_LINGER` = 200 ticks (10 s at 20 TPS).** A constant in `WaitingRoom`, counted in ticks and not measured with a
  clock, so the test is explicit-tick and Repeatable. Not a setting. The owner answered Q3 with 10 s.
- **D5. The room does not start a countdown for the racers it sent away.** After the handoff the lobby is empty. A later join
  starts the room as it does today.
- **D6. One source for the lobby text.** `LobbyBoard` builds the cup name and the map lines. The sidebar and the hologram both read
  it. The boss bar reads the cup name from it too. A test asserts that the three agree.
- **D7. The cup shown while waiting is the cup currently loaded.** The lobby reads `CatalogHolder.current()`. A pending reload
  shows only once a round starts and promotes it, and a countdown keeps the cup it began with (section 10, Q6).
- **D8. The sidebar shows only while a racer waits.** It is hidden at `RUNNING` and removed when a racer enters a map. It follows
  the same situation rule as the boss bar.
- **D9. The hologram is optional and needs a lobby.** It is spawned once at boot in the lobby instance, after the lobby world is
  loaded. It is not persisted: the lobby world is not saved on stop (`add-lobby-world`), so the entity is recreated each boot.
- **D10. The configuration check refuses a hologram without a lobby.** It names both settings, as the spawn check does.

## 6. Fitness rules

`voyager-fitness` covers every module with a rule that names its package prefix (`FitnessCoverageTest`). The new boundary is the
bridge naming a Voyager package, so two changes are needed in `CloudNetBoundaryTest` and one new rule:

1. **Modify** `bridgeDependsOnlyOnJdkAndLibraries`: the allowed list gains `net.elytrarace.voyager.api.handoff..`. Nothing else in
   Voyager is allowed. (Task 8.4 writes the failing test first.)
2. **Add** `onlyTheBridgeInstallsAPlayerHandoff`: `noClasses().that().resideOutsideOfPackage(BRIDGE).should()` call
   `PlayerHandoffs.install(..)`. ArchUnit's call-based condition is to be checked against the pinned ArchUnit version; if it is not
   available, the rule is written as `dependOnClassesThat` on the install method's owner with a per-method check. Both need
   `allowEmptyShould(false)`.
3. **Add** `handoffPortDependsOnlyOnJdkAndApi`: classes in `..api.handoff..` depend only on `java..` and `net.elytrarace.voyager.api..`.
   This keeps the api module pure. It goes in `SliceBoundaryRulesTest` or a new `HandoffBoundaryTest`.
4. **Add** a Gradle check, `verifyNoVoyagerApiInBridgeJar`, next to `verifyNoCloudNetInFatJars`. The bridge jar must not contain
   `net/elytrarace/voyager/`, because the bridge is loaded beside the root and a second copy of the port would break the shared slot.

## 7. Settings and configuration

| Setting | Required | Default | Meaning |
|---|---|---|---|
| `VOYAGER_LOBBY_WORLD` | with `VOYAGER_LOBBY_SPAWN` | none (`add-lobby-world`) | The lobby world. Needed for the hologram. |
| `VOYAGER_LOBBY_SPAWN` | with `VOYAGER_LOBBY_WORLD` | none (`add-lobby-world`) | The lobby spawn. |
| `VOYAGER_LOBBY_HOLOGRAM` | no | none | `x,y,z` in block coordinates, the same format as the spawn. Needs a lobby world. |

There is no hub setting. The target is CloudNet's fallback configuration, so an operator changes it in CloudNet and not in Voyager.
`RESULTS_LINGER` is a constant (D4).

Configuration check (`ConfigCheck`, keyed by setting name):
- no hologram: no problem and no warning;
- a hologram without a lobby world: an error that names `VOYAGER_LOBBY_HOLOGRAM` and `VOYAGER_LOBBY_WORLD`;
- a hologram that is not three numbers: an error that names `VOYAGER_LOBBY_HOLOGRAM`.

## 8. Conflicts with planned changes

These are the places where the parallel changes touch the same code. None of them is in this repository yet, except
`add-lobby-world` and `add-cloudnet-deployment`, which are unarchived but on `main`.

| Planned or open change | Where it collides | Expected conflict |
|---|---|---|
| `add-lobby-world` (unarchived, #314 on `main`) | `WaitingRoom.tick` (the cup-end return branch), `LobbyTransition.returnTo`, `WaitingRoomTest` ("a finished cup returns racers through LobbyTransition"), `LobbyTransitionTest` ("a finished cup returns racers to the lobby spawn"), the golden `golden/cup-session/lobby-world-start.txt`, the spec scenario "The next cup starts on its own" | **Direct.** This change replaces the return. `add-lobby-world` must be archived first, or this change's delta must be applied on top. `LobbyTransition.returnTo` becomes dead code, which is a separate `refactor(race)` commit. |
| `add-lobby-world` boss bar keys | `Messages.lobbyBossBarWaiting`/`lobbyBossBarCountdown`, `LobbyHud` titles | Signature change to add the cup name. `add-lobby-world` tests change with it. |
| `add-cloudnet-deployment` (unarchived) | `voyager/cloudnet-bridge/build.gradle.kts` (adds `compileOnly(project(":voyager:api"))`), `VoyagerCloudNetExtension`, `CloudNetBoundaryTest`, ADR-0026 | Direct on the bridge module and its fitness rule. Its spec (`cloudnet/bridge-extension`) must exist before this change's delta. |
| `checkpoint-resets` (planned) | `CupSession.raceTick` and the `RunResetter`, `RunReset`/`ResetPlan` | Both touch `CupSession`. Mostly separate methods; the risk is in the `RunResetter` field set and its constructor. Not in `WaitingRoom`. |
| `per-map-boost` (planned) | `CupSession.requestBoost`, `FireworkBoostTracker`, `BoostConfig` (api.race), and `CupSession.start` which clears the boosts | Shared file with `CupSession`; `start` is touched by both changes. The lobby changes do not touch `start`. |
| `full-course-guide-line` (planned) | `GuideLineRenderer`, `CupSession.raceTick` (per-racer lines) | Same `CupSession` file. The lobby changes do not touch race ticks. |
| bot-racer change (planned) | **Largest.** Bots counted as online in `players.get()` (the minimum, the maximum, the join line, the countdown), and `hub`/handoff would send a bot to the hub. The sidebar and hologram loops iterate players. | Bots must be excluded from the handoff and from the racer count, or a bot keeps a cup alive and gets handed off. This change's `HubHandoff` takes a `Collection<Player>`; the bot change must decide what a bot is before this lands. |

## 9. Verification plan

- Unit: the explicit-tick tests of `WaitingRoom`, `LobbyBoard`, `LobbySidebar`, `LobbyHologram`, `HubHandoff`, `CloudNetPlayerHandoff`,
  `PlayerHandoffs`, the settings and the configuration check. None reads the clock or sleeps.
- Architecture: the fitness rules in section 6, each with `allowEmptyShould(false)`.
- Build: `./gradlew build` for both trees, `verifyNoCloudNetInFatJars`, `verifyNoVoyagerApiInBridgeJar`.
- Node (owner only, not CI): spike 2.1 (the class identity across the boundary) and spike 2.2 (`connectToFallback` with and without a
  configured fallback). Then a real cup end on a two-racer test: both racers reach the hub, and the standings are readable first.

## 10. Owner decisions (2026-10-13)

The owner approved the change and answered its questions. Each answer is binding; where a section above still reads as
the recommendation it replaces, the answer below governs.

- **Q1. Greenfield design and ADR-0026: option A.** The bridge may name `net.elytrarace.voyager.api.handoff..` and nothing
  else of Voyager. ADR-0026 gets an amendment note dated 2026-10-13, and the greenfield design paragraph "the extension names
  no Voyager type" is amended to say the same. `CloudNetBoundaryTest` is changed deliberately in its own `test(fitness)`
  commit. Option B is not used.
- **Q2. A refused handoff: disconnect, no retry.** When no handoff is installed, or the installed one throws
  `HandoffUnavailableException`, the racer is disconnected with the translated `voyager.handoff.disconnect` and a refused
  handoff logs a warning that names the racer. Nothing is retried, and the racer is not left in the lobby.
- **Q3. The linger: 10 s (200 ticks) after the standings, then the handoff.** `RESULTS_LINGER_TICKS` is a constant in
  `WaitingRoom`, not a setting (D4).
- **Q4. The proxy needs a fallback.** `connectToFallback` reports nothing back, so what a racer sees with no fallback
  configured is the proxy's behaviour, not Voyager's. This is an operator note in `docs/guides/how-to-deploy-on-cloudnet.md`
  (task 9.4), with no code. The spike on the owner's node (task 2.2) is still the owner's to run.
- **Q5. Racers who join during the linger are handed off too (D3).** Every racer online on the handoff tick is handed off.
- **Q6. The cup shown while waiting is the cup that is currently loaded or pinned.** The lobby shows the cup the holder
  currently holds (`CatalogHolder.current()`). A reload that is pending is not shown: it becomes current when the next round
  starts, and the lobby shows it from then on. A countdown keeps the cup it began with, because the holder changes only at a
  round start. Decision D7 states this rule; the scenario "A reloaded cup shows before the next round" is replaced in the delta spec.
- **Bots (COM racers).** Not implemented yet. The handoff selects racers in one place, `WaitingRoom.handoffCandidates()`,
  which carries a TODO that a bot is excluded there. Until the bot change lands, no bot exists, so no bot is handed off.
- **Display.** A sidebar with the cup name and the maps in play order; a `TextDisplay` hologram at the optional
  `VOYAGER_LOBBY_HOLOGRAM=x,y,z`, read through the avaje `ConfigLoader`; the cup name in the lobby boss bar. All text is in
  both bundles, `voyager_en_US.properties` and `voyager_de_DE.properties`.
- **Sidebar limit (Q7), ADR number (Q8) and scope (Q9).** Approved as recommended: twelve map lines and a "+N more" line; ADR-0033;
  one change with one `feat(race)` title and `feat(cloudnet)` on the bridge commits.
- **ADR-0033 stays Proposed** until the owner has playtested the change. Task 1.2 is therefore not done by this change, and
  ADR-0029 is not marked as amended until that happens (task 1.3, the ADR-0029 part).
- **Spikes 2.1 and 2.2** run on the owner's node and are not part of the implementation. The handoff must not be enabled
  on the owner's network before both results are recorded here.

## Scope and split

The change keeps one type (`feat`). The bridge part is a different scope (`cloudnet`), not a different type, so the rule that a mixed
type is two changes does not force a split. The handoff's only caller is the lobby's cup end, so shipping the port without that
caller gains nothing. If the owner wants the bridge verified on the node before the lobby changes, Q9 splits the change at task 8.
