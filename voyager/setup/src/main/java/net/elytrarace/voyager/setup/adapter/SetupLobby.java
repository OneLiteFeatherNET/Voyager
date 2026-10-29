package net.elytrarace.voyager.setup.adapter;

import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;

/**
 * The setup server's lobby: a flat world with one floor, where a builder joins and where a builder goes back to when
 * the builder has no map open. The setup server has no other world of its own; each map is a world of its own.
 *
 * <p>A builder who joins is placed here, because Minestom requires a spawning instance for every joining player.
 * {@link #register} sets it on the global handler, and a builder who closes a map or has a map unlocked is moved here
 * with the setup wand taken away (see {@link SetupCommands}).
 */
public final class SetupLobby {

    /** The spawn of the lobby: the middle of the floor, one block above it. */
    private static final Pos SPAWN = new Pos(0.5, 1, 0.5);

    private final InstanceContainer instance;

    /**
     * Builds the lobby and loads the chunks around its spawn now. Minestom completes a move into an instance only once
     * the chunks around the destination are loaded, and it does not cancel an earlier move that is still waiting. A
     * builder sent here must therefore arrive at once, or a map move issued straight after could be overtaken by it.
     *
     * @param instances the instance manager that registers the lobby's world
     */
    public SetupLobby(InstanceManager instances) {
        this.instance = instances.createInstanceContainer();
        this.instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.BEDROCK));
        ChunkRange.chunksInRange(SPAWN, instance.viewDistance() + 1,
                (chunkX, chunkZ) -> instance.loadChunk(chunkX, chunkZ).join());
    }

    public Instance instance() {
        return instance;
    }

    public Pos spawn() {
        return SPAWN;
    }

    /**
     * Places every joining player in the lobby.
     *
     * @param events the global handler in the setup server; the process's handler in a test
     */
    public void register(EventNode<Event> events) {
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(SPAWN);
        });
    }
}
