# Migration Status: Paper to Minestom

As of: 2026-03-29

## Milestone Overview

| Milestone | Status | Tickets |
|---|---|---|
| M1: Foundation | Complete | 6/6 |
| M2: Shared Cleanup | Almost complete | 4/5 |
| M3: Core Game | Almost complete | 5/7 |
| M4: Gameplay | Almost complete | 6/7 |
| M5: Polish & Deploy | Partial | 4/9 |

## Server Module Structure

```
server/src/main/java/net/elytrarace/server/
├── VoyagerServer.java                  # Entry point (main()), server bootstrap
├── cup/
│   ├── CupDefinition.java             # Record: Cup with list of MapDefinitions
│   ├── CupFlowService.java            # Interface: Cup progression (next map, status)
│   ├── CupFlowServiceImpl.java        # Implementation: Map index management
│   └── MapDefinition.java             # Record: Map with rings, spawn, world path
├── game/
│   ├── GameLoopSystem.java            # ECS game loop (physics + collision per tick)
│   └── GameSession.java               # Session state: players, velocities, ring tracking
├── phase/
│   ├── GamePhaseFactory.java          # Factory for phase series (Lobby->Game->End)
│   ├── MinestomEndPhase.java          # End phase: results display, cleanup
│   ├── MinestomGamePhase.java         # Game phase: elytra flight, ring collision
│   └── MinestomLobbyPhase.java        # Lobby phase: waiting for players
├── physics/
│   ├── ElytraPhysics.java             # Vanilla-like elytra flight physics (gravity, drag, lift)
│   ├── Ring.java                      # Record: Ring definition (center, normal, radius, points)
│   └── RingCollisionDetector.java     # Segment-plane intersection for ring passthrough
├── platform/
│   ├── MinestomEventListener.java     # Event handler registration
│   ├── MinestomEventRegistrar.java    # Event registrar infrastructure
│   ├── MinestomPhaseScheduler.java    # Phase scheduler via Minestom SchedulerManager
│   └── MinestomPhaseTask.java         # Phase task wrapper
├── player/
│   ├── PlayerEventHandler.java        # Join/leave event handling
│   ├── PlayerService.java             # Interface: Player management
│   └── PlayerServiceImpl.java         # Implementation: Lobby assignment, lookup
├── scoring/
│   ├── CupScoring.java                # Cup-wide score aggregation
│   ├── PlayerScore.java               # Record: Player points (ring + position bonus)
│   ├── ScoringService.java            # Interface: Score management
│   └── ScoringServiceImpl.java        # Implementation: Ring points, ranking, position bonuses
├── ui/
│   ├── GameHud.java                   # Actionbar, BossBar, title, sound feedback
│   └── GameHudManager.java            # Manages GameHud instances per player
└── world/
    ├── AnvilMapInstanceService.java    # Anvil world loading via Minestom InstanceContainer
    └── MapInstanceService.java         # Interface: Map load/unload
```

## Test Coverage

49 tests passing:

| Test Class | Tests | Description |
|---|---|---|
| VoyagerServerTest | 2 | Server process runs, instance creation |
| ElytraPhysicsTest | 8 | Gravity, drag, lift, firework boost, speed limits |
| RingCollisionDetectorTest | 10 | Passthrough detection (center, edge, angle, backwards, parallel, tilted) |
| PlayerServiceTest | 2 | Unknown player, lobby instance access |
| AnvilMapInstanceServiceTest | 3 | Map load, unload, multiple maps simultaneously |
| ScoringServiceTest | 7 | Ring points, ranking, position bonuses, cup aggregation, reset |
| CupFlowServiceTest | 10 | Cup progression, map switching, boundaries, empty cup |
| GameSessionTest | 7 | Player management, velocity tracking, ring tracking |

## Detailed Milestone Status

### M1: Foundation -- Complete (6/6)

- [x] M1-01: Java 25 upgrade (toolchain to JDK 25, `--release 25`)
- [x] M1-02: Minestom dependency in version catalog (`net.minestom:minestom:2026.03.25-1.21.11`)
- [x] M1-03: `server` module created (Gradle submodule with shared dependencies)
- [x] M1-04: Server bootstrap (`VoyagerServer` with `main()`, instance creation, event handlers)
- [x] M1-05: CI/CD updated (shadow JAR, Java 25)
- [x] M1-06: Server bootstrap test (`VoyagerServerTest`)

### M2: Shared Cleanup -- Almost complete (4/5)

- [x] M2-01: Platform abstractions defined
- [x] M2-02: Conversation API Bukkit-free (all shared modules without Bukkit imports)
- [x] M2-03: Minestom adapter implemented
- [x] M2-04: Unit tests for conversation API
- [ ] M2-05: Paper dependency removed from shared/conversation-api -- **Verification pending**

### M3: Core Game -- Almost complete (5/7)

- [x] M3-01: Event handler infrastructure (`MinestomEventListener`, `MinestomEventRegistrar`, `PlayerEventHandler`)
- [x] M3-02: Scheduler adapter (`MinestomPhaseScheduler`, `MinestomPhaseTask`, `GameLoopSystem`)
- [x] M3-03: Anvil world loading (`AnvilMapInstanceService` with `AnvilLoader`)
- [x] M3-04: Phase system (`MinestomLobbyPhase`, `MinestomGamePhase`, `MinestomEndPhase`, `GamePhaseFactory`)
- [x] M3-05: Player management (`PlayerService`, `PlayerServiceImpl`, `PlayerEventHandler`)
- [ ] M3-06: GameSession/Components on Minestom types -- **Partial** (GameSession uses Minestom `Vec`, but no `Instance` reference)
- [ ] M3-07: Integration tests phase lifecycle -- **Pending** (no end-to-end phase run test)

### M4: Gameplay -- Almost complete (6/7)

- [x] M4-01: Elytra flight physics (`ElytraPhysics` with gravity, drag, lift, firework boost)
- [x] M4-02: Ring collision detection (`RingCollisionDetector` with segment-plane intersection)
- [x] M4-03: Cup system (`CupFlowService`, `CupDefinition`, `MapDefinition`, `ScoringService`, `CupScoring`)
- [x] M4-04: Scoreboard/BossBar UI (`GameHud`, `GameHudManager` with actionbar, BossBar, title, sound)
- [x] M4-05: Sound/particle effects (integrated in `GameHud.showRingPassed()`)
- [x] M4-06: Elytra physics tests (8 tests in `ElytraPhysicsTest`)
- [ ] M4-07: Collision/cup tests -- **Partial** (ring tests exist, but no integrated collision+cup flow test)

### M5: Polish & Deploy -- Partial (4/9)

- [ ] M5-01: CloudNet v4 integration -- **Pending**
- [ ] M5-02: Extend database schema -- **Pending**
- [ ] M5-03: Persist results -- **Pending**
- [x] M5-04: Docker image -- **Partial** (shadow JAR exists, Dockerfile pending)
- [ ] M5-05: Performance profiling -- **Pending**
- [ ] M5-06: Migration documentation -- **In progress** (plan.md + status.md exist)
- [x] M5-07: Boost rings/power-ups -- **Foundation ready** (Ring record has points field, extensible)
- [ ] M5-08: Ghost replay system -- **Pending**
- [ ] M5-09: Leaderboard system -- **Pending**

## Open Items

### High Priority

1. **M3-06: GameSession instance reference** -- `GameSession` holds no reference to the active `InstanceContainer`. Needed for map switching and player teleportation.
2. **M3-07: Phase lifecycle integration tests** -- No test covers the complete Lobby -> Game -> End flow with real Minestom players.
3. **M4-07: Integrated collision+scoring test** -- Ring collision and cup flow are tested separately, but not together (player flies through ring -> points -> next map).

### Medium Priority

4. **M2-05: Verification** -- Check if `paper-api` dependency was actually removed from `shared/conversation-api/build.gradle.kts`.
5. **M5-04: Dockerfile** -- Shadow JAR (`server/build/libs/*.jar`) exists, but a multi-stage Dockerfile and docker-compose integration are missing.
6. **M5-01: CloudNet v4** -- No integration yet. Required for production deployment.

### Low Priority (Nice-to-Have / Post-MVP)

7. **M5-02/M5-03: Database persistence** -- Scoring only exists in-memory. Hibernate entities for match results are missing.
8. **M5-08: Ghost replay** -- No position recording yet.
9. **M5-09: Leaderboard** -- Depends on database persistence.
10. **M5-05: Performance profiling** -- Most useful after all gameplay systems are integrated.
