# How to run a playtest with several racers

This guide shows how to start a local Voyager server for a playtest with N racers, what each racer sees while the cup
waits, starts, is cancelled and finishes, and what to do when a countdown does not start. It assumes the world data is
already in place and passes `validateCatalog`. See [configuration check](../reference/config-check.md) for the checks
the server runs before it starts.

## Choose the minimum number of racers

A cup starts only when enough racers are online. The minimum comes from `VOYAGER_MIN_PLAYERS`:

| Run | Minimum when not set | How to set it |
|---|---|---|
| Production-like (`runServer`) | 2 | `./gradlew :voyager:server:runServer -PminPlayers=<N>` |
| Dev (`runServerDev`, `voyager.dev` set) | 1 | `./gradlew :voyager:server:runServerDev -PminPlayers=<N>` |

An explicit value wins in both modes. A value that is not a whole number, or is below 1, refuses the start and names
`VOYAGER_MIN_PLAYERS` in the log.

## Set the lobby and the maximum

Waiting racers stand in a lobby world, which the server loads at boot. Two settings name it, and both are needed
together:

| Setting | What it sets |
|---|---|
| `VOYAGER_LOBBY_WORLD` | The lobby's world directory name, under the worlds directory. |
| `VOYAGER_LOBBY_SPAWN` | The spot a waiting racer stands on, as `x,y,z` in block coordinates. |
| `VOYAGER_MAX_PLAYERS` | The maximum racer count, 16 when not set. It sets the join line and the full-lobby countdown, and it refuses no one. |

Without a lobby world the server warns once at boot and waiting racers stand at the first map's spawn. The boss bar is
then shown there too, until the start is committed. A lobby world must not be a map's world. The lobby is checked at
boot like a map's world, so a missing folder stops the start before anyone can join. The lobby world is never written.

The lobby world is operator data. It is not in the repository, so a playtest on a machine without it runs with the
fallback.

## Start the server

For a playtest with two racers on a development machine:

```bash
./gradlew :voyager:server:runServerDev -PminPlayers=2
```

The shipped data holds two cups, `alpha_cup` and `legacy_cup`, so name the one to play. Without a name the server
refuses to boot as ambiguous:

```bash
./gradlew :voyager:server:runServerDev -PminPlayers=2 -Pcup=legacy_cup
```

Dev mode also shortens the lobby to 10 seconds and the results screen to 5 seconds, and it registers `/race start` and
`/race skip`. For a run that behaves like production, use the packaged server instead:

```bash
./gradlew :voyager:server:runServer -PminPlayers=2
```

Wait for the line `Listening on <host>:<port>` in the log before connecting. Connect each racer with a 26.2 client to
the same address.

## What each racer sees

| Moment | What the racer sees |
|---|---|
| Anyone joins | Every racer online is told in chat: `<name> joined the game (<online>/<maximum>)`. |
| Joined, fewer racers online than the minimum | A boss bar: `Waiting for racers: <online> of <needed> online`. The racer stands at the lobby spawn with no elytra and no rockets. |
| The minimum is met | The lobby starts. The boss bar counts it down in whole seconds: `Cup starts in <n> s`, and its fill is the part of the lobby left. |
| The server reaches its maximum during the countdown | The countdown is shortened to 10 seconds from that moment. It is never lengthened, and a committed start is not shortened. |
| The last three seconds of the lobby | The digits 3, 2, 1 and `GO` are shown, and the racer is placed on the spawn facing the first ring with the elytra and the rockets in hand. |
| GO | The racer is released, not launched. The subtitle `Jump, then deploy your elytra.` appears with the title. The racer jumps and deploys the elytra; a rocket is refused until the racer is gliding, so the rocket comes after the deployment. |
| A racer leaves before the last three seconds | The lobby is cancelled and the room returns to waiting. The remaining racers are told `Start cancelled: <online> of <needed> racers online. Waiting for more.` A new countdown starts from its full length once the minimum is met again. |
| A racer leaves in the last three seconds | The start is committed. The first map starts for the racers still online, and they are released at GO. |
| A racer joins while a map is racing | The racer is told `A cup is running. You join at the next map.` and enters the next map when it starts. |
| The last racer leaves a running cup | The cup stops without a result, and the room waits for the minimum again. |
| A racer is still flying when a map's race time ends | That map is scored as DNF: ring points only. |
| The cup finishes and enough racers are still online | The racers are returned to the lobby spawn, and the next countdown begins without any operator action. |
| The cup finishes and too few racers are online | The racers are returned to the lobby spawn and the room waits. |

## Check the state from the console

`/race` with no argument prints the cup's state and one room line, for example:

```text
room: 1 of 2 racer(s) online, waiting
```

The room line says how many racers are online against the minimum, and whether the room is waiting, counting down,
committed to the start, or racing.

## Operator commands in dev mode

| Command | Effect |
|---|---|
| `/race start` | Starts the cup from its first map at once, with the lobby skipped, whatever the number of racers online. It bypasses the minimum. |
| `/race skip` | Ends the map being raced now, as if every racer had finished. A skip asked for between maps is refused. |

These commands are not registered on a production run.

## When a countdown does not start

1. Run `/race` and read the room line. If it shows fewer racers online than the minimum, the minimum has not been met.
   Check that every racer is connected to this server and not to another instance.
2. Check the minimum: `-PminPlayers` sets it for the Gradle run tasks. Without it, production needs 2 and dev needs 1.
3. Read the start-up log. A refused `VOYAGER_MIN_PLAYERS` stops the server before it listens, with the key named.
4. If the cup is not waiting but racing, a map is in progress. A countdown starts only after the cup has finished or
   was aborted. Use `/race start` in dev mode to start a cup at once, or `/race skip` to end the map being raced.
5. If the server reports a problem with a world, run `./gradlew :voyager:server:validateCatalog` and fix what it lists.

## Related

- [Configuration check](../reference/config-check.md) for the settings the server validates.
- [Local testing](local-testing.md) for the earlier server setup.
- [ADR-0029](../decisions/0029-dedicated-lobby-world-for-waiting-racers.md) for why waiting racers stand in a lobby world.
- [ADR-0022](../decisions/0022-waiting-lobby-in-first-map.md), superseded by ADR-0029, for the earlier placement.
