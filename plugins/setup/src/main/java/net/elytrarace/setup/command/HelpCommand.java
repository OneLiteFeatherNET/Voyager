package net.elytrarace.setup.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

/**
 * Handles {@code /elytrarace help} — shows onboarding sequence and command reference.
 */
public class HelpCommand {

    public void handle(CommandContext<PlayerSource> context) {
        var player = context.sender().source();

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("ElytraRace Setup", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.empty());

        // Onboarding
        player.sendMessage(Component.text("Getting Started:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        sendStep(player, 1, "/elytrarace setup", "Enter setup mode");
        sendStep(player, 2, "/elytrarace cup create <name> <displayName>", "Create a cup");
        sendStep(player, 3, "/elytrarace map load <worldFolder>", "Load a world");
        sendStep(player, 4, "/elytrarace map create <cup> <name> <displayName>", "Register map");
        sendStep(player, 5, "/elytrarace portal", "Place portals (FAWE select first)");
        sendStep(player, 6, "/elytrarace portal show", "See portal outlines");
        sendStep(player, 7, "/elytrarace portal testfly", "Test your map");
        player.sendMessage(Component.empty());

        // Portal commands
        player.sendMessage(Component.text("Portal Commands:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        sendCmd(player, "/elytrarace portal", "Save FAWE selection as portal");
        sendCmd(player, "/elytrarace portal edit <index>", "Edit portal in FAWE");
        sendCmd(player, "/elytrarace portal save", "Save edited portal");
        sendCmd(player, "/elytrarace portal cancel", "Abort active portal edit");
        sendCmd(player, "/elytrarace portal delete <index>", "Delete portal");
        sendCmd(player, "/elytrarace portal undo", "Undo last action");
        sendCmd(player, "/elytrarace portal show", "Toggle particle preview");
        sendCmd(player, "/elytrarace portal path", "Toggle spline line");
        sendCmd(player, "/elytrarace portal testfly [startIndex]", "Test fly the map");
        sendCmd(player, "/elytrarace portals", "Open portal manager GUI");
        player.sendMessage(Component.empty());

        // Guide commands
        player.sendMessage(Component.text("Guide Points:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        sendCmd(player, "/elytrarace guide", "Place guide point (auto)");
        sendCmd(player, "/elytrarace guide between <a> <b>", "Place between specific portals");
        sendCmd(player, "/elytrarace guide move <id>", "Move to current position");
        sendCmd(player, "/elytrarace guide delete <id>", "Delete guide point");
        sendCmd(player, "/elytrarace guide list", "List all guide points");
        player.sendMessage(Component.empty());

        // Map/Cup commands
        player.sendMessage(Component.text("Map & Cup:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        sendCmd(player, "/elytrarace map status", "Show map overview");
        sendCmd(player, "/elytrarace map tp <name>", "Teleport to map world");
        sendCmd(player, "/elytrarace map load <folder>", "Load world from disk");
        sendCmd(player, "/elytrarace map rename <old> <new> <display>", "Rename map");
        sendCmd(player, "/elytrarace map delete <name>", "Delete map");
        sendCmd(player, "/elytrarace cup", "Open cup manager GUI");
        sendCmd(player, "/elytrarace cup rename <old> <new> <display>", "Rename cup");
        player.sendMessage(Component.empty());

        // Spline config
        player.sendMessage(Component.text("Spline Config:", NamedTextColor.YELLOW, TextDecoration.BOLD));
        sendCmd(player, "/elytrarace spline preset <name>", "easy/medium/hard/builder");
        sendCmd(player, "/elytrarace spline spacing <0.1-3.0>", "Particle density");
        sendCmd(player, "/elytrarace spline size <0.5-4.0>", "Particle size");
        sendCmd(player, "/elytrarace spline color <r> <g> <b>", "RGB color");
        sendCmd(player, "/elytrarace spline info", "Show current config");
        player.sendMessage(Component.empty());
    }

    private void sendStep(org.bukkit.entity.Player player, int step, String cmd, String desc) {
        player.sendMessage(
                Component.text("  " + step + ". ", NamedTextColor.WHITE)
                        .append(Component.text(cmd, NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand(cmd))
                                .hoverEvent(Component.text("Click to fill command")))
                        .append(Component.text(" — " + desc, NamedTextColor.GRAY))
        );
    }

    private void sendCmd(org.bukkit.entity.Player player, String cmd, String desc) {
        player.sendMessage(
                Component.text("  ", NamedTextColor.WHITE)
                        .append(Component.text(cmd, NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.suggestCommand(cmd))
                                .hoverEvent(Component.text("Click to fill command")))
                        .append(Component.text(" — " + desc, NamedTextColor.GRAY))
        );
    }

    public static void register(PaperCommandManager<Source> commandManager) {
        var cmd = new HelpCommand();

        // /elytrarace help
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("help")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );

        // /elytrarace (bare, no subcommand) also shows help
        commandManager.command(commandManager.commandBuilder("elytrarace")
                .senderType(PlayerSource.class)
                .handler(cmd::handle)
        );
    }
}
