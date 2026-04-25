# ADR-0006: Move the GameMode enum into shared/common

## Status

Accepted. Supersedes the placement decision in [ADR-0005](0005-gamemode-enum-session-discriminator.md). The session-discriminator role and the `RACE`/`PRACTICE` constants from ADR-0005 stay in force; only the package and module change.

## Date

2026-04-25

## Decision makers

- Voyager server team
- Voyager setup team

## Context and problem statement

[ADR-0005](0005-gamemode-enum-session-discriminator.md) introduced `GameMode { RACE, PRACTICE }` and placed it in the `server` module. Two more consumers now need the same enum:

- The Setup plugin (Paper, `plugins/setup`) writes the mode into cup configuration JSON when a designer assigns a cup to Race or Practice.
- The persistence layer (`shared/database`) writes the mode column on `game_results` and reads it back for queries (see [ADR-0010](0010-game-results-mode-discriminator.md)).

Setup runs on Paper and cannot import from `server` (Minestom). `shared/database` must not import server- or game-specific classes (CLAUDE.md, Module Isolation). With the enum in `server/`, the only options were duplicating the constants in Setup or punching an illegal dependency through the module boundary.

## Decision drivers

- One source of truth for the mode constants across Setup, Game, and persistence
- Module Isolation rules from CLAUDE.md: `shared/common` carries no platform imports; `shared/database` carries no game imports
- ManisGame Rule 6 (cached `VALUES`, `byName()` lookup) for enums with external representations
- Modes are a finite, closed set with no per-instance state, so a runtime polymorphism hierarchy is unwarranted

## Considered options

- Option A: Keep `GameMode` in `server/` and duplicate the constants in Setup and `shared/database`
- Option B: Move `GameMode` to `shared/common` at `net.elytrarace.common.game.mode`
- Option C: Replace the enum with a sealed interface `GameMode permits RaceMode, PracticeMode`

## Decision outcome

Chosen option: **Option B — `GameMode` in `shared/common` at `net.elytrarace.common.game.mode`**, because every consumer (Setup, Game, persistence) can import from `shared/common`, and the enum is the smallest construct that captures a closed two-value set.

The enum carries only mode metadata (`minimumPlayers`, `leaderboardRanked`, `dslKey`) and no behaviour. Behaviour lives in mode-specific strategies (see [ADR-0007](0007-scoring-strategy-pattern.md) and [ADR-0009](0009-hud-component-and-renderer-split.md)).

The enum follows ManisGame Rule 6: a cached `VALUES` array, a `byName(String)` lookup that returns `@Nullable GameMode`, and a `byDslKey(String)` lookup for the configuration key.

### Consequences

- Good, because Setup, Game, and `shared/database` all import the same constants, so renaming or adding a mode is a single change.
- Good, because `shared/common` already hosts `MedalTier` and `MedalBrackets` (DRY), so mode-related primitives live together.
- Good, because no module gains an illegal platform dependency.
- Bad, because `shared/common` now owns a domain enum that previously felt server-local. The trade-off is acceptable because the enum has zero behaviour.
- Neutral, because `GameOrchestrator`, phases, and the persistence layer change one import line each.

### Confirmation

- `net.elytrarace.common.game.mode.GameMode` exists in `shared/common`.
- `server/`, `plugins/setup/`, and `shared/database/` import `GameMode` from `net.elytrarace.common.game.mode`.
- No file under `shared/common/src/main/` imports `net.minestom.*` or `org.bukkit.*`.
- The ArchUnit test in `server/src/test/java/net/elytrarace/arch/LayerArchitectureTest.java` passes.
- `GameMode` exposes a `private static final GameMode[] VALUES = values();` array and a `byName(String)` method that returns `@Nullable GameMode`.

## Pros and cons of the options

### Option A — keep `GameMode` in `server/` and duplicate constants in Setup

- Good, because `server/` keeps a self-contained domain model.
- Bad, because Setup must duplicate the enum constants and metadata.
- Bad, because adding a new mode requires synchronised edits in two modules.
- Bad, because `shared/database` cannot reference the enum at all and must store the mode as a raw string with no compile-time check.

### Option B — `GameMode` in `shared/common` at `net.elytrarace.common.game.mode`

- Good, because every consumer imports the same type.
- Good, because the enum sits next to `MedalTier` and other shared gameplay primitives.
- Good, because no illegal dependency edge appears in the module graph.
- Bad, because `shared/common` grows by one domain enum.

### Option C — sealed interface `GameMode permits RaceMode, PracticeMode`

- Good, because it would allow per-mode behaviour on the type itself.
- Bad, because the mode set is closed and finite with no per-instance state, so runtime polymorphism adds no value.
- Bad, because every consumer would have to switch on the permitted type or instantiate singletons, which is what an enum already does.
- Bad, because it conflicts with ManisGame Rule 6, which assumes enum semantics for closed value sets.

## More information

- Predecessor: [ADR-0005: GameMode enum session discriminator](0005-gamemode-enum-session-discriminator.md)
- Related: [ADR-0007: Scoring uses the Strategy pattern](0007-scoring-strategy-pattern.md)
- Related: [ADR-0009: HUD component and renderer split](0009-hud-component-and-renderer-split.md)
- Related: [ADR-0010: Single game_results table with mode discriminator](0010-game-results-mode-discriminator.md)
- ManisGame design rules: CLAUDE.md, section "Design Reference Rules", Rule 6
