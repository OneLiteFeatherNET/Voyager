---
name: docs-structure
description: Current layout of the Voyager docs/ tree and which Diataxis directories exist
type: project
---

The Voyager repository uses the Diataxis-based docs layout defined in the tech-writer system prompt, but most directories are bootstrapped on demand.

As of 2026-04-25:

- `docs/decisions/` — ADRs. Existing on disk: `0001-firework-boost-in-ecs.md`, `0003-race-mode-time-bracket-scoring.md`, `0004-practice-mode-replaces-tutorial.md`, `0005-gamemode-enum-session-discriminator.md`. **`0002` is reserved for `0002-elytra-flight-client-authority.md`** (referenced by CLAUDE.md and `docs/elytra-physics-reference.md` but not yet written to disk). Skip 0002 when assigning new numbers until that file lands.
- `docs/guides/` — how-to guides. First entry is `how-to-register-an-ecs-system.md`
- `docs/reference/` — reference pages. First entry is `firework-boost.md`
- `docs/explanation/`, `docs/tutorials/`, `docs/migration/`, `docs/research/` — not yet created as of this writing
- `CHANGELOG.md` exists at the repo root and is managed by semantic-release (see commit history; format follows conventional commits output, not strictly Keep a Changelog)

**Why:** Knowing which directories already exist avoids re-scanning the tree and prevents accidentally fragmenting the same topic across multiple locations.

**How to apply:** Before creating a new doc, check if a sibling file already sits in the target directory. If the directory does not exist yet, create it by writing the file — no explicit mkdir needed with the Write tool.
