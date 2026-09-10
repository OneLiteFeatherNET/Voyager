---
name: java-style
description: Judgment-call guidance for writing, changing, or reviewing Java code in Voyager — when to use sealed, record vs class, exception design, nullability, interface size, and physics numeric types. Use whenever producing or reviewing production Java anywhere in this repository — the voyager-* rebuild modules as well as server/, shared/* and plugins/*.
---

# Java Style — Judgment Calls

This skill covers only what a compiler or ArchUnit test **cannot** decide for you. If a rule can be expressed as a red test, it belongs in an architecture test instead — `voyager-fitness/src/test/java/net/elytrarace/fitness/` for the rebuild's modules, `server/src/test/java/net/elytrarace/arch/` for the tree being replaced — putting it here just duplicates a check that a violation will surface automatically. This skill exists for the questions that need a "why", not a checklist.

Baseline reality check: a repo scan found that of the ten ManisGame design rules in `CLAUDE.md`, the `server` module fully satisfies **zero** of them — three partially, seven not at all. No `sealed` interface exists under `server/`, no `package-info.java` in any of its packages, no domain exception. A documented rule nobody enforces gets ignored. So every section below explains *when the rule applies* and *when it legitimately doesn't* — not just what the rule says.

## What is already checked automatically

Don't restate these here — read the architecture tests if you need the exact assertion (`voyager-fitness/src/test/java/net/elytrarace/fitness/` for `voyager-*`, `server/src/test/java/net/elytrarace/arch/` for the old tree):

- `EcsArchitectureTest` — `*Component` implements `Component`, `*System` implements `System` + lives in a `..system..` package.
- `NamingConventionTest` — `*Factory` has only private constructors; `*ServiceImpl` implements a `*Service` interface; `*Exception` extends `RuntimeException`; `Default*` concrete classes are `final`.
- `LayerArchitectureTest` — `shared/common` never depends on `net.minestom..` or `org.bukkit..`; `server` never depends on `org.bukkit..`.

Which tree you are in decides who checks this. In the `voyager-*` modules, `NullabilityConventionTest` walks the source roots the Gradle build supplies and fails the build the moment a package with sources lacks `package-info.java` or the annotation — including a freshly split-out `exception` subpackage, see §3. Trust it; do not check by hand. In `server`, `shared/*` and `plugins/*` nothing enforces it and most packages have none, so add one when you touch a package that is missing it.

## 1. When `sealed` is worth it

`sealed` buys you an exhaustive, closed set of variants the compiler can check in a `switch`. It costs you the ability for anyone outside the `permits` list to add a variant — including yourself, from another Gradle module, because **permitted subtypes must be visible to the compiler when the sealed type itself compiles**. Since Voyager's modules depend one-way (`server` → `shared/common`/`shared/database`, never the reverse), a type in `shared/*` cannot list a `permits` class that only exists in `server` — it doesn't exist yet when `shared/*` compiles.

The rebuild hit exactly this: repository ports live in `voyager-api` and their implementations in `voyager-persistence`, so `sealed … permits DefaultPlayerRepository` across those two modules does not compile. The ports are therefore deliberately not sealed, while the provider that hands them out is — sealing the port would also forbid the in-memory fakes its tests depend on, which is the thing a port exists for.

Good, real example — a genuinely closed 2-variant domain type, both variants in the same module:

```java
// shared/common/.../cup/model/CupDTO.java
public sealed interface CupDTO permits FileCupDTO, ResolvedCupDTO {
    Key name();
    Component displayName();
}
```

Contrast with `shared/database`'s repository ports (`ElytraPlayerRepository`, `MapRecordRepository`, `GameResultRepository`): plain `public interface`, not sealed. That's correct, not an oversight — they follow the ordinary Interface+Impl convention (one production implementation), and a future test double or an implementation living in a different module must stay possible. Sealing a single-impl service/repository interface adds a permits list that will need editing the moment anyone needs a second implementation, for no compiler benefit — nothing was ever exhaustively switched over.

Ask before sealing: *is this a closed set of alternative representations someone will `switch` over, or is it "one interface, one implementation, maybe a test double"?* Only the first case earns `sealed … permits BaseX` / `non-sealed abstract class BaseX`.

## 2. Record or class

Default to `record`. Real examples already in `server/.../ecs/component/`: `ElapsedTimeComponent(long elapsedMs)`, `BracketConfigComponent(MedalBrackets brackets)`, `GameModeComponent(GameMode mode)` — all one-line, immutable, no reason to be anything else.

Put invariants in the compact constructor, not in a setter or a separate validate() call:

```java
// shared/common/.../game/scoring/MedalBrackets.java
public record MedalBrackets(double diamond, double gold, double silver, double bronze) {
    public MedalBrackets {
        if (diamond <= 0 || gold <= 0 || silver <= 0 || bronze <= 0) {
            throw new IllegalArgumentException("all bracket multipliers must be positive (...)");
        }
        if (!(diamond < gold && gold < silver && silver < bronze)) {
            throw new IllegalArgumentException("bracket multipliers must be strictly ordered (...)");
        }
    }
}
```

Note `IllegalArgumentException`, not a domain exception, is correct here — see §3 for why.

The two documented exceptions to "default record", and only these two:

- **Hibernate entities** (`shared/database/.../entity/GameResultEntity.java` etc.) — JPA requires a mutable class with a no-arg constructor. Don't fight the ORM.
- **A component proven hot on the tick path** — e.g. `RingEffectComponent`, which wraps a mutable `Queue<PendingEffect>` because per-tick allocation of a new immutable component every time an effect is queued/polled would churn garbage on the 20 TPS loop. This exception is never taken speculatively — CLAUDE.md requires an ADR before you introduce a new mutable component "because it might be hot." If you're about to add a mutable component without measuring, write the record first and profile before reopening this question.

## 3. Exception design

Throw a domain exception when the **caller is expected to catch it and branch** — `DatabaseInitializationException` (`shared/database`) signals "the connection pool couldn't come up," which `VoyagerServer` catches to fail startup cleanly with a clear message. Throw a plain `IllegalArgumentException`/`IllegalStateException` when the failure is a programming error the caller should never catch, only fix — `MedalBrackets`'s compact constructor above is the right call: nobody is meant to recover from passing `diamond > gold`, they're meant to pass valid brackets.

Naming collision to watch for: never name a domain exception `PersistenceException` — it collides with `jakarta.persistence.PersistenceException`, which is already imported all over `shared/database`. Pick a name that says what actually went wrong (`DatabaseInitializationException`, not `PersistenceException`).

**Placement:** exceptions live in an `exception` subpackage next to the domain they belong to, not in one repo-wide collection package. Each domain area gets its own, so the exception stays next to the code that throws it and the pattern scales as more modules are added:

```
net/elytrarace/api/math/Vec3.java
net/elytrarace/api/math/Aabb.java
net/elytrarace/api/math/exception/NonFiniteVectorException.java
net/elytrarace/api/math/exception/InvalidBoundingBoxException.java
net/elytrarace/api/math/exception/package-info.java
```

This keeps the domain type's file free of error-handling noise — you read `Aabb.java` for what a bounding box *is*, not for every way constructing one can fail. The `package-info.java` in the `exception` subpackage is not optional: see the `NullabilityConventionTest` pointer above — it's a real, separate package and needs its own `@NotNullByDefault`, not an inherited one from the parent package.

## 4. String messages: `String.formatted`, not concatenation

Build exception messages, assertion descriptions, and log output with `String.formatted(...)`, not `+`:

```java
// Avoid
super("bounding box minimum must not exceed its maximum on any axis, was " + min + " to " + max);

// Prefer
super("bounding box minimum must not exceed its maximum on any axis, was %s to %s".formatted(min, max));
```

The reason is readability at the read site: the shape of the final message is visible directly in the format string, instead of having to mentally re-assemble it from a chain of `+` operators. This doesn't apply to plain multi-line string-literal concatenation with nothing to interpolate — there's no format string to clarify when you're just joining fixed text.

## 5. Nullability

`@NotNullByDefault` per package is the default; `@Nullable` is the deliberate exception, not a shortcut around `Optional`. Use `@Nullable` for a single always-optional field or parameter where "absent" carries no further state of its own — `GameResultEntity` does this correctly for fields that are genuinely sometimes-null in the schema.

Reach for a `sealed` state type instead of `@Nullable`/`Optional` when "absent" isn't one flat case but several distinct ones the caller needs to branch on differently (still loading vs. failed to load vs. loaded — three different follow-up actions, not one `if (x == null)`). Don't introduce this for a plain optional value; that's over-engineering a single `@Nullable` field into a hierarchy nobody asked for. Use it when the caller currently has to smuggle "why is it null" through a side channel (a separate boolean flag, a log line, a second field) — that's the signal a flat null is hiding real state.

## 6. Interface segregation, from the caller's side

Design the interface around what the one calling system actually needs, not around everything the underlying data structure could theoretically expose. `CollisionSystem` (`plugins/game/.../system/CollisionSystem.java`) needs exactly `PlayerPositionsComponent` to do its job — it doesn't reach for an interface exposing the full player entity. When you add a new port, write the calling code first, or at least the call site, and let the method signature fall out of that — don't start from "what could a Repository/Service for X offer."

## 7. YAGNI

If a type has exactly one operation anyone calls, it doesn't need an interface — a concrete class or even a static method is enough (see `ElytraPhysics`, a `final` utility class with two static methods, no interface). Don't add `divide()`/`normalize()`/`lerp()` to a vector-like type because "a real vector class would have them" — add the operation when a caller needs it, in the same commit as that caller.

## 8. Numeric fidelity in physics code

`ElytraPhysics` (`server/.../physics/ElytraPhysics.java`) takes pitch/yaw in degrees as `double` and does `Math.toRadians` internally, mirroring the vanilla formula this repo decompiled it from. When touching flight, boost, or collision math, match the constant types and conversion path vanilla actually uses rather than "cleaning up" to whatever feels more idiomatic — a different rounding path changes ring-collision and boost outcomes in ways that are hard to notice locally and easy to notice in a bug report. Full constants table and the decompiled pseudocode are in `docs/elytra-physics-reference.md` — read it before changing anything under `server/.../physics/`, don't re-derive the formula from the Minecraft Wiki from scratch.

## 9. Test style

AssertJ (`assertThat(...)`), never `assertNotNull`/`assertEquals` from JUnit's own `Assertions`. Assert on the actual value, not just its nullness — `RingEffectComponentTest.pollEffectReturnsAndRemoves()` checks `effect.type()` and `effect.ticksRemaining()` individually rather than only `assertThat(effect).isNotNull()`.

For a `sealed` hierarchy with real behavior on each variant (not just data), write one abstract contract test class with the shared assertions, and one concrete subclass per variant that supplies the instance under test — that's how you get Liskov substitutability checked, which ArchUnit cannot do (it can check that a class exists, not that it behaves like its siblings). `CupDTO permits FileCupDTO, ResolvedCupDTO` is the first sealed hierarchy in this repo that qualifies for this pattern once either variant grows behavior beyond plain accessors — don't build the abstract test class pre-emptively for a type that's still pure data (see §7).

## 10. Patterns avoided on purpose

- **Singleton / static mutable state.** The existing `create()` factories (`CupDTOBuilder`, `*Service.create()`) exist specifically so tests can construct a fresh instance instead of reaching through a static accessor. A singleton undoes that on day one.
- **Service locator.** Pass dependencies through constructors/factories, matching the Interface+Impl convention already in use — don't add a registry that hides what a class actually needs.
- **Generic `*Manager` classes.** Name the responsibility (`CupService`, `PhaseUiHelper`), not the vagueness. A class named `Manager` is a sign the split into cohesive services hasn't happened yet.
- **Telescoping overloads instead of a builder.** `GamePhaseFactory` (`server/.../phase/GamePhaseFactory.java`) currently has five overloads of `createGamePhases()`, each delegating to the next with more `null` defaults — this is the actual example of the anti-pattern in this repo, not a hypothetical. Don't copy this shape for a new factory; use a builder or a parameter object instead. Fixing the existing one is a separate, deliberate refactor — don't do it as a drive-by while touching unrelated code in that file.

## When you're not sure

If a rule above doesn't clearly apply and you're about to invent a new exception to it, that's a judgment call worth a line in the PR description, not a silent choice — see `CLAUDE.md`'s Human-in-the-Loop checkpoints for architecture decisions.
