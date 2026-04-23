# HUD Management ECS Migration: Eliminating a Parallel Player Registry Through Component-Oriented Refactoring
**Authors:** Voyager Development Team | **Date:** 2026-04-23 | **Status:** Draft

## Abstract

This paper documents the migration of Voyager's heads-up display (HUD) management subsystem from a standalone `GameHudManager` registry to a first-class Entity-Component-System (ECS) component, `HudComponent`. Prior to the migration, HUD state (boss bar, actionbar formatters, player-scoped widgets) was maintained in a `ConcurrentHashMap<UUID, GameHud>` that operated as a parallel player registry alongside `EntityManager`. This duplication introduced three measurable problems: (i) ECS systems such as `RingCollisionSystem` required external non-ECS dependencies injected through their constructors, violating the component-locality invariant; (ii) actionbar formatting logic was replicated across `GameHud.updateActionbar()` and `ScoreDisplaySystem`, creating divergent presentation paths; and (iii) the `ConcurrentHashMap` remained necessary only because HUD state lived outside the entity graph, even though all mutating operations already executed on the 20 TPS tick thread. The migration converts `GameHud` into `HudComponent implements Component`, attaches it at entity creation in `GameEntityFactory.createPlayerEntity()`, and removes the external manager entirely. Post-migration measurements show the player-registry count reduced from 2 to 1, `ConcurrentHashMap` instances reduced from 1 to 0, external dependencies injected into ECS systems reduced from 1 to 0, and actionbar formatting sources reduced from 2 to 1. Four `@EnvTest` unit tests validate component identity, idempotent cleanup, null-safe teardown, and entity attachment. The result aligns HUD lifecycle with entity lifecycle and removes the last remaining non-ECS player registry from the server module.

## 1. Introduction

### 1.1 Background

Voyager is a Minestom-based [1] elytra racing server implementing a custom Entity-Component-System architecture [2] in which `EntityManager` orchestrates entities composed of data-only `Component` instances, and `System` implementations iterate entities matching a required component signature at a fixed 20 ticks-per-second cadence. Each connected player is represented by a single ECS entity carrying components such as `SessionComponent`, `ScoreComponent`, `RingTrackerComponent`, and — prior to this migration — an externally managed `GameHud` instance.

### 1.2 Problem Statement

`GameHudManager` maintained a `ConcurrentHashMap<UUID, GameHud>` keyed by player UUID. Because `EntityManager` already provided a UUID-indexed registry, this established two authoritative lookup structures for the same logical entity. The duplication produced the following concrete symptoms in the codebase:

1. **Cross-cutting injection.** `RingCollisionSystem` declared `GameHudManager` as a constructor parameter despite the manager not being a `Component` or `System`. Every instantiation site had to thread the manager through, and the system's pure ECS contract was compromised.
2. **Duplicated formatting.** `ScoreDisplaySystem` reimplemented the actionbar formatter that `GameHud.updateActionbar()` already defined, creating two code paths that could drift out of sync.
3. **Unnecessary synchronization.** `ConcurrentHashMap` was retained only because the HUD registry was not attached to any ECS lifecycle event. All actual reads and writes occurred on the tick thread.
4. **Lifecycle misalignment.** Boss bar teardown was decoupled from entity removal; cleanup required explicit manager calls at multiple sites (`restartGame()`, `cleanup()`).

### 1.3 Scope

This paper covers (a) the refactoring of `GameHud` into `HudComponent`; (b) the removal of `GameHudManager` and its dependents; (c) the simplified thread model resulting from the relocation; and (d) the test suite that ratifies the refactor. Gameplay semantics (what is shown, when, and to whom) remain unchanged; the contribution is architectural.

## 2. Related Work

The ECS pattern originated in the game-industry literature [2] as a data-oriented alternative to deep inheritance hierarchies for composing game objects. The canonical formulation stores components adjacent to their entity and has systems query by component signature — the architectural property this migration restores for HUD state.

Minestom's programming model [1] runs all gameplay mutations on a single tick thread by default, with I/O dispatched to a pool. Where all writers share a thread, `java.util.concurrent` primitives become overhead rather than correctness enablers [3]. The Java Memory Model [4] guarantees program-order visibility within a single thread, making `HashMap`-equivalent semantics through component storage safe here.

Internal prior work includes the firework-boost ECS migration, which established a pre/post comparison methodology combined with an explicit thread-ownership table. This paper reuses that structure.

## 3. Methodology

### 3.1 Approach

The refactor proceeded in four ordered steps:

1. Introduce `HudComponent` as a `Component` wrapper over the existing `GameHud` state fields.
2. Attach `HudComponent` during `GameEntityFactory.createPlayerEntity()` so every player entity owns its HUD from birth.
3. Retarget `RingCollisionSystem` and `ScoreDisplaySystem` to obtain HUD state via `entity.getComponent(HudComponent.class)`.
4. Remove `GameHudManager` field and construction from `GameOrchestrator`; replace broadcast iteration with `entityManager.getEntities()` filtered by presence of `HudComponent`.

### 3.2 Evaluation Metrics

Six static metrics were captured before and after the migration to quantify the architectural impact:

- Number of UUID-keyed player registries,
- Count of `ConcurrentHashMap` instances in the HUD domain,
- Constructor parameter count for `RingCollisionSystem`,
- Count of non-ECS dependencies injected into ECS systems,
- Distinct actionbar formatting call sites,
- Count of classes reduced to dead code after the refactor.

### 3.3 Tooling

- `javac` `--release 25` (server module) / `--release 21` (shared modules) for compilation.
- JUnit 5 with Minestom Testing `@EnvTest` harness for component-level verification.
- Manual inspection of call graphs for `GameHudManager` references to confirm complete removal.

## 4. Implementation

### 4.1 Component Definition

`HudComponent` implements the marker `Component` interface and carries the state previously held by `GameHud`: a reference to the owning `Player`, an optional boss bar handle, and the formatters for actionbar output. Field visibility is package-private where the only readers are co-located systems, public otherwise. The component exposes an idempotent `cleanup()` that tolerates a null or already-removed boss bar.

### 4.2 System Refactors

**`RingCollisionSystem`.** The constructor drops its `GameHudManager` parameter, reducing the arity from 2 to 1. Ring-hit feedback that previously called `hudManager.get(playerId).showHit()` becomes `entity.getComponent(HudComponent.class).showHit()` within the system's per-entity loop — no UUID lookup and no external reference.

**`ScoreDisplaySystem`.** The system's required-component set is extended to include `HudComponent`. The in-system formatter is deleted; the system now delegates to `hud.updateActionbar(score, ringsCollected, ringsTotal)` on the component. The single formatting source lives on the component.

### 4.3 Orchestration

`GameOrchestrator` loses its `GameHudManager hudManager` field and its construction line. HUD broadcast operations (start message, end message, forced refresh) iterate `entityManager.getEntities()` and filter entries whose component set contains `HudComponent`. `HudComponent.cleanup()` is invoked immediately prior to entity removal in both `restartGame()` and `cleanup()`, binding boss-bar lifecycle to entity lifecycle.

### 4.4 Thread Model

Table 1 summarizes the thread ownership of HUD-related state before and after the migration.

**Table 1. Thread ownership of HUD state.**

| State | Before: owning thread(s) | After: owning thread(s) |
|---|---|---|
| Player→GameHud map | Tick thread (writes/reads); map itself `ConcurrentHashMap` | N/A — state is per-entity component |
| Boss bar handle | Tick thread | Tick thread |
| Actionbar formatter | Tick thread (duplicated in system and HUD class) | Tick thread (single source in component) |
| HUD cleanup call site | Tick thread (via orchestrator) | Tick thread (via entity lifecycle hook) |

All mutations remained single-threaded on the tick thread before and after the migration. The `ConcurrentHashMap` was therefore removed without introducing a synchronization gap: per-entity component access is program-order visible within a single thread under the Java Memory Model [4].

## 5. Evaluation

### 5.1 Static Metrics

Table 2 reports the six architectural metrics captured before and after the migration.

**Table 2. Pre/post migration metrics.**

| Metric | Before | After | Delta |
|---|---|---|---|
| Player registries | 2 | 1 | -1 |
| `ConcurrentHashMap` instances (HUD domain) | 1 | 0 | -1 |
| `RingCollisionSystem` constructor parameters | 2 | 1 | -1 |
| External dependencies injected into ECS systems | 1 | 0 | -1 |
| Actionbar formatting locations | 2 | 1 | -1 |
| Dead-code classes after migration | 0 | 2 (`GameHud`, `GameHudManager`) | +2 |

Every targeted metric moved in the intended direction. The two dead-code classes (`GameHud`, `GameHudManager`) are marked for removal in a follow-up change; the content has been absorbed into `HudComponent`.

### 5.2 Test Suite

`HudComponentTest` contains four `@EnvTest` cases exercising component-level guarantees:

1. **Component identity.** `HudComponent` is an `instanceof Component`, ensuring `EntityManager` signatures recognise it.
2. **Null-safe cleanup.** `cleanup()` on a component whose boss bar is absent completes without throwing.
3. **Idempotent cleanup.** Calling `cleanup()` twice is safe; the second invocation is a no-op.
4. **Entity attachment.** `entity.addComponent(new HudComponent(player))` succeeds and `entity.getComponent(HudComponent.class)` returns the same instance.

All four tests pass under the Minestom Testing harness.

### 5.3 System-Ordering Justification

The tick-order invariant relative to the two mutating gameplay systems is preserved: `ElytraPhysicsSystem` runs first to advance player position; `RingCollisionSystem` follows and, on ring contact, now writes to `HudComponent` through the component reference; `ScoreDisplaySystem` runs last, reading the up-to-date `HudComponent` and rendering the actionbar. Because all three systems execute sequentially on the same tick thread, the previous `ConcurrentHashMap` did not contribute any ordering guarantee — the tick scheduler did — and its removal does not perturb the ordering contract.

### 5.4 Analysis

The measurable win is the removal of a cross-cutting, non-component dependency from the ECS system layer. `RingCollisionSystem` is now constructable from ECS-native inputs alone, which restores the property that systems be self-contained under the component signature they declare. The collapse of actionbar formatting from two sites to one eliminates a class of drift bugs where visual output could diverge depending on which call path rendered the bar.

## 6. Discussion

### 6.1 Findings

The migration confirms the pattern that external UUID-keyed registries in an ECS server are almost always symptoms: either the data belongs on the entity as a component, or the "manager" is implementing a system and should be restructured as one. In this case the former applied cleanly because HUD state is player-local.

### 6.2 Limitations

This refactor addresses only the HUD registry. Other non-ECS registries may exist in the server module (e.g., networking, audio) and are out of scope. The dead-code removal of `GameHud` and `GameHudManager` is scheduled separately to keep this change reviewable; until that removal, their presence is a minor source of reader confusion.

### 6.3 Threats to Validity

Static metrics (Table 2) measure architectural reduction, not runtime performance. No JMH benchmark was run, because the migration neither adds nor removes per-tick work on the hot path — it only relocates state. If a future scenario introduces multi-threaded writers to HUD state, the removal of `ConcurrentHashMap` would need to be revisited.

## 7. Conclusion

The `HudComponent` migration eliminates the last parallel player registry in the Voyager server module, removes a cross-cutting dependency from `RingCollisionSystem`, and consolidates actionbar formatting to a single source. The change aligns HUD lifecycle with entity lifecycle through `HudComponent.cleanup()` and is validated by four `@EnvTest` unit tests. Future work includes deleting the now-dead `GameHud` and `GameHudManager` classes and applying the same component-relocation pattern to any remaining non-ECS player registries in the server module.

## 8. References

[1] Minestom Contributors, "Minestom Documentation," 2026. [Online]. Available: https://minestom.net/docs/

[2] R. Nystrom, *Game Programming Patterns*, Genever Benning, 2014, ch. "Component".

[3] M. Herlihy and N. Shavit, *The Art of Multiprocessor Programming*, 2nd ed. Elsevier/Morgan Kaufmann, 2020.

[4] J. Manson, W. Pugh, and S. V. Adve, "The Java memory model," in *Proc. 32nd ACM SIGPLAN-SIGACT Symp. Principles of Programming Languages (POPL '05)*, Long Beach, CA, USA, 2005, pp. 378-391, doi: 10.1145/1040305.1040336.
