package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.server.game.CupSession;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;

/**
 * {@code /race} — enough to run a cup by hand and no more.
 *
 * <table>
 *   <caption>The three things an acceptance run needs to do</caption>
 *   <tr><th>{@code /race}</th><td>where the cup stands: phase, map, race clock, every racer's ring
 *       count, where the client says they are and where the server's simulation has them</td></tr>
 *   <tr><th>{@code /race start}</th><td>restart the cup from map one with no lobby wait</td></tr>
 *   <tr><th>{@code /race skip}</th><td>end the current map now, as if everybody had finished</td></tr>
 * </table>
 *
 * <p>The status subcommand is always registered; the two that change the race are registered only
 * under {@code -Dvoyager.dev=true}, which is the gate the tree being replaced put {@code /dev-start}
 * behind and for the same reason — these are an operator's tools, not a player's.
 *
 * <p>{@code /race skip} exists because the alternative for exercising a map-to-map advance is flying
 * a 1588-block course to its last ring, or waiting out a five-minute race phase, every single time.
 * It is also the only way to reach the second map of a cup without a second map's worth of flying.
 */
public final class RaceCommand extends Command {

    /**
     * @param devMode whether to register {@code start} and {@code skip}; the status syntax is
     *     registered either way
     */
    public RaceCommand(CupSession session, boolean devMode) {
        super("race");
        setDefaultExecutor((sender, context) -> status(sender, session));
        if (!devMode) {
            return;
        }
        addSyntax((sender, context) -> {
            session.start(true);
            sender.sendMessage(Component.text("Cup '%s' restarted, lobby skipped".formatted(session.cup().name())));
        }, ArgumentType.Literal("start"));
        addSyntax((sender, context) -> sender.sendMessage(Component.text(session.requestSkip()
                ? "Ending the current map on the next tick"
                : "Nothing is racing — a skip is not banked for the next map")),
                ArgumentType.Literal("skip"));
    }

    /**
     * Sends {@link CupSession#describe()} one line per message. Adventure renders an embedded newline
     * in chat, but one message per line is what keeps a long status readable in a console sender's
     * log as well, and the console is where an acceptance run reads it from.
     */
    private static void status(CommandSender sender, CupSession session) {
        for (String line : session.describe().split("\n")) {
            sender.sendMessage(Component.text(line));
        }
    }
}
