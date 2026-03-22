# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Voyager (internally "ElytraRace") is a Minecraft Paper plugin for an elytra racing minigame. It's a multi-module Java 21 project built with Gradle 9.4, targeting Paper API 1.21.5.

## Build Commands

```bash
./gradlew build                          # Build all modules
./gradlew :plugins:game:build           # Build only the game plugin
./gradlew :plugins:setup:build          # Build only the setup plugin
./gradlew :plugins:game:test            # Run game plugin tests
./gradlew :plugins:game:shadowJar       # Build fat JAR for game plugin
./gradlew :plugins:game:runServer       # Run a local Paper 1.21.5 test server
./gradlew :plugins:game:test --tests "net.elytrarace.game.ElytraRaceTest.testPluginLoads"  # Run a single test
```

Tests use JUnit 5 with MockBukkit for Bukkit API mocking. JaCoCo coverage reports are generated automatically after tests.

## Module Structure

- **`plugins/game`** — Main game plugin (`ElytraRace-Game`). Handles gameplay, phases, collision, and ECS systems. Entry point: `net.elytrarace.game.ElytraRace`
- **`plugins/setup`** — Setup plugin (`ElytraRace-Setup`) for map/cup/portal configuration via in-game conversations. Depends on FastAsyncWorldEdit. Entry point: `net.elytrarace.setup.ElytraRace`
- **`shared/common`** — Shared utilities: ECS framework, map/cup services, file handling (Gson-based JSON), language/i18n, spline math, builders
- **`shared/phase`** — Phase lifecycle framework (Phase → TimedPhase/TickedPhase, with LinearPhaseSeries/CyclicPhaseSeries collections)
- **`shared/conversation-api`** — Player conversation/prompt system (similar to Bukkit's old Conversation API but custom)
- **`shared/database`** — Hibernate ORM + HikariCP + MariaDB persistence layer for player data

## Architecture

### Entity-Component-System (ECS)
The game uses a custom ECS pattern in `shared/common` (`net.elytrarace.common.ecs`):
- `Entity` — UUID-identified container for components (stored as `Map<Class, Component>`)
- `Component` — Marker interface for data holders
- `System` — Declares required components and processes matching entities each tick
- `EntityManager` — Orchestrates entities and systems; `update(deltaTime)` drives the game loop at 20 TPS

Game-specific components are in `plugins/game/src/.../components/` (GameState, Phase, Cup, Map, World, Spline, Session). Systems are in `.../system/` (CollisionSystem, PhaseSystem, CupSystem, etc.).

### Phase System
Game phases (Lobby → Preparation → Game → End) are managed via `shared/phase`. Phases have start/finish lifecycle with callbacks. `LinearPhaseSeries` chains phases sequentially.

### Data Flow
Map and cup definitions are stored as JSON files (via `GsonFileHandler`). The setup plugin uses a conversation-based wizard to create these configs. The game plugin loads them at runtime through `MapService`/`CupService`.

## Dependencies (via version catalog in settings.gradle.kts)

- **Paper API** 1.21.5 — Minecraft server API
- **Cloud** (Incendo) — Command framework
- **Hibernate ORM** + HikariCP — Database ORM
- **MariaDB** client — Database driver
- **FastAsyncWorldEdit** — World editing (setup plugin only)
- **Commons Geometry** — Euclidean geometry/spline calculations
- **ShadowJar** — Fat JAR packaging
- **plugin-yml** — Auto-generates plugin.yml from build config

## Local Development

A Docker Compose file for MariaDB is at `docker/mariadb/compose.yml`. Start with:
```bash
docker compose -f docker/mariadb/compose.yml up -d
```

## Code Conventions

- Base package: `net.elytrarace`
- Java 21 with `--release 21`
- UTF-8 source encoding
- Interface + Impl pattern for services (e.g., `GameService` / `GameServiceImpl`, `CupService` / `CupServiceImpl`)
- Builder pattern for DTOs (e.g., `MapDTOBuilder`, `CupDTOBuilder`)
- Components are named `*Component`, systems are named `*System`
