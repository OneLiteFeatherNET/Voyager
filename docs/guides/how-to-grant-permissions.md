# Grant permissions

This guide grants Voyager's permission nodes to a player through LuckPerms. It also shows how to give a player the same
access without LuckPerms, by operator level. The node list and the fallback rules are in
[Permission nodes](../reference/permission-nodes.md).

## Before you start

- The server has the LuckPerms loader on its class path. The `WARN` line `LuckPerms is not on the class path` at startup
  means it does not. Without LuckPerms, only the console can run the gated commands; see the operator level section.
- The player has joined the server at least once, so LuckPerms has a user record for the player.
- You have operator rights to run the LuckPerms command in the server console.

## Grant a node to one player

1. In the server console, grant the node. The example grants `/race reload` to one player:

   ```text
   lp user <player name> permission set voyager.command.race.reload true
   ```

2. Check the grant:

   ```text
   lp user <player name> permission check voyager.command.race.reload
   ```

   The answer must read `true`.

3. Ask the player to run `/race reload`. The reload runs, and the player gets the report.

Grant only the nodes a player needs. For example, a builder needs `voyager.setup.use` on the setup server, and nothing else.

## Grant a node to a group

Use a group when several staff members need the same nodes:

```text
lp creategroup staff
lp group staff permission set voyager.command.race.reload true
lp user <player name> parent add staff
```

## Remove a grant

```text
lp user <player name> permission unset voyager.command.race.reload
```

## Without LuckPerms: the operator level

When LuckPerms is absent, the fallback allows a player at operator level 4 to run every gated command, and denies a player
below level 4. The console may run all of them.

Voyager has no command that sets a player's operator level, and Minestom 26.2 ships no `/op` command. In this build, the
only way to reach level 4 without LuckPerms is code that sets the level on the player. So without LuckPerms, only the
console can run the gated commands. Grant the nodes through LuckPerms, or give the operator level by a route your server
provides.

Check the startup log for the `WARN` line that names the fallback. It confirms which policy the server runs.

## Check what you did

- A player who is not granted a node is told that the command is not allowed. The subcommand is not reported as unknown.
- `/race` with no subcommand works for every player, because it only reads state.
