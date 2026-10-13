# ADR-0036: Load server configuration through avaje-config

## Status

Accepted

The project owner accepted this record when approving the change `adopt-avaje-config`, which implements it.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner), who asked for avaje-config as the OneLiteFeather project Titan uses.
- Drafted with the change `adopt-avaje-config`.

## Context and problem statement

The rebuild (`voyager-*`) reads its settings from the JVM in four places, each with its own parsing. The greenfield design
(`docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md`, section "Configuration") specifies a layered resolver with
four ranks and a `ConfigSource` abstraction, and no code for it exists. The first database keys (`player-stats`) are the next
settings to add. Operators already use `-D` flags and environment variables on the CloudNet node, and those must keep working.

## Decision drivers

- Existing `-D` flags, environment variables, keys and validation messages keep their meaning.
- One loader, with one documented precedence, and no hand-written file format.
- No static state in tests (the rebuild's test rules).
- A library the organisation already runs in production with a known precedence.
- Java 25, no reflection on the configuration path.

## Considered options

1. **Hand-written resolver, as the design sketches.** Full control, about 300 lines to maintain and test, and a file format
   we would have to define and document.
2. **avaje-config 5.2, instance API, built in the composition root (chosen).** Layered sources and a documented order, a YAML
   file format, environment mapping, tested by the Titan project in production. Compile-time friendly with avaje-inject, which
   the rebuild already uses.
3. **Typesafe Config (HOCON).** A new format for operators and no shared ecosystem with the organisation.
4. **Static `Config` facade, as Titan uses.** Easiest, but global state, which the rebuild's test rules forbid.

## Decision outcome

Chosen option: **2**.

- `voyager-platform` depends on `io.avaje:avaje-config:5.2`. One class, `platform.config.ConfigLoader`, builds the
  `Configuration` instance. Nothing else in the rebuild imports `io.avaje.config`.
- Precedence, lowest to highest: defaults in code, `application.yaml` in the working directory, `CONFIG_FILE`, environment
  variables, system properties. The velocity secret keeps environment before property, because
  `server/proxy-forwarding` requires it.
- Keys keep their names. The environment name of a key is avaje-config's mapping of it. Two keys gain environment names
  (`SERVICE_BIND_HOST`, `SERVICE_BIND_PORT`), which is additive.
- Database credentials follow the design's secrets rule: environment only, never in a YAML file in the repository or the jar.

**Consequences.**
- The design's hand-written `ConfigResolver` is replaced. Design decision D16 records it.
- Titan's static facade is not adopted. Voyager builds an instance, so tests own their configuration.
- Operators gain a working-directory file. They are not required to use it.

## Pros and cons of the options

### Option 1, hand-written resolver
- Good, because it matches the design text exactly.
- Bad, because it duplicates a mature library and its tests, and adds a file format to document.

### Option 2, avaje-config
- Good, because the precedence and the environment mapping are given, and Titan runs them in production.
- Good, because the instance API lets tests build their own configuration without global state.
- Bad, because one more dependency enters the platform module, and its behaviour must be characterised (task 2.1).
- Bad, because Titan's note about enumerated sections means keys read by section need their own check later.

### Option 3, HOCON
- Good, because it is a well-known format with includes.
- Bad, because it adds a format and a library with no tie to the organisation.

### Option 4, static facade
- Good, because it is the shortest path.
- Bad, because it is global state that the rebuild's tests forbid.

## More information

- Titan's design records: `openspec/changes/archive/2026-09-25-avaje-config-facade` and
  `2026-09-25-standardized-config-profiles`.
- Current call sites: `voyager/server/.../config/ServerSettings.java`, `voyager/server/.../config/ConfigCheck.java`,
  `voyager/server/.../VoyagerServer.java`, `voyager/platform/.../proxy/ProxyForwardingSettings.java`.
- Velocity secret rule: `openspec/specs/server/proxy-forwarding/spec.md`.
- Owner decisions Q1 to Q3 are recorded in `openspec/changes/adopt-avaje-config/design.md`, "Owner decisions".
