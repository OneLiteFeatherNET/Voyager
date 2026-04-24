# Work with the Voyager agent team

The Voyager agent team is a roster of 23 specialized Claude Code agents defined in `.claude/agents/`. You delegate work to these agents to parallelize expertise — architecture, Minestom internals, ECS, database, documentation, and more — instead of tackling every task alone. This guide documents the team roster and the two conventions that make cross-agent coordination readable: codenames and peer networks.

## Codename convention

Every agent has two identifiers:

- **Agent ID** (for example, `voyager-tech-writer`) — the technical handle. You use this to invoke the agent.
- **Codename** (for example, Scribe) — the persona. Agents use this when referencing peers, writing reports, and signing off cross-agent messages.

Codenames draw from a flight, racing, and craftsmanship vocabulary that fits the Voyager theme. Compass (voyager-product-manager) navigates scope, Thrust (voyager-game-developer) builds forward motion, Forge (voyager-senior-backend) shapes services, and Loom (voyager-agent-architect) weaves the team itself. The persona adds character to agent-to-agent dialogue without replacing the canonical ID.

Each agent file declares its codename in the frontmatter as `persona:` and opens its prompt body with a line introducing the persona.

## Team roster

Your always-active agents shepherd every significant task from intake to sign-off:

| Codename | Agent ID | Role | Model |
|---|---|---|---|
| Compass | `voyager-product-manager` | Tracks tickets, defines acceptance criteria, validates delivery | Sonnet |
| Pulse | `voyager-game-psychologist` | Reviews gameplay decisions for retention, flow, and engagement | Opus |
| Scribe | `voyager-tech-writer` | Writes and maintains `docs/` content, ADRs, and migration guides | Opus |
| Lumen | `voyager-scientist` | Records methodology and findings in `docs/research/` | Opus |

Architecture and research specialists set direction before implementation starts:

| Codename | Agent ID | Role | Model |
|---|---|---|---|
| Atlas | `voyager-architect` | Owns architecture decisions, module boundaries, and ADR content | Opus |
| Scout | `voyager-researcher` | Gathers current upstream docs through Context7, WebSearch, WebFetch | Opus |
| Loom | `voyager-agent-architect` | Creates and tunes agents in `.claude/agents/` | Opus |
| Anvil | `voyager-skill-creator` | Creates reusable slash-command skills in `.claude/skills/` | Sonnet |

Domain experts answer platform-specific questions:

| Codename | Agent ID | Role | Model |
|---|---|---|---|
| Helix | `voyager-minestom-expert` | Minestom API, instance management, event routing | Opus |
| Bedrock | `voyager-minecraft-expert` | Vanilla mechanics, elytra physics, protocol, collision rules | Opus |
| Origami | `voyager-paper-expert` | Setup plugin, Paper API, MockBukkit tests | Sonnet |
| Vault | `voyager-database-expert` | Hibernate entities, HikariCP, MariaDB schema | Opus |
| Hangar | `voyager-devops-expert` | CI/CD, GitHub Actions, CloudNet v4, Docker | Opus |
| Beacon | `voyager-social-media` | Community posts, announcements, changelog summaries | Sonnet |

Game design and development cover the player-facing loop:

| Codename | Agent ID | Role | Model |
|---|---|---|---|
| Drift | `voyager-game-designer` | Gameplay loops, balancing, ring and map design | Opus |
| Thrust | `voyager-game-developer` | Physics code, ring collision, scoring, cup flow | Opus |
| Vector | `voyager-math-physics` | 3D geometry, collision algorithms, spline math | Opus |

Senior engineers carry the patterns and test culture:

| Codename | Agent ID | Role | Model |
|---|---|---|---|
| Forge | `voyager-senior-backend` | Services, repositories, adapters, Java patterns | Opus |
| Lattice | `voyager-senior-ecs` | ECS components, systems, EntityManager, tick budgets | Opus |
| Quench | `voyager-senior-testing` | JUnit 5 tests, coverage, test architecture | Sonnet |
| Piston | `voyager-java-performance` | JVM tuning, GC, profiling, benchmarks | Opus |

Specialists cover the remaining surfaces:

| Codename | Agent ID | Role | Model |
|---|---|---|---|
| Glint | `voyager-junior-frontend` | Scoreboards, BossBars, action bar, sounds, particles | Sonnet |
| Spark | `voyager-junior-creative` | Creative prototypes, edge cases, wild ideas | Sonnet |

## Peer network convention

Each agent file ends with a `## Peer Network` section that names the peers it hands off to or pulls in. Entries use a dual identifier so you can read the personality and still copy the invokable ID:

```markdown
- **Atlas** (voyager-architect) — on every ADR. Atlas owns the decision content; I own MADR structure, sentence case, and consequences wording.
```

The format is `**<Codename>** (voyager-<id>) — when <trigger>, because <reason>`. The trigger explains the situation that warrants the handoff; the reason clarifies which slice of the work each agent owns. You find this section at the end of every file under `.claude/agents/voyager-<id>.md`.

Peer networks keep collaboration explicit. When Scribe writes an ADR, the peer network tells you Atlas joins for decision content. When Thrust touches ring collision, the peer network points to Vector for geometry review.

A minimal peer-network section looks like this:

```markdown
## Peer Network
Pull in or hand off to these specialists when the task crosses my scope:

- **Atlas** (voyager-architect) — on every ADR. Atlas owns the decision content; I own MADR structure, sentence case, and consequences wording.
- **Lumen** (voyager-scientist) — when a topic deserves a research paper in docs/research/ instead of a how-to. We hand off by document type.
- **Compass** (voyager-product-manager) — when a shipped ticket needs CHANGELOG and migration-guide updates. Compass hands me the merged scope.
```

Read the peer network before you start a task. If your own scope intersects another agent's trigger, pull that peer in rather than reimplementing their expertise.

## Using an agent

You invoke an agent through its `voyager-*` ID — codenames are not valid invocation targets. Codenames surface in reports, peer-network cross-references, and human-readable conversation, where they make multi-agent threads easier to scan.

For example, you invoke the tech writer as `voyager-tech-writer`, and Scribe signs the resulting documentation. You invoke the game developer as `voyager-game-developer`, and Thrust reports back on the ring collision change. The ID drives execution; the codename drives readability.

> [!NOTE]
> The mandatory workflow, always-active responsibilities, and human-in-the-loop checkpoints live in [Agent Team Workflow](../../CLAUDE.md#agent-team-workflow-mandatory). This guide documents the roster and conventions only; it does not duplicate the workflow rules.

## Pick the right agent

Match the task surface to the roster:

- Architecture or module boundaries? Start with Atlas and bring in Scout for current upstream docs.
- Minestom event routing or instance code? Helix owns the API specifics; Bedrock covers vanilla mechanics behind them.
- Ring collision, scoring, or cup flow? Thrust implements, Vector reviews the geometry, Lattice checks the ECS fit.
- Database schema or repository change? Vault leads; Forge validates service-layer patterns.
- Documentation, ADRs, migration guides? Scribe writes; Compass confirms the ticket scope; Lumen mirrors methodology in `docs/research/`.

When a task spans surfaces, read each candidate's Peer Network section and invoke the agents in parallel rather than serializing the handoffs.

## Maintenance

Loom (voyager-agent-architect) maintains the agent files. Edits happen in `.claude/agents/<agent-id>.md`. You must not change the `name`, `tools`, or `model` fields without an approved ADR — those fields drive invocation, capability scoping, and cost, and any change there is an architecture decision. Codenames and Peer Network entries are free to evolve as the team grows, provided the `CLAUDE.md` roster stays in sync.
