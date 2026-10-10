# ADR-0017: Organise the rebuild in Clean Architecture rings and Vertical Slices

## Status

Accepted

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). Decided the cup move and the fold of progress into the `ring` slice on 2026-10-09,
  and accepted this record on 2026-10-10.
- Atlas (architect, drafted this record with the change `define-clean-architecture-with-vertical-slices`)

## Context and problem statement

The greenfield rebuild has six Gradle modules in production code: `voyager-api`, `voyager-physics`, `voyager-race`,
`voyager-platform`, `voyager-server` and `voyager-setup`. The module graph follows decision D8 of the greenfield design
(`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`). Inside the modules, the packages mix three
organising principles: the module (a ring), the feature (`race.scoring`, `platform.hud`) and the technical layer
(`platform.render`, `platform.text`, `platform.tick`). The rules behind the module graph and the package layout are
not written down in one place.

Two concrete symptoms exist on `main`:

- The composition root holds use-case logic. `voyager-server/src/main/java/net/elytrarace/voyager/server/game/CupSession.java`
  (808 lines) computes map scores, cup standings and medal outlooks, and it imports `race.scoring` directly.
- Feature code is split across technical packages. The line renderer sits in `platform.render`, the flight tick in
  `platform.tick`, and the race-pass and progress logic is spread over `race.collision`, `race.progress` and `race.effect`.

Dependency injection rules are split between decision D10 of the greenfield design, ADR-0016 and the ArchUnit suite.
No document says which ring may carry which DI annotation. Agents and reviewers need one normative target before the
next slice (race run, cup flow, map setup) is built.

## Decision drivers

- Gradle must enforce the direction of dependencies at compile time, not review alone
- A whole race must run in a plain JUnit test, without a Minestom server, a socket or a file
- A feature must be findable in one place, so a contributor can place a new class without a search
- DI annotations must not leak into the inner rings, and wiring must stay in one composition root per deployable
- Every rule must map to a rule that fails the build, or to a named follow-up change
- The change must not move code. Moves are separate changes with their own Conventional Commits types

## Considered options

1. **Status quo.** Keep the mixed package layout and rely on review.
2. **Clean Architecture only.** Four rings as Gradle modules, with packages inside each module organised by layer.
3. **Vertical Slices only.** One package per feature, with no ring rule between modules.
4. **Rings as Gradle modules, slices as packages inside each module.** Clean Architecture for the direction of
   dependencies, Vertical Slice Architecture for the ownership of types.
5. **One module per slice.** Each feature gets its own Gradle module.

## Decision outcome

Chosen option: **4, rings as Gradle modules and slices as packages**, because it is the only option that enforces both
the direction of dependencies (through Gradle and ArchUnit) and the ownership of a feature (through packages that a
contributor can find and that ArchUnit can check for cycles and internal access).

The decision has four parts.

**Rings.** Four rings, numbered from the innermost.

| Ring | Modules | Rule |
|---|---|---|
| 1 entities | `voyager-api`, `voyager-physics` | No framework type, no file I/O, no DI annotation |
| 2 use cases | `voyager-race` | No framework type, no DI annotation; constructed with `new` |
| 3 adapters and frameworks | `voyager-platform` (later `voyager-persistence`) | Minestom, Xerus, Gson, Falco and JDBC live here only; no DI annotation |
| 4 composition roots | `voyager-server`, `voyager-setup` | Wire, sequence and start the slices; compute no score, standing or ordering |

The allowed module edges are: `voyager-physics` to `voyager-api`; `voyager-race` to `voyager-api` and
`voyager-physics`; `voyager-platform` to `voyager-api`, `voyager-physics` and `voyager-race`; `voyager-persistence` to
`voyager-api`; `voyager-setup` to `voyager-api` and `voyager-platform`; `voyager-server` to every rebuild module.
Ports are declared in the innermost module that calls them, or in `voyager-api` when more than one module calls them,
and they are implemented in an outer ring.

**Slices.** A slice is a feature column that owns its use-case types, ports and adapters in every ring it touches. Its
packages are `net.elytrarace.voyager.<module>.<slice>..`. The slices are `flight`, `ring`, `run`, `flow`, `scoring`,
`cup`, `line`, `catalog`, `hud` and `mapsetup`, and the future slice `record` in `voyager-persistence`. A module with one
slice uses its root package as that slice. Slice contracts used by another module are declared in
`net.elytrarace.voyager.api.<slice>..`.

Decisions taken by the owner on 2026-10-09:

- The cup flow (`CupSession`, `CupStandings`, `CupResolution`) moves from `server/game` into a new `race.cup` slice.
  `voyager-server` keeps wiring only.
- `progress` folds into the `ring` slice together with `collision` and `effect`. A separate `progress` package would
  close a cycle through `run` and `scoring`.

Decisions taken by default on 2026-10-10, owner may revise:

- The slice names `catalog` and `hud` stay.
- `api.physics` keeps its name for now. A rename to `api.flight` belongs to the `flatten-api-by-slice` follow-up.

**Cross-slice rules.** A slice calls another slice only through its root package and its `exception` package. A helper
that must stay hidden lives in `<slice>.internal`, and no class outside the slice imports it. The slice graph inside a
module is acyclic. The shared kernel is limited to `net.elytrarace.voyager.api.math` and the `GameMode` discriminator.
Inside a tick, slices talk through method calls that the composition root sequences.

**Dependency injection.** The rules of decision D10 are refined as follows (decision record: ADR-0016).

- `jakarta.inject` annotations and avaje-inject types (`@Factory`, `@Bean`, `@External`, `BeanScope`) appear only in the
  composition roots, in their `inject` packages and in the process entry point (`VoyagerServer`, `SetupServer`).
- Rings 1 to 3 carry no DI annotation. A `@Bean` method in the composition root calls their constructors.
- One composition root per deployable. `voyager-server` and `voyager-setup` do not share a root.
- One `@Factory` class per slice group, named for the slice (for example `RaceBeans`, `CupBeans`). Factories construct
  objects and contain no game logic.
- The per-tick steps are one unmodifiable ordered `List` built in one `@Bean` method. Injected collections are not used
  for steps, because their order is not guaranteed.
- No service locator outside a composition root, and no mutable static field in any rebuild module.

This record does not decide the migration. The migration list of 22 open items is recorded in the change's `design.md`
and summarised in `docs/explanation/architecture.md`. Each item belongs to a follow-up change with its own type.

## Consequences

**Positive**

- Dependency direction is checked by Gradle at compile time, and by ArchUnit for the rules Gradle cannot express.
- A feature is one package per module. A new contributor places a class by its feature first and its ring second.
- Use cases run in plain JUnit tests. The cup logic can move out of the server without a Minestom instance.
- DI placement is one rule per ring. A reviewer checks an annotation against the ring of the class, not against a list.

**Negative**

- The current code does not follow the rules yet. Twenty-two migration items are open, among them the cup use case in
  `voyager-server` and the platform packages that hold slice content. Until the follow-ups land, the composition root
  and some platform packages break the rules, and the slice rules are held back.
- Folding `progress` into `ring` makes the `ring` slice bigger. The change accepts this, because ring passes, progress
  and effects change together.
- Two packages of `voyager-setup` (`setup.adapter`) hold Minestom code inside a composition-root module. This breaks
  the rule that platform code lives in `voyager-platform`, and the layered ArchUnit rule cannot see it.
- The slice table must be kept in step with every follow-up that moves a package. Each follow-up updates the table in
  `design.md` and in `docs/explanation/architecture.md` in the same pull request.

**Neutral**

- The decision refines decisions D8 and D10 of the greenfield design. D8 (eight modules) is unchanged, and slices are
  packages inside the modules. D10 keeps avaje-inject and gains the rule that DI annotations appear only in the
  composition roots.
- The greenfield statement "ECS at the tick layer only" is not changed by this record. The rebuild has no ECS framework
  today. Its tick is the ordered step list described above. Whether the word ECS stays in the greenfield design is a
  question for that document's owner.

## Confirmation

- The rules that exist today are named in `docs/explanation/architecture.md` and in the change's `specs/`. Each one
  is an `@ArchTest` in `voyager-fitness`: `ApiPurityTest` (purity and platform isolation), `DesignRuleTest` (records,
  exceptions and their subpackages), `NullabilityConventionTest` (`@NotNullByDefault` on packages) and
  `FitnessCoverageTest` (every module is imported and named by a rule).
- The proposed rules are the follow-up `add-architecture-slice-rules` (`test(fitness)`). Each uses
  `allowEmptyShould(false)`. A rule enters the suite only after the violations it would flag are fixed.
- `openspec validate define-clean-architecture-with-vertical-slices --strict` checks the change artifacts.
- A review checks each new class against the table of rings and the slice table before merge.

## More information

- Refines decisions D8 and D10 of `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`.
- Depends on [ADR-0016](0016-replace-guice-with-avaje-inject.md) for the DI container.
- Related: [ADR-0018](0018-pose-placement-authoring-model-for-setup.md) (setup authoring model) and
  [ADR-0019](0019-pin-catalogue-snapshot-per-round.md) (catalogue reload).
- Explanation for readers: `docs/explanation/architecture.md`.
- Normative specs and the migration list: `openspec/changes/archive/2026-10-10-define-clean-architecture-with-vertical-slices/`.
  The archive step moves this folder and updates the links.
- Anchors: "Clean Architecture" and "Vertical Slice Architecture (VSA)" in `docs/reference/semantic-anchors.md`.
