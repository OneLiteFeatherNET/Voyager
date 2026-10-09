# Semantic anchors

Semantic anchors are established terms, such as methodologies and principles, that name a whole
concept in one phrase. When a prompt names an anchor exactly, an LLM loads the full concept behind
it, so one word replaces a paragraph of description. The catalog lives at
[Semantic Anchors](https://llm-coding.github.io/Semantic-Anchors/) and holds about 235 terms.

Voyager uses anchors as a shared vocabulary for humans and agents.

## Rules

- Use the exact anchor name, in quotes, in prompts, specs, ADRs, agent definitions and OpenSpec
  artifacts.
- Do not paraphrase an anchor. Write "Clean Architecture", not "layered architecture with inward
  dependencies".
- Add an anchor to this page only after you check its exact spelling on the catalog.
- Mark an anchor as "candidate" until the code, a spec or a decision applies it.

## Architecture

| Anchor (exact) | How Voyager applies it | Where it is enforced or used |
|---|---|---|
| Vertical Slice Architecture (VSA) | The rebuild organises behaviour in slices: race, ring, cup and map setup. | `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md` |
| Clean Architecture | Dependencies point inward. `voyager-api` holds no implementation, and Minestom appears only in the platform and server adapters. | `voyager-api`, `voyager-fitness/src/test/java/net/elytrarace/fitness/ApiPurityTest.java` |
| Hexagonal Architecture (Ports & Adapters) | Domain ports live in `voyager-api`, and `voyager-platform` supplies the Minestom adapters. | `voyager-platform`, `voyager-fitness` |
| Domain-Driven Design according to Evans | Domain packages (`race`, `physics`, `math`) use the ubiquitous vocabulary of the game. | `voyager-api/src/main/java/net/elytrarace/voyager/api/` |
| Tracer Bullet | Candidate. Use it for the first flyable end-to-end slice. | Not yet applied |
| Walking Skeleton | Candidate. Use it for the first build that starts a server and loads a map. | Not yet applied |
| ADR according to Nygard | Candidate. Current decision records use MADR, see Documentation. | Not yet applied |

## Design principles

| Anchor (exact) | How Voyager applies it | Where it is enforced or used |
|---|---|---|
| SOLID Principles | Interfaces stay small and depend on abstractions. | `.claude/skills/java-style/SKILL.md` (judgment) |
| SOLID-Dependency Inversion Principle | Services depend on `voyager-api` interfaces, not on Minestom types. | `voyager-fitness` (ArchUnit) |
| Separation of Concerns | Each `voyager-*` module owns one concern. | `settings.gradle.kts`, `FitnessCoverageTest` |
| Defensive Programming according to McConnell | Record compact constructors check invariants before use. | `voyager-api/src/main/java/net/elytrarace/voyager/api/` |
| Code Smells | Review criterion for refactoring. | Code review |
| Refactoring Catalog according to Fowler | Named refactorings guide structural changes. | Code review |

## Testing

| Anchor (exact) | How Voyager applies it | Where it is enforced or used |
|---|---|---|
| Testing Pyramid | Many unit tests in `voyager-api`, with architecture rules at the top in `voyager-fitness`. | `voyager-api/src/test`, `voyager-fitness/src/test` |
| Red/Green TDD | New behaviour starts with a failing test, then production code. | Review (F.I.R.S.T. criteria, see below) |
| TDD, Chicago School | Applied in unit tests: state-based assertions on real collaborators, no mocks of our own types. Matches F.I.R.S.T. | `.claude/agents/voyager-senior-testing.md` |
| Arrange-Act-Assert (AAA) | Test bodies follow arrange, act, assert in that order. | `voyager-api/src/test` |
| Property-Based Testing | Candidate for numeric code in `math` and `physics`. | Not yet applied |

Candidate, not yet applied: "TDD, London School" (mock-based interaction tests). Use it only after a
module decides to test interactions instead of state, and record that choice in the module's docs.

## Requirements

| Anchor (exact) | How Voyager applies it | Where it is enforced or used |
|---|---|---|
| EARS-Requirements | Acceptance criteria use the EARS sentence patterns. | `openspec/config.yaml` |
| MoSCoW | Requirement priorities are sorted by MoSCoW. | `openspec/config.yaml` |
| User Story Mapping | Candidate. Story maps are not yet in the repository. | Not yet applied |

## Documentation

| Anchor (exact) | How Voyager applies it | Where it is enforced or used |
|---|---|---|
| Diátaxis Framework | `docs/` splits into tutorial, how-to, reference and explanation pages. | `docs/reference/`, `docs/guides/` |
| MADR | Architecture decisions use MADR 4.0. | `docs/decisions/` |
| Docs-as-Code according to Ralf D. Müller | Documentation lives in the repository and changes through pull requests. | `docs/` |
| arc42 Architecture Documentation | Candidate for a system overview once the rebuild lands. | Not yet applied |
| C4-Diagrams | Candidate for context and container diagrams. | Not yet applied |

## Workflow

| Anchor (exact) | How Voyager applies it | Where it is enforced or used |
|---|---|---|
| Conventional Commits | Commit and pull request titles follow `type(scope): summary`. | `CLAUDE.md` (Code Conventions) |
| Thin Vertical Slice | Candidate for splitting epics into deliverable slices. | Not yet applied |

## Problem solving

Candidates for analysing a problem before a fix. None is applied yet.

- Five Whys (Ohno)
- Premortem
- Chesterton's Fence (before removing old code in the tree being replaced)

## Project terms not in the catalog

These terms are project rules, not catalog anchors. Use them by name, and do not invent a catalog
entry for them.

- **F.I.R.S.T.** (Fast, Independent, Repeatable, Self-validating, Timely). Ottinger and Schuchert,
  "Agile in a Flash"; Robert C. Martin, "Clean Code" chapter 9. Tests are reviewed against these
  five criteria. No mechanical check exists yet.
- **The ten ManisGame design rules.** Sealed hierarchies, factories, registries, components as
  records, `@NotNullByDefault`, enum DSL, injectable creators, Gson adapters, exception hierarchy
  and the module API boundary. Full list and enforcement: `CLAUDE.md` (Design Reference Rules).
  Rules 5 and 9 are enforced by `NullabilityConventionTest` and `DesignRuleTest` in `voyager-fitness`.
