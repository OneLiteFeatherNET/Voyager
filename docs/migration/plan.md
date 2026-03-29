# Voyager Migration Plan: Paper to Minestom

> **Status:** Draft
> **Created:** 2026-03-29
> **Goal:** Migrate the ElytraRace game plugin from Paper to Minestom as a standalone server

## Decisions

| Decision | Value |
|---|---|
| Java Version | 25 |
| Server Type | Minestom Standalone (own `main()`) |
| World Format | Anvil (existing maps compatible) |
| Conversation API | Rewritten, platform-agnostic |
| Setup Plugin | Out of scope (will be solved separately later) |

## Affected Modules

| Module | Action |
|---|---|
| `shared/common` | Already Bukkit-free -- no changes needed |
| `shared/phase` | Already Bukkit-free -- no changes needed |
| `shared/conversation-api` | **Rewrite** -- 7 files with Bukkit imports |
| `shared/database` | Unchanged -- Hibernate/HikariCP stays |
| `plugins/game` | **Fully migrate** to Minestom |
| `plugins/setup` | **Out of scope** for MVP (FAWE-dependent) |

---

## Milestones

### Milestone 1: Foundation (Infrastructure)

Set up basic build and server infrastructure.

| ID | Title | Description | Size | Dependency | Agent |
|---|---|---|---|---|---|
| M1-01 | `chore: configure Java 25 upgrade in Gradle` | Set `sourceCompatibility`, `targetCompatibility` and `--release` flag to Java 25. Switch Gradle toolchain to JDK 25. | S | -- | voyager-build-agent |
| M1-02 | `chore: add Minestom dependency to version catalog` | Add Minestom as library in `settings.gradle.kts` version catalog. Paper dependency remains for shared modules. | S | -- | voyager-build-agent |
| M1-03 | `refactor: create new 'server' module for standalone server` | Create new Gradle submodule `server` that acts as standalone Minestom server. Configure dependencies to `shared/common`, `shared/phase`, `shared/database`. | M | M1-01, M1-02 | voyager-build-agent |
| M1-04 | `feat: implement Minestom server bootstrap with main()` | `main()` method, `MinecraftServer.init()`, create default instance, start server on configurable port. Basic server configuration (MOTD, max players) via file or environment variables. | M | M1-03 | voyager-core-agent |
| M1-05 | `chore: update GitHub Actions CI/CD for Java 25 and new module` | Update build matrix to Java 25. Include `server` module in build pipeline. Generate shadow JAR artifact for server module. | S | M1-03 | voyager-build-agent |
| M1-06 | `test: write server bootstrap integration test` | Test that starts the Minestom server, binds to a port, and shuts down cleanly. Verifies lifecycle works. | S | M1-04 | voyager-test-agent |

---

### Milestone 2: Shared Module Cleanup

Rewrite conversation API platform-agnostic and remove last Bukkit dependencies.

| ID | Title | Description | Size | Dependency | Agent |
|---|---|---|---|---|---|
| M2-01 | `refactor: define platform abstractions for conversation API` | Define interfaces for `ConversationPlayer`, `ConversationScheduler` and `ConversationMessenger` that don't use Bukkit types. These replace direct Bukkit references (`Plugin`, `Player`, `Bukkit.getScheduler()`). | M | -- | voyager-core-agent |
| M2-02 | `refactor: free conversation API from Bukkit imports` | Migrate all 7 files in `shared/conversation-api`: `Conversation`, `ConversationContext`, `ConversationFactory`, `ConversationTracker`, `InactivityConversationCanceller`, `PlayerNamePrompt`, `PluginNameConversationPrefix`. Replace Bukkit types with new abstractions from M2-01. | L | M2-01 | voyager-core-agent |
| M2-03 | `feat: implement Minestom adapter for conversation API` | Create implementations of `ConversationPlayer`, `ConversationScheduler` and `ConversationMessenger` for Minestom. These live in the `server` module. | M | M2-02, M1-03 | voyager-core-agent |
| M2-04 | `test: unit tests for platform-agnostic conversation API` | Tests for `Conversation`, `ConversationFactory` and `ConversationTracker` with mock implementations of the new interfaces. Ensure prompt chains, timeout, and cancellation work correctly. | M | M2-02 | voyager-test-agent |
| M2-05 | `refactor: remove Paper dependency from shared/conversation-api build.gradle.kts` | After successful migration, remove `paper-api` dependency from the conversation API module. Verify compilation. | S | M2-02 | voyager-build-agent |

---

### Milestone 3: Core Game (Minestom)

Port core game infrastructure to Minestom: events, scheduler, worlds, phases, players.

| ID | Title | Description | Size | Dependency | Agent |
|---|---|---|---|---|---|
| M3-01 | `feat: build Minestom event handler infrastructure` | Set up event listener registration for Minestom events (PlayerLoginEvent, PlayerDisconnectEvent, PlayerMoveEvent, PlayerStartFlyingWithElytraEvent etc.) in the server module. Modular setup analogous to `DefaultListener`. | M | M1-04 | voyager-core-agent |
| M3-02 | `feat: implement scheduler adapter for ECS EntityManager` | Use Minestom `SchedulerManager` to drive the ECS game loop (`EntityManager.update(deltaTime)`) at 20 TPS. The existing `EntityManager` should remain unchanged. | M | M1-04 | voyager-core-agent |
| M3-03 | `feat: implement Anvil world loading with InstanceContainer` | Use Minestom's `AnvilLoader` to load existing Anvil maps into an `InstanceContainer`. Adapt existing `WorldComponent`/`SimpleWorldComponent` to Minestom `Instance` references. | L | M1-04 | voyager-core-agent |
| M3-04 | `refactor: adapt phase system to Minestom lifecycle` | Rewrite `LobbyPhase`, `PreparationPhase`, `GamePhase`, `EndPhase` to Minestom events and APIs. `LinearPhaseSeries` remains unchanged (platform-agnostic in `shared/phase`). | L | M3-01, M3-02 | voyager-core-agent |
| M3-05 | `feat: implement player management (join, leave, teleport)` | Player join flow: assign instance, set gamemode, teleport to spawn. Leave flow: cleanup, session update. Teleport logic for phase transitions (lobby spawn, start position). | M | M3-01, M3-03 | voyager-core-agent |
| M3-06 | `refactor: migrate GameSession and components to Minestom types` | Migrate `GameSession`, `GameStateComponent`, `SessionComponent` and other components from Bukkit types (`World`, `Location`, `Player`) to Minestom equivalents (`Instance`, `Pos`, `Player`). | L | M3-03, M3-05 | voyager-core-agent |
| M3-07 | `test: integration tests for phase lifecycle and player management` | Tests that simulate a complete phase run (Lobby -> Game -> End) with mock players. Verify correct event triggering and state transitions. | L | M3-04, M3-05 | voyager-test-agent |

---

### Milestone 4: Gameplay

Core gameplay: elytra physics, collision detection, cup system, and player feedback.

| ID | Title | Description | Size | Dependency | Agent |
|---|---|---|---|---|---|
| M4-01 | `feat: implement elytra flight physics` | Implement vanilla-like elytra physics in Minestom. Minestom has no built-in elytra physics -- gravity, glide angle, velocity calculation and rocket boost must be implemented manually via velocity manipulation. Reference: `docs/elytra-physics-reference.md`. | XL | M3-02, M3-05 | voyager-physics-agent |
| M4-02 | `feat: port ring collision detection to Minestom` | Port `CollisionSystem` and spline-based ring detection (`SplineSystem`, `SimpleSplineSystem`) to Minestom `Pos` types. Existing `commons-geometry-euclidean` logic remains. | L | M3-06 | voyager-core-agent |
| M4-03 | `feat: migrate cup system (map rotation, scoring)` | Port `CupSystem`/`SimpleCupSystem` and `CupService`/`CupServiceImpl` to Minestom. Map switching via instance management. Scoring logic (points per ring, overall ranking) retained. | L | M3-03, M4-02 | voyager-core-agent |
| M4-04 | `feat: implement scoreboard and BossBar UI` | Set up Minestom `Sidebar` for scoreboard (current ranking, remaining rings) and `BossBar` for timer/progress. | M | M3-05 | voyager-ui-agent |
| M4-05 | `feat: add sound and particle effects` | Ring passthrough sound, countdown sounds, goal particles via Minestom `SoundEvent` and `ParticleCreator`/`sendGroupedPacket`. | M | M4-02 | voyager-ui-agent |
| M4-06 | `test: elytra physics unit tests` | Tests for velocity calculation, glide angle, gravity and rocket boost. Cover boundary values and edge cases (max speed, ground collision). | L | M4-01 | voyager-test-agent |
| M4-07 | `test: collision and cup system tests` | Tests for ring passthrough detection (correct, barely missed, backwards), cup rotation and scoring calculation. | M | M4-02, M4-03 | voyager-test-agent |

---

### Milestone 5: Polish and Deploy

Integration, persistence, performance, and deployment.

| ID | Title | Description | Size | Dependency | Agent |
|---|---|---|---|---|---|
| M5-01 | `feat: implement CloudNet v4 integration` | Integrate CloudNet v4 bridge module: service registration, player routing, server status updates. Automatic shutdown after game end. | L | M3-04 | voyager-infra-agent |
| M5-02 | `feat: extend database schema with scores and statistics` | New Hibernate entities for match results, lap times, and player statistics. Migration scripts for the extended schema. | M | M4-03 | voyager-core-agent |
| M5-03 | `feat: persist results and statistics` | Write match results, lap times and player statistics to database after game end. Use existing `shared/database` layer. | M | M5-02 | voyager-core-agent |
| M5-04 | `chore: create Docker image for standalone server` | Multi-stage Dockerfile: build with Java 25, runtime as minimal JRE image. Extend `docker-compose.yml` with server service alongside MariaDB. | M | M1-05 | voyager-infra-agent |
| M5-05 | `perf: performance profiling and optimization` | Test physics loop, collision detection, and instance management under load (20+ players). Identify hotspots and optimize. | L | M4-01, M4-02 | voyager-core-agent |
| M5-06 | `docs: migration documentation and operations manual` | Documentation for deployment, configuration, map format, and CloudNet setup. Document changes compared to the Paper version. | M | M5-01, M5-04 | voyager-docs-agent |
| M5-07 | `feat: implement boost rings and power-ups` | Special ring types (speed boost, slow, altitude change) as extensible ring components in the ECS. | L | M4-02 | voyager-core-agent |
| M5-08 | `feat: ghost replay system` | Record and replay best times as ghost riders. Store positions serially in file or database, replayed as invisible entities. | XL | M4-01, M5-03 | voyager-core-agent |
| M5-09 | `feat: leaderboard system` | Load global and per-map leaderboards from database and display as holograms or chat commands. | M | M5-03 | voyager-core-agent |

---

## Dependency Graph (simplified)

```
M1-01 (Java 25) ──┐
M1-02 (Minestom) ──┼── M1-03 (Server Module) ── M1-04 (Bootstrap) ── M1-06 (Test)
                   │                                    │
                   │                                    ├── M3-01 (Events)
                   │                                    ├── M3-02 (Scheduler)
                   │                                    └── M3-03 (Anvil Loading)
                   │
M1-05 (CI/CD) ─────┘

M2-01 (Abstractions) ── M2-02 (Migration) ── M2-03 (Adapter)
                                │                      │
                                ├── M2-04 (Tests)      │
                                └── M2-05 (Cleanup)    │

M3-01 + M3-02 ── M3-04 (Phases)
M3-01 + M3-03 ── M3-05 (Players) ── M3-06 (Components)
M3-04 + M3-05 ── M3-07 (Tests)

M3-02 + M3-05 ── M4-01 (Physics) ── M4-06 (Tests)
M3-06 ── M4-02 (Collision) ── M4-03 (Cup) ── M4-07 (Tests)
M4-02 ── M4-05 (Effects)
M3-05 ── M4-04 (UI)

M4-03 ── M5-02 (DB Schema) ── M5-03 (Persistence) ── M5-09 (Leaderboard)
M5-03 + M4-01 ── M5-08 (Ghost Replay)
M3-04 ── M5-01 (CloudNet)
M1-05 ── M5-04 (Docker)
M4-01 + M4-02 ── M5-05 (Performance)
```

## Total Effort Estimate

| Milestone | Tickets | Estimated Effort |
|---|---|---|
| M1: Foundation | 6 | ~2 weeks |
| M2: Shared Cleanup | 5 | ~2 weeks |
| M3: Core Game | 7 | ~4 weeks |
| M4: Gameplay | 7 | ~5 weeks |
| M5: Polish & Deploy | 9 | ~5 weeks |
| **Total** | **34** | **~18 weeks** |

> M1 and M2 can partially be worked on in parallel (different modules).
> M5-07, M5-08, M5-09 are nice-to-have and can be prioritized after the MVP.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Elytra physics deviates from vanilla | Gameplay feel suffers | Early prototyping in M4-01, comparison tests against Paper |
| Minestom API changes | Breaking changes | Pin Minestom version, use adapter pattern |
| Java 25 compatibility with dependencies | Build errors | Test early in M1-01, fallback to Java 24 if needed |
| Anvil loader limitations | Maps don't load correctly | Test known maps early in M3-03 |
| Performance with many players | Lag in physics/collision | Profiling in M5-05, spatial partitioning if needed |
