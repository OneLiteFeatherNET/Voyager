# Permission nodes

Voyager gates five actions with named permission nodes. A permission backend answers for each node: LuckPerms when its
loader is on the class path, and a level-based fallback when it is not. This page lists the nodes, what each one gates,
and the rule that applies when LuckPerms is absent. The how-to guide
[Grant permissions](../guides/how-to-grant-permissions.md) shows how to grant them.

## Nodes

| Node | Gates | Registered |
|---|---|---|
| `voyager.command.race.reload` | `/race reload`, which re-reads the maps and cups | Always |
| `voyager.command.race.start` | `/race start`, which restarts the cup from its first map | Dev mode only (`-Dvoyager.dev=true`) |
| `voyager.command.race.skip` | `/race skip`, which ends the current map | Dev mode only (`-Dvoyager.dev=true`) |
| `voyager.command.stop` | `/stop`, which shuts the game server down cleanly | Always |
| `voyager.setup.use` | Every `/map` subcommand on the setup server, and the wand | Always, on the setup server |

`/race` with no subcommand shows the cup's status. It asks for no node and stays open to every sender, because it reads
state and changes nothing.

The console is allowed every node. It is never asked the policy.

A player the policy refuses is told so with a denial message. The subcommand is not reported as unknown.

## Fallback when LuckPerms is absent

The fallback applies when the LuckPerms loader is not on the class path. The server logs one `WARN` line at startup that
names the fallback.

| Sender | Gated node | Result |
|---|---|---|
| Console | Any | Allowed |
| Player at operator level 4 | Any | Allowed |
| Player at operator level 0 to 3 | Any | Denied |

The fallback never grants a player more than operator level 4 gives. Voyager has no command that sets the operator level,
so without LuckPerms only the console runs the gated commands unless the level is set by other means.

## LuckPerms

When the loader is on the class path, LuckPerms decides each node from the player's cached permission data:

| LuckPerms answer for the node | Result |
|---|---|
| `true` | Allowed |
| `false` | Denied |
| Not set | Denied |
| No LuckPerms user for the player's UUID | Denied |

The console is still allowed every node.

If the loader is on the class path but fails to start, the server logs the reason, exits with status 1, and does not bind
its port. It does not fall back to the level-based policy in that case.

LuckPerms keeps its files under `data/` in the server's working directory. This is fixed, not configurable. The default
storage is H2, in `data/luckperms-h2-v2.mv.db`.

## Related

- [Grant permissions](../guides/how-to-grant-permissions.md)
- [Run behind a proxy](../guides/how-to-run-behind-a-proxy.md)
