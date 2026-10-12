# ADR-0029: Waiting racers stand in a dedicated lobby world, configured by server settings

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. When it is
accepted, ADR-0022 becomes "Superseded by ADR-0029". The change that implements it is `add-lobby-world`, which does not
edit ADR-0022 before then.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided that the lobby works as the old game did: players gather in a lobby
  world, see the join line and the countdown, and do not fly there.
- Drafted with the change `add-lobby-world`. The owner's answers to its five questions (2026-10-12) are recorded as
  decisions in its design.md.

## Context and problem statement

ADR-0022 put waiting racers on the first map's spawn, because a lobby world needed a new asset and a catalogue entry
that is not a map. That choice keeps waiting players in a race world, next to the rings and the cup's state, and leaves
the first map as the only place where a racer can be seen before a race. The old game gathered players in a flat lobby
instance, and the owner wants that back: a place to wait, a join line, and a count and countdown that stay on screen.

The rebuild has no lobby world yet. The candidate world, `ElytraLobby`, is an existing asset that needs an upgrade to
Minecraft 26.2 before it can be used.

## Decision drivers

- Waiting racers must not share a world with race state.
- The lobby world must be configured without a catalogue format change, so existing catalogues stay valid.
- Existing deployments must keep running without a lobby world, and must say so at boot.
- The lobby world has to be checked at boot like every other world, without opening two worlds at once during the check.
- A started cup's per-tick behaviour, pinned by the golden transcripts, must not change.

## Considered options

1. **Keep the first map's spawn (ADR-0022).** Rejected by the owner.
2. **A lobby world named by a catalogue file, `catalog/lobby.json`.** Rejected for now. One lobby per server is a
   deployment fact. A new catalogue kind needs its own schema, loader, reload path and tests. It can be added later if the
   owner wants lobbies edited without a restart.
3. **A lobby world named by two server settings, `VOYAGER_LOBBY_WORLD` and `VOYAGER_LOBBY_SPAWN` (chosen).** The
   settings are read in `ServerSettings`, checked by `ConfigCheck`, and loaded at boot. The catalogue does not change.
4. **A field in each cup file.** Rejected: the lobby is not per cup, and it would be repeated in every cup.

## Decision outcome

Chosen option: **3**, with these rules.

- Waiting racers stand at the lobby spawn of the lobby world, without elytra and without rockets. A racer moves to the
  first map when the start countdown reaches its last three seconds, and returns to the lobby when a cup finishes.
- Without `VOYAGER_LOBBY_WORLD` and `VOYAGER_LOBBY_SPAWN`, the server keeps the first map's spawn and logs a warning. The
  configuration check reports the same as a warning. Setting only one of the two is an error.
- The lobby world is loaded at boot, held open, never saved, and checked with at most one world open at a time. It may
  not be the world of any catalogue map.
- A boss bar shows the count and the countdown. The join line is `<name> joined the game (x/y)`. A full lobby, at
  `VOYAGER_MAX_PLAYERS` racers (default 16), shortens the countdown to 10 seconds. The maximum does not reject joins.

## Consequences

- Good: a waiting racer is never inside a race world, and the first map is entered only when the cup starts.
- Good: the catalogue format, the golden transcripts of a started cup and the map and cup files do not change.
- Good: a deployment with no lobby world runs as before and says so at boot.
- Bad: one more held world per server, and one more asset for the operator to keep upgraded.
- Bad: a lobby spawn is a three-number setting, not a value edited next to the map it belongs to.
- Bad: the maximum is a soft threshold, so the join line can read past its maximum until a hard cap is decided.
- Neutral: a production server without a lobby world boots with a warning, not a refusal. The owner chose this on
  2026-10-12, so a deployment must set the lobby settings to get the lobby.

## Confirmation

- `LobbyDefinitionTest`, `StartGateShortenTest`, `LobbyHudTest`, `LobbyTransitionTest` and `WaitingRoomTest` cover the
  rules above.
- `ServerSettingsLobbyTest` and `ConfigCheckLobbyTest` cover the settings, the fallback and the check.
- `LobbyWiringTest` covers the boot load, the spawn and the fallback on a fresh Minestom instance.
- `CupSessionGoldenMasterTest` stays byte-identical. A new transcript, `lobby-world-start.txt`, characterises the transfers.
- `CompositionRootRulesTest` has the rule `onlyServerConfigReadsLobbySettings`, with `allowEmptyShould(false)`.

## Pros and cons of the options

### Option 3, chosen

- Good: one place for the operator to set the lobby, no catalogue change, checked by the same configuration check as
  every other setting.
- Bad: the spawn is written as a string in the environment.

### Option 2, a catalogue file

- Good: the lobby could be edited and reloaded like maps.
- Bad: a new file kind, schema and reload path for one value per server.

## More information

- ADR-0022: the waiting room in the first map's world, superseded by this record for the location of waiting racers.
- ADR-0027 is reserved by `openspec/oval-tilted-rings` (unmerged), and ADR-0028 is claimed by
  `openspec/remove-start-launch` (commit `0dd208a`). This record takes the next free number, 0029.
- `openspec/changes/add-lobby-world/`: the proposal, design, delta specs and tasks for this decision.
