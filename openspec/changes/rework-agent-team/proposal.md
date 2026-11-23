## Conventional Commits

- Type and scope: `refactor(agents)`. The change ships as one code PR `refactor(agents): recut the agent team to the subagent format`.
- This proposal PR is `docs(openspec): propose rework-agent-team` and contains only files under `openspec/changes/rework-agent-team/`.

## Why

The 24 agent files in `.claude/agents/` do not match the subagent format Claude Code documents. Every file carries a `persona:` field that Claude Code silently ignores, 16 agents run on `opus` and 8 on `sonnet` although the team rule is Haiku subagents with a per-task effort, and four bodies run to 365 to 1001 lines and load on every spawn. Several roles overlap, some content targets retired code (Paper, the legacy ECS, JUnit 5), and `CLAUDE.md` still requires four always-active agents by codename even though OpenSpec now carries the ticket, the docs and the evidence for each change.

## What Changes

- **BREAKING** Replace the 24 agent files with a team of thirteen `voyager-*` agents, each with `model: haiku`, an `effort` level, `memory: project`, a short description and a focused body. No persona names remain in agent files or in `CLAUDE.md`.
- **BREAKING** Remove `voyager-paper-expert`, `voyager-social-media`, `voyager-skill-creator` and `voyager-agent-architect`. The built-in skill creator and the subagent documentation cover the last two.
- Restrict reviewers to read-only through `disallowedTools` (game designer, vanilla reference).
- Move long reference material out of agent bodies into `docs/` or a skill.
- Rewrite the "Agent Team Workflow" section of `CLAUDE.md` around OpenSpec: no mandatory four, no codename table, one short table of the thirteen agents and when to use each.
- Migrate the existing notes in `.claude/agent-memory/` into the memory directories of the successor agents.
- Update `.claude/skills/create-pr.md`, which names the four always-active agents.
- Add an agent check that fails on the rules above, wired into CI.
- Leave historical documents (`docs/decisions`, `docs/research`, archived OpenSpec changes) unchanged, including their codenames.

## Capabilities

### New Capabilities

- `tooling/agent-team`: the subagent team that works on Voyager. Covers the agent file format, the model and effort rules, reviewer restrictions, memory scope, the list of agents and the CLAUDE.md team table. It is a single durable capability because all these rules describe one team.

### Modified Capabilities

None. No existing capability under `openspec/specs/` describes the agent team.

## Impact

- `.claude/agents/`: 24 files replaced by 13 (4 deleted, 13 created or rewritten).
- `.claude/agent-memory/`: three directories folded into two new ones.
- `.claude/skills/create-pr.md`: codename references removed.
- `CLAUDE.md`: "Agent Team Workflow" section rewritten. No other section changes.
- `docs/`: one how-to guide on using the team, plus reference files that receive the moved material.
- CI: one step runs the agent check (wiring decided in design.md).
- No Java code, no game behaviour, no greenfield design decision changes. No vertical slice (race, ring, cup, map setup) is touched.
- Out of scope: renaming historical codenames in `docs/decisions`, `docs/research` and archived changes; changing the OpenSpec workflow itself; changing the Haiku-subagent rule.

## Trade-offs

### Pros

- Format-conformant: no field is silently ignored, so the stated model, effort and memory settings actually take effect.
- Cheaper: Haiku with per-agent effort and shorter bodies reduce cost per spawn.
- Clearer delegation: no overlapping roles, and each description names one job.
- Enforced read-only reviewers through `disallowedTools`, not only through prompt text.
- Persistent per-agent memory in the repository, so knowledge survives sessions.
- The workflow matches OpenSpec: ticket, docs and evidence come from the change itself.

### Cons

- Loses some specialist depth: the psychology checklist and the scientist's research-paper voice are reduced to reference material and a docs-writer role.
- The codenames used in older docs and on the stream overlay stop matching the team; history is kept, but new readers must map old names to new IDs.
- Migration effort: 24 files change, memory is moved, and the CI check must be written first.
- Merged roles (spec-writer, backend-developer, docs-writer) have broader prompts and may need tuning.
- Haiku may be weaker on hard architecture or physics work. Effort `high` mitigates this, and the owner can override the model per call.
