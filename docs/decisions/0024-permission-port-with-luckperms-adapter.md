# ADR-0024: Permission port in voyager-api with a LuckPerms adapter and a fail-closed fallback

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. The change that
implements it is `add-luckperms-permissions`, which does not mark this record Accepted.

## Date

2026-10-10

## Decision makers

- TheMeinerLP (project owner). Confirmed the fallback rule, the five node names and the scope on 2026-10-10.
- Drafted with the change `add-luckperms-permissions`.

## Context and problem statement

Voyager has one permission check: `ReloadPermission` allows `/race reload` for the console and for a player at operator
level 4. The setup server has no check at all. Minestom 2026.08.28-26.2 has no named permissions, so every staff action is
either open or tied to the operator level. Minestom 26.2 ships no `/op` command, and Voyager has no command that sets the
operator level, so nothing in this server can grant level 4 to a player. The network runs LuckPerms on its other services, and the owner wants per-node grants (for
example, reload for staff without operator rights, and the setup server for builders only), stored in the same place.

The sibling project Cygnus already runs LuckPerms on Minestom. Its `LuckPermsSupport` treats an absent LuckPerms as "grant every
permission" and logs one warning. Command code in Cygnus names LuckPerms directly.

Two questions need an answer: where the permission rule lives in the architecture, and what a player may do when LuckPerms is
missing from the class path.

## Decision drivers

- Command and listener code must ask one question, and must not name a permission backend. Clean Architecture: dependencies point
  inward.
- The loader is optional at runtime. A jar without it must still compile, test and start.
- A failure of the permission backend must not widen access on a public network. Absent LuckPerms must never grant a player more
  than operator level 4 gives.
- The port must also answer for node names Voyager does not own, because the CloudNet bridge asks about names such as
  `cloudnet.bridge.maintenance` (change `add-cloudnet-deployment`).

## Considered options

1. **Port in `voyager-api`, LuckPerms adapter and level-based fallback in `voyager-platform`, fail closed (chosen).** The port
   is a pure interface with a sealed `PermissionSubject` and an enum `PermissionNode`. Without LuckPerms, the console is allowed
   every node and a player only at operator level 4. A LuckPerms start that throws stops the server; it does not fall back.
2. **Cygnus's behaviour: absent LuckPerms grants every check.** Rejected. A jar that lost the loader would grant every player
   every permission, and the only evidence would be two log lines. This is the failure mode of a public server.
3. **Deny every node when LuckPerms is absent.** Rejected. Local runs and the setup server would be unusable without LuckPerms,
   and the level-4 rule that `/race reload` already has would be lost.
4. **LuckPerms calls inside each command.** Rejected. Every gate would name a `net.luckperms` type, and the rule could not be
   tested without starting LuckPerms.
5. **Load LuckPerms as a Minestom extension from `extensions/`.** Rejected. Minestom 26.2 has no extension system; the loader
   would never start that way.

## Decision outcome

Chosen option: **1**.

- `voyager-api` (`net.elytrarace.voyager.api.permission`) holds `PermissionNode`, `PermissionSubject` (sealed: `Console`,
  `Player`) and the interface `PermissionPolicy`. Its string form takes any node name, and its enum form delegates to it.
- `voyager-platform` holds `LevelPermissionPolicy` (the fallback), `PlayerSubjects` (Minestom player to subject), and the
  `permission.luckperms` package. Only that package names `net.luckperms` or `me.lucko`, which the fitness rule
  `luckPermsIsConfinedToItsAdapter` enforces.
- The composition roots (`voyager-server`, `voyager-setup`) choose the bean: `LuckPermsPolicy` when the loader class is present
  and started, `LevelPermissionPolicy` otherwise. Command classes receive the policy through their constructor and never
  reference the LuckPerms adapter (`commandsAskOnlyThePolicy`).
- The loader is detected by class name and started with `MinestomLoader.get().load().start()`, without the loader's own JVM
  shutdown hook. `LuckPermsBootstrap.stop()` disables LuckPerms from the server's shutdown task, before the JVM runs its
  hooks, so H2 closes its database first (see the Consequences)
  before the port binds.

## Consequences

- Good: command code asks one port. The rule is testable with a fake gateway, without LuckPerms.
- Good: a jar without the loader starts with the fail-closed fallback, and a player never gains more than operator level 4 gives.
- Good: the console keeps full access during a rollback, so an operator can still run `stop` and `/race reload`.
- Bad: without LuckPerms, only the console runs gated commands. A local run that needs a player to run them needs LuckPerms
  with the node granted, or a code path that sets operator level 4. The WARN line at startup names the fallback.
- Bad: LuckPerms' own JVM shutdown hook raced H2's exit hook and printed `NoClassDefFoundError: org/h2/api/ErrorCode`. The
  fix disables LuckPerms in Voyager's shutdown instead, which reads the loader's private `plugin` field. A LuckPerms release
  that renames the field fails the start with a clear error, not silently.
- Bad: operators who relied on `/race reload` for non-operators lose it until LuckPerms grants `voyager.command.race.reload`.
  This is a behaviour change for operators only; the reference page and the how-to say so.
- Bad: LuckPerms adds runtime requirements: Guava and failureaccess on the class path, and network access on its first start,
  when it downloads its libraries into `data/libs`. Recorded in `add-luckperms-permissions/design.md`, risk R3.
- Neutral: `PermissionPolicy` is not sealed. Three implementations in two modules would have to share one file.

## More information

- `openspec/changes/add-luckperms-permissions/design.md`, decisions 1 to 5, and the risks section (spike results).
- `openspec/changes/add-luckperms-permissions/specs/server/command-permissions/spec.md`.
- ADR-0025 (Velocity forwarding), which the same change records.
- Cygnus `common/src/main/java/net/onelitefeather/cygnus/common/permission/LuckPermsSupport.java`, the reference this decision
  departs from on the fallback.
