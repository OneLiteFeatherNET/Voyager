package net.elytrarace.setup.command;

import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.spline.SplineConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import static org.incendo.cloud.parser.standard.FloatParser.floatParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

/**
 * Commands for adjusting the spline preview per player.
 *
 * <ul>
 * <li>{@code /elytrarace spline preset <easy|medium|hard|builder>}</li>
 * <li>{@code /elytrarace spline spacing <0.1-3.0>}</li>
 * <li>{@code /elytrarace spline size <0.5-4.0>}</li>
 * <li>{@code /elytrarace spline color <r> <g> <b>}</li>
 * <li>{@code /elytrarace spline info}</li>
 * </ul>
 */
public class SplineConfigCommand {

    private final ParticlePreviewManager previewManager;

    public SplineConfigCommand(ParticlePreviewManager previewManager) {
        this.previewManager = previewManager;
    }

    public void handlePreset(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        String preset = context.<String>get("preset").toLowerCase();
        var config = switch (preset) {
            case "easy" -> SplineConfig.EASY;
            case "medium" -> SplineConfig.MEDIUM;
            case "hard" -> SplineConfig.HARD;
            case "builder" -> SplineConfig.BUILDER;
            default -> {
                player.sendMessage(Component.text("Unknown preset. Use: easy, medium, hard, builder", NamedTextColor.RED));
                yield null;
            }
        };
        if (config == null) return;

        previewManager.setConfig(player.getUniqueId(), config);
        player.sendActionBar(Component.text("Spline preset: " + preset, NamedTextColor.GREEN));
    }

    public void handleSpacing(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        float spacing = context.get("spacing");
        var current = previewManager.getConfig(player.getUniqueId());
        previewManager.setConfig(player.getUniqueId(),
                current.withParticles(spacing, current.particleSize(),
                        current.colorR(), current.colorG(), current.colorB()));
        player.sendActionBar(Component.text("Particle spacing: " + spacing + " blocks", NamedTextColor.GREEN));
    }

    public void handleSize(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        float size = context.get("size");
        var current = previewManager.getConfig(player.getUniqueId());
        previewManager.setConfig(player.getUniqueId(),
                current.withParticles(current.particleSpacing(), size,
                        current.colorR(), current.colorG(), current.colorB()));
        player.sendActionBar(Component.text("Particle size: " + size, NamedTextColor.GREEN));
    }

    public void handleColor(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        if (SetupGuard.getSetupHolder(player).isEmpty()) {
            player.sendMessage(Component.translatable("error.portal.quick.no_setup"));
            return;
        }

        int r = context.get("r");
        int g = context.get("g");
        int b = context.get("b");
        var current = previewManager.getConfig(player.getUniqueId());
        previewManager.setConfig(player.getUniqueId(),
                current.withParticles(current.particleSpacing(), current.particleSize(), r, g, b));
        player.sendActionBar(Component.text("Color: RGB(" + r + ", " + g + ", " + b + ")", NamedTextColor.GREEN));
    }

    public void handleInfo(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        var config = previewManager.getConfig(player.getUniqueId());

        player.sendMessage(Component.text("Spline Config:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Spacing: " + config.particleSpacing() + " blocks", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Size: " + config.particleSize(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Color: RGB(" + config.colorR() + ", " + config.colorG() + ", " + config.colorB() + ")", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Visibility: " + config.visibility(), NamedTextColor.GRAY));
    }

    public static void register(PaperCommandManager<Source> commandManager, ParticlePreviewManager previewManager) {
        var cmd = new SplineConfigCommand(previewManager);

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("spline").literal("preset")
                .required("preset", stringParser())
                .senderType(PlayerSource.class)
                .handler(cmd::handlePreset));

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("spline").literal("spacing")
                .required("spacing", floatParser(0.1f, 3.0f))
                .senderType(PlayerSource.class)
                .handler(cmd::handleSpacing));

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("spline").literal("size")
                .required("size", floatParser(0.5f, 4.0f))
                .senderType(PlayerSource.class)
                .handler(cmd::handleSize));

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("spline").literal("color")
                .required("r", integerParser(0, 255))
                .required("g", integerParser(0, 255))
                .required("b", integerParser(0, 255))
                .senderType(PlayerSource.class)
                .handler(cmd::handleColor));

        commandManager.command(commandManager.commandBuilder("elytrarace")
                .literal("spline").literal("info")
                .senderType(PlayerSource.class)
                .handler(cmd::handleInfo));
    }
}
