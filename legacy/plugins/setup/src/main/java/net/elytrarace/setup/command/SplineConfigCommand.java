package net.elytrarace.setup.command;

import net.elytrarace.setup.preview.ParticlePreviewManager;
import net.elytrarace.setup.util.SetupGuard;
import net.elytrarace.spline.SplineConfig;
import net.kyori.adventure.text.Component;
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
                player.sendMessage(Component.translatable("spline.preset.unknown"));
                yield null;
            }
        };
        if (config == null) return;

        previewManager.setConfig(player.getUniqueId(), config);
        player.sendActionBar(Component.translatable("spline.preset.set", Component.text(preset)));
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
        player.sendActionBar(Component.translatable("spline.spacing.set", Component.text(spacing)));
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
        player.sendActionBar(Component.translatable("spline.size.set", Component.text(size)));
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
        player.sendActionBar(Component.translatable("spline.color.set",
                Component.text(r), Component.text(g), Component.text(b)));
    }

    public void handleInfo(CommandContext<PlayerSource> context) {
        var player = context.sender().source();
        var config = previewManager.getConfig(player.getUniqueId());

        player.sendMessage(Component.translatable("spline.info.header"));
        player.sendMessage(Component.translatable("spline.info.spacing", Component.text(config.particleSpacing())));
        player.sendMessage(Component.translatable("spline.info.size", Component.text(config.particleSize())));
        player.sendMessage(Component.translatable("spline.info.color",
                Component.text(config.colorR()), Component.text(config.colorG()), Component.text(config.colorB())));
        player.sendMessage(Component.translatable("spline.info.visibility", Component.text(config.visibility().name())));
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
