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

- `voyager-cloudnet-bridge` depends on Minestom, Adventure and `eu.cloudnetservice` (driver-api, bridge-api, bridge-impl) as
  `compileOnly`, and on nothing else of Voyager. It does not call the PermissionPolicy port: the pointer that it reads is
  resolved through the port by `VoyagerPlayer` in `voyager-platform` (Decision 2). This is how Cygnus's `bridge` module works too,
  which reads `PermissionChecker.POINTER` and never names its LuckPerms backend (align commit, `docs(openspec)`).
- No composition root and no platform class depends on the bridge module. The roots reach it only at runtime, through the
  `extensions/` folder. This keeps the shadow jars free of CloudNet classes by construction, not only by a check.
- `voyager-platform` holds `VoyagerPlayer`, the pointer-carrying `Player` subclass, and `VoyagerPlayerProvider`. Both use
  `PermissionPolicy`, Minestom and Adventure only.

| Type | Module and package | Notes |
|---|---|---|
| `CloudNetPermissionChecker` | `voyager-cloudnet-bridge`, `net.elytrarace.voyager.cloudnet.bridge` | CloudNet-free logic: reads `PermissionChecker.POINTER` from a Adventure `Pointered` and answers with its `test(node)`, false when the pointer is absent; unit-tested with a fake `Pointered` |
| `VoyagerCloudNetExtension` (`@ExtensionInfo`, `dependencies = "CloudNet_Bridge"`) | same | the only class that calls CloudNet's `ServiceRegistry`; `ExtensionInfo` has source retention, so its test reads the generated `extension.json` |
| `VoyagerPlayer` and `VoyagerPlayerProvider` | `voyager-platform`, `net.elytrarace.voyager.platform.player` | `pointers()` carries `PermissionChecker.POINTER` backed by the policy |

### 2. The permission exchange: POINTER on the player, a string form on the port

The owner's instruction is that the CloudNet checker reads Adventure's `PermissionChecker.POINTER`. Minestom does not install that
pointer, so `VoyagerPlayer` installs it, as Cygnus's `PermissionAwarePlayer` does, and `VoyagerPlayerProvider` makes every
player that class (`ConnectionManager.setPlayerProvider`, which Minestom 26.2 exposes).

**Conflict found in the first change, and resolved here.** CloudNet asks for permission names that Voyager does not own,
for example `cloudnet.bridge.maintenance`. The `PermissionNode` enum of `add-luckperms-permissions` is closed, so the port
cannot answer those names. Answering them from LuckPerms directly would break the confinement rule. This change therefore
adds a second method to `PermissionPolicy`: `boolean allows(PermissionSubject subject, String node)`, which the enum method
delegates to. Checked on `main` at apply time (2026-10-11): `add-luckperms-permissions` is archived and already has this method
(`voyager/api/.../permission/PermissionPolicy.java`), so no amendment is needed in this change. Voyager's own commands keep the enum overload.

### 3. Extension bootstrap (the `minestom-extensions` fork)

Minestom 26.2 has no extension system; the greenfield design says so and gives the fix: the fork `net.onelitefeather:minestom-extensions`
(Cygnus pins 2.2.0). **Corrected at apply time, against the 2.2.0 jar (align commit):** the fork has no `ExtensionBootstrap.bootstrap()`.
Its API is `ExtensionBootstrap.init()` or `init(Auth)`, which calls `MinecraftServer.init(auth)` itself, and then
`bootstrap.start(host, port)`, which binds. Cygnus's `CygnusLoader` uses exactly this pair, and `init` must be the call that the
roots already make to `MinecraftServer.init(auth)`, because Velocity forwarding is fixed there. So the bootstrap replaces two calls in
each root: `MinecraftServer.init(auth)` becomes `ExtensionBootstrap.init(auth)` at the same place (after the settings and the
LuckPerms start, before the graph, because the graph needs the registries), and `server.start(host, port)` becomes
`bootstrap.start(host, port)` at the end. "After the graph is wired" still holds for the bind.
Checked at apply time (2026-10-11, the fork's 2.2.0 POM): the fork's runtime dependencies are Maven Resolver and slf4j, and its POM
names no `com.github.Minestom:DependencyGetter`. So no JitPack proxy repository is needed, and none is declared. The fork resolves from
`https://repo.onelitefeather.dev/releases`, which the root `build.gradle.kts` already lists. Cygnus declares the proxy for its `Canis`
dependency, which Voyager does not use. This corrects the first draft of this section, which said the proxy was required.

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

### 6. CloudNet version: 4.0.0-RC17, the release the owner's network runs (owner decision, 2026-10-11; supersedes the RC16 choice)

**Decided 2026-10-11:** the bridge builds against `4.0.0-RC17`, the version the owner's network runs. The pin is `version("cloudnet", "4.0.0-RC17")`
in `settings.gradle.kts`, and RC17 resolves from Maven Central (`bridge-api`, `bridge-impl`, `driver-api` and the `bom`, checked by
`:voyager:cloudnet-bridge:dependencies`). The RC16 choice below is kept as history: it was the version Cygnus builds against, and
the bridge's API calls are the same in both releases (spike 1.4). Risk R8 is resolved by the owner's decision; the node acceptance (group 10) still
verifies the bridge on the node.

The earlier choice, which the owner has now superseded: the version is whatever Cygnus runs on the same network, which is `4.0.0-RC16` in Cygnus's version catalog
(`cloudnet` version, `cloudnet-bom`, `cloudnet-bridge` = `bridge-api`, `cloudnet-bridge-impl` = `bridge-impl`,
`cloudnet-driver-api` = `driver-api`). This change uses the same coordinates, and the same `minestom-extensions` 2.2.0 that
Cygnus pins. Cygnus's bridge (`bridge/`, `CygnusBridgePermissionExtension`) is the pattern.

Spike 1.4 (run 2026-10-11, `javap` on the Maven Central jars of 4.0.0-RC16 and 4.0.0-RC17): the calls the extension makes are the same
in both releases. `MinestomPermissionChecker` is `boolean hasPermission(Player, String)` in both, `ServiceRegistry.registerProvider(Class,
String, S)` returns `ServiceRegistryRegistration<S>` in both, and that interface has `markAsDefaultService()` in both. So the RC16 compile
target serves the RC17 node for this extension. The RC17 node runs a locally patched CloudNet-Bridge for Minestom (owner, 2026-10-10). The
HOTFIX-README is not at either path given (`/home/cloud/cloudnet`, `~/cloudnet`), so that patch is not read here, and spike 1.1 on the node
is the test of it.

Spike 1.1, partial (2026-10-11): the RC16 `bridge-impl` jar is the `CloudNet_Bridge` extension itself (its `extension.json` names
`CloudNet_Bridge` 4.0.0-RC16). Placed in `extensions/` beside the Voyager bridge and started on a bare JVM with the setup fat jar on
Minestom 2026.09.12-26.2, it fails at initialisation with `NoClassDefFoundError: eu/cloudnetservice/ext/platforminject/api/PlatformEntrypoint`.
That class belongs to the node's wrapper, which is absent on a bare JVM, so the failure is the expected one for this setup and not a
Minestom 26.2 incompatibility. Two findings follow. The fork does not isolate a failing extension: the boot aborts in
`ExtensionBootstrap.start` before the Voyager bridge is reached. And a broken `CloudNet_Bridge` stops the service, where a missing one
is skipped. The node acceptance (group 10) is the real test of the bridge; spike 1.1 is closed as "resolved on the node only".

Evidence recorded, read from the CloudNet repository's GitHub releases on 2026-10-10:
- `4.0.0-RC16`, published 2026-01-11: the only Minecraft version named is 1.21.11. Its notes also say the CloudNet-Bridge "does not
  enable any proxy authentication anymore", which the Minestom implementation must do. That is the Velocity forwarding of
  `add-luckperms-permissions`.
- `4.0.0-RC17`, published 2026-08-09: "Support Minecraft 26.X (#1866)".

So RC16 is not documented for Minecraft 26.2, and RC17 is. The owner decided on RC17 on 2026-10-11 (above). Before that decision the gap was
recorded as Risk R8, to be tested by spike 1.1 and acceptance group 10. The earlier fallback text follows, kept for history: if the bridge fails on 26.2 with RC16, the fallback is RC17 with its bridge API re-checked (spike 1.4), which
is a new decision for the owner, not a silent upgrade.

### 7. Owner-confirmed (2026-10-10): one change, one commit per type

The owner confirmed that this stays one change, as `switch-di-to-avaje-inject` did. Its commits are one per type: `feat(cloudnet)` for
the bridge and the deployment specs, `build(setup)` for the setup shadow jar, and `docs(cloudnet)` for the guide, ADR-0026 and CLAUDE.md.
The squash title is the `feat` line. The owner also confirmed the catalogue layout of Decision 5.

### 8. Launch and stop, as the node runs them (aligned with Cygnus's removed deployment doc)

Cygnus's `docs/cloudnet-deployment.md` (removed in `c216431f`) describes how the node starts a service, and this change follows it:

```
java … -javaagent:<wrapper.jar> -Dservice.bind.host=<host> -Dservice.bind.port=<port>
       -cp <wrapper.jar>:<app.jar>  <Main-Class from the app manifest>
```

- The application jar is launched through `-cp` plus its manifest `Main-Class`, not `-jar`, so the wrapper and the application share one
  class loader. This is why no CloudNet artifact may be bundled into a fat jar.
- The node writes `end` and then `stop` to stdin. `end` is not a registered command and is ignored, and `stop` ends the service
  cleanly through `ServiceShutdown` (`add-luckperms-permissions`).
- The guide names the Voyager properties and env variables the code reads, not Cygnus's: `-Dminestom-velocity-secret` becomes the
  `VOYAGER_VELOCITY_SECRET` environment variable or `-Dvoyager.velocity.secret` (`ProxyForwardingSettings`).
- Standalone runs need none of this: `service.bind.host` and `service.bind.port` default to the server's own defaults, and no
  `extensions/` folder means no bridge.

### Fitness rules (voyager-fitness)

| Boundary | Rule | Where |
|---|---|---|
| `eu.cloudnetservice..` referenced only from the bridge package | `cloudNetIsConfinedToBridge` | new `CloudNetBoundaryTest` |
| roots and platform never reference CloudNet or the bridge | `rootsAndPlatformDoNotDependOnCloudNet` | `CloudNetBoundaryTest` |
| the bridge depends only on JDK, Minestom, Adventure, CloudNet and its own package; no `voyager` package (the pointer is resolved in the platform) | `bridgeDependsOnlyOnJdkAndLibraries` | `CloudNetBoundaryTest` |
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
  with Voyager's rebuild pin `2026.09.12-26.2` (`voyager/platform/build.gradle.kts`; the first draft of this design said `2026.08.28-26.2`)
  is therefore not verified until spike 1.2 boots the server through the bootstrap. Renovate must not
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
- **R8 RC16 not documented for 26.2. RESOLVED 2026-10-11 by the owner's decision to build against RC17** (Decision 6), which is the release
  documented for 26.X and the one the owner's network runs. What remains is verification on the node (group 10), not a version question.

## Migration Plan

1. Spikes (group 1), then ADR-0026 (group 2) and the fitness rules (group 3), all in Proposed state until the owner accepts.
2. Red then Green per group, and the Gradle checks after each.
3. Deploy: copy the service root JAR and the bridge extension into the CloudNet template by hand, as Cygnus does, start one game and one setup service, run acceptance 10.
4. Rollback: stop the services with `stop`, remove the bridge extension from `extensions/`, and redeploy the previous template. The
   service runs without the bridge (R4), so a rollback never leaves a service that cannot start.

## Runtime configuration: how Cygnus gets its values, and what Voyager does (owner requirement, 2026-10-11)

The owner's goal: an ElytraRace service (the live CloudNet task name; Voyager is the code name) needs only its JAR or JARs and
its world and catalogue data, with no hand-edited file and no `-D` flag beyond what the node sets itself. The Cygnus sources were
read at `/mnt/projects/oss/onelitefeather/Cygnus` (read-only). Secret values are not recorded here.

| Value | Cygnus: where it comes from | Voyager before this change | Voyager target |
|---|---|---|---|
| Bind host and port | `service.bind.host`, `service.bind.port` system properties, set by the node (`common/.../bootstrap/ServiceBootstrap.java`) | same properties (`ServerSettings`, `SetupSettings`) | unchanged: set by the node, nothing in the JAR |
| Working directory | `service.working.dir` property from the node, default the process's directory (`ServiceBootstrap.java`) | relative paths resolve against the process directory | unchanged |
| Data and worlds paths | Hard-coded relative paths `game/maps` and `setup/maps` under the working directory (removed doc `c216431f^:docs/cloudnet-deployment.md`) | `VOYAGER_DATA_PATH`, `VOYAGER_WORLDS_PATH` system properties only; defaults `run/data` and `run/worlds` (`ServerSettings.java` 88-90), so every service needs two `-D` flags | production defaults become the template layout, `catalog` and `worlds` under the working directory, as Cygnus fixes its own; the Gradle run tasks keep `run/…` by passing the properties explicitly. Decision 9 |
| Velocity forwarding secret | `-Dminestom-velocity-secret` system property, from the node's JVM options (`ServiceBootstrap.java` 31, 83) | `VOYAGER_VELOCITY_SECRET` environment variable, or `-Dvoyager.velocity.secret` (`ProxyForwardingSettings.java` 20-23) | unchanged, and the node sets the environment variable in the task's environment; no file holds it |
| Gameplay values (players, timings, teams, page hints, resource pack URL and checksum, Sentry DSN) | `config.properties` in the working directory, optional, every key defaulted in code (`GameConfigReader.java`) | none; `VOYAGER_CUP`, `VOYAGER_MIN_PLAYERS` system properties for optional tuning | no file in this change. Where config defaults come from is Owner decision 2 |
| Telemetry service name and exporter | Environment: `OTEL_SERVICE_NAME`, `OTEL_EXPORTER_OTLP_ENDPOINT`, plus the OpenTelemetry agent attached with `-javaagent` (`docs/telemetry.md`); nothing in the JAR | no OpenTelemetry in Voyager | not applicable. If Voyager adds telemetry, it follows Cygnus: the agent from the node, and `OTEL_SERVICE_NAME=ElytraRace` from the environment |
| LuckPerms storage, server context, messaging | Not in the repository. The `data/config.yml` of the service is placed by hand on the node. The repository reads nothing for it | the loader creates `data/config.yml` with `storage-method: h2` and `server: global` on first start (checked in the smoke run, 2026-10-11); H2 under `data/` | Decided 2026-10-11, owner decision 1, "like Cygnus": a `data/config.yml` placed by hand in the service template, with the network's storage (MariaDB, Redis) and the context `server: elytrarace`. No repository code, no credentials in the repository or a JAR. The how-to is in the guide, "LuckPerms" |
| JVM flag `minestom.automatic-component-translation=true` | Not examined in Cygnus's sources for this note | required: both roots refuse to start without it (`VoyagerServer.main`, `SetupServer.main`), and the Gradle run tasks pass it | Decision 10 |
| Application file, Main-Class launch | `-cp <wrapper>:<app>` with the manifest `Main-Class` (Decision 8) | same | same; the node names the application file |

**Decision 9 (Voyager, in this change, implemented unless the owner says otherwise):** a production service reads its catalogue
and worlds from `catalog` and `worlds` under its working directory with no property set. Cygnus does the same with its fixed
relative paths, so this is the same mechanism. The two `-D` flags remain for overriding and for the Gradle run tasks, which keep
their `run/` layout by passing the properties explicitly. The defaults in `ServerSettings` and `SetupSettings` change, and the
test that pins them changes with them. This is a change of behaviour for a bare `java -jar` run, which is the reason it is recorded.
**Callout (owner, 2026-10-11):** a deploy that passes `-DVOYAGER_DATA_PATH` and `-DVOYAGER_WORLDS_PATH` explicitly is unaffected, because
an explicit property always wins. The live ElytraRace task passes `run/data` and `run/worlds` explicitly, so it keeps working unchanged.
Only a service started with neither property changes: it now reads `catalog` and `worlds` instead of `run/data` and `run/worlds`.

**Owner decision 1 — DECIDED 2026-10-11: "like Cygnus".** The LuckPerms storage configuration is a `data/config.yml` placed by hand in the
service template, as Cygnus does. The repository holds no code for it, and no credential goes into the repository or the JAR. The
how-to is in `docs/guides/how-to-deploy-on-cloudnet.md` ("LuckPerms configuration"). Options A to C below are the alternatives the
owner did not choose; they stay here as the record of what was weighed.

The repository holds no such configuration for Cygnus either, so "the Cygnus way" is a file placed by hand on the node. Options:
- A. Keep the hand-placed `data/config.yml` per service, outside every JAR. It holds the credentials, so it cannot be bundled. This
  is the current live behaviour. It is not zero-touch, and `data/` is per service, so the MariaDB storage would also need the
  "per service data" rule of `cloudnet/service-template` changed.
- B. A non-secret `config.yml` template in the JAR, written into `data/` on first start only when absent, with the MariaDB and
  Redis credentials read from environment variables that the node sets. This needs substitution code in `voyager-platform`, a
  spec change, and a check that LuckPerms' own config accepts the values. It is zero-touch for the operator.
- C. avaje-config, the configuration library on the stats roadmap (`project_stats_persistence_roadmap`). It is the right long-term
  answer and a new dependency, so it is a separate change.
Recommendation: B for this change, C later. The owner decides. Until then, the owner's MariaDB and Redis settings stay hand-placed.

**Owner decision 2 (open):** where configuration defaults come from for values that are not paths: the code (as `GameConfigReader`
does for Cygnus), a file (as `config.properties`), or avaje-config (option C above). Not decided here.

**Decision 10 (partly done 2026-10-11):** the translation flag is read once, when `ServerFlag` loads, so it can only be set before
Minestom's classes initialise. Status: the setup server sets it in code, first thing in `SetupServer.main`, as #299 (`fix(setup)`) landed
on `main`. The game server still needs the JVM flag until #295 (`fix(server)`) lands. The text below is the original question. A launcher class as the Main-Class could call `System.setProperty` and then start the root. If the
spike shows that works on 26.2 with the fork, the flag becomes unnecessary. If not, the node's task sets it as a JVM option, and
it is documented as the one flag an operator sets.

**Result of the spike (2026-10-11): it works.** A scratch launcher (outside the repository) sets the property and then invokes
`SetupServer.main` by reflection. The setup fat jar then starts with no translation flag, listens, logs "Minestom server started
successfully", and stops on stdin with exit 0. A control run of the same jar without the launcher is refused with "-Dminestom.automatic-component-translation=true is
not set". So the launcher is the cause. The launcher changes the Main-Class of both roots and touches the composition-root comments,
so it is a separate change (tasks 8A.5), and this change keeps the flag for the Gradle run tasks. Until that change lands, the node's
task sets the flag as a JVM option, the one flag an operator sets.

## Open Questions

These two are for the owner. They do not change the capability specs, which name the contract, but they change the template
and so block group 10 (node acceptance) until answered.

1. **Does the setup service write into `worlds/`?** If it does, the setup service needs its own `worlds/` per service, and the
   template contract in `cloudnet/service-template` changes.
2. **Does the node's task definition accept JVM options**, such as `-DVOYAGER_DATA_PATH=catalog`? Cygnus relies on relative paths and sets
   none. If the node cannot set them, the defaults must change so that `run/data` resolves inside the service directory, which is a spec change.
