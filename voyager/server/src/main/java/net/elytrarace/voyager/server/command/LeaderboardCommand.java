package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.writer.LayoutVersion;
import net.elytrarace.voyager.platform.stats.StatsReplies;
import net.elytrarace.voyager.platform.stats.StatsRequests;
import net.elytrarace.voyager.platform.text.Messages;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;

import java.util.Optional;

/**
 * {@code /leaderboard <map>}: the fastest times on the map's current layout, in ranked mode, at most ten, fastest first.
 *
 * <p>The map is looked up in the catalogue the server is playing, and its layout version is the one the records are kept
 * under, so a map edit that changes the layout starts an empty leaderboard. Anyone may run it; no permission node is
 * checked. The read goes through {@link StatsRequests}, so the reply is sent from the tick.
 */
public final class LeaderboardCommand extends Command {

    private static final String MAP = "map";

    /**
     * @param catalog  the catalogue the server plays, for the map and its layout
     * @param reader   the statistics read side; asked whether statistics are kept, then for the times
     * @param requests the queue the read runs through and the tick answers from
     */
    public LeaderboardCommand(CatalogHolder catalog, StatsReader reader, StatsRequests requests) {
        super("leaderboard");
        ArgumentWord map = ArgumentType.Word(MAP);
        setDefaultExecutor((sender, context) -> sender.sendMessage(Messages.statsUsage()));
        addSyntax((sender, context) -> leaderboard(sender, catalog, reader, requests, context.get(map)), map);
    }

    private static void leaderboard(CommandSender sender, CatalogHolder catalog, StatsReader reader,
            StatsRequests requests, String mapKey) {
        if (!reader.recorded()) {
            sender.sendMessage(Messages.statsNotRecorded());
            return;
        }
        Optional<MapDefinition> found = catalog.current().snapshot().mapByName(mapKey);
        if (found.isEmpty()) {
            sender.sendMessage(Messages.statsUnknownMap(mapKey));
            return;
        }
        MapDefinition map = found.get();
        String layout = LayoutVersion.of(map);
        // Ranked mode only: practice runs are kept, but a leaderboard lists races.
        requests.ask(line -> sender.sendMessage(line),
                () -> reader.topTimes(map.name(), layout, GameMode.RACE, StatsReplies.LEADERBOARD_LENGTH),
                entries -> StatsReplies.leaderboard(map.name(), entries));
    }
}
