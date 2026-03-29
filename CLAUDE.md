# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Voyager (internally "ElytraRace") is a Minecraft elytra racing minigame (Mario Kart style — fly through cups of maps, each map has rings that give points). Multi-module Java project built with Gradle 9.4.

**Migration in progress:** Game plugin is being migrated from Paper to Minestom. Setup plugin stays on Paper.

### Key Decisions (approved)
- **Java**: 25 (Minestom requirement)
- **Game Server**: Minestom 2026.x as standalone server (own main(), no extensions)
- **Setup Server**: Paper API 1.21.5 (unchanged)
- **World Format**: Anvil (direct loading, compatible with Paper setup)
- **Conversation API**: Complete rewrite (platform-agnostic)
- **Deployment**: CloudNet v4 (primary), Cloud-Native/K8s (later)
- **Commits**: Conventional Commits, no Co-Author

## Build Commands

```bash
./gradlew build                          # Build all modules
./gradlew :plugins:game:build           # Build only the game plugin
./gradlew :plugins:setup:build          # Build only the setup plugin
./gradlew :plugins:game:test            # Run game plugin tests
./gradlew :plugins:game:shadowJar       # Build fat JAR for game plugin
./gradlew :plugins:game:runServer       # Run a local Paper 1.21.5 test server
./gradlew :plugins:game:test --tests "net.elytrarace.game.ElytraRaceTest.testPluginLoads"  # Run a single test
./gradlew :server:build                  # Build server module
./gradlew :server:test                   # Run server tests
./gradlew :server:shadowJar             # Build fat JAR
java -jar server/build/libs/*.jar        # Run standalone server
```

Tests use JUnit 5 with MockBukkit (plugins) or Minestom Testing (server) for API mocking. JaCoCo coverage reports are generated automatically after tests.

## Module Structure

- **`server`** — Standalone Minestom game server. Handles gameplay, physics, scoring, cup flow, and UI. Entry point: `net.elytrarace.server.VoyagerServer` (own `main()`). Depends on `shared/common`, `shared/phase`, `shared/database`.
- **`plugins/game`** — Legacy Paper game plugin (`ElytraRace-Game`). Being replaced by `server`. Entry point: `net.elytrarace.game.ElytraRace`
- **`plugins/setup`** — Setup plugin (`ElytraRace-Setup`) for map/cup/portal configuration via in-game conversations. Depends on FastAsyncWorldEdit. Entry point: `net.elytrarace.setup.ElytraRace`
- **`shared/common`** — Shared utilities: ECS framework, map/cup services, file handling (Gson-based JSON), language/i18n, spline math, builders (Bukkit-frei)
- **`shared/phase`** — Phase lifecycle framework (Phase -> TimedPhase/TickedPhase, with LinearPhaseSeries/CyclicPhaseSeries collections) (Bukkit-frei)
- **`shared/conversation-api`** — Player conversation/prompt system, plattform-agnostisch (Bukkit-frei)
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

- **Minestom** 2026.03.25-1.21.11 — Standalone Minecraft server (server module)
- **Minestom Testing** — Test framework for Minestom (server module tests)
- **Paper API** 1.21.5 — Minecraft server API (plugins only)
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
- Java 25 with `--release 25` (server module), Java 21 with `--release 21` (plugins, shared)
- UTF-8 source encoding
- Interface + Impl pattern for services (e.g., `GameService` / `GameServiceImpl`, `CupService` / `CupServiceImpl`)
- Builder pattern for DTOs (e.g., `MapDTOBuilder`, `CupDTOBuilder`)
- Components are named `*Component`, systems are named `*System`
- Commits follow Conventional Commits (feat:, fix:, docs:, refactor:, test:, chore:, ci:) — no Co-Author line

## Agent Team Workflow (MANDATORY)

**Every non-trivial task MUST involve the Agent Team.** Do not work alone — delegate to specialized agents and run them in parallel where possible.

### Always-Active Agents

For every significant task, these three agents MUST be involved:

1. **`voyager-product-manager`** — Tracks the task as a ticket, defines acceptance criteria, validates the result matches requirements. **Can request creation of new agents or skills** by delegating to `voyager-agent-architect` or `voyager-skill-creator` — but MUST ask the user for approval first before any new agent/skill is created.
2. **`voyager-tech-writer`** — Documents every change in `docs/` (German), updates migration status, writes ADRs for decisions
3. **`voyager-scientist`** — Records the work in `docs/research/` (English, research paper style), documents methodology, findings, and rationale

### Domain Agents (use as needed)

Select the right specialist(s) based on the task:

| Agent | When to use |
|---|---|
| `voyager-architect` | Architecture decisions, system design, module boundaries |
| `voyager-minestom-expert` | Any Minestom API code, instance management, events |
| `voyager-minecraft-expert` | Vanilla mechanics, elytra physics, protocol, collision |
| `voyager-paper-expert` | Setup plugin, Paper API, MockBukkit tests |
| `voyager-database-expert` | Hibernate entities, repositories, queries, schema changes |
| `voyager-devops-expert` | CI/CD, GitHub Actions, CloudNet v4, Docker, deployment |
| `voyager-researcher` | Deep research before decisions (Context7, WebSearch, WebFetch) |
| `voyager-skill-creator` | Creating reusable slash-command skills |
| `voyager-agent-architect` | Creating or improving agents |

### Workflow Pattern

```
1. PLAN    — Product Manager defines scope + Architect designs approach
2. RESEARCH — Researcher gathers current docs/info via Context7 + WebSearch
3. IMPLEMENT — Domain experts write code in parallel where possible
4. DOCUMENT — Tech Writer (DE) + Scientist (EN) document in parallel
5. VALIDATE — Tests run, Product Manager checks acceptance criteria
```

### Skill Creation (Proactive)

When you notice a task or workflow that is repeated or could be reused, **proactively create a Skill** for it using the `voyager-skill-creator` agent. Skills are stored in `.claude/skills/` and callable as slash-commands.

**Trigger for skill creation:**
- A workflow was executed more than once
- A complex multi-step process could be simplified into one command
- The user asks for something that could become a reusable pattern
- A common validation, build, or deployment step is needed repeatedly

**Always ask the user:** "Soll ich dafuer einen Skill erstellen?" — then create it if confirmed.

### Human in the Loop (ALWAYS)

The user MUST be consulted at every significant decision point. Never assume — always ask.

**Mandatory checkpoints (always ask before proceeding):**
- Before starting a new milestone or epic
- Before choosing between alternative approaches (present options with pro/contra)
- Before making architecture decisions (ADRs must be approved)
- Before deleting, renaming, or restructuring existing code
- Before creating PRs or commits that affect shared modules
- When an implementation deviates from the original plan
- When a blocker or unexpected issue is discovered
- When test results reveal unexpected behavior

**How to ask:**
- Use `AskUserQuestion` with clear options and descriptions
- Present trade-offs transparently (pro/contra for each option)
- Give a recommendation but let the user decide
- If the user says "mach einfach" / "just do it", proceed autonomously until the next major checkpoint

### Rules

- **Parallel execution**: Launch independent agents simultaneously, not sequentially
- **Human in the Loop**: Ask the user at every decision point — do not assume (see checkpoints above)
- **Research first**: Always check current docs (Context7) before writing code
- **Document everything**: No implementation without matching documentation
- **Test everything**: No code change without tests
- **Skill creation**: Proactively suggest and create skills for repeated workflows
- **Transparency**: Always explain what agents are doing and why
