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
- **Version Catalog**: Defined programmatically in `settings.gradle.kts` via `dependencyResolutionManagement { versionCatalogs { ... } }` — do NOT use `gradle/libs.versions.toml`

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

## Design Reference Rules (from ManisGame)

The following rules are derived from [ManisGame](https://github.com/OneLiteFeatherNET/ManisGame) and are **mandatory** for all new code.

### 1. Sealed Interface Hierarchy

Domain interfaces in `shared/common` must use `sealed` + `permits BaseSomething` where a controlled extension point is intended. The `Base*` implementation is `non-sealed abstract` so consumers extend it instead of the root interface.

```java
public sealed interface Scare permits BaseScare { ... }

public abstract non-sealed class BaseScare implements Scare { ... }
```

### 2. Factory as abstract utility class with `@ApiStatus.Internal`

Factory classes are `abstract` with a `private` constructor, annotated `@ApiStatus.Internal`. All factory methods are annotated `@Contract(pure = true, value = "... -> new")`.

```java
@ApiStatus.Internal
public abstract class ScareFactory {
    private ScareFactory() {}

    @Contract(pure = true, value = "_, _, _ -> new")
    public static Scare create(ScareCategory category, Key key, Map<Class<?>, T> components) { ... }
}
```

### 3. Provider/Registry as sealed interface with static `create()`

- Provider/Registry interfaces are `sealed`; the implementation is `final`.
- Static `create()` factory method lives on the interface.
- Backing storage uses `ConcurrentHashMap`; returned collections are `@Unmodifiable`.
- The single implementation is named `Default*`.

```java
public sealed interface ScareProvider permits DefaultScareProvider {
    @Contract(pure = true)
    static ScareProvider create() { return new DefaultScareProvider(); }

    boolean add(Scare scare);
    @Unmodifiable Collection<Scare> getScares();
}

public final class DefaultScareProvider implements ScareProvider {
    private final Map<Key, Scare> scaresByKey = new ConcurrentHashMap<>();
    // ...
}
```

### 4. Components as Java `record`s

Data components are `record`s. The compact constructor validates invariants. A static `of(Annotation)` factory enables annotation-driven creation.

```java
public record TitleComponent(Component header, Component subHeader, long fadeIn, long stay, long fadeOut)
        implements ScareComponent {

    public TitleComponent {
        Check.argCondition(fadeIn < 0, "fadeIn must be greater than 0");
    }

    @Contract(pure = true, value = "_ -> new")
    public static TitleComponent of(TitleMeta titleMeta) { ... }
}
```

### 5. `@NotNullByDefault` on packages

Every package declares a `package-info.java` with `@NotNullByDefault`. Nullability is the exception, not the default.

```java
@NotNullByDefault
package net.theevilreaper.manis.scare;

import org.jetbrains.annotations.NotNullByDefault;
```

### 6. Enum DSL pattern

Enums with external representations cache `VALUES`, provide `byName()`/`byId()` lookup methods, and return `@Nullable` or `Optional`.

```java
public enum ScareCategory {
    SOUND("sound"), TITLE("title");

    private static final ScareCategory[] VALUES = values(); // cached!
    private final String dslKey;

    ScareCategory(String dslKey) { this.dslKey = dslKey; }

    public static @Nullable ScareCategory byName(String name) {
        ScareCategory category = null;
        for (int i = 0; i < VALUES.length && category == null; i++) {
            if (VALUES[i].dslKey.equalsIgnoreCase(name)) category = VALUES[i];
        }
        return category;
    }
}
```

### 7. Functional interface as injectable Creator

Abstract factories must be injectable via a `@FunctionalInterface` Creator so tests can substitute a different factory implementation.

```java
@FunctionalInterface
public interface ScareCreator {
    <T extends ScareComponent> Scare apply(ScareCategory category, Key key, Map<Class<?>, T> components);
}
// Usage: passed to adapters/loaders so tests can inject a different factory.
```

### 8. Gson Adapter naming

- Deserializer classes are named `*Adapter` and live in an `adapter` subpackage.
- Component adapters live in `adapter.component`.
- All adapter classes are `final`.

```java
// package scare.adapter
public final class ScareAdapter implements JsonDeserializer<Scare> { ... }

// package scare.adapter.component
public final class TitleComponentAdapter implements JsonDeserializer<TitleComponent> { ... }
```

### 9. Exception hierarchy

All domain exceptions extend `RuntimeException`, carry the `Exception` suffix, and use domain-specific names.

Examples: `MissingAnnotationException`, `InvalidDataException`, `InvalidCategoryException`.

### 10. Module API boundary

- `shared/api` — pure interfaces, enums, exceptions; zero implementation.
- `shared/common` — implementations of shared logic; NO platform-specific imports.
- `extensions/*` or `server` — platform-specific code; may import Minestom API.

### Module Isolation

- `shared/common`, `shared/phase`, `shared/conversation-api`, `shared/spline` must NOT import `net.minestom.*`.
- `server` module must NOT import `org.bukkit.*` (Paper).
- `shared/database` must NOT import server- or game-specific classes.

### ArchUnit Enforcement

These rules are enforced by ArchUnit tests in `server/src/test/java/net/elytrarace/arch/`.

## Agent Team Workflow (MANDATORY)

**Every non-trivial task MUST involve the Agent Team.** Do not work alone — delegate to specialized agents and run them in parallel where possible.

### Always-Active Agents

For every significant task, these four agents MUST be involved:

1. **Compass** (`voyager-product-manager`) — Tracks the task as a ticket, defines acceptance criteria, validates the result matches requirements. **Can request creation of new agents or skills** by delegating to `voyager-agent-architect` or `voyager-skill-creator` — but MUST ask the user for approval first before any new agent/skill is created.
2. **Pulse** (`voyager-game-psychologist`) — Reviews every gameplay and design decision for player retention, flow state, and engagement. Ensures features are psychologically optimized.
3. **Scribe** (`voyager-tech-writer`) — Documents every change in `docs/` (English), updates migration status, writes ADRs for decisions
4. **Lumen** (`voyager-scientist`) — Records the work in `docs/research/` (English, research paper style), documents methodology, findings, and rationale

### Domain Agents (use as needed)

Each agent has a codename (persona) used in agent-to-agent references; invoke via the `voyager-*` ID.

| Codename | Agent ID | When to use |
|---|---|---|
| Atlas | `voyager-architect` | Architecture decisions, system design, module boundaries |
| Helix | `voyager-minestom-expert` | Any Minestom API code, instance management, events |
| Bedrock | `voyager-minecraft-expert` | Vanilla mechanics, elytra physics, protocol, collision |
| Drift | `voyager-game-designer` | Gameplay loops, balancing, ring/map/cup design, feedback timing |
| Thrust | `voyager-game-developer` | Physics code, ring collision, scoring, cup system, game loop |
| Origami | `voyager-paper-expert` | Setup plugin, Paper API, MockBukkit tests |
| Vault | `voyager-database-expert` | Hibernate entities, repositories, queries, schema changes |
| Hangar | `voyager-devops-expert` | CI/CD, GitHub Actions, CloudNet v4, Docker, deployment |
| Scout | `voyager-researcher` | Deep research before decisions (Context7, WebSearch, WebFetch) |
| Forge | `voyager-senior-backend` | Services, repositories, Java patterns, adapters |
| Lattice | `voyager-senior-ecs` | ECS components/systems, EntityManager, game loop, tick budgets |
| Quench | `voyager-senior-testing` | JUnit 5 tests, coverage, test architecture |
| Vector | `voyager-math-physics` | 3D geometry, collision algorithms, splines, formulas |
| Piston | `voyager-java-performance` | JVM tuning, GC, profiling, benchmarks |
| Spark | `voyager-junior-creative` | Creative solutions, prototypes, edge cases, wild ideas |
| Glint | `voyager-junior-frontend` | Scoreboards, BossBars, actionbar, sounds, particles |
| Beacon | `voyager-social-media` | Community posts, announcements, changelogs |
| Anvil | `voyager-skill-creator` | Creating reusable slash-command skills |
| Loom | `voyager-agent-architect` | Creating or improving agents |

### Workflow Pattern

```
1. PLAN    — Product Manager defines scope + Architect designs approach
2. RESEARCH — Researcher gathers current docs/info via Context7 + WebSearch
3. IMPLEMENT — Domain experts write code in parallel where possible
4. DOCUMENT — Tech Writer + Scientist document in parallel (both English)
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
