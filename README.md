# Voyager

**An experiment in building games with AI agents.**

Voyager is a Minecraft elytra racing minigame — think Mario Kart, but you're flying with elytra through rings in the sky. Players compete in cups made up of multiple maps, collecting points by flying through rings at high speed.

## The Experiment

This entire project is being designed, architected, implemented, and documented by a team of **22 specialized AI agents** working together through [Claude Code](https://claude.ai/code). Every line of code, every architecture decision, every physics formula, and every piece of documentation was produced through human-AI collaboration.

**This is a prototype for a new way of building games:**
- A human game designer directs the vision
- AI agents handle the specialized work — from elytra physics calculations to database schema design to gaming psychology optimization
- The agents collaborate, review each other's work, and maintain documentation automatically
- Development happens at a pace that would be impossible for a small team alone

We're continuing this approach and want to see how far it can go. Can a team of AI agents, guided by a human, build a polished, competitive Minecraft minigame? That's what we're finding out.

## The Agent Team

| Agent | Role |
|---|---|
| **Product Manager** | Organizes tickets, plans milestones, leads the team |
| **Architect** | Designs system boundaries, enforces architecture rules |
| **Game Psychologist** | Ensures every feature maximizes player retention and fun |
| **Game Designer** | Designs gameplay loops, balancing, and player feedback |
| **Game Developer** | Implements physics, collision, scoring, and game feel |
| **Minestom Expert** | Handles all Minestom API code and Paper migration |
| **Minecraft Expert** | Knows decompiled vanilla formulas for elytra physics |
| **Math & Physics** | Writes collision algorithms and ensures numerical stability |
| **Senior Backend** | Writes clean service and repository code |
| **Senior ECS** | Builds the Entity-Component-System game loop |
| **Senior Testing** | Ensures 80%+ test coverage with JUnit 5 |
| **Database Expert** | Designs Hibernate entities and optimizes queries |
| **Java Performance** | Tunes JVM, GC, and ensures 20 TPS under load |
| **DevOps** | CI/CD, Docker, CloudNet deployment |
| **Researcher** | Deep-dives into docs before anyone writes code |
| **Tech Writer** | Maintains all documentation and ADRs |
| **Scientist** | Writes formal research papers on technical decisions |
| **Paper Expert** | Maintains the setup plugin that stays on Paper |
| **Junior Creative** | Brings wild ideas and quick prototypes |
| **Junior Frontend** | Builds scoreboards, BossBars, and sound design |
| **Skill Creator** | Builds reusable slash-command workflows |
| **Agent Architect** | Creates and improves the agents themselves |

## Tech Stack

| Layer | Technology |
|---|---|
| Game Server | [Minestom](https://minestom.net/) 2026.03.25-1.21.11 (standalone, no vanilla code) |
| Setup Tool | [Paper](https://papermc.io/) 1.21.5 + FastAsyncWorldEdit |
| Language | Java 25 |
| Build | Gradle 9.4 + ShadowJar 9.4.0 |
| Dependencies | Version catalog defined in `settings.gradle.kts` |
| Database | MariaDB via Hibernate ORM 7 + HikariCP |
| Deployment | CloudNet v4 (primary), Kubernetes (planned) |
| Commands | [Cloud](https://github.com/Incendo/cloud) (Incendo) |
| Geometry | Apache Commons Geometry |

## Project Structure

```
server/              Standalone Minestom game server (the main thing)
plugins/game/        Legacy Paper plugin (being replaced by server/)
plugins/setup/       Map/cup editor plugin (stays on Paper)
shared/common/       ECS framework, services, utilities (platform-agnostic)
shared/phase/        Game phase lifecycle (Lobby → Game → End)
shared/conversation-api/  Player prompt system (platform-agnostic)
shared/database/     Hibernate persistence layer
docs/                Architecture decisions, research papers, ADRs
.claude/agents/      The 22 AI agent definitions
.claude/skills/      Reusable slash-command workflows
```

## Quick Start

```bash
# Start MariaDB for local development
docker compose -f docker/mariadb/compose.yml up -d

# Build everything
./gradlew build

# Build the standalone server
./gradlew :server:shadowJar

# Run it
java -jar server/build/libs/*.jar

# Run tests
./gradlew test
```

## Branch Strategy and Releases

`main` is the primary branch. All feature and fix branches are cut from `main` and merged back via pull request.

Releases are fully automated via [semantic-release](https://github.com/semantic-release/semantic-release). Pushing to `main` triggers a pipeline that determines the next version from commit messages, updates `CHANGELOG.md`, and publishes a GitHub Release with the Shadow JAR attached.

Version increments follow [Conventional Commits](https://www.conventionalcommits.org/):

| Commit type | Version bump |
|---|---|
| `fix:` | patch (e.g. 1.2.3 → 1.2.4) |
| `feat:` | minor (e.g. 1.2.3 → 1.3.0) |
| `feat!:` or `BREAKING CHANGE:` | major (e.g. 1.2.3 → 2.0.0) |

CI runs on GitHub Actions with SHA-pinned actions, a matrix build across Ubuntu, Windows, and macOS, JaCoCo coverage reporting, and Gradle dependency caching.

## Contributing

This is an open experiment. If you're interested in AI-assisted game development, agent team design, or just want to race with elytra — contributions are welcome.

The `.claude/agents/` directory contains the full agent team definitions. Each agent has specific domain knowledge, trigger conditions, and working methods. If you use Claude Code, you can leverage the entire team.

All commits must follow [Conventional Commits](https://www.conventionalcommits.org/) — this drives the automated versioning pipeline.

## License

MIT — see [LICENSE](LICENSE)

---

*Built with AI. Directed by humans. Racing through the sky.*
