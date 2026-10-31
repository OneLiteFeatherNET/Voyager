package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.stats.StatsReplies;
import net.elytrarace.voyager.platform.stats.StatsRequests;
import net.elytrarace.voyager.platform.text.Messages;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code /stats [player]}: the statistics of the player who types it, or of the player named.
 *
 * <p>Anyone may run it; no permission node is checked. The read goes to {@link StatsRequests}, so the reply is sent from the
 * tick and a slow database answers "unavailable" after two seconds. With persistence off the command says so at once, without
 * a read. A console sender with no name gets the usage line.
 */
public final class StatsCommand extends Command {

    private static final String PLAYER = "player";

    /**
     * @param catalog  the catalogue the server plays, for the display names of the maps in a profile
     * @param reader   the statistics read side; asked whether statistics are kept, then for the profile
     * @param requests the queue the read runs through and the tick answers from
     */
    public StatsCommand(CatalogHolder catalog, StatsReader reader, StatsRequests requests) {
        super("stats");
        ArgumentWord player = ArgumentType.Word(PLAYER);
        setDefaultExecutor((sender, context) -> {
            if (sender instanceof Player self) {
                profileOf(sender, catalog, reader, requests, self.getUuid(), self.getUsername());
            } else {
                sender.sendMessage(Messages.statsUsage());
            }
        });
        addSyntax((sender, context) -> profileNamed(sender, catalog, reader, requests, context.get(player)), player);
    }

    private static void profileOf(CommandSender sender, CatalogHolder catalog, StatsReader reader,
            StatsRequests requests, UUID playerId, String name) {
        if (!reader.recorded()) {
            sender.sendMessage(Messages.statsNotRecorded());
            return;
        }
        requests.ask(line -> sender.sendMessage(line), () -> reader.profile(playerId),
                profile -> StatsReplies.profile(profile, name, catalog.current().snapshot()));
    }

    private static void profileNamed(CommandSender sender, CatalogHolder catalog, StatsReader reader,
            StatsRequests requests, String name) {
        if (!reader.recorded()) {
            sender.sendMessage(Messages.statsNotRecorded());
            return;
        }
        requests.ask(line -> sender.sendMessage(line), () -> reader.findPlayer(name).map(reader::profile),
                found -> found
                        .map(profile -> StatsReplies.profile(profile,
                                Objects.requireNonNullElse(profile.lastKnownName(), name),
                                catalog.current().snapshot()))
                        .orElseGet(() -> List.of(Messages.statsUnknownPlayer(name))));
    }
}
