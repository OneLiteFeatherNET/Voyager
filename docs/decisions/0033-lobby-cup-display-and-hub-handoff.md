# ADR-0033: The lobby shows the cup, and a finished cup hands racers to the hub through a port the CloudNet bridge installs

## Status

Proposed

The project owner has not accepted this record yet. It becomes Accepted only when the owner accepts it. When it is accepted,
ADR-0029's return rule ("a racer returns to the lobby world when a cup finishes") is replaced by this record, and ADR-0026 gets
an amendment note on the bridge's allowed Voyager package. The change that implements it is `lobby-cup-display-and-hub-handoff`.

## Date

2026-10-12

## Decision makers

- TheMeinerLP (project owner). On 2026-10-12 decided that every racer goes to the network hub after a cup ends, with no hard kick,
  and that the lobby shows the cup on a sidebar, on a hologram at a fixed position, and in the boss bar text.
- Drafted with the change `lobby-cup-display-and-hub-handoff`. The owner's answers to its open questions are recorded as decisions in
  its design.md once given.

## Context and problem statement

Two things change together. First, a finished cup returns racers to the lobby world (ADR-0029). The owner now wants them sent to the
network hub instead, through the CloudNet bridge (ADR-0026). Second, a waiting racer sees a count and a countdown but not the cup they
are waiting for.

The server cannot name a CloudNet type: `CloudNetBoundaryTest` confines `eu.cloudnetservice` to `voyager-cloudnet-bridge`, and the
fat jars must not carry CloudNet. The move to the hub is a CloudNet call (`PlayerManager.playerExecutor(uuid).connectToFallback()` in
bridge-api RC17). So a value has to cross the extension boundary from the bridge to the server, and the server has to be able to run
without the bridge.

## Decision drivers

- A racer is never kicked on the normal path. The disconnect is only the local-development fallback, and the refusal path.
- The server names no CloudNet type, and the bridge names no Voyager type beyond one package.
- The server runs with no bridge (local development) and says so.
- The cup's text is built once, so the sidebar, the hologram and the boss bar cannot disagree.
- The change does not alter the golden transcripts of a running cup except at the cup end, which the owner asked to change.

## Considered options

For the handoff port:

1. **A typed port `PlayerHandoff` in `voyager-api`, held in one slot `PlayerHandoffs` in the same package, installed by the bridge at
   start (chosen).** The bridge names one Voyager package. The slot is shared because the extension's class loader delegates to the
   application loader first (fork `minestom-extensions` 2.2.0, `ExtensionClassLoader`). Pro: typed, testable with a fake, one check.
   Contra: a static slot, which the api module's "no implementation" rule does not allow without an exception; the design spec's
   sentence "the extension names no Voyager type" needs amending.
2. **A JDK-only rendezvous.** The server stores a `java.util.function.Consumer<UUID>` in a JDK-owned place and the bridge reads it.
   Pro: the greenfield sentence stays true. Contra: untyped and stringly keyed; nothing checks it.
3. **A `ServiceLoader` service read through the extension's class loader.** Contra: the server must know the extension's name and
   reach its loader; the interface still has to be in `voyager-api`.
4. **A Minestom event.** Contra: the event type has to be named by both sides, which is a shared Voyager type again.
5. **Do nothing: the server kicks racers with `PlayerExecutor.kick`.** Rejected by the owner: the hard kick is not the wanted path.

For the lobby display: a sidebar, a hologram, and a cup name on the boss bar, all read from one board (`LobbyBoard`). The sidebar is
shown only while a racer waits, because the race HUD has none. The hologram is optional and needs a lobby world.

## Decision outcome

Chosen: option 1 for the port, with the design's decisions D1 to D10 (`lobby-cup-display-and-hub-handoff`/design.md), which fix the
linger (200 ticks, 10 s, owner answer Q3), the empty room after the handoff, a refused handoff that disconnects with no retry (Q2), the cup shown while waiting (the cup currently loaded; a pending reload shows from the round that promotes it), and the
configuration rules (`VOYAGER_LOBBY_HOLOGRAM` needs `VOYAGER_LOBBY_WORLD`).

Verified and pending: the class identity across the boundary is read from the fork's source and is to be confirmed by a spike on the
owner's node before the handoff is enabled. `connectToFallback` returns no result, so a refusal is detected only by an exception, and the
owner's proxy must have a fallback configured for the racers to go anywhere.

## Consequences

- Good: a racer is never kicked on the normal path, and the local run keeps working with a disconnect and a message.
- Good: the bridge names exactly one Voyager package, enforced by an ArchUnit rule and by a build check on the bridge jar.
- Good: the three lobby displays read one board, so they cannot disagree.
- Bad: the handoff's static slot is a process-wide value. Tests reset it, and a second bridge install replaces the first.
- Bad: the greenfield design's sentence and ADR-0026's wording are amended. Until the owner accepts that, the spec text and the older texts disagree.
- Bad: after a cup the lobby is empty, so no countdown runs until new racers join.
- Neutral: the hub is whatever CloudNet's fallback configuration names. Voyager does not choose it.

## More information

- `openspec/changes/lobby-cup-display-and-hub-handoff/` (proposal, design with the investigation and the open questions, delta specs, tasks).
- ADR-0026 (the bridge as a Minestom extension), ADR-0029 (the dedicated lobby world, which this record amends), and the change
  `add-lobby-world` (the lobby world's specs and its unarchived delta).
- The fork's class loader: `net.minestom.server.extensions.ExtensionClassLoader` in `net.onelitefeather:minestom-extensions:2.2.0`.
- CloudNet bridge-api RC17: `eu.cloudnetservice.modules.bridge.player.PlayerManager` and `PlayerExecutor.connectToFallback()`.
