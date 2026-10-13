# Research 010: a hub handoff through the CloudNet bridge, and the class identity it relies on

Status: read from sources, not yet observed on the owner's node. The change `lobby-cup-display-and-hub-handoff` (ADR-0033)
implements the handoff. Two node spikes remain the owner's to run before the handoff is enabled on the network.

## Question

After a cup, the server must send every racer to the network hub. Two things had to be known before the code could depend on
them. First, how CloudNet moves a player that the server does not own. Second, whether a value that the server installs can be
read by the CloudNet extension, given that the server and the extension load in different class loaders. A third question came
with it: what a racer sees when the move is refused, and how long the results stay up before the move.

## Method

The bridge API was read from the sources of `bridge-api` and `driver-api` 4.0.0-RC17 in the Gradle cache, with `javap` on the
compiled jars for the signatures. The extension loader was read from the sources of `net.onelitefeather:minestom-extensions`
2.2.0, the fork the roots pin. The existing Cygnus bridge was searched for a precedent of a player move: none exists, and its
only CloudNet use is a permission checker. Nothing was run on a node.

## Findings

**The move.** `PlayerManager.playerExecutor(UUID)` returns a `PlayerExecutor` (package
`eu.cloudnetservice.modules.bridge.player.executor`). Its `connectToFallback()` returns `void`, so a refusal is not reported to
the caller; the fallback is chosen from the proxy's `ProxyFallback` configuration. `ServiceRegistry.registry()
.defaultInstance(PlayerManager.class)` obtains the manager. The executor also offers `kick`, which the owner rejected as the
normal path.

**The class identity.** The fork's `ExtensionClassLoader` calls its parent's `loadClass` first. The parent is the application
loader, which holds the root's jar. So a `voyager-api` class loaded by the root is the same `Class` inside the extension, and a
static slot in it is one value. The owner's node must confirm this: a throwaway extension logs the identity of the class from both
sides (task 2.1). If the identities differ, the design reopens the rendezvous question.

**The refusal.** `connectToFallback` throws a `RuntimeException` when CloudNet refuses. A refusal is therefore a thrown exception.
The bridge maps any such exception to `HandoffUnavailableException`, and the server disconnects the racer with the translated
message. No retry is made, because a retry would race the proxy's own move.

**The timing.** The cup's standings are announced on the tick the cup is finished, so a hand-off on that tick would take the racers
away before they could read the result. The results therefore linger for 200 ticks (10 s at 20 TPS) before the hand-off, and the
room holds any countdown during the linger. Without the hold, a countdown started by a racer still on the server would cancel the
linger, because the cup it started would replace the results.

## Limits

- The class identity and the fallback behaviour are not observed. The spike results go into `design.md` section 2 before the
  handoff is enabled on the owner's network.
- `connectToFallback` gives no feedback. A proxy without a fallback leaves the racer where they are, and Voyager cannot tell.
  The deploy guide carries that as an operator note.
- The measurements are for one racer at a time. A large number of handoffs in one tick has not been measured.

## Decision

The port `PlayerHandoff` lives in `voyager-api`, and the bridge installs it through `PlayerHandoffs` (ADR-0033). The bridge
names only that package. The linger is a constant, not a setting.
