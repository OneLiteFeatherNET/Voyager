# Proposal

**Conventional Commits:** `feat(server)`. Squash-merge PR title: `feat(server): add LuckPerms permissions, Velocity forwarding and stdin stop`.

Slices touched: the permission slice (new, cross-cutting), the game server composition root (`voyager-server`) and the
setup server composition root (`voyager-setup`). The vertical slices race, ring and cup change only where a gated
command is wired in. Out of scope: CloudNet (its own change, `add-cloudnet-deployment`, which depends on this one),
PostgreSQL storage for LuckPerms (the persistence stage), CloudNet maintenance bypass, the tree being replaced
(`legacy/*`), and the broken `docker/server/Dockerfile`, which copies `*-all.jar` from the legacy tree.

## Why

Voyager has no permission system. The only gate is `/race reload`, which checks a numeric operator level, and the setup
server has no check at all. Minestom 2026.08.28-26.2 has no named permissions, so every staff action is either open or
tied to `/op`. A network service needs per-node grants (for example, reload for staff without operator rights, and the
setup server for builders only), and it needs them from a store the owner already runs on the other OneLiteFeather
services: LuckPerms. The owner's CloudNet network will run these services behind a proxy, so the game server must also
read the real player UUID that the proxy forwards, or LuckPerms looks up the wrong player.

## What Changes

- Add a permission port, `PermissionPolicy`, with the sealed `PermissionSubject` and the enum `PermissionNode`, to
  `voyager-api`. Its two forms of `allows` take an enum node (Voyager's own gates) or a free-form node name (a name Voyager
  does not own, such as CloudNet's `cloudnet.bridge.maintenance`, asked by the bridge in `add-cloudnet-deployment`). No
  implementation and no Minestom or LuckPerms type in it.
- Add a fallback adapter in `voyager-platform`: console allowed for every node, a player allowed only at operator
  level 4. This is the policy whenever LuckPerms is absent. Absent LuckPerms never grants a player more than `/op` does.
- Add a LuckPerms adapter in `voyager-platform` (`platform.permission.luckperms`). It is the only package that names
  a `net.luckperms` or `me.lucko` type. It is optional at runtime: detected by class name and started through the
  loader, as Cygnus does.
- Gate `/race reload`, the dev-mode `/race start` and `/race skip`, `/stop`, and every setup command and the wand behind
  the nodes listed in the `command-permissions` capability. `/race` status stays open, because it only reads state.
- Add Velocity modern forwarding to both composition roots, configured by `VOYAGER_VELOCITY_SECRET` or
  `voyager.velocity.secret`. Without a secret the server runs offline and logs a WARN. A blank secret refuses to start.
- Read `service.bind.host` and `service.bind.port` system properties, after the positional arguments and before the
  defaults, so a CloudNet node can set the bind address without arguments.
- Accept `stop` on stdin as a clean shutdown in both roots, run on a thread other than the reader. Move
  `ConsoleCommandReader` from `voyager-server` to `voyager-platform` so both roots share it.
- Record two decisions as MADR ADRs in Proposed state: `docs/decisions/0024-*.md` (permission port and fallback) and
  `docs/decisions/0025-*.md` (Velocity forwarding). Add the matching rows to the greenfield design spec's decision table
  (D13, D14).
- **BREAKING** for operators only: `/race reload` is no longer checked by `ReloadPermission`. It checks the
  `voyager.command.race.reload` node through the policy. Without LuckPerms the effective rule is unchanged (console or
  operator level 4).

## Capabilities

### New Capabilities
- `server/command-permissions`: how commands and listeners decide whether a sender may act: the port, the node list, the
  LuckPerms adapter, the level-based fallback and the console rule. It covers both composition roots.
- `server/proxy-forwarding`: how the game and setup servers accept player identity from a Velocity proxy, the secret
  source, the offline fallback and the secret handling rules.
- `server/service-lifecycle`: how a server takes its bind address from the service environment, and how it stops on
  stdin `stop` or `/stop`, so that a node can stop it cleanly.

### Modified Capabilities
- None. `server/config-check-mode` describes the settings check and keeps its behaviour. The bind precedence is new
  behaviour in `server/service-lifecycle`.

## Impact

- `voyager-api`: new `net.elytrarace.voyager.api.permission` package. Pure types only.
- `voyager-platform`: new `permission`, `permission.luckperms` and `proxy` packages, and the moved `lifecycle` package
  (`ConsoleCommandReader`, `ServiceShutdown`, `StopCommand`).
- `voyager-server` and `voyager-setup`: composition roots gain beans for the policy and the proxy settings, the stop
  command and reader, and the runtime dependency on the LuckPerms loader (not on the test class path).
- `voyager/fitness`: new `PermissionBoundaryTest` (confinement of `net.luckperms` and `me.lucko`).
- Build: `net.luckperms:api:5.5` compileOnly in `voyager-platform`; `net.luckperms:minestom-loader:5.6-SNAPSHOT` runtimeOnly
  in both composition roots, excluded from their test class paths. Both pinned in `settings.gradle.kts`.
- Operators: new environment variable `VOYAGER_VELOCITY_SECRET`, new node names to grant in LuckPerms, and a new
  stdin command `stop`.
- Docs: two how-to guides and one reference page in `docs/`, the design spec decision rows, and the two ADRs.
