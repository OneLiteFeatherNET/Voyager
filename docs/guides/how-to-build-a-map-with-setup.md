# How to build a map with the setup server

This guide shows how to create a map on the setup server, place its rings with the wand, and get a map the game server
can load. It covers the first slice of `voyager-setup`: the commands `/map new`, `/map open`, `/map spawn` and
`/map status`, the wand, and the preview of each ring. Terrain is not built here; build it in your external editor.

## Before you start

- Start the setup server from the project root:

  ```bash
  ./gradlew :voyager:setup:runSetupDev
  ```

  It listens on port 25566 by default. Pass `-Pport=<port>` to change it.
- The server reads and writes two directories under the project root, `run-setup/data` and `run-setup/worlds`. Both
  must exist before the server starts; the run task creates them on a first run. If a directory is missing, the server
  logs the absolute path it expected and exits without listening.
- Connect with a Minecraft client for the version the rebuild targets, 26.2.

## Create a map

1. Run `/map new <id>`, for example `/map new skyfortress`.

   The id is a folder name: lower-case letters, digits, `-` and `_`, at most 32 characters, starting with a letter or a
   digit. Any other id is refused, and nothing on disk changes.

   The command creates the draft at `run-setup/data/drafts/<id>.json`, the void world at `run-setup/worlds/<id>`, moves
   you into the world in creative mode, and gives you the wand.

2. Stand where the racers start, and run `/map spawn`. The spawn is your feet position, and it is saved at once.

## Place and remove rings with the wand

- **Right-click** places a ring at your eye position, facing the direction you look. The ring's normal is that direction,
  its radius is `sqrt(13)` (3.605551275463989), it scores 10 points and it is a standard ring. Its index is the number of
  rings before it. A preview block appears where the ring is.
- **Sneak plus left-click** (hold Shift, then left-click) removes the nearest ring that your look ray crosses within 32
  blocks. The rings after it are renumbered. If no ring is in reach, nothing changes and you are told so. A plain
  left-click changes no ring.

Every change is saved before you see it. If a save fails, you are told that the change was not saved, and the draft
stays as it was.

There is no undo in this slice. A sneak plus left-click removes a ring for good; place it again if you removed it by
mistake.

Right-clicking a block with the wand places a ring and does not place a block. Breaking and placing blocks in an open map
is cancelled, because the setup server never saves a world.

## Check the map

Run `/map status`. It reports whether a spawn is set, the number of rings, and each problem that keeps the map from being
loadable: no spawn, or no ring. The status reads the last saved draft.

## Where the draft lives

The setup server writes the draft in the game server's own map format, so no publish step exists.

| The draft has | Its file is |
|---|---|
| no ring, or no spawn | `run-setup/data/drafts/<id>.json`; the game server never reads this folder |
| at least one ring and a spawn | `run-setup/data/maps/<id>.json`; the game server reads this file |

When a draft moves from one folder to the other, the copy in the folder it left is deleted after the new copy exists.
Removing the last ring moves the file back to `drafts/`.

To open a map later, run `/map open <id>`. The map's world is opened and its rings are shown.

### Two copies of one draft

A crash during a save can leave a copy of the draft in both folders. `/map open <id>` then refuses, and the message names
both files. Keep the copy you want, delete the other file, and run `/map open <id>` again. The server does not choose
between the two copies, because either choice can lose your last change.

## Run the game server with the map

The game server reads its maps from the `maps/` folder of its data directory (`VOYAGER_DATA_PATH`, default `run/data`) and
its worlds from its worlds directory (`VOYAGER_WORLDS_PATH`, default `run/worlds`). The setup server never writes into those
directories. A map in `run-setup/data/maps/` is loadable by the game server once you copy that file, and its world folder
`run-setup/worlds/<id>`, into the game server's directories. A publish step is a follow-up change.
