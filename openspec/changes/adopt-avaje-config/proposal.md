# Proposal

**Conventional Commits:** `refactor(server)`. Squash-merge PR title: `refactor(server): load configuration through avaje-config`.

**Why this type, and not `feat(server)`:** no player sees a difference and no existing deployment has to change anything.
Every key, every `VOYAGER_*` name, every `-D` flag and the velocity secret keep their meaning. The new capabilities (a
YAML file in the working directory, a documented precedence) are the means to the next change, `player-stats`, which
ships the player-visible feature. If the owner wants the YAML file announced as a feature, the release note can say so
without changing the type.

**Decision record:** `docs/decisions/0036-avaje-config.md` (MADR, Proposed). Accepted only when the owner accepts it.

**Decision changed in the greenfield design:** `docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, section
"Configuration", the layered resolver (`ConfigResolver`, `ConfigSources`, `ConfigSource`). It is replaced by avaje-config
and recorded as design decision **D16** (task 6.4). The precedence ranks of that section are kept.

**Slices touched:** `platform/config` (new: the loader, the one place that reads the environment and the JVM), `platform/proxy`
(the velocity secret reads its source through the loader), `server/config` (`ServerSettings` and `ConfigCheck` take the
lookup), `server` (the composition root builds the loader and passes it in), `fitness` (the rules that confine environment
access). `api`, `physics`, `race` and the legacy tree are not touched.

**Depends on:** nothing. It must merge before `player-stats`, which adds database keys to the same loader.

**Out of scope:** runtime reload (`onChange`), feature flags, configuration of gameplay tuning (kind (c) in the design,
which stays in git), profiles beyond what avaje-config gives by default, and any change to `legacy/`.

## Why

`ServerSettings.fromProperties`, `ProxyForwardingSettings` and `ConfigCheck` each read the JVM themselves: four call sites
of `System.getProperty`, `System.getenv` and `Boolean.getBoolean` in `voyager-server` and `voyager-platform`. The greenfield
design specifies a layered resolver with four ranks and a `ConfigSource` abstraction, but no code for it exists yet. Building
that resolver by hand means maintaining a loader, precedence and a file format that avaje-config already provides and that
the sister project Titan already runs in production (`titan/defaults/database.yaml`, `TITAN_DATABASE_URL`).

Adopting it now, before `player-stats` adds the first database keys, means one loader carries every key, and the database
credentials go through the same path as every other setting.

## What Changes

- **One loader.** `voyager-platform` gets `io.avaje:avaje-config:5.2` (catalogue entry in `settings.gradle.kts`). The class
  `platform.config.ConfigLoader` builds one `io.avaje.config.Configuration` instance in the composition root, using the
  avaje-config builder. It is not the static `Config` facade: a static instance is global state, which the rebuild's
  test rules forbid (design section "Testability").
- **Same keys, same names.** `VOYAGER_DATA_PATH`, `VOYAGER_WORLDS_PATH`, `VOYAGER_CUP`, `VOYAGER_MIN_PLAYERS`,
  `VOYAGER_LOBBY_WORLD`, `VOYAGER_LOBBY_SPAWN`, `VOYAGER_MAX_PLAYERS`, `voyager.dev`, `voyager.config.check`,
  `service.bind.host`, `service.bind.port`, `voyager.velocity.secret` and `VOYAGER_VELOCITY_SECRET` keep their names and
  defaults. `ServerSettings.fromProperties` keeps its `Function<String, String>` seam, so its tests do not change.
- **Precedence, stated once.** Lowest to highest: defaults in code (`ServerSettings` constants, design rank 1) < `application.yaml`
  or `.properties` in the working directory (rank 2, the service directory of a CloudNet task) < `CONFIG_FILE` < environment
  variables (rank 3) < system properties (rank 4). This is avaje-config's own order, so no custom merge is written.
- **One exception, kept on purpose.** The velocity secret keeps "environment before property" as
  `openspec/specs/server/proxy-forwarding` requires. The loader reads that single key from the environment first. The
  exception is written in one place and covered by a test. Owner question Q2 asks whether to unify the order.
- **Additive environment names.** Under avaje-config every key can also come from its environment name, so
  `VOYAGER_CUP` in the environment now works as well as `-DVOYAGER_CUP`, and `SERVICE_BIND_PORT` is read too. Nothing that
  works today changes meaning. The CloudNet guide lists the names.
- **Fitness.** Environment and property access is confined to `platform.config`, `server` and `setup`, and avaje-config
  classes to `platform.config`. The API may not depend on it. Each rule uses `allowEmptyShould(false)`.
- **Documentation.** The CloudNet guide's table of node-provided values, a new reference page listing every key, the
  ADR, and the design-spec amendment.

## Capabilities

### New Capabilities
- `server/configuration`: the loader, the precedence, the file format, and the confinement rule.

### Modified Capabilities
- `server/proxy-forwarding`: no requirement changes. The secret keeps "environment before property". Its source moves to the
  loader, which the characterisation tests pin before the move.
- `server/config-check-mode`: no requirement changes. The check reads through the loader; its report is byte-identical for
  the characterised inputs (task 5.1).
