# Proposal

**Type and scope:** `feat(setup)`. Ships as one code change under the title `feat(setup): fly a draft map in a test flight before publishing`. It does not change the game server, the race slice or the file format.

## Why

A builder places rings from a pose and checks them by walking, or by asking another person to fly, and then publishes on trust. The first real flight of a course happens in a race, and a ring that is too small, out of order or badly placed shows up there, with racers waiting. The builder needs to fly the course as it stands, with the rings, the guide line, the timing and the medals the game shows, and to stop without anything being published or recorded.

## What Changes

- `/map fly` starts a test flight of the map the builder holds. The builder gets an elytra and firework rockets, flies the rings in order, sees the guide line the racers see, and gets the time, the rings passed and the medal tier at the finish.
- `/map fly stop` ends the flight and returns the builder to editing: creative mode, the wand and the ring previews. The claim, the draft and the undo history are unchanged.
- A flight starts only from the saved draft. A draft with a blocking problem of `/map status` is refused, and so is a stored copy that changed on disk.
- While a builder flies, the edit commands (ring placement and removal, the spawn, undo and redo) are refused with a message to stop the flight first.
- A flight records nothing: no file, no claim, no history, no statistics and no leaderboard.
- The rules of the flight are the race's own pure rules (ring order, the finish, the time, the medal tiers). The flight does not run the cup machinery (ADR-0049, Proposed).
- Known gap in version 1: firework boost, boost rings and ring effects are not applied, and the reply on start says so.
- Both bundles get the new messages.

## Capabilities

### New Capabilities

- `setup/test-flight`: the flight command, its start and stop, its refusals, the flight's rules and what it does not record, and the flight items.

### Modified Capabilities

None. `/map fly` is a new subcommand, specified in the new capability. No requirement of an existing capability changes.

## Out of scope

- Publishing. A flight reads the saved draft and writes nothing, so it does not depend on `publish-map-to-game`, and that change does not depend on it.
- Firework boost, boost rings and ring effects in version 1 (owner question 1).
- The cup's own orchestration: the lobby, the GO hold, the glide-out, the rotation and the hub hand-off.
- Out-of-bounds reset. A builder who leaves the course starts again with `/map fly`.
- Leaderboards, statistics and personal bests of test flights. Nothing is kept.
- Flights of several builders on one map at once. The claim gives one editor, and one flyer.

## Impact

- Vertical slices: `setup` (the pure flight state in `setup.testflight`, the Minestom wrappers in `setup.inject`, the messages). `race` is used read-only for its pure rules and is not changed. `platform` is used for the map reader and the presentation that already exist, and is not changed. `api` is not changed.
- Greenfield design: no decision changes.
- ADR-0049 (Proposed) records the choice of the race rules over the cup machinery.
- Size: M.
