# Proposal

**Conventional Commits:** `feat(cloudnet)`. Squash-merge PR title: `feat(cloudnet): add the CloudNet bridge and service deployment`.

**Owner-confirmed (2026-10-10):** this stays one change, with one commit per type (`feat`, `build`, `docs`), as `switch-di-to-avaje-inject` did.

**Depends on:** `add-luckperms-permissions` (`feat(server)`). This change needs the PermissionPolicy port, the
LuckPerms-backed policy, the Velocity forwarding and the `service.bind.*` and stdin `stop` behaviour from that change. It
starts only after that change is archived.

Slices touched: the deployment of the game and setup services (new), a new bridge module `voyager-cloudnet-bridge`
(the ninth module in the greenfield design, D15), and the distribution of `voyager-setup` (a fat jar, like
`voyager-server`). The race, ring and cup slices are not touched.

Decided by the owner (2026-10-10): CloudNet 4.0.0-RC16, the version Cygnus pins; Velocity as the proxy; application JARs copied
by hand into the CloudNet template, as Cygnus does. Out of scope: PostgreSQL
storage for LuckPerms (the persistence stage), the broken `docker/server/Dockerfile` (it copies `*-all.jar` from the
legacy tree, so it is not part of this change), and any change to the tree being replaced.

## Why

The owner runs CloudNet 4 on a network node and wants the rebuilt game and setup servers to run there as services. A
CloudNet service needs three things from the server: a bridge extension so the node can route, maintain and check
permissions for players, the bind address and a clean `stop` (added by `add-luckperms-permissions`), and a service
template with a fixed directory layout. The greenfield design deferred the bridge to a later stage (D15 is written as
deferred). The owner has now brought it forward, so the decision changes, and the change records it.

The owner's network already runs CloudNet 4.0.0-RC16 with Cygnus, and this change matches that version. The RC16 release
notes name Minecraft 1.21.11 as the newest supported version, and RC17 (2026-08-09) adds "Support Minecraft 26.X". Whether
RC16 works with Minecraft 26.2 is therefore a recorded risk, and spike 1.1 and acceptance group 10 settle it.

## What Changes

- New Gradle module `voyager-cloudnet-bridge` (Gradle path `:voyager:cloudnet-bridge`). It is the only module that
  references `eu.cloudnetservice..`, with every such dependency `compileOnly`. It is packaged as a Minestom extension
  with `extension.json` declaring the dependency `CloudNet_Bridge`, and it is not bundled into any fat jar.
- The bridge registers a CloudNet permission checker that reads Adventure's `PermissionChecker.POINTER` from the player,
  as Cygnus's `cygnus-luckperms` bridge does. The pointer is supplied on every Voyager player by a `Player` subclass
  registered through `ConnectionManager.setPlayerProvider`, and it resolves through the `PermissionPolicy` port.
- Both composition roots start the Minestom extension bootstrap before binding the port, through the
  `net.onelitefeather:minestom-extensions` fork (Minestom 26.2 has no extension system of its own).
- `voyager-setup` gets a fat jar (`shadowJar`) with a manifest `Main-Class` and merged service files, matching
  `voyager-server`. No CloudNet artifact is bundled into either fat jar.
- A CloudNet service template layout for the game and setup services, documented as a how-to in `docs/guides/`,
  adapted from Cygnus's removed `docs/cloudnet-deployment.md`.
- Decision records: `docs/decisions/0026-*.md` (the bridge as an extension module, Proposed), the greenfield design
  decision table row D15 with the status of the ninth module changed from deferred to in progress, and a CLAUDE.md
  module-table row for `voyager-cloudnet-bridge`.
- CloudNet version: `4.0.0-RC16`, the version Cygnus pins (`cloudnet` in its version catalog).

## Capabilities

### New Capabilities
- `cloudnet/bridge-extension`: the Minestom extension that connects Voyager's permission port to the CloudNet bridge,
  its dependency on `CloudNet_Bridge`, the pointer each player carries, and the rule that only JDK types and `voyager-api`
  types cross the extension boundary.
- `cloudnet/service-template`: the directory layout of a game or setup service under a CloudNet node, which paths are
  shared and which are per service, and how a service stops when the node asks.
- `server/distribution`: the fat jars that the rebuilt servers ship as, and the rule that none of them contains a
  CloudNet class.

### Modified Capabilities
- None.

## Impact

- New module `voyager/cloudnet-bridge`, added to `settings.gradle.kts`, to the fitness coverage map and to the
  `voyager.modulesWithSources` list.
- `voyager-platform`: `VoyagerPlayer` (the pointer-carrying player) and its provider, in `net.elytrarace.voyager.platform.player`.
- `voyager-server` and `voyager-setup`: extension bootstrap before the port binds; `voyager-setup` gains `shadow` and a fat jar.
- `voyager/fitness`: new `CloudNetBoundaryTest` (confinement of `eu.cloudnetservice..` to the bridge), and a Gradle check that
  scans fat jars for CloudNet classes.
- Docs: how-to `docs/guides/how-to-deploy-on-cloudnet.md`, the CLAUDE.md module table, the design spec's D15, ADR-0026.
- Operators: the node must run CloudNet 4.0.0-RC16, must hold the `CloudNet_Bridge` extension in every service's
  `extensions/` folder, and must set the template's JVM options for the data and worlds paths.
