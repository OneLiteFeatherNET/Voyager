# Proposal

**Conventional Commits:** `feat(race)`. Squash-merge PR title: `feat(race): show the cup in the lobby and hand finished racers to the hub`.

Commits inside the change keep one type each. The platform, api and server commits are `feat(race)`. The bridge commits are `feat(cloudnet)`, the same type under another scope. Section 8 of `tasks.md` names them. The change is not split (see design.md, "Scope and split").

**Owner decisions (2026-10-12)** that this change implements:

1. After a cup ends and its results have been shown, every racer on the server goes to the network hub or fallback through the CloudNet bridge. No hard kick on that path. This replaces the return to the lobby world that `add-lobby-world` (#314, on `main`, not archived) specifies.
2. The lobby shows the cup in three places: a sidebar with the cup name and the maps in play order; a text hologram (Minestom `TextDisplay`) at a fixed position in the lobby world; and the cup name in the lobby boss bar text.

**Amends:**
- `add-lobby-world` requirement "A finished cup returns the room to waiting", with its scenarios, and the return rule of ADR-0029 (Proposed). ADR-0033 (`docs/decisions/0033-lobby-cup-display-and-hub-handoff.md`, Proposed) records the new rule. ADR-0029 is not edited until the owner accepts ADR-0033.
- The greenfield design spec, section "CloudNet v4", paragraph "the extension names no Voyager type", and ADR-0026's wording that the bridge depends on nothing else of Voyager. The handoff needs one Voyager package in the extension's view (design.md, decision D1). The in-flight `add-cloudnet-deployment` spec already allows `voyager-api` types across the boundary ("Only JDK and voyager-api types cross the extension boundary"), so the owner's approval is needed for the two older texts, not for the spec. The bridge test `bridgeDependsOnlyOnJdkAndLibraries` is narrower than that spec; this change widens it to `api.handoff` only.

**Depends on:**
- `add-lobby-world` (#314 is on `main`). Its waiting-lobby delta and `server/lobby-world` capability are not archived yet. This change's delta is written against them.
- `add-cloudnet-deployment`, which creates `voyager-cloudnet-bridge`, ADR-0026 and the `cloudnet/bridge-extension` capability. Not archived yet.

Slices touched: `platform/lobby` (waiting room, boss bar, new board, sidebar and hologram), `platform/text` (boss bar keys, the disconnect message), `platform/cup` (the trigger only, read from `CupSession`; `CupSession` itself is not changed), `api/handoff` (new port, a new package in the api module), `cloudnet-bridge` (the connector and its install), `server` (settings, configuration check, boot spawn of the hologram), `fitness` (the CloudNet boundary and one new handoff boundary). The rings, the scoring, the map format, the physics and the standings screen are not touched.

## Why

A finished racer goes back to the lobby world today, so the network hub never gets them back. The owner wants the server to hand them to the network hub. The lobby also shows nothing about the cup it is waiting for, so a racer who joins cannot see which maps come next. This change adds both, and it names the one place where a Minestom server calls CloudNet, which ADR-0026 has so far kept out of the application.

## What Changes

- **Hub handoff at cup end.** When a cup has finished and its results have lingered for `RESULTS_LINGER` (10 s, 200 ticks), every racer online at that tick is handed to the network hub. The handoff goes through a port, `PlayerHandoff`, in `voyager-api`. The CloudNet bridge installs its implementation at extension start. Without the bridge the racer is disconnected with a translated message (local development).
- **Sidebar.** While a racer waits, a sidebar shows the cup name and the maps in play order. It is hidden while a cup races.
- **Hologram.** With `VOYAGER_LOBBY_HOLOGRAM=x,y,z` set, one text display sits at that position in the lobby world and shows the same cup text as the sidebar. Without it there is no hologram and no warning.
- **Boss bar.** The waiting and countdown lines name the cup. The chat join line is unchanged.
- **Configuration.** `VOYAGER_LOBBY_HOLOGRAM` is optional and needs `VOYAGER_LOBBY_WORLD`. The configuration check refuses it without a lobby, or with a position that is not three numbers.

**BREAKING (behaviour):** a finished cup no longer leaves its racers in the lobby world. They go to the hub. No setting changes meaning.

Out of scope:
- Choosing the hub. CloudNet's fallback configuration on the proxy decides where a racer goes. Voyager does not name a group or a task.
- A per-group or per-task target, and any hub setting in `ServerSettings`.
- A hard kick as the normal path on a node with the bridge. The disconnect is only the local fallback and the refusal path.
- Several holograms, a hologram on each map, and a hologram that follows the racer.
- Changing the standings screen or its duration (`RaceTimings.endAfterLastMap`).
- Bot racers. A bot-racer change is planned separately; see design.md, "Conflicts with planned changes".
- Operator tasks on `ElytraLobby` (that is `add-lobby-world`'s task 2).
- Any change to `legacy/`, `run/`, `.claude/agent-memory/`, `.serena/`, `docs/design/` or `net/`.

## Capabilities

### New Capabilities
- `race/lobby-cup-display`: the sidebar and the hologram that show the cup while racers wait, and the single source both read.
- `cloudnet/hub-handoff`: the handoff port in `voyager-api`, its install by the bridge, the local fallback, and the boundary that keeps the port the only Voyager type the bridge names.

### Modified Capabilities
- `race/waiting-lobby`: the cup-end requirement is replaced by the hub handoff, the boss bar text names the cup, and the countdown after a cup no longer starts from the racers who were sent away.
- `server/lobby-world`: the optional hologram position, and its configuration check. This adds to a capability that `add-lobby-world` creates.
