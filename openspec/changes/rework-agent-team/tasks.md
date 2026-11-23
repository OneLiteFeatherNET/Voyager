## 1. Check first (red)

- [ ] 1.1 Write `scripts/check-agent-team.py` (stdlib only) implementing the rules in `specs/tooling/agent-team/spec.md`, with unit tests that use temp directories and no wall-clock or network access (F.I.R.S.T.).
- [ ] 1.2 Run the check against the 24 current agent files and record the failures (persona field, model, missing effort, missing memory, description bound, codenames, team table) as the red bar in the code PR body.

## 2. Reference material leaves the agent bodies

- [ ] 2.1 Move the `voyager-devops-expert` recipes to `docs/reference/devops/` as Diátaxis reference pages, and drop the hard-coded Gradle version in favour of reading `gradle/wrapper/gradle-wrapper.properties`.
- [ ] 2.2 Move the V-O-Y-A-G-E-R checklist and theory summaries from `voyager-game-psychologist` to `docs/reference/player-psychology-checklist.md`.
- [ ] 2.3 Move the T1 to T6 tiers and method from `voyager-researcher` to `docs/reference/research-method.md`, and the MADR and Diátaxis templates from `voyager-tech-writer` to `docs/reference/doc-templates.md`.

## 3. Memory migration

- [ ] 3.1 Move the `voyager-tech-writer` and `voyager-scientist` notes into `.claude/agent-memory/voyager-docs-writer/` with `git mv`, merge the two `MEMORY.md` indexes into one, and flag the legacy-ECS notes as history in their first line.
- [ ] 3.2 Keep `.claude/agent-memory/voyager-researcher/` under its existing name and confirm its index still resolves.

## 4. New agent files (each step turns part of the check green)

- [ ] 4.1 Write `voyager-architect` and `voyager-spec-writer` (Haiku, effort set per the design table, `memory: project`, short description).
- [ ] 4.2 Write the reviewers `voyager-game-designer` and `voyager-vanilla-reference` with `disallowedTools: Edit, Write, Bash` (game designer) and `Edit, Write, Bash` (vanilla reference).
- [ ] 4.3 Write the Java developers `voyager-gameplay-developer`, `voyager-minestom-developer`, `voyager-backend-developer`, `voyager-hud-developer`, `voyager-test-engineer` and `voyager-performance`, the five Java ones preloading `java-style`. The gameplay developer targets the rebuild, not `legacy/shared/common`, and the test engineer uses JUnit 6.
- [ ] 4.4 Write `voyager-devops`, `voyager-docs-writer` and `voyager-researcher` with the moved reference paths from section 2.
- [ ] 4.5 Delete the 24 old agent files, including the four removed agents (`voyager-paper-expert`, `voyager-social-media`, `voyager-skill-creator`, `voyager-agent-architect`).

## 5. Skills, CLAUDE.md and the team guide

- [ ] 5.1 Update `.claude/skills/create-pr.md` to drop the codename references, and grep `.claude/skills/` for agent IDs that no longer exist.
- [ ] 5.2 Replace the "Agent Team Workflow (MANDATORY)" section of `CLAUDE.md` with the OpenSpec-based section: the thirteen-row table, the delegation rule, the short list of human checkpoints. Remove the codename table and the four always-active agents.
- [ ] 5.3 Write `docs/how-to/use-the-agent-team.md` (Diátaxis how-to): when to delegate, which agent to pick, how to read a result, and how the check is run.

## 6. Enforce, validate and open the code PR

- [ ] 6.1 Add a step that runs `python3 scripts/check-agent-team.py` to `.github/workflows/pr-lint.yml` (or `build.yml`, per the owner's answer to design.md question 5). The check is green on the new team.
- [ ] 6.2 Run the script's unit tests, the check, and `openspec validate rework-agent-team --strict`, and record the output in the PR body.
- [ ] 6.3 Open the code PR titled `refactor(agents): recut the agent team to the subagent format`, containing only the implementation (no file under `openspec/`), based on the merged proposal. This task is last.
