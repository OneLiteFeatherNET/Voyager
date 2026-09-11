package net.elytrarace.tools.recorder;

import io.papermc.paper.command.brigadier.BasicCommand;
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
 * through Paper's Brigadier lifecycle event, instead of in {@code build.gradle.kts}. The command is
 * registered through an anonymous {@link BasicCommand} rather than the {@code this::record} method
 * reference used before: a method reference can only implement {@code execute}, and gating the
 * command behind {@link #RECORD_PERMISSION} means overriding the {@code permission()} default too,
 * which only a class body can do.
 *
 * <p>This class stays thin by design: it only reads the script and orchestrates the handoff.
 * Everything about what a valid recording is lives in {@link TraceCollector}, and everything about
 * driving the entity lives in {@link GliderRunner}. The things it does decide are mechanical, not
 * physical: {@code profile} is restricted to a safe character set before it ever reaches a path (a
 * name like {@code ../../x} would otherwise walk the script/trace lookup out of the data folder),
 * only one recording runs at a time (two gliders can push each other despite
 * {@code setAware(false)} — that suppresses look control, not collision — and two concurrent
 * recordings of the same profile would race the same output file), and the command requires
 * {@link #RECORD_PERMISSION} (spawning entities and writing files to disk on request is not
 * something an arbitrary player should be able to trigger).
 */
public final class RecorderPlugin extends JavaPlugin {

    private static final String RECORD_PERMISSION = "trace-recorder.record";
    private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private boolean recordingInProgress;

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                "record",
                "Fly a scripted profile and write its trace fixture",
                new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack source, String[] args) {
                        record(source, args);
                    }

                    @Override
                    public String permission() {
                        return RECORD_PERMISSION;
                    }
                }));
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
            sender.sendMessage("No script found at %s: %s".formatted(scriptPath, e.getMessage()));
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
        GliderRunner runner = null;
        try {
            runner = new GliderRunner(this, above);
            double gravity = runner.gravity();
            TraceCollector collector = new TraceCollector(Bukkit.getMinecraftVersion(), profile, gravity, script);
            recordingInProgress = true;
            runner.start(collector, profile, sender, () -> recordingInProgress = false);
        } catch (RuntimeException e) {
            // Reached only if the glider was already spawned (by this same call) and something
            // after that — reading its gravity attribute, building the collector — threw before
            // start() could take over responsibility for cleaning it up. recordingInProgress is
            // never set in that case, so there is nothing to release here beyond the entity itself.
            if (runner != null) {
                runner.disposeWithoutStarting();
            }
            sender.sendMessage("Failed to prepare the recording: %s".formatted(e.getMessage()));
        }
    }
}
