## Context

Voyager's agent team lives in `.claude/agents/` as 24 Markdown files with YAML frontmatter. Claude Code reads only the documented subagent fields (see https://code.claude.com/docs/en/sub-agents). Unknown fields are ignored without an error. The documentation also states that combined descriptions above 15,000 tokens trigger a startup warning, that `project` is the recommended memory scope, and that `tools` and `disallowedTools` are the way to restrict a subagent.

Verified baseline, read from the repository on 2026-11-23 (all 24 files):

- Every file has a `persona:` field, which is not a documented field. The 24 persona fields have no effect.
- Models: 16 files `model: opus`, 8 files `model: sonnet`. No file declares `effort`. Only three files declare `memory` (`voyager-researcher`, `voyager-scientist`, `voyager-tech-writer`, all `project`).
- Body length: `voyager-devops-expert.md` 1001 lines, `voyager-tech-writer.md` 779, `voyager-game-psychologist.md` 537, `voyager-researcher.md` 365. All other files are 66 to 281 lines.
- Descriptions above 60 words: `voyager-game-psychologist` (117), `voyager-project-manager` (98), `voyager-game-designer` (94), `voyager-tech-writer` (91), `voyager-researcher` (87), `voyager-product-manager` (66). The 60-word bound is this design's choice, not a documented limit. The 15,000-token startup threshold is a documented limit; the current descriptions total well below it, so the bound is about focus, not the warning.
- Stale targets, confirmed by grep:
  - `voyager-senior-testing` writes "JUnit 5". The catalog pins `junit` 6.1.2 (`settings.gradle.kts`, line 78), and `CLAUDE.md` says JUnit 6.
  - `voyager-senior-ecs` targets the legacy ECS in `legacy/shared/common`. The rebuild has no such target.
  - `voyager-paper-expert` says the setup plugin "stays on Paper" and cites Paper 1.21.11. The project memory records that Paper was dropped (decision D6). This design did not re-read D6 in the greenfield design spec; the owner should confirm before the file is deleted.
  - `voyager-devops-expert` says "Gradle 9.4". The wrapper pins Gradle 9.8.1 (`gradle/wrapper/gradle-wrapper.properties`). `CLAUDE.md` says 9.5.1. Both are stale; the new `voyager-devops` body must read the wrapper instead of hard-coding a version.
  - `voyager-senior-backend` lists "Java 21+ features". This describes language knowledge, not the target. The project targets Java 25. The new body states the target as Java 25.
- Overlaps, confirmed from the description fields: `game-developer`, `math-physics` and `senior-ecs` all claim collision and physics code; `product-manager` and `project-manager` both claim ticket and planning work; `tech-writer` and `scientist` both claim documentation and research records.
- Always-active agents: `CLAUDE.md` lines 209 to 221 require four codenames on every non-trivial task. `.claude/skills/create-pr.md` line 18 names them too. A grep for the other codenames across `.claude/skills/` found no further references; the agent IDs inside skills are re-checked at apply time.
- Brief correction: the brief says "twelve" agents, but its table has thirteen rows, including `voyager-researcher`. This design uses thirteen.

## Goals / Non-Goals

Goals:
- Every agent file conforms to the documented format, and the rules are enforced by a check rather than by review.
- Haiku subagents with an explicit effort per agent, short descriptions and focused bodies.
- Read-only reviewers enforced through `disallowedTools`.
- Persistent project memory per agent.
- A team table in `CLAUDE.md` that matches the files one to one.

Non-goals:
- No change to Java code, game behaviour, or any vertical slice.
- No rewrite of historical docs or archived OpenSpec changes.
- No change to the OpenSpec workflow or to the Haiku-subagent rule itself.

## Decisions

### D1. Thirteen focused agents, Haiku with effort

| Agent | Replaces | Tools | Disallowed | Memory | Model | Effort | Skills |
|---|---|---|---|---|---|---|---|
| `voyager-architect` | `voyager-architect` | Read, Grep, Glob, Edit, Write (docs/, ADRs) | — | project | haiku | high | — |
| `voyager-spec-writer` | `voyager-product-manager`, `voyager-project-manager` | Read, Grep, Glob, Edit, Write (openspec/) | — | project | haiku | medium | — |
| `voyager-game-designer` | `voyager-game-designer`, `voyager-game-psychologist`, `voyager-junior-creative` | Read, Grep, Glob | Edit, Write, Bash | project | haiku | high | — |
| `voyager-gameplay-developer` | `voyager-game-developer`, `voyager-math-physics`, `voyager-senior-ecs` | Read, Grep, Glob, Edit, Write, Bash | — | project | haiku | high | java-style |
| `voyager-minestom-developer` | `voyager-minestom-expert` | Read, Grep, Glob, Edit, Write, Bash | — | project | haiku | medium | java-style |
| `voyager-vanilla-reference` | `voyager-minecraft-expert` | Read, Grep, Glob, WebFetch | Edit, Write, Bash | project | haiku | medium | — |
| `voyager-backend-developer` | `voyager-senior-backend`, `voyager-database-expert` | Read, Grep, Glob, Edit, Write, Bash | — | project | haiku | medium | java-style |
| `voyager-hud-developer` | `voyager-junior-frontend` | Read, Grep, Glob, Edit, Write, Bash | — | project | haiku | medium | — |
| `voyager-test-engineer` | `voyager-senior-testing` | Read, Grep, Glob, Edit, Write, Bash | — | project | haiku | medium | java-style |
| `voyager-performance` | `voyager-java-performance` | Read, Grep, Glob, Edit, Write, Bash | — | project | haiku | high | java-style |
| `voyager-devops` | `voyager-devops-expert` | Read, Grep, Glob, Edit, Write, Bash, WebFetch | — | project | haiku | medium | — |
| `voyager-docs-writer` | `voyager-tech-writer`, `voyager-scientist` | Read, Grep, Glob, Edit, Write (docs/) | — | project | haiku | medium | — |
| `voyager-researcher` | `voyager-researcher` | Read, Grep, Glob, WebFetch, WebSearch, Context7 tools, Write (memory notes only) | Edit, Bash | project | haiku | medium | — |

Note on `voyager-researcher`: the brief gives it read and web tools and no edit rights. `Write` stays allowed so it can save its memory notes, while `Edit` and `Bash` are denied. Open question 7 covers whether to tighten this.

`voyager-architect`, `voyager-spec-writer` and `voyager-docs-writer` may write only to their own directories. The tool list cannot scope paths, so that limit is a prompt rule and the check cannot enforce it. Only the two reviewer restrictions in the spec are enforced through `disallowedTools`.

Removed: `voyager-paper-expert` (Paper dropped), `voyager-social-media` (not in the brief's team; open question), `voyager-skill-creator` (built-in skill creator), `voyager-agent-architect` (subagent documentation and this change's spec replace it).

### D2. Descriptions

Each description is at most 60 words, names the one role, and says when to delegate. Example shape: "Writes and reviews OpenSpec proposals for Voyager. Delegate when a change needs proposal, specs, design or tasks."

### D3. Bodies

Bodies state role, scope, the files the agent may touch, the rules that apply and the output shape. Reference tables, checklists and long recipes move out (D4). Target: no body above 300 lines. This is a proposal, not yet a spec requirement; see the open questions.

### D4. Where moved reference material goes

- `voyager-devops-expert` recipes (GitHub Actions pins, Docker multi-stage, CloudNet v4 templates, Trivy and Cosign steps) move to `docs/reference/devops/` as Diátaxis reference pages. The agent reads them with Read when needed.
- `voyager-game-psychologist` checklist (V-O-Y-A-G-E-R) and the theory summaries move to `docs/reference/player-psychology-checklist.md`. The game designer agent cites it.
- `voyager-researcher` tier definitions (T1 to T6) and method move to `docs/reference/research-method.md`.
- `voyager-tech-writer` MADR and Diátaxis templates move to `docs/reference/doc-templates.md`, with the existing `.claude/skills/adr.md` kept as the ADR entry point.
- Exact file names are fixed in the tasks, not here, because they depend on the docs tree at apply time.

### D5. Memory migration

The memory directories are `.claude/agent-memory/<agent>/`, as the existing directories show.

| From | To | Files |
|---|---|---|
| `voyager-researcher/` | `voyager-researcher/` (unchanged name) | `MEMORY.md`, `pm_agent_methodology.md`, `hotswap_agent_jbr.md`, `minestom_velocity_and_elytra.md`, `elytra_physics.md` |
| `voyager-tech-writer/` | `voyager-docs-writer/` | `MEMORY.md`, `adr-0011-flyway.md`, `gamemode-and-scoring-decisions.md`, `ecs_system_registration.md`, `elytra_authority_model.md`, `docs_structure.md` |
| `voyager-scientist/` | `voyager-docs-writer/` | `MEMORY.md`, `project_authority_divergence_pattern.md`, `project_ecs_migration_pattern.md` |

Two `MEMORY.md` index files merge into one `voyager-docs-writer/MEMORY.md`, with each entry kept. Notes that describe the retired ECS (`ecs_system_registration.md`, `project_ecs_migration_pattern.md`) are kept as history and flagged in their first line as describing the legacy tree. Files are moved with `git mv` so history follows them.

### D6. Enforcement check

A stdlib-only Python script `scripts/check-agent-team.py` (no third-party dependency) enforces the spec. It parses each file's frontmatter between the first two `---` lines, reads `key: value` lines and the indented continuation lines of `description`, and checks:

1. Field names against a list of documented fields, copied from the official table dated 2026-11-23. `persona` is not in it.
2. `model` equals `haiku`; `effort` is present and in the allowed set.
3. `description` has at most 60 words.
4. `memory` equals `project`.
5. For the reviewer agents, `disallowedTools` contains `Edit`, `Write`, `Bash`.
6. For the five Java agents, `skills` contains `java-style`.
7. Codename scan of agent bodies and the `CLAUDE.md` agent section, using the codename list from the old files. Historical docs are excluded.
8. The `CLAUDE.md` team table lists each file in `.claude/agents/` exactly once and no other identifier.
9. The directory holds exactly the thirteen names.

Wiring: a step in `.github/workflows/pr-lint.yml` (or `build.yml`; the owner picks) runs `python3 scripts/check-agent-team.py` on every pull request. It exits non-zero with one line per violation.

Alternative considered: a JUnit test in `voyager/fitness`. It fits the project's existing fitness module, but that module is for Java architecture rules and the check needs no Java. The Python script is smaller and cheaper to run. Open question below.

Red step: the script is written first and run against today's 24 files. It must fail on persona fields, on `opus`/`sonnet`, on missing effort, on missing memory, on the 60-word bound (6 files), on the codename scan and on the team table. Those failures are the red bar.

### D7. CLAUDE.md

The "Agent Team Workflow (MANDATORY)" section is replaced. New content: when to use the team (non-trivial OpenSpec change work), the thirteen-row table (agent, use it for), the rule that the main session delegates and reviews diffs (existing Haiku rule kept), and the OpenSpec workflow order. The four always-active agents, the codename table, the "Human in the Loop" checkpoints and the workflow pattern are either removed or moved: the checkpoints stay as a short list because they are project policy, not persona behaviour. The owner confirms this during review.

## Open questions for the owner

1. Keep `voyager-social-media`? This design removes it (community posts are not in the OpenSpec workflow). A skill could replace it later.
2. Keep `voyager-performance` separate from `voyager-gameplay-developer`? This design keeps it separate because its tools and effort differ and it is used only on tick and JVM work.
3. Effort levels: the table in D1 gives `high` to architect, game-designer, gameplay-developer, performance and researcher is `medium`. Confirm or change per agent.
4. Team size: thirteen agents, not twelve as the brief says. Confirm.
5. Enforcement location: stdlib Python script in CI (proposed) or a JUnit test in `voyager/fitness`.
6. Body length: add a requirement that no body exceeds 300 lines? Not in the spec yet.
7. `voyager-researcher` memory writes: keep Write allowed (proposed) or deny it and let the harness write memory?
8. Confirm that Paper was dropped (D6) before `voyager-paper-expert` is deleted, since the agent body says the opposite.
