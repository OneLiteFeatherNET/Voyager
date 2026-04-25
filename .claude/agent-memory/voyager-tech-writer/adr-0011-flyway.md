---
name: adr-0011-flyway
description: ADR-0011 chose Flyway as sole DDL authority with Hibernate set to validate; baseline script is V1__initial_schema.sql
type: project
---

ADR-0011 was accepted on 2026-04-25.

**Decision:** Flyway is the sole DDL authority for the `shared/database` module. Hibernate runs with `hbm2ddl.auto=validate` in production and staging — it never alters the schema, only verifies entity-schema alignment on startup.

**Layout:**

- Migration scripts live in `shared/database/src/main/resources/db/migration/`
- Naming convention: `V{n}__{description}.sql`
- `V1__initial_schema.sql` is the baseline that backfills the current production schema
- Every entity change requires a matching new `V{n}__*.sql` script — entities are not the source of DDL changes alone

**CI gate:** Flyway `migrate` runs against an empty DB, then the app boots with Hibernate `validate`. PRs that change an `@Entity` without adding a migration fail the schema check.

**Trigger:** PR #176 added `game_mode` and `medal_tier` columns to `GameResultEntity` and surfaced the risk of `hbm2ddl.auto=update` in production.

**Why:** Future docs about database changes, deployment runbooks, or onboarding should cite ADR-0011 instead of re-explaining the Flyway/Hibernate split.

**How to apply:** When writing how-to guides for adding entities, reference docs for `shared/database`, or migration guides that cross schema boundaries, link ADR-0011 and remind contributors to add a `V{n}__*.sql` script. If the policy itself changes (rollback strategy, baseline reset, switch to Liquibase), supersede ADR-0011 — never edit it.
