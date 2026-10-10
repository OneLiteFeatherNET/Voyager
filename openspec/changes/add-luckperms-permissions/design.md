# Design

## Context

Voyager's two composition roots, `voyager-server` (game) and `voyager-setup` (map authoring), run on Minestom 2026.08.28-26.2
and are wired by avaje-inject (ADR-0016 in the greenfield design, D10). Authorisation today is one check in
`voyager/server/.../command/ReloadPermission.java`: the console, or a player at operator level 4. The setup server has no
check. Minestom 26.2 carries no named permission set, only the numeric level 0 to 4 on `Player`.

The sibling project Cygnus already runs LuckPerms on Minestom for the OneLiteFeather network. Its design is the reference
here, with two changes: LuckPerms sits behind a port so that command code never names it, and the fallback without
LuckPerms fails closed (see Decisions 3 and 4).

## Goals / Non-Goals

**Goals:**
- One permission port that every gated command and listener asks, in `voyager-api`, with no implementation there.
- LuckPerms as an optional runtime adapter, confined to one platform package, started by both composition roots.
- A fallback that never gives a player more than operator level 4 gives, and a console that stays fully allowed.
- Velocity modern forwarding, the `service.bind.*` system properties and a stdin `stop`, so that the same jar runs under
  a CloudNet node in change `add-cloudnet-deployment` with no further code.

**Non-Goals:**
- CloudNet: the bridge module, the service template, the shadow jar for `voyager-setup` and the deployment guide belong to
  `add-cloudnet-deployment`, which depends on this change. Nothing here references `eu.cloudnetservice`.
- LuckPerms storage: H2 per service now, PostgreSQL with the stats and persistence stage. No database code here.
- LuckPerms contexts (server name, world): the adapter asks LuckPerms with its default contextual options. Context
  calculation is a follow-up once a second server exists.
- Bungee (legacy) forwarding: only Velocity is configured. `Auth.Bungee` exists in Minestom 26.2 but is not used.
- `/race status`: stays open to every sender, because it reads state and changes nothing.
- The `docker/server/Dockerfile` copy of `*-all.jar` from the legacy tree: broken, out of scope, not touched.

## Decisions

### 1. Permission port in voyager-api (Clean Architecture: the innermost ring)

New package `net.elytrarace.voyager.api.permission`, all pure:

| Type | Kind | Role |
|---|---|---|
| `PermissionNode` | enum | the five node constants, `value()`, `VALUES`, `byValue(String)` returning `@Nullable` (ManisGame rule 6) |
| `PermissionSubject` | sealed interface, permits `Console` and `Player` (records) | who asks. `Player(UUID id, int operatorLevel)` validates `0 <= level <= 4` in its compact constructor |
| `PermissionPolicy` | interface with `boolean allows(PermissionSubject, String node)` and a default `boolean allows(PermissionSubject, PermissionNode node)` that delegates with `node.value()` | the port. The string form exists for node names Voyager does not own: the CloudNet bridge in `add-cloudnet-deployment` asks about names such as `cloudnet.bridge.maintenance`, which a closed enum cannot hold. Voyager's own gates use the enum form |

Dependency direction: `voyager-platform` and both composition roots depend on `voyager-api`. Nothing in `voyager-api`
depends on them. `PermissionPolicy` is deliberately not sealed: it has three implementations in two modules, and a sealed
hierarchy would force them into one file. The `PermissionSubject` hierarchy is sealed because its variants are closed.

### 2. Adapters in voyager-platform (Hexagonal Architecture, outbound adapter)

| Type | Package in `voyager-platform` | Depends on |
|---|---|---|
| `LevelPermissionPolicy` | `permission` | `api.permission` only |
| `PlayerSubjects` (Minestom `Player` to `PermissionSubject.Player`) | `permission` | Minestom |
| `LuckPermsPolicy` | `permission.luckperms` | `api.permission`, the gateway seam |
| `LuckPermsGateway` (interface) and `NetLuckPermsGateway` (the only class naming `net.luckperms`) | `permission.luckperms` | `net.luckperms` |
| `LuckPermsBootstrap` (`isPresent()`, `start()`) | `permission.luckperms` | `me.lucko.luckperms.minestom.loader` by class name |
| `ProxyForwardingSettings` (secret source, pure) | `proxy` | none |
| `ConsoleCommandReader`, `ServiceShutdown`, `StopCommand` (moved from `voyager-server`) | `lifecycle` | Minestom, `api.permission` |

`LuckPermsPolicy` takes a `LuckPermsGateway` (a seam returning a tri-state per UUID and node), so its unit tests run with a
fake gateway and never start LuckPerms. The `NetLuckPermsGateway` is exercised only by the acceptance task.

### 3. Fallback without LuckPerms: fail closed on operator level

`LevelPermissionPolicy`: `Console` is allowed every node; `Player` is allowed every node only at operator level 4 (the
bar `ReloadPermission.REQUIRED_LEVEL` already sets). Players below level 4 are denied everything.

Rejected alternative, Cygnus's behaviour: "absent LuckPerms grants every check". Cygnus's own guide records that a jar which
lost the loader would then grant every player every permission, with only two log lines to show for it. For a server that
runs on a public network, that is the wrong failure mode. The cost of the chosen fallback is that local runs need
`/op` (level 4) to use gated commands, which the server already requires for `/race reload` today. The WARN line at startup
names the fallback.

### 4. LuckPerms: optional, detected, and a failed start stops the server

Detection reads the loader class by name with `Class.forName(..., false, ...)`, as Cygnus does, so the server compiles and runs
without the jar. If the class is present, `LuckPermsBootstrap.start()` calls `MinestomLoader.get().load().registerShutdownHook().start()`
before the port binds. A throw here stops startup (spec requirement "A failed LuckPerms start refuses to listen"). The fallback
is never used silently when the loader is present but broken.

Rejected alternative: loading LuckPerms as a Minestom extension through `extensions/`. Minestom 26.2 has no extension
system (the jar holds no class whose name contains "extension"), so the loader would never start that way. Voyager
starts LuckPerms directly, the same as Cygnus.

### 5. Node gating and the composition roots

`VoyagerServer` and `SetupServer` each build one `PermissionPolicy` bean: `LuckPermsPolicy` when `LuckPermsBootstrap.isPresent()`,
`LevelPermissionPolicy` otherwise. The choice lives in the composition root, the only place that may branch on the backend.
Commands receive the bean through their constructor. `RaceCommand` replaces the `ReloadPermission` call with
`policy.allows(PlayerSubjects.of(sender), VOYAGER_COMMAND_RACE_RELOAD)`, keeping the console rule and the denial message.

### 6. Velocity forwarding (Minestom `Auth.Velocity`)

`ProxyForwardingSettings` reads `VOYAGER_VELOCITY_SECRET`, else `voyager.velocity.secret`. A non-blank value gives
`new Auth.Velocity(secret)`, passed to `MinecraftServer.init(auth)`. No value gives `MinecraftServer.init()` (offline) and a WARN.
A blank value throws before the server starts. The record's `toString` masks the secret. The composition roots map the record to
Minestom's `Auth`; the platform keeps the record Minestom-free.

Why it is needed: the CloudNet RC16 release notes say the CloudNet bridge "does not enable any proxy authentication anymore. This
must be done by the Minestom server implementation". Without forwarding, behind a proxy the player UUID is wrong and LuckPerms
finds no user.

### 7. Bind address and stop

`ServerSettings` and `SetupSettings` take the host and port from the positional arguments, then the properties
`service.bind.host` and `service.bind.port`, then the defaults `0.0.0.0` and 25565. `ConsoleCommandReader` (moved to `lifecycle`)
dispatches `stop` to `ServiceShutdown`, which runs `MinecraftServer.stopCleanly()` and `System.exit(0)` on a new platform
thread, because stopping closes the reader's own input (greenfield design, "Extensions must be bootstrapped explicitly" paragraph
on the stop thread). `end` is not registered and is ignored. `/stop` is a Minestom command, gated by `voyager.command.stop`.

The move of `ConsoleCommandReader` is the one structural change. Its tests move with it, and they still read an injected stream.

### Fitness rules (voyager-fitness)

| Boundary | Rule | Where |
|---|---|---|
| `net.luckperms..` and `me.lucko..` are referenced only from `net.elytrarace.voyager.platform.permission.luckperms..` | `luckPermsIsConfinedToItsAdapter` (new) | new `PermissionBoundaryTest` |
| Command and setup-adapter classes never reference the LuckPerms adapter | `commandsAskOnlyThePolicy` (new) | `PermissionBoundaryTest` |
| `voyager-api` stays free of Minestom and of LuckPerms | `ApiPurityTest` existing Minestom rules, plus the first rule above | `ApiPurityTest`, `PermissionBoundaryTest` |
| `voyager-platform` does not depend on the composition roots | existing `CompositionRootRulesTest` | unchanged |

Both new rules use `allowEmptyShould(false)`. The Red step is that the first rule has nothing to match until the adapter exists,
so the rule fails on a `failed to check any classes` result.

### 8. Owner-confirmed defaults (2026-10-10)

The owner confirmed these four points, so they are decisions, not open questions:
- The fallback without LuckPerms is console allowed for every node, and a player allowed only at operator level 4 (Decision 3).
- The five node names are as proposed: `voyager.command.race.reload`, `voyager.command.race.start`, `voyager.command.race.skip`,
  `voyager.command.stop` and `voyager.setup.use`.
- `/race status` stays open to every sender.
- This change is one change, and its commits are one per type.

## Risks / Trade-offs

### Phase 0 feasibility: minestom-loader on Minestom 26.2 (read-only, 2026-10-10)

**Verdict: compatible at member-name level on 26.2, not runtime-verified. The first task is a runtime spike.**

Evidence collected:
- Cygnus pins `net.luckperms:api` 5.5 (compileOnly) and `net.luckperms:minestom-loader` 5.6-SNAPSHOT (runtimeOnly, excluded from
  the test class path), as declared in its `settings.gradle.kts` (`versionCatalogs { create("libs") ... }`). The loader resolves from
  `https://central.sonatype.com/repository/maven-snapshots/`, which is in Cygnus's repository list.
- The loader jar in the local Gradle cache (`net.luckperms/minestom-loader/5.6-SNAPSHOT/27c932bb.../minestom-loader-5.6-SNAPSHOT.jar`)
  contains `me/lucko/luckperms/minestom/loader/MinestomLoader.class`, with `load()`, `registerShutdownHook()` and `start()` as
  `LuckPermsSupport.bootstrap()` calls them. It also contains `extension.json` (LuckPerms 5.6.55) and a nested bundle
  `luckperms-minestom.jarinjar` that carries the Minestom platform code.
- The nested bundle references 39 distinct Minestom and Adventure members (commands, entities, events, scheduler, `ConnectionManager`,
  `Task.Builder`). All 39 exist by name in `minestom-2026.08.28-26.2.jar` (read from the Gradle cache). Six flagged by a first
  name-only pass were checked by hand: `Command(String, String...)` exists; `setSuggestionCallback` is declared on `Argument`;
  `getOrDefault(Pointer, Object)` is a default on Adventure's `Pointered`; `Scheduler.buildTask` is a default on `Scheduler`;
  `Entity.getUuid` exists; `GameMode.toString` is inherited from `Enum`. This is a member-name check, not a signature check.
- Cygnus's Minestom version: the cached `aonyx-bom` 0.8.7 pulls `mycelium-bom` 1.8.5, which pins
  `net.minestom:minestom:2026.08.28-26.2`. Cygnus HEAD pins `aonyx-bom` 0.8.8, which is not in the local cache and not reachable
  without credentials (`repo.onelitefeather.dev` returns 401 for the BOM path). Its Minestom pin is therefore inferred from 0.8.7,
  not read.

Risks found:
- **R1 Snapshot pin.** `5.6-SNAPSHOT` is not reproducible: it changes whenever the snapshot repository does. Mitigation:
  pin the cached jar's SHA-256 in the spike notes and re-check on each Renovate bump; any bump needs owner approval.
- **R2 Duplicate LuckPerms API.** The loader jar contains `net/luckperms/api/*` classes itself, and the adapter compiles against
  `net.luckperms:api:5.5`. Mitigation: spike task 1.3 compares the two; the adapter uses only the intersection of both APIs.
- **R3 Runtime unknown.** Nothing here has booted LuckPerms on 26.2. Mitigation: spike task 1.1, with a stated fallback.
- **R4 Cygnus's fail-open fallback.** Copying Cygnus's `TRUE` fallback would expose every node. Mitigation: Decision 3.
- **R5 No Minestom extension system.** Minestom 26.2 contains no extension classes. The loader is therefore started directly,
  and must stay out of `extensions/` in change `add-cloudnet-deployment`, where a fork of the extension system arrives.

**Fallback if task 1.1 fails:** ship `LevelPermissionPolicy` only (operator-level gating, the same behaviour as today plus the
setup node), keep the port and the nodes, and move the LuckPerms adapter to a follow-up change. Stop before task 2 and report to the
owner. Do not switch to a different permission backend without a new decision.

### Other risks

- **R6 Operators lose `/race reload` unless they hold level 4 or LuckPerms grants the node.** Mitigation: the reference page and the
  how-to state it; the WARN line names the fallback.
- **R7 Velocity secret in logs.** Mitigation: the record masks it; a unit test asserts the masked output; the spec forbids printing.
- **R8 `ConsoleCommandReader` move breaks imports in the boot tests.** Mitigation: the move is its own commit (`refactor` scope inside
  the feature PR), and the fitness suite runs after it.

## Migration Plan

1. Spike (group 1), then the two ADRs (group 2), all in Proposed state.
2. Red then Green per group; the fitness suite runs after each group.
3. Deploy: the operator sets `VOYAGER_VELOCITY_SECRET` on the proxy-facing servers, grants the nodes in LuckPerms, and starts the
   jar as before. Rollback: unset the secret (offline, with the WARN) and redeploy the previous jar. The fallback keeps the console
   working, so an operator can still run `stop` and `/race reload` from the console during a rollback.
