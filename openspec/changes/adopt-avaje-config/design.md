# Design

## Context

The rebuild reads its configuration from the JVM in four places (`voyager/server/.../config/ServerSettings.java`,
`voyager/server/.../VoyagerServer.java` for `voyager.config.check`, `voyager/server/.../config/ConfigCheck.java`, and
`voyager/platform/.../proxy/ProxyForwardingSettings.java`). The greenfield design
(`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, "Configuration") specifies four ranks and a
hand-written `ConfigResolver` with `ConfigSource` functions. None of it is implemented.

Titan (`/mnt/projects/oss/onelitefeather/Titan`, archived changes `2026-09-25-avaje-config-facade` and
`2026-09-25-standardized-config-profiles`) runs avaje-config 5.2 in production with the same precedence the design asks for.
Its findings, read from its design documents and its `persistence/src/main/resources/titan/defaults/database.yaml`:

- The static `io.avaje.config.Config` facade builds its configuration in a static initialiser on first access. A failure
  there surfaces as `ExceptionInInitializerError` and the class is unusable afterwards.
- Titan's loader precedence: classpath `application.yaml` < working-directory file < profile file < `CONFIG_FILE` <
  environment < system property. Environment names are the key in upper case with dots as underscores
  (`titan.database.url` is `TITAN_DATABASE_URL`).
- Titan's own note: environment variables and `-D` properties override only keys that already exist in a YAML file when a
  section is enumerated (`forPath(...).asProperties()`). A key given only as `-D` or as an environment variable is then
  ignored. Voyager reads named keys, not sections, so this does not apply in v1. Task 2.1 verifies it for direct lookups.
- Titan's `Configuration.builder().load(...)` and `Configuration.builder().put(key, value).build()` create instances that
  tests can own. That is the form this change uses.

**Verified facts about the current code** (read, not assumed):
- The velocity secret is read environment first, then property (`ProxyForwardingSettings.from`), and the spec
  `openspec/specs/server/proxy-forwarding/spec.md` requires it. avaje-config orders environment below property. The two
  disagree when both are set, so the exception is a requirement, not a leftover.
- The bind settings, the data and worlds paths, the cup, the racer counts and the lobby are read from system properties only,
  under upper-case names (`VOYAGER_DATA_PATH`). The name `voyager.dev` is lower-case. Keys keep their exact spelling.

## Goals / Non-Goals

**Goals**
- One loader, one place that touches the JVM and the environment.
- Keys, defaults, validation messages and the config-check report unchanged for every existing input.
- A single documented precedence, which the CloudNet guide can state in one table.
- Instance-based configuration: no static state, so tests run independently.

**Non-Goals**
- Runtime reload. The loader is read once at boot, as today.
- Feature flags (`flags.properties`, Togglz) and gameplay tuning (design kind (c), which stays in git, not in configuration).
- Profiles beyond what avaje-config provides without code. v1 documents the working-directory file and `CONFIG_FILE` only.
- Moving the legacy tree (`legacy/server`, `legacy/plugins/*`) off `System.getProperty`. It is deleted at cut-over.

## Decisions

### D1. Library: avaje-config 5.2, instance API, built in the composition root

**Decision.** Add `io.avaje:avaje-config:5.2` to the `libs` catalogue in `settings.gradle.kts` (released 2026-06-11, older than
the two-week cooling window). `voyager-platform` depends on it as an implementation detail. `platform.config.ConfigLoader`
builds a `Configuration` instance. The composition roots (`voyager-server`, later `voyager-setup`) call the loader once and
pass the result or a lookup function to the code that needs values.

**Alternatives considered.**
1. *Hand-written resolver as the design sketches.* About 300 lines to maintain, a second file format to define, and the
   precedence to test ourselves. Rejected: avaje-config already does exactly this, and Titan has shown it works.
2. *Typesafe Config (HOCON).* A new format for operators. Rejected: a second file format with no ecosystem tie.
3. *Static `Config` facade, as Titan uses.* Rejected for Voyager: global state that the rebuild's test rules forbid. Titan
   accepts it; this rebuild does not.
4. *Keep the status quo.* Rejected: four call sites, no file layer, and `VOYAGER_DB_*` (needed by `player-stats`) would be a fifth.

**Consequence.** avaje-config is a dependency of one module only, so the API, physics and race modules stay free of it. The
fitness rules say so (D6).

### D2. Keys stay as they are; names are the key with its environment spelling

**Decision.** Every key keeps its current spelling. The environment name of a key is avaje-config's mapping of the key (dots and dashes
to underscores, upper case). The keys with a non-matching environment name are:
- `voyager.velocity.secret`: environment name `VOYAGER_VELOCITY_SECRET`, as the spec says. Covered by D3.
- `service.bind.host` / `service.bind.port`: environment names `SERVICE_BIND_HOST` / `SERVICE_BIND_PORT`. New, additive.

**Consequence.** Any environment variable that is set today and read today is still read. A new environment variable that
collides with a key now takes effect (for example `VOYAGER_CUP`). Task 2.1 lists every such name for the CloudNet guide.

### D3. Precedence and the velocity-secret exception

**Decision.** The default order is avaje-config's: working-directory file < `CONFIG_FILE` < environment < system property. The
loader adds the defaults in code as rank 1, which `ServerSettings` already does with its constants. No `application.yaml` is
shipped inside the jar (design rank 1 is code, and two jars on one classpath must not both carry one, as Titan notes).

The velocity secret is the one exception: `VOYAGER_VELOCITY_SECRET` wins over `voyager.velocity.secret`, as
`openspec/specs/server/proxy-forwarding` requires. The loader reads that one key from the environment before asking
avaje-config, and the exception is written once, in `ConfigLoader.velocitySecret()`, with a test per branch.

**Alternative.** Unify on "property wins" for every key. This changes the spec for one key and needs the owner's answer. It is
owner question Q2. The change is written so that either answer is a one-line change.

### D4. Where the code lives

| Piece | Module | Package |
|---|---|---|
| avaje-config dependency | `voyager-platform` | (build file) |
| `ConfigLoader` (builds the `Configuration`, the velocity exception, the lookup function) | `voyager-platform` | `net.elytrarace.voyager.platform.config` |
| `ServerSettings`, `ConfigCheck` (take a lookup; no JVM access) | `voyager-server` | `net.elytrarace.voyager.server.config` (unchanged) |
| `ProxyForwardingSettings` (takes the velocity secret from the loader) | `voyager-platform` | `net.elytrarace.voyager.platform.proxy` (signature change) |
| Construction of the loader and its hand-over | `voyager-server` | `net.elytrarace.voyager.server` (`VoyagerServer`) |

Dependency direction: `server -> platform.config -> avaje-config`, `server.config -> (lookup function only)`. Nothing points back.
`api`, `physics`, `race` have no configuration access.

### D5. Keeping the public signatures

`ServerSettings.fromProperties(String[], Function<String, @Nullable String>)` stays. Its caller passes `loader.lookup()`
instead of `System::getProperty`. `ProxyForwardingSettings.from(Map, Map)` is kept for the unit tests and a factory taking
the loader is added. Existing tests keep compiling; the diff in tests is only where the test used the JVM.

### D6. Fitness rules (ArchUnit, `allowEmptyShould(false)`)

Added to `voyager/fitness`:
- `environmentAccessIsConfinedToTheEdge`: classes outside `net.elytrarace.voyager.platform.config..`, `..server..` and
  `..setup..` must not call `System.getenv`, `System.getProperty`, `Integer.getInteger`, `Long.getLong` or `Boolean.getBoolean`
  (the list from design section "Configuration"). The first run must fail on `ProxyForwardingSettings` and `ConfigCheck`
  (the red), which proves the rule is not empty.
- `avaje.config` classes (`io.avaje.config..`) may be referenced only from `net.elytrarace.voyager.platform.config..` and
  `..server..`.
- `apiDoesNotDependOnConfigLibrary`: the API may not depend on `io.avaje.config..`.
- `FitnessCoverageTest` is unchanged: no new module.

**No baseline.** The rule is red on the four current call sites and green when task 3.3 moves them, in the same change. It
is not frozen with an archunit store, because a frozen entry for a rule this small would only hide a regression.

### D7. Config-check report stays byte-identical

`ConfigCheck` prints the source of each problem ("system property X", "command line"). avaje-config returns values, not
their source. The loader therefore names the source itself by reading the layers it can see (environment and system
properties directly, the file by its path). The attribution is a second read of the same keys in the same order. Task 5.1
proves the report matches the current jar for a matrix of inputs; any difference fails the change. Owner question Q3 asks
whether a simpler "effective value only" report is acceptable.

### D8. Testing (F.I.R.S.T.)

- Unit tests build a `Configuration` with `Configuration.builder().put(...)`, one per test, with no JVM state. They never call
  `System.setProperty` or `Config.setProperty`.
- Environment-only behaviour (the real environment cannot be set from Java) is tested through the loader's lookup seam, which
  takes the environment map as an argument.
- One child-JVM test (`ConfigurationPrecedenceTest`, `@TempDir` as working directory) covers what needs the real JVM: working
  directory file discovery and `CONFIG_FILE`. It is slow but independent.
- No `Thread.sleep`, no wall clock. Every test is named after its behaviour.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| avaje-config's environment mapping differs from the assumed one | Task 2.1 spike, recorded in D2 before any code |
| Operators set `VOYAGER_*` in the environment expecting it to be ignored | Additive and listed in the CloudNet guide; the config check shows effective keys |
| `ConfigCheck` report changes wording | Golden matrix in task 5.1 compares old and new jar output |
| The velocity exception drifts (someone "fixes" it) | Dedicated test per branch and a line in the reference page |
| A future `application.yaml` in a shaded jar overrides operator values | No such file is shipped; the fitness check of the jar contents is part of task 5.2 |

## Migration Plan

1. Merge the characterisation tests first (tasks 2.2 and 2.3), against the current code, so they pin today's behaviour.
2. Merge the loader and the call-site moves (3.x) with the rule (4.x) red then green.
3. Run the golden comparison (5.1) against the shipped jar of `main`.
4. Deploy: nothing to change in any CloudNet task. Operators may now use the working-directory file. No deploy step is needed.
5. Rollback: revert the squash commit. No data, no schema.

## Owner questions

- **Q1.** Accept ADR-0036 as Proposed (task 1.1)?
- **Q2.** Keep "environment before property" for the velocity secret only (recommended, it is the spec), or unify on "property wins"?
- **Q3.** Keep the config-check report with sources (recommended, the report is operator-facing), or print effective values only?
