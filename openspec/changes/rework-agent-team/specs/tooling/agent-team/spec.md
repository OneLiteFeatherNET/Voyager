## Purpose

Defines the Claude Code subagent team that works on Voyager: which agent files exist, how each is formatted, what each may do, and how the team is documented and checked, so that delegation stays predictable and cheap.

## ADDED Requirements

### Requirement: Agent files use only documented frontmatter fields

MUST: every file in `.claude/agents/` SHALL contain only frontmatter fields documented for Claude Code subagents (`name`, `description`, `tools`, `disallowedTools`, `model`, `effort`, `memory`, `skills`, and the other documented fields). The field `persona` MUST NOT appear in any agent file, because Claude Code silently ignores unknown fields.

#### Scenario: Persona field is rejected
- **WHEN** the agent check runs against an agent file that contains a `persona:` line in its frontmatter
- **THEN** the check fails and names the file and the offending field

#### Scenario: Documented fields pass
- **WHEN** every agent file contains only documented frontmatter fields
- **THEN** the check reports no unknown-field violation

### Requirement: Every agent runs on Haiku with an explicit effort

MUST: every agent file SHALL declare `model: haiku` and an `effort` value from `low`, `medium`, `high`, `xhigh` or `max`. Agents MUST NOT declare any other model.

#### Scenario: Agent without effort fails
- **WHEN** the check runs against an agent file that omits `effort`
- **THEN** the check fails and names the file

#### Scenario: Agent on a non-Haiku model fails
- **WHEN** the check runs against an agent file with `model: opus` or `model: sonnet`
- **THEN** the check fails and names the file and the model

### Requirement: Agent descriptions are short and single out one agent

MUST: each agent `description` SHALL contain at most 60 words and SHALL state one role that distinguishes the agent from the others. SHOULD: the description names the situations in which Claude should delegate to the agent.

#### Scenario: Long description fails
- **WHEN** the check runs against an agent whose description exceeds 60 words
- **THEN** the check fails and reports the word count and the file

#### Scenario: Short description passes
- **WHEN** every description contains at most 60 words
- **THEN** the check reports no description violation

### Requirement: Reviewer agents cannot modify files

MUST: the reviewer agents `voyager-game-designer` and `voyager-vanilla-reference` SHALL deny the `Edit`, `Write` and `Bash` tools through `disallowedTools`. A prompt instruction alone MUST NOT be the only restriction.

#### Scenario: Game designer cannot write
- **WHEN** the check reads the `voyager-game-designer` frontmatter
- **THEN** `disallowedTools` contains `Edit`, `Write` and `Bash`

#### Scenario: Vanilla reference cannot write
- **WHEN** the check reads the `voyager-vanilla-reference` frontmatter
- **THEN** `disallowedTools` contains `Edit`, `Write` and `Bash`

### Requirement: Java-writing agents preload the java-style skill

MUST: the agents `voyager-gameplay-developer`, `voyager-minestom-developer`, `voyager-backend-developer`, `voyager-test-engineer` and `voyager-performance` SHALL list `java-style` under `skills`, so the judgment-call guidance is loaded at spawn instead of copied into the agent body.

#### Scenario: Java agent without the skill fails
- **WHEN** the check runs against one of the five named agents and its `skills` do not include `java-style`
- **THEN** the check fails and names the file

### Requirement: Every agent uses project memory

MUST: every agent file SHALL declare `memory: project`, so knowledge an agent accumulates is versioned with the repository and shared by the team.

#### Scenario: Agent without project memory fails
- **WHEN** the check runs against an agent file without `memory: project`
- **THEN** the check fails and names the file

### Requirement: Agent bodies and CLAUDE.md carry no persona codenames

MUST: neither the bodies of agent files in `.claude/agents/` nor the agent section of `CLAUDE.md` SHALL use a persona codename (for example Compass, Pulse, Scribe, Lumen, Atlas, Helix, Thrust) as an identifier or as a delegation target. Historical documents under `docs/` and archived OpenSpec changes are excluded and keep their wording.

#### Scenario: Codename in an agent body fails
- **WHEN** the check scans an agent body and finds a persona codename
- **THEN** the check fails and reports the file and line

#### Scenario: Historical documents are not scanned
- **WHEN** a codename appears under `docs/decisions/`, `docs/research/` or `openspec/changes/archive/`
- **THEN** the check does not report it

### Requirement: CLAUDE.md lists exactly the agent files

MUST: the team table in the agent section of `CLAUDE.md` SHALL list each agent in `.claude/agents/` exactly once, and SHALL NOT list an agent that has no file. Each row states the agent identifier and when to use it.

#### Scenario: Missing row fails
- **WHEN** an agent file has no row in the `CLAUDE.md` team table
- **THEN** the check fails and names the agent

#### Scenario: Extra row fails
- **WHEN** the `CLAUDE.md` team table lists an identifier with no file in `.claude/agents/`
- **THEN** the check fails and names the identifier

### Requirement: The team has thirteen agents

MUST: `.claude/agents/` SHALL contain exactly thirteen agent files, and the four retired agents MUST NOT exist there.

#### Scenario: Team names
- **WHEN** the check lists the files in `.claude/agents/`
- **THEN** the names are `voyager-architect`, `voyager-spec-writer`, `voyager-game-designer`, `voyager-gameplay-developer`, `voyager-minestom-developer`, `voyager-vanilla-reference`, `voyager-backend-developer`, `voyager-hud-developer`, `voyager-test-engineer`, `voyager-performance`, `voyager-devops`, `voyager-docs-writer` and `voyager-researcher`

#### Scenario: Removed agent files are absent
- **WHEN** the change is complete
- **THEN** no file named `voyager-paper-expert.md`, `voyager-social-media.md`, `voyager-skill-creator.md` or `voyager-agent-architect.md` exists in `.claude/agents/`

### Requirement: Agent memory is migrated and retained

MUST: the existing memory notes under `.claude/agent-memory/voyager-researcher/`, `voyager-scientist/` and `voyager-tech-writer/` SHALL be moved to the memory directory of the successor agent (`voyager-researcher`, `voyager-docs-writer`) without loss, and the old directories SHALL be removed.

#### Scenario: Notes survive the migration
- **WHEN** the migration is complete
- **THEN** every note file that existed before the change exists under exactly one new agent memory directory, and no old agent memory directory remains
