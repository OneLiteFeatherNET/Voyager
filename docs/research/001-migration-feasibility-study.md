# Migration Feasibility Study: Paper to Minestom for Elytra Racing

**Document ID:** RES-001
**Date:** 2026-03-29
**Status:** Draft
**Authors:** Voyager Research Team

---

## Abstract

This study evaluates the feasibility of migrating the Voyager elytra racing game from Paper (Bukkit API) to Minestom as the underlying server framework. A comprehensive analysis of the existing codebase was conducted, encompassing all six modules, their inter-dependencies, and their coupling to the Bukkit/Paper platform. The study concludes that the migration is **feasible with controlled risk**. Key architectural decisions include targeting Java 25 (as required by Minestom), operating as a standalone server with its own `main()` entry point, retaining the Anvil world format for map data, implementing custom elytra physics, and replacing the Bukkit-coupled conversation API with a platform-agnostic alternative. The existing Entity-Component-System architecture and phase system provide a solid, partially decoupled foundation that significantly reduces migration effort.

---

## 1. Introduction

### 1.1 Background

Voyager (internally "ElytraRace") is a Minecraft minigame in which players race through a sequence of aerial ring portals using elytra gliding mechanics, conceptually similar to Mario Kart with flight-based navigation. The game is structured around cups (collections of maps), where each map defines a track through a series of portals that players must fly through in order. A spline-based path visualization guides players along the intended route.

The project is currently implemented as a Paper plugin ecosystem consisting of six Gradle modules, targeting Paper API 1.21.5 on Java 21.

### 1.2 Problem Statement

Paper, as a fork of the CraftBukkit/Spigot server, inherits the full Vanilla Minecraft server codebase. This introduces several limitations for a purpose-built minigame server:

- **Vanilla overhead:** The server processes Vanilla mechanics (mob AI, redstone, weather, etc.) that are irrelevant to the elytra racing use case and consume computational resources unnecessarily.
- **Limited physics customization:** Elytra flight physics are baked into the Vanilla server implementation and cannot be modified without NMS (net.minecraft.server) reflection or mixins.
- **Plugin lifecycle coupling:** The plugin model requires conforming to the Paper server's initialization sequence, thread model, and scheduler API.
- **Deployment complexity:** A full Paper server must be deployed and configured, even though the minigame uses only a fraction of Vanilla functionality.

### 1.3 Scope

The migration scope encompasses the `plugins/game` module as the primary target. The `shared/common`, `shared/phase`, and `shared/database` modules are already largely framework-agnostic and require minimal changes. The `shared/conversation-api` module requires a complete rewrite due to pervasive Bukkit coupling. The `plugins/setup` module is explicitly **out of scope** for the initial migration, as it depends on FastAsyncWorldEdit and serves an offline map-authoring function that can remain on Paper.

---

## 2. Current Architecture Analysis

### 2.1 Module Inventory

The project comprises six modules organized into two groups:

| Module | Files | Bukkit-Coupled Files | Purpose |
|--------|-------|---------------------|---------|
| `plugins/game` | 40 | 29 | Main game plugin: phases, ECS systems, services, listeners |
| `plugins/setup` | 17 | 17 | Map/cup/portal setup wizard (out of scope) |
| `shared/common` | 22 | 0 | ECS framework, map/cup services, file handling, i18n, spline math |
| `shared/phase` | 12 | 0 | Phase lifecycle framework (Phase, TimedPhase, TickedPhase, series) |
| `shared/conversation-api` | 22 | 7 | Player conversation/prompt system |
| `shared/database` | 5 | 0 | Hibernate ORM + HikariCP persistence layer |

**Total Bukkit import occurrences across `plugins/game`:** 79 across 29 files.
**Total Bukkit import occurrences across `shared/conversation-api`:** 9 across 7 files.
**Total Bukkit imports in `shared/common`:** 0 (uses only Adventure API via `net.kyori`).
**Total Bukkit imports in `shared/phase` and `shared/database`:** 0.

### 2.2 Platform Abstraction Layer

The codebase already contains a partial platform abstraction:

- **`EventRegistrar`** (interface in `shared/phase`) with `BukkitEventRegistrar` implementation in `plugins/game` -- wraps listener registration/unregistration.
- **`PhaseScheduler`** (interface in `shared/phase`) with `BukkitPhaseScheduler` implementation in `plugins/game` -- wraps repeating task scheduling.

This abstraction was introduced in a recent refactoring commit (`7269234 - refactor(shared): decouple shared modules from Bukkit APIs`) and provides the foundation for creating Minestom-specific implementations.

### 2.3 Entity-Component-System Architecture

The ECS framework in `shared/common` (`net.elytrarace.common.ecs`) is entirely platform-agnostic:

- `Entity` -- UUID-identified component container
- `Component` -- Marker interface
- `System` -- Declares required components, processes matching entities
- `EntityManager` -- Orchestrates systems with `update(deltaTime)`

However, several ECS **system implementations** in `plugins/game` contain direct Bukkit API calls:

- `CollisionSystem` -- calls `Bukkit.getPlayer()` for notification (2 Bukkit imports)
- `PlayerUpdateSystem` -- calls `Bukkit.getOnlinePlayers()`, `Player.getLocation()` (3 Bukkit imports)
- `CupSystem` / `SimpleCupSystem` -- uses `Bukkit` scheduler and `WorldCreator` (4 Bukkit imports each)
- `SplineSystem` / `SimpleSplineSystem` -- uses `Bukkit.getWorld()` (1-2 Bukkit imports)
- `PhaseSystem` / `SimplePhaseSystem` -- uses `Bukkit.getOnlinePlayers()` (2 Bukkit imports each)
- `GameStateSystem` / `SimpleGameStateSystem` -- uses `Bukkit.shutdown()` (1 Bukkit import each)

### 2.4 Event Handling

The `DefaultListener` class is the most heavily Bukkit-coupled file in the codebase with 16 Bukkit imports. It handles 12 distinct event types:

- Player events: join, login, move, interact, armor stand manipulate, bed enter, drop item
- Block events: break, place, leaves decay
- Entity events: damage, food level change, pickup item
- Server events: list ping

All of these serve a protective function (cancelling Vanilla behaviors) or implement game-specific logic (portal detection via player movement, phase-aware join/login).

### 2.5 Test Coverage

**No test files exist anywhere in the project.** The `*Test*.java` glob returns zero results. This represents a significant risk factor for migration, as there is no automated validation of existing behavior.

### 2.6 Adventure API Usage

The `shared/common` module uses `net.kyori.adventure` extensively (Key, Component, MiniMessage, TranslationRegistry) but does **not** import any Bukkit APIs. Since Minestom natively supports the Adventure API, these modules require zero changes for the migration.

---

## 3. Target Architecture

### 3.1 Server Framework

**Minestom 2026.03.25-1.21.11** shall serve as the server library. Minestom is a lightweight, modular Minecraft server library that provides:

- No Vanilla code -- only the protocol implementation and core server abstractions
- Full control over world generation, physics, and game mechanics
- Native Adventure API support for text components and translations
- Built-in Anvil world format reader for loading existing maps
- An event system based on Java functional interfaces rather than annotation-based listeners

### 3.2 Java Version

**Java 25** is required by the target Minestom version. This is an upgrade from the current Java 21 baseline. Java 25 is expected to be available as an LTS candidate and introduces no breaking changes for the existing codebase.

### 3.3 Application Model

The application shall transition from a **plugin** deployed into a Paper server to a **standalone server application** with its own `main()` method. The Minestom `MinecraftServer` instance will be created and configured programmatically, replacing the `JavaPlugin` lifecycle (`onEnable`/`onDisable`).

### 3.4 World Management

Existing map data stored in the Anvil world format shall be loaded using Minestom's `AnvilLoader`. The current `VoidGenProvider` (an empty `ChunkGenerator` subclass) will be replaced by a no-op Minestom `ChunkGenerator` or omitted entirely, as Minestom does not generate terrain by default.

### 3.5 Elytra Physics

Since Minestom does not include Vanilla physics, custom elytra flight physics must be implemented. A reference document already exists at `docs/elytra-physics-reference.md`. The implementation will need to handle:

- Glide vector calculation based on player pitch and velocity
- Gravity and drag coefficients
- Firework rocket boost mechanics (if applicable)
- Collision detection with terrain

### 3.6 Conversation API

The `shared/conversation-api` module shall be rewritten to be platform-agnostic, replacing all `org.bukkit.plugin.Plugin` and `org.bukkit.entity.Player` references with generic interfaces. This benefits both the Minestom migration and any future platform targets.

---

## 4. Migration Scope Analysis

### 4.1 Class-Level Migration Inventory

The following table catalogs all classes requiring modification, their current Bukkit coupling depth, and the estimated effort level (S = Small / hours, M = Medium / 1-2 days, L = Large / 3-5 days, XL = Extra Large / 1+ week).

#### 4.1.1 Entry Points and Lifecycle

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `ElytraRace.java` (main plugin) | Extends `JavaPlugin`, uses `onEnable`/`onDisable`, Bukkit scheduler | New `MinestomServer` class with `main()`, Minestom scheduler | **L** |
| `SimpleElytraRace.java` (alt plugin) | Extends `JavaPlugin`, same pattern | Merge into single entry point or remove | **M** |
| `PluginInstanceHolder.java` | Static holder for `JavaPlugin` reference | Replace with `MinecraftServer` reference or DI | **S** |

#### 4.1.2 Platform Adapters

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `BukkitEventRegistrar.java` | Wraps `Bukkit.getPluginManager().registerEvents()` | `MinestomEventRegistrar` using `GlobalEventHandler` / `EventNode` | **M** |
| `BukkitPhaseScheduler.java` | Wraps `Bukkit.getScheduler().runTaskTimer()` | `MinestomPhaseScheduler` using `SchedulerManager` | **M** |

#### 4.1.3 Phase Implementations

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `LobbyPhase.java` | `Bukkit.getWorlds()`, `Bukkit.getOnlinePlayers()`, `player.teleportAsync()`, `player.setMetadata()`, `FixedMetadataValue`, `org.bukkit.Sound` | Replace with Minestom `InstanceManager`, `MinecraftServer.getConnectionManager()`, `Player.teleport()`, custom metadata via ECS | **L** |
| `GamePhase.java` | `org.bukkit.Particle`, `world.spawnParticle()`, `getServer().getWorld()` | Minestom `ParticlePacket`, `Instance.sendGroupedPacket()` | **M** |
| `EndPhase.java` | `Bukkit.getOnlinePlayers()`, `Bukkit.shutdown()`, `org.bukkit.Sound` | Minestom connection manager, `MinecraftServer.stopCleanly()` | **M** |
| `PreparationPhase.java` | `Bukkit.shutdown()` | `MinecraftServer.stopCleanly()` | **S** |

#### 4.1.4 ECS Systems

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `CollisionSystem.java` | `Bukkit.getPlayer()` for notifications | Minestom `MinecraftServer.getConnectionManager().getOnlinePlayerByUuid()` | **S** |
| `PlayerUpdateSystem.java` | `Bukkit.getOnlinePlayers()`, `Player.getLocation()`, `Player.getUniqueId()` | Minestom player collection and `Pos` | **M** |
| `CupSystem.java` / `SimpleCupSystem.java` | `WorldCreator`, `Bukkit.getScheduler()`, `World.Environment` | Minestom `InstanceManager`, `AnvilLoader`, custom instance creation | **L** |
| `SplineSystem.java` / `SimpleSplineSystem.java` | `Bukkit.getWorld()` | Minestom instance reference | **S** |
| `PhaseSystem.java` / `SimplePhaseSystem.java` | `Bukkit.getOnlinePlayers()` | Minestom connection manager | **S** |
| `GameStateSystem.java` / `SimpleGameStateSystem.java` | `Bukkit.shutdown()` | `MinecraftServer.stopCleanly()` | **S** |

#### 4.1.5 Services

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `GameServiceImpl.java` | `PaperCommandManager`, `Bukkit.shutdown()`, `Bukkit.getScheduler().getMainThreadExecutor()`, `Bukkit.getPluginManager().registerEvents()` | Minestom command system, event nodes, scheduler | **XL** |
| `GameCupService.java` | `WorldCreator`, `Bukkit.getScheduler().getMainThreadExecutor()`, `World.Environment`, `WorldType` | Minestom `InstanceManager`, `AnvilLoader` | **L** |
| `PortalDetectionService.java` | `Bukkit.getOnlinePlayers()`, `player.hasMetadata()`, `player.getMetadata()`, `FixedMetadataValue` | Minestom player access, ECS-based metadata (already partially migrated to `PlayerMetadataComponent`) | **M** |

#### 4.1.6 Models and DTOs

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `GameMapDTO.java` | `Bukkit.getWorld()`, `org.bukkit.World` field | Minestom `Instance` reference | **M** |
| `GamePortalDTO.java` | `org.bukkit.World`, `org.bukkit.Location`, `TextDisplay`, `Display.Billboard` | Minestom `Instance`, `Pos`, custom entity or packet-based display | **L** |

#### 4.1.7 Event Handling

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `DefaultListener.java` | 12 Bukkit event handlers, 16 Bukkit imports | Minestom event listeners via `EventNode`; most cancellation events become unnecessary (Minestom has no Vanilla behaviors to cancel) | **L** |

#### 4.1.8 World Generation

| Class | Current State | Target State | Effort |
|-------|--------------|-------------|--------|
| `VoidGenProvider.java` | Empty `ChunkGenerator` subclass | Remove entirely (Minestom generates void by default) | **S** |

#### 4.1.9 Shared Modules

| Module | Current State | Target State | Effort |
|--------|--------------|-------------|--------|
| `shared/common` | No Bukkit imports; uses Adventure API | No changes required | **None** |
| `shared/phase` | No Bukkit imports; platform-agnostic interfaces | No changes required | **None** |
| `shared/database` | No Bukkit imports; pure Hibernate/HikariCP | No changes required | **None** |
| `shared/conversation-api` | 7 files with Bukkit imports (`Plugin`, `Player`) | Complete rewrite with generic interfaces | **XL** |

### 4.2 Effort Summary

| Category | S | M | L | XL | Total Classes |
|----------|---|---|---|----|----|
| Entry Points & Lifecycle | 1 | 1 | 1 | 0 | 3 |
| Platform Adapters | 0 | 2 | 0 | 0 | 2 |
| Phase Implementations | 1 | 2 | 1 | 0 | 4 |
| ECS Systems | 4 | 1 | 1 | 0 | 6 |
| Services | 0 | 1 | 1 | 1 | 3 |
| Models/DTOs | 0 | 1 | 1 | 0 | 2 |
| Event Handling | 0 | 0 | 1 | 0 | 1 |
| World Generation | 1 | 0 | 0 | 0 | 1 |
| Shared Modules | 0 | 0 | 0 | 1 | 1 |
| **Total** | **7** | **8** | **6** | **2** | **23** |

**Estimated total effort:** 6-8 developer-weeks, assuming one experienced developer familiar with both Paper and Minestom APIs.

### 4.3 Eliminated Complexity

Several aspects of the current codebase become unnecessary under Minestom, reducing the effective migration surface:

1. **`VoidGenProvider`** -- Minestom defaults to void worlds; no generator needed.
2. **Most `DefaultListener` handlers** -- Block break/place, leaf decay, food level, entity damage, armor stand manipulation, bed enter, item drop/pickup events exist solely to cancel Vanilla behavior. Minestom has none of these behaviors, so approximately 8 of 12 event handlers can be deleted outright.
3. **`FixedMetadataValue` usage** -- The Bukkit metadata API is already being migrated to ECS components (`PlayerMetadataComponent`, `PlayerPositionsComponent`). This migration path is correct and should be completed.
4. **`PaperCommandManager`** -- Minestom has a built-in command system. The Cloud command framework also provides a Minestom adapter (`cloud-minestom`), enabling a like-for-like replacement.

---

## 5. Risk Assessment

### 5.1 Risk Matrix

| Risk | Probability | Impact | Rating | Mitigation |
|------|------------|--------|--------|------------|
| Elytra physics accuracy | Medium | High | **Medium** | Reference document exists at `docs/elytra-physics-reference.md`. Implement iteratively with side-by-side comparison against Vanilla. Consider recording player trajectories on a Paper test server as ground truth. |
| No existing test coverage | High | High | **High** | Write unit tests for ECS systems and phase logic **before** migration begins. The ECS framework and phase system are already platform-agnostic and can be tested without mocking. Use this as an opportunity to establish a test baseline. |
| CloudNet integration | Medium | Medium | **Medium** | The current `EndPhase.onFinish()` calls `Bukkit.shutdown()` to signal CloudNet for server recycling. Minestom's `MinecraftServer.stopCleanly()` must produce an equivalent process exit. The CloudNet Minestom bridge should be evaluated for compatibility. Known shutdown signaling issues exist in CloudNet's Minestom module. |
| Java 25 toolchain | Low | Low | **Low** | Straightforward upgrade from Java 21. No deprecated APIs in use. Gradle 9.4 already supports Java 25 toolchains. CI/CD pipelines need updated JDK images. |
| Performance regression | Low | Medium | **Low** | Minestom is inherently lighter than Paper due to the absence of Vanilla code. The ECS game loop already runs at 20 TPS with `entityManager.update(1.0f / 20.0f)`. Minestom's tick loop can drive this directly. |
| Adventure API compatibility | Low | Low | **Low** | Minestom natively uses Adventure for all text, key, and sound APIs. The `shared/common` module's extensive Adventure usage is directly compatible. |
| Anvil world loading | Low | Medium | **Low** | Minestom's `AnvilLoader` supports the standard Anvil format. Existing map worlds should load without modification. Verification testing is required for each map. |
| TextDisplay entity (portal markers) | Medium | Low | **Medium** | `GamePortalDTO` spawns `TextDisplay` entities for portal visualization. Minestom supports display entities, but the API surface differs. This may require packet-level implementation or use of Minestom's entity API. |
| Cloud command framework migration | Low | Low | **Low** | The Incendo Cloud framework provides `cloud-minestom` as a first-party module, offering a direct replacement for `cloud-paper`. Command definitions should transfer with minimal changes. |
| Dual codebase maintenance during migration | Medium | Medium | **Medium** | The `plugins/setup` module remains on Paper while `plugins/game` moves to Minestom. The shared modules must remain compatible with both. The existing platform abstraction layer (`EventRegistrar`, `PhaseScheduler`) supports this, but care must be taken not to introduce Minestom-specific code into shared modules. |

### 5.2 Critical Path Items

The following items represent the critical path for the migration and should be addressed in order:

1. **Test harness establishment** -- Write tests for the ECS framework, phase system, and collision detection logic before any migration work begins.
2. **Elytra physics prototype** -- Build a minimal Minestom server with custom elytra physics and validate against Vanilla behavior. This is the highest-uncertainty item.
3. **World loading validation** -- Confirm that all existing maps load correctly via `AnvilLoader`.
4. **CloudNet shutdown integration** -- Verify that `MinecraftServer.stopCleanly()` produces the expected behavior in the CloudNet environment.

---

## 6. Conclusion

### 6.1 Feasibility Assessment

**The migration from Paper to Minestom is FEASIBLE with controlled risk.**

The primary factors supporting this assessment are:

1. **Strong architectural foundation.** The recent refactoring to decouple shared modules from Bukkit APIs has established platform abstraction interfaces (`EventRegistrar`, `PhaseScheduler`) that directly support a multi-platform strategy. The `shared/common`, `shared/phase`, and `shared/database` modules require zero changes.

2. **Bounded migration surface.** Only 29 files in `plugins/game` and 7 files in `shared/conversation-api` contain Bukkit imports. Of those, approximately 8 event handlers in `DefaultListener` can be deleted rather than migrated, and `VoidGenProvider` becomes unnecessary.

3. **API compatibility.** Minestom's native Adventure API support means that all text component, translation, key, and sound code in the shared modules transfers directly. The Cloud command framework offers a Minestom adapter.

4. **Reduced complexity.** Minestom eliminates the need to suppress Vanilla behaviors (the majority of the current `DefaultListener`), manage Vanilla world generators, or work around the plugin lifecycle model.

### 6.2 Key Risks

The two highest-risk items are:

- **No test coverage** (High) -- This must be addressed before migration begins.
- **Elytra physics accuracy** (Medium) -- Custom physics implementation is the single largest unknown and should be prototyped early.

### 6.3 Recommended Approach

A phased migration strategy is recommended:

1. **Phase 0: Test Baseline** -- Write unit tests for ECS systems, phase logic, and collision detection against the current Paper codebase.
2. **Phase 1: Physics Prototype** -- Implement custom elytra physics on a minimal Minestom server. Validate accuracy.
3. **Phase 2: Core Migration** -- Create Minestom platform adapters, migrate entry point, world loading, and ECS systems.
4. **Phase 3: Game Logic Migration** -- Migrate phases, services, and event handling.
5. **Phase 4: Conversation API Rewrite** -- Decouple the conversation API from Bukkit for both Minestom and continued Paper setup usage.
6. **Phase 5: Integration and Validation** -- End-to-end testing with existing maps, CloudNet integration, and performance benchmarking.

---

## 7. References

1. **Minestom GitHub Repository.** https://github.com/Minestom/Minestom -- Server library documentation and API reference.
2. **Minestom Wiki.** https://wiki.minestom.net/ -- Official documentation for Minestom concepts and usage.
3. **Paper API Documentation.** https://docs.papermc.io/ -- Current server API reference.
4. **Elytra Physics Reference.** `docs/elytra-physics-reference.md` -- Internal document describing Vanilla elytra flight mechanics for reimplementation.
5. **Incendo Cloud Framework.** https://github.com/Incendo/cloud -- Command framework with Minestom support via `cloud-minestom`.
6. **CloudNet v4.** https://github.com/CloudNetService/CloudNet -- Cloud orchestration platform; Minestom module compatibility to be verified.
7. **Apache Commons Geometry.** https://commons.apache.org/proper/commons-geometry/ -- Euclidean geometry library used for portal collision detection (platform-independent).
8. **Adventure API.** https://docs.advntr.dev/ -- Text component library natively supported by both Paper and Minestom.
