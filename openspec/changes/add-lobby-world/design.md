# Design

## Context

- The waiting room (`platform.lobby.WaitingRoom`) decides with `race.flow.StartGate` when a cup starts, is cancelled,
  commits or aborts. It places a waiting racer by calling a `WaitingSpawn` function. Today that function is the first
  map's spawn, and `VoyagerServer` sends each joining racer to the same world (`VoyagerServer.registerEvents`).
- `MapInstances` holds one Minestom instance per world directory, cached by name, and never saves. `CupSession` already
  holds every cup world open for the life of the server (`VoyagerServer.openEveryWorld`), so a held lobby world is one
  more loader of the same kind.
- `MapTransition.advanceTo` moves racers between instances with a chunk wait and a confirmed teleport. Its
  cross-instance branch is what a lobby-to-map move needs, so the transfer needs no new Minestom mechanism.
- `RaceHud` already shows a per-racer boss bar during a race (`voyager.hud.bossbar.last`). `platform` has no scoreboard
  or sidebar code.
- `ServerSettings` is the one place for values the server needs before it can resolve an object. `ConfigCheck` already
  checks each setting and names its key in the problem.
- The catalogue (`cup.schema.json`, `map.schema.json`) describes cups and maps, and nothing is per server.

## Goals / Non-Goals

**Goals:**
- Waiting racers stand in a lobby world with no race state, and go to the first map only when the cup starts.
- A deployment without a lobby configured runs as before, and says so at boot.
- The lobby world is checked at boot like every other world, without opening two worlds at once during the check.
- The join line, the full-lobby countdown and the boss bar behave as the owner described.
- No change to the per-tick behaviour of a started cup, so the golden transcripts stay byte-identical.

**Non-Goals:**
- Flight in the lobby: no elytra, no rockets, no collision world (the owner's choice).
- A hard cap on racers, a spectator mode, a grace period for reconnects, a per-map time limit (see proposal).
- Converting the `ElytraLobby` asset in this change (an operator task in tasks.md).
- Persistence, catalogue schema changes, or any change to the map or cup format.

## Decisions

### D1. The lobby world is named by two server settings, not by a catalogue entry

`VOYAGER_LOBBY_WORLD` names the world directory (resolved against `VOYAGER_WORLDS_PATH`, as map worlds are).
`VOYAGER_LOBBY_SPAWN` is `x,y,z` in block coordinates, with yaw 0. Both are read in `ServerSettings.fromProperties`,
which gains `Optional<LobbyDefinition> lobby`. The record `LobbyDefinition(String world, Vec3 spawn)` lives in
`voyager-api` (`net.elytrarace.voyager.api.race`) with its own `InvalidLobbyException` in `api.race.exception`.

Alternatives considered:
- A catalogue file `catalog/lobby.json` next to `maps/` and `cups/`. Rejected for now: one lobby per server is a
  deployment fact, not catalogue content, and a new catalogue kind needs a schema, a loader, a reload path and tests for
  each of them. It can come later if the owner wants lobbies edited without a restart.
- A field in `cup.json`. Rejected: the lobby is not per cup, and it would be repeated in every cup.

The catalogue JSON and `cup.schema.json` do not change, so existing catalogues stay valid without any migration.

### D2. Without a lobby world, the waiting room uses the first map's spawn, with a warning

When neither setting is set, the server behaves as ADR-0022 describes: waiting racers stand at the first map's spawn.
Boot logs one warning naming both settings, and `ConfigCheck` reports the same as a warning (not an error), so a
configuration check still exits 0.

Alternative: refuse to boot without a lobby world. It would make the decision visible at once, but it would stop dev
runs, the setup flow's playtests and every CloudNet node that has not yet been given the asset, for a feature the owner
has only just approved. **Decided by the owner (OQ2, 2026-10-12): warn at boot and fall back to the first map's spawn.**

Setting only one of the two is an error (`InvalidLobbyException`, naming the missing setting), because a world without a
spawn, or a spawn without a world, is a typo and not a fallback.

### D3. Boot load, memory, and the one-world rule

- `VoyagerServer.openGraph` resolves the lobby world eagerly, after the cup's worlds, through `MapInstances.forWorld`, so
  a missing folder is refused at boot rather than at the first join. The instance is held for the life of the server and
  is never saved (`MapInstances` does not save).
- The cost is one more Falco loader and the chunks the lobby holds. The candidate world has two region files
  (`r.0.0.mca`, `r.0.-1.mca`, about 15 MB on disk), so the cost is small compared with a cup's worlds. It is measured
  in the operator task, not assumed.
- The boot check (`ConfigCheck` and the boot run of `catalog-validation`) reads every chunk of each world it checks
  through `MapInstances.healthOf`. The lobby world is checked with the same rule as the cup's worlds: each world is closed
  before the next is loaded, so at most one world is open during the check. This is the "at most one world" requirement
  of `docs/openspec-boot-world-scope`; it is restated in `specs/server/lobby-world` so this change does not depend on that
  commit being merged first.
- Runtime is not governed by that rule: all cup worlds and the lobby world are held open together, as the cup's worlds
  already are.
- The lobby world may not be the world of any catalogue map (`ConfigCheck` error). A racer standing in a map's world
  would be in that map's instance with no run, which is the state the lobby exists to prevent.

### D4. Transfers reuse the existing arrival step

- Lobby to first map: `CupSession.enterMap` already calls `MapTransition.advanceTo` three seconds before launch. Racers
  now come from the lobby instance, so `advanceTo` takes its cross-instance branch: chunk wait, confirmed teleport, then a
  fresh run. No change to `CupSession`'s transition calls.
- Cup finished back to the lobby: `WaitingRoom` calls a new `LobbyTransition` (`platform.lobby`) instead of
  `WaitingSpawn.place` at the first map's spawn. It holds the racers and calls the same arrival step.
- The arrival step (chunk wait, confirmed teleport) moves from `MapTransition` into a small package-private helper in
  `platform.world`, used by both. Its behaviour does not change, and `MapTransitionTest` stays as it is.
- Mid-cup joiners spawn in the lobby world (`AsyncPlayerConfigurationEvent`), so they wait there until the next map,
  as before.
- A same-instance move (lobby to lobby) is a reposition, which `arrive` already handles.

### D5. The full-lobby countdown is a pure rule, applied through one new method on `CupSession`

- `race.flow.StartGate` gains `FULL_LOBBY_COUNTDOWN = Duration.ofSeconds(10)` and a pure function
  `Optional<Duration> shortenedCountdown(Situation situation, Duration remaining, int online, int maximum)`. It answers
  the new length only when the situation is `COUNTDOWN`, `online >= maximum` and `remaining > 10 s`. It never answers
  when committed, so the commit window keeps its meaning.
- The room calls `CupSession.shortenLobby(Duration)`, which sets the first lobby's remaining time to the new value.
  The phase is driven by Xerus, so the mechanism is a risk: spike task 4.1 checks whether the phase length can be
  changed on a running phase. If it cannot, `CupSession` keeps its own deadline for the lobby and the driver reads it.
- The rule never lengthens the countdown. A racer leaving and rejoining does not restore the longer length while the
  countdown runs. A cancel (below the minimum, more than three seconds left) restarts the full lobby, and a full lobby
  shortens it again.
- Dev mode: the dev lobby is 10 s, so `min(10 s, 10 s)` leaves it unchanged.

### D6. The maximum racer count is a soft threshold

`VOYAGER_MAX_PLAYERS` defaults to 16 (OQ1) and must be at least the minimum (`ConfigCheck` error otherwise). It sets the
y of the join line and the full-lobby trigger of D5. It does not reject a join.

Rejecting joins at the maximum would contradict the waiting-lobby requirement "Late joiners are never rejected or
spectated", and that requirement is the owner's accepted behaviour. A hard cap is a follow-up change that amends it. Until
then the join line can read `17/16`, which is accepted for this change.

### D7. A boss bar, not a scoreboard

- The boss bar is per racer, as `RaceHud` already is, and is owned by the waiting room (`platform.lobby.LobbyHud`).
- It is shown while the racer's instance is the lobby instance, so `RaceHud`'s bar takes over when the racer enters the
  first map, with no coordination between the two.
- Its title is a MiniMessage line from `platform.text.Messages`: the waiting count and the maximum while waiting, and the
  seconds left while counting down. Progress is the fraction of the lobby left, and 1 while waiting.
- It is updated only when the title or the progress changes, not every tick, following the refresh pattern of `RaceHud`.
- The action bar keeps no waiting or countdown line. Chat keeps the join line and the cancel line.

Alternative: a scoreboard sidebar. Rejected: it would be a second HUD subsystem with no existing code, its own tests,
and its own per-racer lifecycle, for a view a boss bar gives. The owner's wording ("a scoreboard and/or bossbar") is
answered with the boss bar. The owner decided the boss bar alone (OQ4, 2026-10-12); a scoreboard is out of scope.

### D8. The join line is built in `platform.text`

`voyager.lobby.player.join=<c-ok><arg:0> <c-dim>joined the game (<arg:1>/<arg:2>)`, with the racer's name, the online
count after the join and the maximum. The legacy key is `<lang:plugin.prefix> <green><arg:0> <white>joined the game
(<arg:1>/<arg:2>)`. The prefix is not carried over, because the rebuild has no plugin prefix. The line is sent to every
online racer from `WaitingRoom.joined`, after `Racers.prepare`, and it is built only in `platform.text.Messages`
(`ApiPurityTest.onlyTheTextPackageBuildsAUserFacingString`).

### D9. Golden master

- `CupSessionGoldenMasterTest` and the transcripts under `golden/cup-session/` do not change. `CupSession` gains one
  method that no golden fixture calls, and its per-tick order is unchanged.
- `CupTranscript` renders chat, title, subtitle, title times and action bar packets. It does not render boss bar packets,
  so the lobby boss bar does not appear in any transcript. The action bar lines that the waiting room sent are removed,
  so any transcript that records them would change. The task list checks this and records the result.
- A new golden `golden/cup-session/lobby-world-start.txt` characterises the lobby to first map transfer and the return to
  the lobby at cup end. It is added, not edited.

### D10. ADR and numbering

- ADR-0029, `docs/decisions/0029-dedicated-lobby-world-for-waiting-racers.md`, Proposed. It supersedes ADR-0022 for the
  location of waiting racers. Its text names 0022 as superseded. The file 0022 is not edited in this change, and
  task 11.4 edits its status after the owner accepts 0029 (task 1.2).
- Numbering: ADR-0027 is reserved by `openspec/oval-tilted-rings` (`docs/decisions/0027-oval-ring-shapes-in-the-map-format.md`,
  on that unmerged branch). ADR-0026 is on `main`. The branch `openspec/remove-start-launch` (commit `0dd208a`) claims
  **0028** for its ADR, so this change takes the next free number, **0029**. The file and every reference in this change
  use 0029.

### D11. Dependency direction and module placement

```
server (composition root, settings, VoyagerServer, ConfigCheck)
   |
   v
platform (lobby: WaitingRoom, LobbyTransition, LobbyHud; cup: CupSession; world: arrival step; text: Messages)
   |
   v
race (flow: StartGate, RaceStateMachine)  --->  api (race: LobbyDefinition, exception: InvalidLobbyException)
```

| New type | Module and package | Notes |
|---|---|---|
| `LobbyDefinition` (record) | voyager-api `net.elytrarace.voyager.api.race` | World name and spawn, validated in the compact constructor |
| `InvalidLobbyException` | voyager-api `net.elytrarace.voyager.api.race.exception` | Runtime, domain-named |
| `StartGate.FULL_LOBBY_COUNTDOWN`, `StartGate.shortenedCountdown` | voyager-race `net.elytrarace.voyager.race.flow` | Pure, no clock |
| `LobbyTransition` | voyager-platform `net.elytrarace.voyager.platform.lobby` | Uses the arrival step |
| `LobbyHud` | voyager-platform `net.elytrarace.voyager.platform.lobby` | Per-racer boss bar |
| `CupSession.shortenLobby(Duration)` | voyager-platform `net.elytrarace.voyager.platform.cup` | The only change in `cup` |
| arrival helper (moved from `MapTransition`) | voyager-platform `net.elytrarace.voyager.platform.world` | Behaviour unchanged |
| `Messages.playerJoined`, boss bar lines | voyager-platform `net.elytrarace.voyager.platform.text` | The only place that builds these strings |
| `ServerSettings` components and parsing, `ConfigCheck` lobby checks | voyager-server `net.elytrarace.voyager.server.config` | Settings are read only here |
| lobby exception for settings | voyager-server `net.elytrarace.voyager.server.config.exception` | Named like `MissingServerDirectoryException` |
| wiring (spawn event, boot load, lobby transfer) | voyager-server `VoyagerServer` and `inject` | DI annotations stay out of the slices |

No new edge is added between `race` and `platform`, and `platform` still does not depend on `server`.

### D12. Fitness rule per boundary

| Boundary | Existing rule that covers it | New rule |
|---|---|---|
| `api.race.LobbyDefinition`, `InvalidLobbyException` | `ApiPurityTest.apiDoesNotDependOnMinestomOrItsWorldLoader`, `apiDoesNotPerformFileIo`, `DesignRuleTest.apiTypesAreRecordsInterfacesOrEnums`, `exceptionsLiveInAnExceptionSubpackage` | none |
| `race.flow.StartGate` additions | `ApiPurityTest.raceDoesNotDependOnMinestomXerusOrItsWorldLoader`, `raceDoesNotDependOnPlatform` | none |
| `platform.lobby` (`LobbyHud`, `LobbyTransition`) | `ApiPurityTest.platformDoesNotDependOnServer`, `onlyTheTextPackageBuildsAUserFacingString`, `NullabilityConventionTest` | none |
| Lobby settings read from system properties | `CompositionRootRulesTest` (DI and composition only in the root) | `onlyServerConfigReadsLobbySettings`: only `server.config.ServerSettings` may name `VOYAGER_LOBBY_WORLD`, `VOYAGER_LOBBY_SPAWN` or `VOYAGER_MAX_PLAYERS`. `allowEmptyShould(false)` |
| `platform.world` arrival helper | `SliceBoundaryRulesTest` | none |

`FitnessCoverageTest` is unchanged, because no module is added.

## Risks / Trade-offs

- **Xerus phase length.** The full-lobby countdown needs the running lobby phase to take a shorter length.
  → Mitigation: spike 4.1 before the code; the fallback in D5 keeps the countdown in `CupSession`.
- **Blocking arrival on the tick thread.** The cup-end return to the lobby is a cross-instance move on the tick thread,
  as the map moves already are. → Mitigation: the move runs once per cup end, not per tick; measured in the playtest.
- **Lobby asset.** `ElytraLobby` is DataVersion 4438 (owner-stated), older than the 26.2 target; chunks that were only
  partly generated in the old world are skipped by the loader, as in the converted maps (`converted/README.md`), and a
  spawn in such a chunk would be a hole. → Mitigation: the operator task upgrades a copy, checks the spawn chunk with the
  configuration check, and checks the spawn in game before the change is accepted.
- **Memory of a held world.** → Mitigation: measured on the operator's machine (task 2.2); if it is large, the lobby can
  be given a smaller chunk set, which is a follow-up.
- **Boss bar and action bar in the same place.** A racer sees the lobby bar and not the action bar line. → Mitigation:
  the golden check in D9 and a visual check in the playtest.
- **Soft maximum.** The join line can exceed its maximum (D6). → Accepted by the owner (OQ1); a hard cap is a follow-up.

## Migration Plan

1. Land the change with `VOYAGER_LOBBY_WORLD` unset. Behaviour is the old one, with the warning, so no deployment breaks.
2. Operator: upgrade the lobby copy (task 2.1 to 2.3), then set `VOYAGER_LOBBY_WORLD` and `VOYAGER_LOBBY_SPAWN` on the
   playtest server first, then on the CloudNet template.
3. Accept ADR-0029 and mark ADR-0022 superseded (tasks 1.2 and 11.4).
4. Rollback: unset `VOYAGER_LOBBY_WORLD`. The fallback path is the same code, and the lobby world is never written, so no
   world needs restoring.

## Owner decisions (2026-10-12)

The owner answered the five questions that were open in the proposal. Each answer is a decision of this change, and the
specs and tasks follow it.

- **OQ1, maximum racers.** The default is 16 and the maximum is a soft threshold. Late joiners are never rejected. A hard
  cap is a possible follow-up and is out of scope here (D6).
- **OQ2, no lobby configured.** The server warns at boot and falls back to the first map's spawn (D2).
- **OQ3, full-lobby countdown.** A 10 s constant, not a setting (D5).
- **OQ4, boss bar.** The boss bar shows the player count and the countdown. A scoreboard is out of scope (D7).
- **OQ5, lobby world.** `ElytraLobby` is the lobby world. Its spawn is read from its `level.dat` in operator task 2.3 (D1,
  tasks 2.1 to 2.3).

No question is open in this change.
