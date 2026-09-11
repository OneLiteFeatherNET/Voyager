package net.elytrarace.tools.recorder;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.elytrarace.tools.recorder.bukkit.GliderRunner;
import net.elytrarace.tools.recorder.capture.TraceCollector;
import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import net.elytrarace.tools.recorder.script.FlightScript;
import net.elytrarace.tools.recorder.script.FlightScriptParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Registers {@code /record <profile>}, which flies a scripted glide and writes its trace fixture.
 *
 * <p>plugin-yml 0.6.0's {@code paper { }} block has no {@code commands { }} DSL — that container is
 * only present on the Bukkit descriptor, not the Paper one — so the command is declared here,
 * through Paper's Brigadier lifecycle event, instead of in {@code build.gradle.kts}.
 *
 * <p>This class stays thin by design: it only reads the script and orchestrates the handoff.
 * Everything about what a valid recording is lives in {@link TraceCollector}, and everything about
 * driving the entity lives in {@link GliderRunner}. The two things it does decide are mechanical,
 * not physical: {@code profile} is restricted to a safe character set before it ever reaches a path
 * (a name like {@code ../../x} would otherwise walk the script/trace lookup out of the data folder),
 * and only one recording runs at a time (two gliders can push each other despite
 * {@code setAware(false)} — that suppresses look control, not collision — and two concurrent
 * recordings of the same profile would race the same output file).
 */
public final class RecorderPlugin extends JavaPlugin {

    private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private boolean recordingInProgress;

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                "record",
                "Fly a scripted profile and write its trace fixture",
                this::record));
    }

    private void record(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length != 1) {
            sender.sendMessage("Usage: /record <profile>");
            return;
        }
        String profile = args[0];
        if (!PROFILE_NAME.matcher(profile).matches()) {
            sender.sendMessage(
                    "Invalid profile name '%s': only letters, digits, '-' and '_' are allowed.".formatted(profile));
            return;
        }
        if (recordingInProgress) {
            sender.sendMessage("A recording is already in progress; wait for it to finish first.");
            return;
        }

        Path scriptPath = getDataFolder().toPath().resolve("scripts").resolve("%s.txt".formatted(profile));
        String scriptText;
        try {
            scriptText = Files.readString(scriptPath);
        } catch (IOException e) {
            sender.sendMessage("No script found at %s".formatted(scriptPath));
            return;
        }

        FlightScript script;
        try {
            script = FlightScriptParser.parse(scriptText);
        } catch (InvalidTraceException e) {
            sender.sendMessage("Failed to parse '%s': %s".formatted(profile, e.getMessage()));
            return;
        }

        Location above = source.getLocation();
        GliderRunner runner = new GliderRunner(this, above);
        TraceCollector collector =
                new TraceCollector(Bukkit.getMinecraftVersion(), profile, runner.gravity(), script);
        recordingInProgress = true;
        runner.start(collector, profile, sender, () -> recordingInProgress = false);
    }
}
