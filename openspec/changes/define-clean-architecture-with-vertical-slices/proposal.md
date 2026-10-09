# Proposal

**Conventional Commits:** `docs(architecture)`. Squash-merge PR title: `docs(architecture): define clean architecture with vertical slices and di`.

This change ships documentation and specs only. It moves no code. Enforcement (new ArchUnit rules) and the code moves it implies are separate changes with their own types (see "Follow-up changes").

## Why

The rebuild has five production modules and a clear dependency direction, but the rules behind that direction are not written down in one place. The package layout inside the modules mixes three organising principles: module (ring), feature (`race.scoring`, `platform.hud`) and technical layer (`platform.render`, `platform.text`, `platform.tick`). The composition root has started to hold use-case logic (`server/game/CupSession.java`, 770 lines). The DI rules are split between the greenfield design (D10), the avaje proposal and the ArchUnit suite, and no document says which ring may carry which annotation. Agents and reviewers need one normative target before the next slice (race run, cup flow, map setup) is built.

## What Changes

- Define **"Clean Architecture"** across the Gradle modules: four rings (entities, use cases, interface adapters and frameworks, composition root), the Dependency Rule, where ports live and who implements them, what data may cross a boundary, and exception policy per ring.
- Define **"Vertical Slice Architecture (VSA)"** inside those rings. Slices (flight, ring, run, flow, scoring, cup, line, catalog, hud, and future map setup and persistence) own their types end to end. Each slice maps to packages `net.elytrarace.voyager.<module>.<slice>..`. The change classifies every current technical package as slice or infrastructure and gives a slice-by-module package table.
- Define cross-slice rules: communication only through a slice's public types or events, no access to another slice's `internal` package, acyclic slice graphs, and a shared kernel limited to `api.math` and the `GameMode` discriminator.
- Define dependency injection with avaje-inject and `jakarta.inject`: which ring may carry which annotation, one composition root per deployable, one `@Factory` per slice group in `server.inject`, an explicit ordered tick pipeline, tests that construct with `new`, and no service locator and no static singletons.
- Specify the ArchUnit rules that enforce each requirement. Existing rules are named. New rules are proposed with `allowEmptyShould(false)` and handed to a follow-up change.
- Record the current violations as a migration list with file and line evidence. This change does not fix them.
- Plan the documentation: ADR-0017 (MADR 4.0), `docs/explanation/architecture.md`, a pointer in the `CLAUDE.md` "Architecture" section, a cross-reference in the greenfield design spec, and a reference update in `docs/reference/semantic-anchors.md`.
- **Refines decisions D8 and D10 of the greenfield design spec.** D8 (eight modules) is unchanged; slices are packages inside modules. D10 is refined: DI annotations (`jakarta.inject` and avaje) appear only in composition roots. The platform and the other inner rings carry none, and their classes are wired by `@Bean` methods in the root. `switch-di-to-avaje-inject` applies the same rule.
- **Out of scope:** moving code or packages, changing any test, adding fitness rules, changing `voyager-setup` (not in the build yet), changing the tree being replaced (`server` as a legacy module, `plugins/*`, `shared/*`), and the Guice-to-avaje swap itself (owned by `switch-di-to-avaje-inject`).

## Capabilities

### New Capabilities
- `architecture/module-rings`: the four rings, the Dependency Rule across Gradle modules, port placement, boundary data shapes and exception policy per ring.
- `architecture/vertical-slices`: how slices are defined, their package mapping per module, cross-slice communication, the shared kernel and the classification of technical packages.
- `architecture/dependency-injection-boundaries`: which rings may carry which DI annotation, composition-root rules, per-slice factories, the ordered tick pipeline and the ban on service locators and static singletons. Bootstrap behaviour (eager construction, refusal to listen) stays in `server/dependency-injection` from `switch-di-to-avaje-inject`; this capability does not restate it.
- `architecture/architecture-enforcement`: which ArchUnit rule enforces each architecture requirement, which rules already exist, which are proposed, and that no architecture rule may pass vacuously.

### Modified Capabilities
- None. `openspec/specs/` is empty, and no existing requirement changes.

## Impact

- **Docs (apply phase):** `docs/decisions/0017-clean-architecture-with-vertical-slices.md` (new), `docs/explanation/architecture.md` (new; the `explanation` quadrant does not exist yet), `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` (cross-reference), `docs/reference/semantic-anchors.md` (two rows), `CLAUDE.md` (one pointer in "Architecture").
- **Code:** none in this change. The migration list in `design.md` names the files a follow-up must touch.
- **Tests and fitness:** none in this change. The proposed rules become a follow-up.
- **Dependencies:** ADR-0017 cites ADR-0016 (avaje, proposed in `switch-di-to-avaje-inject`). The DI requirements assume that change lands. Nothing else depends on it.
- **Runtime:** none.

## Follow-up changes (not part of this change)

1. `add-architecture-slice-rules`, type `test(fitness)`: adds the proposed ArchUnit rules in `architecture-enforcement`. Runs after the violations it would flag are fixed, because a rule must not be merged red.
2. `move-cup-flow-out-of-server`, type `refactor(server)`: moves `server/game` use-case logic into the `cup` and `scoring` slices, and leaves wiring in `server`.
3. `regroup-platform-by-slice`, type `refactor(platform)`: moves slice-owned classes out of `platform.render`, `platform.tick` and `platform.world` into `platform.<slice>`.
4. `flatten-api-by-slice`, type `refactor(api)`: splits the flat `api.race` package and `api.physics` into `api.<slice>`.

Each follow-up needs user approval before it starts, because each renames or moves existing code.
