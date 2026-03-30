package net.elytrarace.setup.command;

import net.elytrarace.setup.wizard.WizardManager;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

public class WizardCommand {

    private final WizardManager wizardManager;

    public WizardCommand(WizardManager wizardManager) {
        this.wizardManager = wizardManager;
    }

    public void handleStart(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        if (wizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(Component.translatable("wizard.already_active"));
            return;
        }
        wizardManager.start(player);
        player.sendActionBar(Component.translatable("wizard.started"));
    }

    public void handleStop(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        if (!wizardManager.isActive(player.getUniqueId())) {
            player.sendMessage(Component.translatable("wizard.not_active"));
            return;
        }
        wizardManager.stop(player);
        player.sendActionBar(Component.translatable("wizard.stopped"));
    }

    public static void register(PaperCommandManager<Source> commandManager, WizardManager wizardManager) {
        var cmd = new WizardCommand(wizardManager);

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("wizard")
                .literal("start")
                .senderType(PlayerSource.class)
                .handler(cmd::handleStart)
        );

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("wizard")
                .literal("stop")
                .senderType(PlayerSource.class)
                .handler(cmd::handleStop)
        );
    }
}
