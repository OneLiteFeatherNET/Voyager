# Decision Document: Paper to Minestom Migration

**Date:** 2026-03-29
**Status:** Under Evaluation
**Affects:** Game plugin (`plugins/game`) — Migration from Paper API to Minestom

---

## 1. Current State (Before)

The Voyager project (internally "ElytraRace") is currently built as a Paper plugin:

- **Paper API 1.21.5** as server platform
- **Java 21** with `--release 21`
- **Bukkit dependencies in `shared/conversation-api`** — 7 files with direct Bukkit imports
- **40+ files in `plugins/game`** with Bukkit imports (events, scheduler, player API, etc.)
- **No standalone server** — the game plugin runs as a plugin within a Paper server
- **Vanilla elytra physics** are fully provided by the server (glide mechanics, boost, collision)
- **MockBukkit** as test framework for unit tests
- **plugin.yml** is auto-generated via the `plugin-yml` Gradle plugin
- **Cloud (Incendo)** as command framework with Paper integration
- **Adventure API** provided through Paper

---

## 2. Proposed Change

The game plugin shall be completely rewritten for Minestom. The setup plugin remains unchanged on Paper.

| Aspect | Description |
|--------|-------------|
| **Game Plugin** | Complete rebuild on Minestom |
| **Setup Plugin** | Stays on Paper (requires FastAsyncWorldEdit) |
| **Java Version** | Java 25 (Minestom requirement) |
| **Server Model** | Standalone server with own `main()` |
| **World Format** | Anvil World Format (direct loading without vanilla server) |
| **Conversation API** | Completely rewritten, platform-agnostic |
| **Elytra Physics** | Custom implementation required |

---

## 3. Already Made Decisions

The following points have already been decided and are not up for discussion:

- **Java 25** as the target version for the game plugin
- **Standalone Server** — no Minestom extensions, but own `main()`
- **Anvil Format** for loading worlds
- **New Conversation API** — platform-agnostic, without Bukkit dependencies

---

## 4. Pro Arguments for Minestom

### 4.1 Legal Safety
No vanilla code included means no EULA or copyright issues. Minestom is a clean reimplementation of the Minecraft protocol without Mojang code.

### 4.2 Lightweight
Significantly less RAM usage and faster startup than a full Paper server. For a minigame that doesn't need vanilla mechanics, the full Paper stack is overhead.

### 4.3 Full Control over Physics and Gameplay
Elytra physics, collision detection, and all gameplay mechanics can be precisely tailored to the racing requirements — without workarounds against vanilla behavior.

### 4.4 Native Adventure API
Minestom uses Adventure natively. No adapter or wrapper needed — the entire text/chat API is directly available.

### 4.5 CloudNet v4 Support
CloudNet v4 supports Minestom as a platform. Deployment and scaling through the existing CloudNet setup is fundamentally possible.

### 4.6 Better Isolation
Minestom offers the concept of Instances (comparable to separate worlds). Each race can run in its own Instance — completely isolated, without mutual interference.

### 4.7 No Plugin Overhead
No plugin classloader, no plugin lifecycle management, no dependency conflicts with other plugins. The server is the application.

---

## 5. Contra Arguments against Minestom

### 5.1 Everything Must Be Implemented Manually
Elytra physics, collision detection, boost mechanics — everything must be built from the ground up. This is significant development effort and a potential source of errors. The vanilla elytra physics are complex (glide angles, firework rocket boost, block collision).

### 5.2 Java 25 Requirement
Java 25 is newer than the previous project standard (Java 21). Build pipelines, CI/CD, and deployment infrastructure must be updated. Java 25 may not yet be an LTS release.

### 5.3 Smaller Ecosystem
Paper has a large community, extensive documentation, and many libraries. Minestom has a significantly smaller ecosystem — when problems arise, there are fewer resources and experience reports.

### 5.4 No Vanilla Mechanics Out-of-the-Box
Everything beyond the pure protocol must be implemented manually or added through community extensions. This also affects seemingly trivial things like block placement, inventory handling, or particle effects.

### 5.5 CloudNet Integration Has Known Issues
The CloudNet-Minestom integration has known issues, particularly with shutdown behavior (see CloudNet Issue #1304). This can lead to problems in production.

### 5.6 API Stability
The Minestom API changes more frequently than the Paper API. Breaking changes between versions are possible and require regular code adjustments.

---

## 6. Before/After Comparison

| Area | Before (Paper) | After (Minestom) |
|---------|---------------|-------------------|
| **Server Type** | Paper server with plugin | Standalone server with own `main()` |
| **Java Version** | Java 21 | Java 25 |
| **World Format** | Paper loads worlds automatically | Anvil format loaded directly via `AnvilLoader` |
| **Physics** | Vanilla elytra physics from server | Custom implementation required |
| **Events** | Bukkit event system (`@EventHandler`) | Minestom event nodes (type-safe event system) |
| **Scheduler** | `BukkitScheduler` / `BukkitRunnable` | Minestom `Scheduler` / `TaskSchedule` |
| **Commands** | Cloud with `cloud-paper` integration | Cloud with `cloud-minestom` integration |
| **Config** | plugin.yml (auto-generated via `plugin-yml`) | Not needed — configuration in `main()` |
| **Tests** | MockBukkit + JUnit 5 | Direct Minestom instance + JUnit 5 (no MockBukkit) |
| **Deployment** | Plugin JAR in Paper server `plugins/` folder | Fat JAR as standalone process |
| **Dependencies** | Paper API, plugin-yml, MockBukkit | Minestom Core, no plugin infrastructure |

---

## 7. Migration Scope

### Affected Modules

| Module | Action | Effort |
|-------|--------|---------|
| `plugins/game` | Complete rebuild on Minestom | High |
| `shared/conversation-api` | Rewrite (platform-agnostic) | Medium |
| `shared/common` (ECS) | Remains unchanged (no Bukkit dependency) | None |
| `shared/phase` | Remains unchanged (no Bukkit dependency) | None |
| `shared/database` | Remains unchanged | None |
| `plugins/setup` | Stays on Paper | None |

### Files with Bukkit Imports (to migrate)

- **`plugins/game`**: 40+ files — Events, listeners, scheduler, player handling, world API
- **`shared/conversation-api`**: 7 files — Complete reimplementation needed

---

## 8. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|--------|-------------------|------------|------------|
| Elytra physics deviates from vanilla | High | Medium | Create reference documentation, iterative tuning |
| CloudNet shutdown problem | Medium | High | Track issue #1304, implement own shutdown hook |
| Minestom breaking changes | Medium | Medium | Pin version, perform updates in a controlled manner |
| Java 25 compatibility in CI/CD | Low | Low | Update build pipeline early |
| Longer development time than planned | High | Medium | Define MVP, migrate incrementally |
