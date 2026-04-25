# ADR-0011: Use Flyway for versioned database schema migrations

## Status

Accepted

## Date

2026-04-25

## Decision makers

- Vault (database expert)
- Atlas (architect)
- Hangar (devops)

## Context and problem statement

The persistence layer in `shared/database` currently relies on Hibernate's `hbm2ddl.auto=update` to evolve the schema at application start. Voyager is approaching its first closed alpha, so the database now holds player data that must survive deployments. Vault flagged the risk while adding `game_mode` and `medal_tier` columns to `GameResultEntity` in PR #176: `hbm2ddl.auto=update` can silently skip complex changes, offers no rollback path, and leaves no audit trail of what DDL ran in which environment.

## Decision drivers

- Production deployments must not run unverified DDL against player data
- Schema changes must be auditable and reproducible across environments
- CI must be able to verify the schema before a release reaches production
- The team already writes SQL by hand and prefers SQL-first tooling
- Hibernate entity changes must remain the trigger for schema work, but must not be the executor

## Considered options

- Flyway as the sole DDL authority, Hibernate set to `validate`
- Liquibase as the sole DDL authority, Hibernate set to `validate`
- Keep `hbm2ddl.auto=update` and accept the risk
- Hand-written SQL scripts applied manually, no migration tooling

## Decision outcome

Chosen option: **Flyway as the sole DDL authority, Hibernate set to `validate`**, because it gives versioned, auditable, rollback-capable migrations without forcing the team off SQL. Hibernate keeps its role as the entity mapper and now verifies on startup that the live schema matches the entity model, but it never alters the schema itself.

Concretely:

- Hibernate runs with `hibernate.hbm2ddl.auto=validate` in production and staging
- Flyway scripts live in `shared/database/src/main/resources/db/migration/`
- Scripts follow the naming convention `V{n}__{description}.sql` (for example, `V2__add_game_mode_to_game_result.sql`)
- `V1__initial_schema.sql` backfills the complete current schema as a baseline
- Every future schema change ships as a new numbered Flyway script — entities are never the source of DDL changes alone

### Consequences

- Good, because every schema change is versioned, reviewable in pull requests, and replayable in any environment
- Good, because CI can run Flyway against an empty database and then start Hibernate in `validate` mode to catch entity-schema drift before deployment
- Good, because production deployments no longer carry the risk of unintended DDL from `hbm2ddl.auto=update`
- Bad, because every schema change now requires writing a migration script in addition to updating the entity — more discipline is required from contributors
- Bad, because `V1__initial_schema.sql` must be authored carefully to match the current production schema exactly; a mismatch breaks the `validate` step on the first deploy
- Neutral, because development databases can still be dropped and recreated by Flyway from `V1` upward, so the local workflow stays simple

### Confirmation

- `shared/database/src/main/resources/db/migration/V1__initial_schema.sql` exists and reproduces the current production schema
- The Hibernate configuration sets `hibernate.hbm2ddl.auto=validate` for production and staging profiles
- The CI pipeline runs Flyway `migrate` against a fresh database, then boots the application and verifies that Hibernate `validate` succeeds
- Pull requests that change a `@Entity` class without adding a corresponding `V{n}__*.sql` script fail the CI schema check

## Pros and cons of the options

### Flyway as the sole DDL authority, Hibernate set to `validate`

- Good, because SQL-first scripts match the team's existing skills and review habits
- Good, because Flyway integrates cleanly with Hibernate and is well-documented for this exact pairing
- Good, because the migration history table gives an explicit audit trail in the database itself
- Bad, because rolling back a migration requires writing an explicit down-script — Flyway Community does not auto-generate one

### Liquibase as the sole DDL authority, Hibernate set to `validate`

- Good, because Liquibase supports database-agnostic changelogs in XML, YAML, or JSON
- Good, because Liquibase ships built-in rollback semantics for many change types
- Bad, because the team writes raw SQL today; the changelog DSL adds a learning curve with no payoff for a single-database project
- Bad, because tooling is heavier and the integration with Hibernate is less idiomatic than Flyway's

### Keep `hbm2ddl.auto=update`

- Good, because no new tooling is required
- Bad, because Hibernate silently skips changes it cannot infer (column type narrowing, renames, index changes)
- Bad, because there is no audit trail and no rollback path
- Bad, because production DDL runs as a side effect of application start, with no chance for review

### Hand-written SQL scripts applied manually, no migration tooling

- Good, because it gives the team full control over every statement
- Bad, because there is no version tracking, no CI integration, and no guarantee that environments stay in sync
- Bad, because applying scripts manually in production is error-prone and does not scale past one operator

## More information

- PR #176 — added `game_mode` and `medal_tier` columns to `GameResultEntity` and surfaced the migration risk
- [Flyway documentation](https://documentation.red-gate.com/fd)
- [Hibernate `hbm2ddl.auto` reference](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#schema-generation)
- [ADR-0005: GameMode enum as session discriminator](0005-gamemode-enum-session-discriminator.md) — drove the entity changes that exposed the migration gap
