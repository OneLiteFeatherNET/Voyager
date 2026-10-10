package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.server.game.CatalogReloadService;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

/**
 * {@code /race} — enough to run a cup by hand and no more.
 *
 * <table>
 *   <caption>The subcommands</caption>
 *   <tr><th>{@code /race}</th><td>where the cup stands: phase, map, race clock, every racer's ring
 *       count, where the client says they are and where the server's simulation has them</td></tr>
 *   <tr><th>{@code /race reload}</th><td>re-read the maps and cups and apply them from the next round;
 *       gated by {@link ReloadPermission}, registered in every mode</td></tr>
 *   <tr><th>{@code /race start}</th><td>restart the cup from map one with no lobby wait</td></tr>
 *   <tr><th>{@code /race skip}</th><td>end the current map now, as if everybody had finished</td></tr>
 * </table>
 *
 * <p>The status subcommand and {@code reload} are always registered; the two that change the race are
 * registered only under {@code -Dvoyager.dev=true}, which is the gate the tree being replaced put
 * {@code /dev-start} behind and for the same reason — these are an operator's tools, not a player's.
 *
 * <p>{@code /race skip} exists because the alternative for exercising a map-to-map advance is flying
 * a 1588-block course to its last ring, or waiting out a five-minute race phase, every single time.
 * It is also the only way to reach the second map of a cup without a second map's worth of flying.
 */
public final class RaceCommand extends Command {

    /**
     * @param devMode whether to register {@code start} and {@code skip}; the status and {@code reload}
     *     syntax are registered either way
     * @param reloads runs {@code reload}; the sender's permission is checked before it is reached
     */
    public RaceCommand(CupSession session, boolean devMode, CatalogReloadService reloads) {
        super("race");
        setDefaultExecutor((sender, context) -> status(sender, session));
        // Checked here rather than as a syntax condition: a failed condition makes Minestom report the command as
        // unknown, which tells a player nothing. A refused sender is told why.
        addSyntax((sender, context) -> {
            if (ReloadPermission.mayReload(sender)) {
                reloads.request(sender::sendMessage);
            } else {
                sender.sendMessage(Messages.reloadDenied());
            }
        }, ArgumentType.Literal("reload"));
        if (!devMode) {
            return;
        }
        addSyntax((sender, context) -> {
            session.start(true);
            sender.sendMessage(Messages.cupRestarted(session.cup().name()));
        }, ArgumentType.Literal("start"));
        addSyntax((sender, context) -> sender.sendMessage(
                session.requestSkip() ? Messages.skipTaken() : Messages.skipRefused()),
                ArgumentType.Literal("skip"));
    }

    /**
     * Sends {@link CupSession#describe()} one line per message. Adventure renders an embedded newline
     * in chat, but one message per line is what keeps a long status readable in a console sender's
     * log as well, and the console is where an acceptance run reads it from.
     *
     * <p><strong>The one place in the rebuild that builds a component from a raw string on purpose,
     * and the fitness rule naming translated output names it as its single exception.</strong> This
     * is an operator diagnostic — coordinates, tick counts, a gliding flag, the drift between the
     * client's position and the server's simulation — assembled by {@code CupSession.describe()} as
     * one block of plain text. There is nothing here to translate: it has no audience but somebody
     * debugging, and turning a dump of numbers into forty translation keys would make it harder to
     * read and impossible to extend without editing a bundle.
     */
    private static void status(CommandSender sender, CupSession session) {
        for (String line : session.describe().split("\n")) {
            sender.sendMessage(Component.text(line));
        }
    }
}
