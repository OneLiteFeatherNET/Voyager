# Reload maps and cups without a restart

Use `/race reload` to apply edited map and cup files to a running server. The server reads the data directory again,
checks all of it, and plays the result from the next round. A round is one cup run, from its start until the cup
finishes or is stopped.

## Run the reload

1. Save the edited files in `maps/` and `cups/` under the data directory.
2. Run `/race reload` from the server console, or as a player with operator level 4.
3. Read the reply.

The reply is one of two things:

- `Catalogue reloaded: the changed maps and cups play from the next round`. The new catalogue is waiting. It plays from
  the next round.
- `Reload refused, the running catalogue is unchanged: N problem(s)`, followed by one line per problem. Nothing changed.

The command returns at once. The check runs on another thread, so the server keeps ticking while it runs.

## When the change plays

The change plays from the next round, not from the moment of the reload:

- A round is pinned when it starts and keeps its catalogue until it ends. A reload made during a cup waits for the next
  cup.
- A cup that has finished starts again only when a player next joins while no cup is running. With `/race start` in dev
  mode, the cup restarts at once.
- `/race` shows `a reload waits for the next round` while a reload is waiting, and the time the running catalogue was
  read.

If the server holds players and no cup is running, a reload waits until a player joins.

## Read a refusal

Each problem is one line in the form `ERROR <source> <key>: <message>`:

```text
ERROR /data/maps/ridge.json file: /data/maps/ridge.json is not a valid definition: ...
ERROR /data/cups cup: 1 cup entry does name a map no map definition provides: cup 'tour' plays 'missing-map'
ERROR ridge-world world: holds no region data; the maps of cup 'tour' need it
```

- A file that does not parse, or a duplicate name, is reported with its full path.
- A cup entry naming a map that no map file provides is reported against the cups directory.
- A cup selected by `-Pcup` or `-DVOYAGER_CUP` that does not exist is reported as a cup-selection problem.
- A world that a cup's maps name and that has no region data is reported by the world name.

Every problem is listed in one reply, so one refusal shows the whole list to fix.

Unlike boot, a reload also refuses a broken cup that the server does not play. Fix or remove that file, then reload.

## What a reload can and cannot change

| Change | Applies on reload | Behaviour |
|---|---|---|
| Map definition, of a world already open | Yes, from the next round | The world is not reread |
| Cup definition, or the list of maps in a cup | Yes, from the next round | A running round keeps its original rotation |
| New map on a world not open yet, with region data | Yes | The world is opened and checked before the reload is applied |
| New map on a world with no region data | No | Refused; the reply names the world |
| Region files of an open world changed on disk | Definitions apply; the world does not | The reply warns that the world needs a restart |
| Map removed from the catalogue | Yes, from the next round | Its world stays open until restart |
| Selected cup removed or renamed | No | Refused |

A world that was open before a refused reload stays as it was. A world that a refused reload opened is closed again.

## Who may reload

The console may always reload. A player needs operator level 4. Minestom has no named permission nodes, and this server
does not yet grant operator levels to anyone, so today only the console can run `/race reload`. A player below level 4
is told that the command needs operator level 4, and the reload does not run.

## Limits

- There is no file watcher. Run the command after every save.
- A reload does not change which worlds a server has open. Changing region data needs a restart.
- There is no rollback. Once a valid reload is applied, the catalogue it replaced is not kept.
