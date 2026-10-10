# Design

## Context

`add-luckperms-permissions` gives both composition roots one PermissionPolicy, Velocity forwarding, the `service.bind.*`
properties and a stdin `stop`. This change connects those to a CloudNet 4 node. The greenfield design (D15, currently
written as "deliberately deferred") fixes three constraints that this change keeps: the bridge is its own extension module
with `compileOnly` CloudNet dependencies, the `voyager-server` composition root references no `eu.cloudnetservice` type, and
the application and the extension exchange only JDK types and `voyager-api` types.

The owner tests directly on the existing CloudNet network, not in a local run first. Decided by the owner (2026-10-10):
the network runs CloudNet 4.0.0-RC16 with Cygnus, the proxy is Velocity, and JARs are copied by hand into the CloudNet
template, as Cygnus does. No CI upload.

Cygnus runs the same arrangement in production (`bridge/` module, `cygnus-bridge` extension, `CloudNet_Bridge` dependency,
`service.bind.*`, stdin `stop`). Its `docs/cloudnet-deployment.md` was removed in commit `c216431f`; its text is the source
for the guide, adapted to Voyager's paths.

## Goals / Non-Goals

**Goals:**
- A bridge module, `voyager-cloudnet-bridge`, that answers CloudNet's permission questions through the PermissionPolicy.
- Fat jars for both roots, with no CloudNet class in any of them.
- A service directory layout that two services on one node can share safely (shared catalogue and worlds, private data).
- A deployment guide that matches the code and the layout, and a node acceptance run on the owner's network.

**Non-Goals:**
- CloudNet features beyond permissions and lifecycle: no lobby routing after a cup, no service snapshots, no player transfer.
  The greenfield design lists those as later uses of the bridge. Each would be its own change.
- A CloudNet node setup: the owner runs the node. This change documents what the node must provide.
- LuckPerms storage changes and PostgreSQL (persistence stage).
- The broken `docker/server/Dockerfile`.

## Decisions

### 1. Module and dependency direction (Clean Architecture; the bridge is an outer adapter)

```
voyager-api  <-  voyager-platform  <-  voyager-server, voyager-setup     (composition roots, unchanged rule)
voyager-api  <-  voyager-cloudnet-bridge                                  (extension, loaded by CloudNet's classloader)
```

- `voyager-cloudnet-bridge` depends on `voyager-api` (the PermissionPolicy port) and on Minestom and Adventure as `compileOnly`.
  It depends on `eu.cloudnetservice` (driver-api, bridge-api, bridge-impl) as `compileOnly` and on nothing else of Voyager.
- No composition root and no platform class depends on the bridge module. The roots reach it only at runtime, through the
  `extensions/` folder. This keeps the shadow jars free of CloudNet classes by construction, not only by a check.
- `voyager-platform` holds `VoyagerPlayer`, the pointer-carrying `Player` subclass, and `VoyagerPlayerProvider`. Both use
  `PermissionPolicy`, Minestom and Adventure only.

| Type | Module and package | Notes |
|---|---|---|
| `CloudNetPermissionChecker` | `voyager-cloudnet-bridge`, `net.elytrarace.voyager.cloudnet.bridge` | CloudNet-free logic: maps a player and a permission name to the policy; unit-tested with a fake policy |
| `VoyagerCloudNetExtension` (`@ExtensionInfo`, `dependencies = "CloudNet_Bridge"`) | same | the only class that calls CloudNet's `ServiceRegistry` |
| `VoyagerPlayer` and `VoyagerPlayerProvider` | `voyager-platform`, `net.elytrarace.voyager.platform.player` | `pointers()` carries `PermissionChecker.POINTER` backed by the policy |

### 2. The permission exchange: POINTER on the player, a string form on the port

The owner's instruction is that the CloudNet checker reads Adventure's `PermissionChecker.POINTER`. Minestom does not install that
pointer, so `VoyagerPlayer` installs it, as Cygnus's `PermissionAwarePlayer` does, and `VoyagerPlayerProvider` makes every
player that class (`ConnectionManager.setPlayerProvider`, which Minestom 26.2 exposes).

**Conflict found in the first change, and resolved here.** CloudNet asks for permission names that Voyager does not own,
for example `cloudnet.bridge.maintenance`. The `PermissionNode` enum of `add-luckperms-permissions` is closed, so the port
cannot answer those names. Answering them from LuckPerms directly would break the confinement rule. This change therefore
adds a second method to `PermissionPolicy`: `boolean allows(PermissionSubject subject, String node)`, which the enum method
delegates to. `add-luckperms-permissions` is amended in the same working tree to add this method (its design Decision 1, its
spec requirement "Gated actions ask one permission port", and its task 4.2). Voyager's own commands keep the enum overload.

### 3. Extension bootstrap (the `minestom-extensions` fork)

Minestom 26.2 has no extension system; the greenfield design says so and gives the fix: the fork `net.onelitefeather:minestom-extensions`
(Cygnus pins 2.2.0) with `ExtensionBootstrap.bootstrap()` and `bootstrap.start(bindHost, bindPort)`. The fork resolves dependencies through Maven
Resolver, which pulls `com.github.Minestom:DependencyGetter` from JitPack, so the organisation proxy
`https://repo.onelitefeather.dev/onelitefeather-proxy` must be declared in `settings.gradle.kts` alongside the other repositories.
The bootstrap replaces the direct `server.start(host, port)` call in both roots, and runs after the graph is wired and before the port binds.

Why the bootstrap must not be skipped: the CloudNet_Bridge extension does not load unless the bootstrap runs, and nothing reports that cause
(greenfield design, "Extensions must be bootstrapped explicitly").

### 4. Fat jars and the no-CloudNet rule

`voyager-server` already has a shadow jar. `voyager-setup` gets the same configuration (`alias(libs.plugins.shadow)`, `Main-Class`,
`mergeServiceFiles()`, signature-file exclusions). A Gradle check `verifyNoCloudNetInFatJars` scans both jars for any entry under
`eu/cloudnetservice/` and is wired into `check`. The fitness rule `rootsAndPlatformDoNotDependOnCloudNet` covers the source side.

### 5. Service directory layout

The owner asked for "maps/worlds, data/ for LuckPerms H2". The catalogue is read from `VOYAGER_DATA_PATH`, which holds `maps/` and
`cups/` directly, so the template keeps those under a directory of their own: `catalog/`, with `catalog/maps` and `catalog/cups`.
Owner-confirmed (2026-10-10): `catalog/maps` and `catalog/cups`, with worlds kept separate in `worlds/`. The reason is that `data/`
must hold per-service state and cannot be a template directory (Cygnus's rule: "everything except data/ belongs in the template").

```
<service>/                              template (shared, read-only in practice)
├── voyager-server.jar | voyager-setup.jar   application file (name as in Cygnus's template, checked in task 10.1)
├── extensions/
│   ├── CloudNet-Bridge.jar             from the node
│   └── voyager-cloudnet-bridge.jar     this change
├── catalog/
│   ├── maps/...                        VOYAGER_DATA_PATH=catalog
│   └── cups/...
├── worlds/                             VOYAGER_WORLDS_PATH=worlds
└── data/                               per service, created at first start: LuckPerms config and H2
```

Path resolution: the server's defaults are relative (`run/data`, `run/worlds`), so the template sets both system properties explicitly
through the service's JVM options. Whether the node's task definition accepts JVM options is an open question.

### 6. CloudNet version: 4.0.0-RC16, matched to Cygnus (owner decision)

The owner's decision: the version is whatever Cygnus runs on the same network, which is `4.0.0-RC16` in Cygnus's version catalog
(`cloudnet` version, `cloudnet-bom`, `cloudnet-bridge` = `bridge-api`, `cloudnet-bridge-impl` = `bridge-impl`,
`cloudnet-driver-api` = `driver-api`). This change uses the same coordinates, and the same `minestom-extensions` 2.2.0 that
Cygnus pins. Cygnus's bridge (`bridge/`, `CygnusBridgePermissionExtension`) is the pattern.

Evidence recorded, read from the CloudNet repository's GitHub releases on 2026-10-10:
- `4.0.0-RC16`, published 2026-01-11: the only Minecraft version named is 1.21.11. Its notes also say the CloudNet-Bridge "does not
  enable any proxy authentication anymore", which the Minestom implementation must do. That is the Velocity forwarding of
  `add-luckperms-permissions`.
- `4.0.0-RC17`, published 2026-08-09: "Support Minecraft 26.X (#1866)".

So RC16 is not documented for Minecraft 26.2, and RC17 is. The owner keeps RC16. The gap is recorded as Risk R8 and tested by spike 1.1
and acceptance group 10. If the bridge fails on 26.2 with RC16, the fallback is RC17 with its bridge API re-checked (spike 1.4), which
is a new decision for the owner, not a silent upgrade.

### 7. Owner-confirmed (2026-10-10): one change, one commit per type

The owner confirmed that this stays one change, as `switch-di-to-avaje-inject` did. Its commits are one per type: `feat(cloudnet)` for
the bridge and the deployment specs, `build(setup)` for the setup shadow jar, and `docs(cloudnet)` for the guide, ADR-0026 and CLAUDE.md.
The squash title is the `feat` line. The owner also confirmed the catalogue layout of Decision 5.

### Fitness rules (voyager-fitness)

| Boundary | Rule | Where |
|---|---|---|
| `eu.cloudnetservice..` referenced only from the bridge package | `cloudNetIsConfinedToBridge` | new `CloudNetBoundaryTest` |
| roots and platform never reference CloudNet or the bridge | `rootsAndPlatformDoNotDependOnCloudNet` | `CloudNetBoundaryTest` |
| the bridge depends only on `voyager-api` (and JDK, Minestom, Adventure) | `bridgeDependsOnlyOnApi` | `CloudNetBoundaryTest` |
| the bridge module is covered by `FitnessCoverageTest` | a `PACKAGE_PREFIX_BY_PROJECT` entry `:voyager:cloudnet-bridge` -> `net.elytrarace.voyager.cloudnet.bridge` | `FitnessCoverageTest` |
| fat jars contain no CloudNet class | Gradle check `verifyNoCloudNetInFatJars` (not ArchUnit, because it reads jars) | `voyager/setup/build.gradle.kts`, `voyager/server/build.gradle.kts` |

## Risks / Trade-offs

- **R1 Bridge API against the node's release unverified.** The bridge's exact CloudNet classes and the registration call are not
  checked against the RC16 `bridge-api` jar yet. Mitigation: spike 1.4 reads that jar; the registration call is Cygnus's, so if it
  differs, the extension changes, not the port.
- **R2 `minestom-extensions` fork on Minestom 26.2 unverified.** Cygnus runs `minestom-extensions` 2.2.0 on the same network, so the
  combination is known to work for Cygnus. Cygnus's Minestom build is the version its `aonyx-bom` pins. The cached `aonyx-bom` 0.8.7
  pulls `mycelium-bom` 1.8.5, which pins Minestom `2026.08.28-26.2`, the same build Voyager uses. Cygnus HEAD pins `aonyx-bom` 0.8.8,
  which could not be read here (401 from the repository), so its Minestom build is inferred, not read. Whether the combination works
  with Voyager's `2026.08.28-26.2` is therefore not verified until spike 1.2 boots the server through the bootstrap. Renovate must not
  raise the fork without a boot test.
- **R3 Player subclass changes creation.** Replacing the player provider affects every player. Mitigation: `VoyagerPlayerTest` in the
  Minestom test environment, and acceptance 10.2 on the node.
- **R4 Missing CloudNet_Bridge fails closed for staff.** With the bridge absent, CloudNet's own checker reads a permission level that is zero
  for every Voyager player, so staff lose maintenance bypass. Mitigation: the extension is skipped with a log line, and the guide says so.
- **R5 Node acceptance is manual.** The owner's network is the only place the full chain runs. Mitigation: group 10 is written as a
  checklist with expected log lines, run once per service type.
- **R6 Alternative not taken: the bridge answers through the port without a pointer.** It would avoid the Player subclass, but the owner
  asked for the POINTER pattern, and Cygnus's guide shows the pointer is what CloudNet's checker reads. If the subclass ever conflicts
  with a Minestom update, the bridge can switch to the port directly; that is a new decision, not a silent change.
- **R7 Writable worlds.** If the setup service writes world data, `worlds/` cannot be a shared template directory. Open question 1.
- **R8 RC16 not documented for 26.2.** See Decision 6. Mitigation: spike 1.1 and the 26.2 result recorded before acceptance; RC17 is the
  documented fallback and needs a new owner decision.

## Migration Plan

1. Spikes (group 1), then ADR-0026 (group 2) and the fitness rules (group 3), all in Proposed state until the owner accepts.
2. Red then Green per group, and the Gradle checks after each.
3. Deploy: copy the service root JAR and the bridge extension into the CloudNet template by hand, as Cygnus does, start one game and one setup service, run acceptance 10.
4. Rollback: stop the services with `stop`, remove the bridge extension from `extensions/`, and redeploy the previous template. The
   service runs without the bridge (R4), so a rollback never leaves a service that cannot start.

## Open Questions

These two are for the owner. They do not change the capability specs, which name the contract, but they change the template
and so block group 10 (node acceptance) until answered.

1. **Does the setup service write into `worlds/`?** If it does, the setup service needs its own `worlds/` per service, and the
   template contract in `cloudnet/service-template` changes.
2. **Does the node's task definition accept JVM options**, such as `-DVOYAGER_DATA_PATH=catalog`? Cygnus relies on relative paths and sets
   none. If the node cannot set them, the defaults must change so that `run/data` resolves inside the service directory, which is a spec change.
