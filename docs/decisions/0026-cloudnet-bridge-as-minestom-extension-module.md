# ADR-0026: The CloudNet bridge as a Minestom extension module, loaded from the node's extensions folder

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `add-cloudnet-deployment`, which does not mark this record Accepted.

## Date

2026-10-11

## Decision makers

- TheMeinerLP (project owner). Brought the bridge forward from the deferred ninth module (greenfield design, D15) on
  2026-10-10, and asked for the runtime configuration to need no hand-edited file (2026-10-11).
- Drafted with the change `add-cloudnet-deployment`, after reading Cygnus's bridge (`bridge/`, `CygnusBridgePermissionExtension`).

## Context and problem statement

The owner's CloudNet 4 network runs the game and setup servers as services. CloudNet's bridge answers permission questions for
its maintenance bypass and task checks by asking the player's permission checker. CloudNet's default checker reads the operator
level, which a LuckPerms-managed server leaves at zero, so every player, staff included, would be refused. The fix is a
checker that reads Adventure's `PermissionChecker.POINTER`, which each Voyager player must carry (ADR-0024 keeps permission
answers behind one port).

Minestom 2026.09.12-26.2 has no extension system, so CloudNet's bridge cannot be loaded as a Minestom extension without the
OneLiteFeather fork `net.onelitefeather:minestom-extensions` (2.2.0, the release Cygnus pins). The fork's `ExtensionBootstrap` is
`init(Auth)` and `start(host, port)`.

The bridge depends on CloudNet classes. Bundling them would load a second, unmanaged CloudNet beside the node's. The roots must
not reference CloudNet at all.

The network runs CloudNet 4.0.0-RC16, the release Cygnus builds against. RC16's release notes name Minecraft 1.21.11 as the newest
supported version, and RC17 (2026-08-09) adds "Support Minecraft 26.X". The owner's live network runs RC17 with a locally patched
bridge, which this record could not read.

## Decision drivers

- A permission answer comes from one place: the port, through the pointer each player carries (ADR-0024).
- No CloudNet class in any shipped jar, enforced by a check, not by convention.
- A service without the bridge starts and runs. Missing CloudNet must not stop the game.
- The bridge is compiled against one CloudNet release that Cygnus also uses, so the two stay compatible on the same node.

## Considered options

1. **A Minestom extension module, `voyager-cloudnet-bridge`, with `compileOnly` CloudNet, loaded from the node's `extensions/` folder (chosen).**
   The bridge is a plain jar, never bundled. It declares `CloudNet_Bridge` as its dependency, so the loader starts it after the bridge.
2. **Bundle the bridge in the server jar.** Rejected. A CloudNet class in the root's jar loads a second CloudNet, and the bridge's
   class loader hierarchy is lost.
3. **Answer CloudNet through the port without a pointer.** Rejected for this change. The owner asked for the pointer, which is
   what CloudNet's checker reads and what Cygnus does. The port is still the source of the answer, because the pointer resolves
   through it.
4. **Use CloudNet's default checker and grant the operator level.** Rejected. Operator level 4 gives every player that rank, and
   LuckPerms is the source of truth for grants.
5. **Upgrade to RC17 now.** Not decided here. RC17 is the documented release for 26.X, but the owner's network already runs RC17
   with a patch, and RC16 is what Cygnus builds against. The CloudNet API surface the extension uses is identical in both (javap,
   2026-10-11). The owner decides the version after the node acceptance run.

## Decision outcome

Chosen option: **1**.

- The module `voyager/cloudnet-bridge` (`net.elytrarace.voyager.cloudnet.bridge`) depends on Minestom, Adventure and CloudNet
  4.0.0-RC16 (`bom`, `bridge-api`, `bridge-impl`, `driver-api`) as `compileOnly`, and on nothing else of Voyager. Its
  `CloudNetPermissionChecker` reads the pointer and returns its answer. Its `VoyagerCloudNetExtension` registers that checker
  as the default with `ServiceRegistry`, and it is the only class that calls CloudNet.
- `voyager-platform` gives every player the pointer (`VoyagerPlayer`, `VoyagerPlayerProvider`), and the pointer answers
  through the `PermissionPolicy` port's string form. Both roots install the provider before the port binds.
- Both roots start the Minestom extension bootstrap with `ExtensionBootstrap.init(auth)` and `start(host, port)`, because the
  fork has no `bootstrap()`. The fork is a dependency of the two roots and of nothing else.
- `verifyNoCloudNetInFatJars` (root build script, wired into `check`) fails when a `voyager-server` or `voyager-setup` jar holds an
  `eu/cloudnetservice/` entry. `CloudNetBoundaryTest` (voyager-fitness) confines every `eu.cloudnetservice` reference to the bridge
  package, keeps the roots and the platform free of it, and limits the bridge's dependencies to the JDK, Minestom, Adventure, CloudNet,
  the annotations and its own package.
- Missing `CloudNet_Bridge` is skipped with a log line by the loader. A broken `CloudNet_Bridge` is not skipped: the bootstrap aborts
  the start, as the spike of 2026-10-11 showed (see the change's design.md, Decision 6).

## Consequences

- Good: the answer for CloudNet's checks comes from the same port as the commands, so LuckPerms is the single source of grants.
- Good: a shipped jar cannot carry CloudNet, because a check fails the build, not because a reviewer noticed.
- Good: the bridge compiles against the release Cygnus uses, so the bridge and Cygnus's bridge stay compatible on one node.
- Bad: a broken `CloudNet_Bridge` stops the service at start, because the fork does not isolate a failing extension. A missing one does not.
- Bad: the fork's version is a pin that Renovate must not raise without a boot test (design.md, risk R2).
- Bad: the bridge is verified only on the owner's node (acceptance group 10). The RC16 result on Minecraft 26.2 is open until that run (risk R8).
- Neutral: the bridge depends on Minestom's and Adventure's types, which is the only place a Minestom type crosses a module boundary
  in the rebuild besides the composition roots.

## More information

- `openspec/changes/add-cloudnet-deployment/design.md`, Decisions 1 to 8, and the risks R1 to R8.
- `openspec/changes/add-cloudnet-deployment/specs/` for the requirements this record enforces.
- ADR-0024 (permission port with a LuckPerms adapter), which supplies the policy that the pointer answers through.
- ADR-0025 (Velocity modern forwarding with a secret), which the roots keep and which the bootstrap receives.
- Cygnus `bridge/src/main/java/net/onelitefeather/cygnus/bridge/CygnusBridgePermissionExtension.java`, the pattern this follows.
