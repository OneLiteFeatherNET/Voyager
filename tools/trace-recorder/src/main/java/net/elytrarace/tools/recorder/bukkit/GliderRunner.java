package net.elytrarace.tools.recorder.bukkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.elytrarace.tools.recorder.capture.GliderSample;
import net.elytrarace.tools.recorder.capture.TraceCollector;
import net.elytrarace.tools.recorder.format.TraceFile;
import net.elytrarace.tools.recorder.script.ScriptedInput;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Spawns a scripted glider above the command's initiator, drives it tick by tick from a
 * {@link TraceCollector}, and writes the finished recording.
 *
 * <p>Every rule about what makes a recording valid lives in {@link TraceCollector} and the rest of
 * the server-free core; this class only exists to reach a live entity from Bukkit — spawn, drive,
 * sample, hand off. It applies the AI switch and the rotation-then-tick-then-sample ordering that
 * the Task 1 spike established: {@code setAware(false)} keeps the glide running while suppressing
 * look-control, and a scripted rotation only takes effect on the world tick <em>after</em> it is
 * written, so the sample for a given index is taken one tick after that index's rotation is set.
 *
 * <p>That includes index {@code 0}: the very first call into {@link #driveTick} is itself deferred
 * onto the scheduler by {@link #start}, rather than run synchronously out of command dispatch. A
 * command handler does not run at the same point in the tick that a scheduler callback does, so
 * calling {@code driveTick} directly from {@code start} let {@code input[0]}'s rotation get written
 * without a real world tick ever elapsing before the first sample — tick 0 came back as the spawn
 * frame itself (exact spawn position, zero velocity), and every later index carried the
 * <em>previous</em> index's input instead of its own. Routing the first call through
 * {@code runTask} puts it on the same heartbeat-to-heartbeat footing as every recursive call after
 * it, which is what made those later jumps correct in the first place.
 *
 * <p>The same invariant is also defended at the other end, against a different failure shape:
 * {@link #tickAndContinue} never records a sample that is bit-for-bit identical to the previous one
 * while airborne. A world tick can occasionally not reach this entity at all — observed live as a
 * brief, self-resolving stall roughly 200 ticks into a recording — and recording that repeat as if
 * it were a fresh response to the next scripted input would silently shift every later tick's input
 * by one, the same bug as the spawn-frame issue in miniature. Instead the wait is retried until a
 * genuinely different sample arrives, or {@link #STALL_TICK_LIMIT} is reached and the recording
 * aborts rather than ever writing a duplicate.
 */
public final class GliderRunner {

    private static final int SPAWN_CHUNK_RADIUS = 3;
    private static final int FOLLOW_CHUNK_RADIUS = 3;
    private static final int STALL_TICK_LIMIT = 20;
    private static final double SPAWN_HEIGHT_OFFSET = 2.0;
    private static final double WORLD_SLICE_RADIUS = 3.0;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private final Plugin plugin;
    private final Mob glider;
    private final List<Firework> activeFireworks = new ArrayList<>();

    private @Nullable GliderSample lastSample;
    private int stallTicks;
    private @Nullable Runnable onFinished;

    public GliderRunner(Plugin plugin, Location above) {
        this.plugin = plugin;
        this.glider = spawn(plugin, above);
    }

    /** The spawned glider's effective gravity attribute, for {@link TraceCollector}'s metadata. */
    public double gravity() {
        AttributeInstance instance = glider.getAttribute(Attribute.GRAVITY);
        if (instance == null) {
            throw new IllegalStateException("the spawned glider has no gravity attribute registered");
        }
        return instance.getValue();
    }

    /**
     * Drives {@code collector}'s script to completion and writes {@code traces/<profile>.json}, or
     * aborts without writing anything if the glider stops being a trustworthy source of samples.
     * {@code onFinished} runs exactly once, on every terminal path — success, a write failure, or an
     * abort — so a caller can use it to release a "one recording at a time" guard.
     */
    public void start(TraceCollector collector, String profile, CommandSender sender, Runnable onFinished) {
        this.onFinished = onFinished;
        // Deferred rather than called directly: see the class Javadoc on why the first tick needs
        // the same heartbeat alignment every later recursive call already has.
        plugin.getServer().getScheduler().runTask(plugin, () -> driveTick(collector, profile, sender));
    }

    private static Mob spawn(Plugin plugin, Location above) {
        Location spawnLocation = above.clone().add(0.0, SPAWN_HEIGHT_OFFSET, 0.0);
        loadChunksAround(plugin, spawnLocation, SPAWN_CHUNK_RADIUS);
        Zombie zombie = spawnLocation.getWorld().spawn(spawnLocation, Zombie.class, entity -> {
            entity.getEquipment().setChestplate(new ItemStack(Material.ELYTRA));
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setPersistent(true);
            // The Task 1 spike found setAI(false) stops the glide entirely while isGliding() still
            // reports true; setAware(false) is the switch that keeps travel() running while
            // suppressing the look control that would otherwise overwrite scripted rotation.
            entity.setAware(false);
        });
        zombie.setGliding(true);
        return zombie;
    }

    /**
     * Force-loads a {@code chunkRadius}-chunk square around {@code center}. Called once at spawn
     * with a small radius, and again every tick around the glider's current position — a corridor
     * that follows the flight rather than a fixed area picked in advance, since a long or boosted
     * profile can travel further than any one guessed radius comfortably covers.
     */
    private static void loadChunksAround(Plugin plugin, Location center, int chunkRadius) {
        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                world.addPluginChunkTicket(centerChunkX + dx, centerChunkZ + dz, plugin);
            }
        }
    }

    private void driveTick(TraceCollector collector, String profile, CommandSender sender) {
        loadChunksAround(plugin, glider.getLocation(), FOLLOW_CHUNK_RADIUS);
        ScriptedInput input = collector.nextInput();
        // Rotation is written now, for the world tick that runs after this method returns — not
        // read back until the sample taken one tick later, per the spike's write/read offset.
        glider.setRotation(input.yaw(), input.pitch());
        if (input.igniteFirework()) {
            igniteFirework();
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> tickAndContinue(collector, profile, sender), 1L);
    }

    private void tickAndContinue(TraceCollector collector, String profile, CommandSender sender) {
        try {
            if (!glider.isValid()) {
                abort("the glider is no longer valid (removed, killed, or despawned)", sender);
                return;
            }
            GliderSample sample = sampleGlider();
            if (sample.equals(lastSample) && !sample.onGround()) {
                // The exact same state as last tick, while airborne, means no world tick actually
                // reached this entity between the two samples — observed live as a brief (roughly
                // 5-6 tick) stall a couple of hundred ticks into a recording, self-resolving on its
                // own. Retrying the wait rather than recording this repeat is what keeps the
                // invariant intact: every recorded tick is a genuinely fresh response to its input,
                // never a duplicate standing in for one. A tick that never arrives at all — the
                // entity killed, despawned, or stuck outside a loaded chunk — still aborts once
                // stallTicks crosses the limit below, rather than retrying forever.
                stallTicks++;
                if (stallTicks >= STALL_TICK_LIMIT) {
                    abort(("the glider reported the exact same state for %s consecutive ticks while not "
                            + "on the ground — treated as frozen rather than genuinely airborne and "
                            + "motionless, which real elytra flight does not do")
                            .formatted(stallTicks),
                            sender);
                    return;
                }
                plugin.getServer().getScheduler()
                        .runTaskLater(plugin, () -> tickAndContinue(collector, profile, sender), 1L);
                return;
            }
            stallTicks = 0;
            lastSample = sample;
            collector.record(sample);

            if (collector.isComplete()) {
                finish(collector, profile, sender);
            } else {
                driveTick(collector, profile, sender);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Recording of '%s' failed".formatted(profile), e);
            sender.sendMessage("Recording failed: %s".formatted(e.getMessage()));
            cleanUp();
        }
    }

    private void igniteFirework() {
        Location location = glider.getLocation();
        Firework firework = location.getWorld().spawn(location, Firework.class, f -> {
            f.setAttachedTo(glider);
            f.setShotAtAngle(true);
        });
        // Appended, not overwritten: a second ignition while an earlier rocket is still burning must
        // not drop it from the boost accounting below, or a chained-boosts profile would only ever
        // report the newest firework while the recorded velocity reflects both.
        activeFireworks.add(firework);
    }

    private GliderSample sampleGlider() {
        Location location = glider.getLocation();
        Vector velocity = glider.getVelocity();

        activeFireworks.removeIf(firework -> !firework.isValid() || firework.isDead());
        boolean boostActive = !activeFireworks.isEmpty();
        int ticksRemaining = 0;
        for (Firework firework : activeFireworks) {
            // No Math.max(0, …) floor here: every firework in this list just passed the isValid/
            // isDead filter above, so its own remaining-tick count is a Vanilla invariant, not a
            // reading that needs coercing into something plausible. If it ever goes negative, that
            // is a real bug and GliderSample's constructor below is meant to catch it, not have it
            // silently rewritten to zero first.
            ticksRemaining = Math.max(ticksRemaining, firework.getTicksToDetonate() - firework.getTicksFlown());
        }

        return new GliderSample(
                location.getX(), location.getY(), location.getZ(),
                velocity.getX(), velocity.getY(), velocity.getZ(),
                location.getYaw(), location.getPitch(),
                glider.isOnGround(),
                boostActive,
                ticksRemaining);
    }

    private void finish(TraceCollector collector, String profile, CommandSender sender) {
        try {
            BukkitSolidBlockSource source = new BukkitSolidBlockSource(glider.getWorld());
            TraceFile trace = collector.finish(source, WORLD_SLICE_RADIUS);

            Path output = plugin.getDataFolder().toPath().resolve("traces").resolve("%s.json".formatted(profile));
            try {
                Files.createDirectories(output.getParent());
                Files.writeString(output, GSON.toJson(trace));
            } catch (IOException e) {
                sender.sendMessage("Recorded %s ticks but failed to write %s: %s"
                        .formatted(trace.ticks().size(), output, e.getMessage()));
                return;
            }
            sender.sendMessage("Wrote %s tick(s) to %s".formatted(trace.ticks().size(), output));
        } finally {
            // finally, not a call at the end of each branch above: collector.finish() itself can
            // throw, and a throw from there must not leave a setPersistent(true) zombie and a pile
            // of chunk tickets behind just because it happened before the cleanup line.
            cleanUp();
        }
    }

    private void abort(String reason, CommandSender sender) {
        String message = "Recording aborted: %s".formatted(reason);
        sender.sendMessage(message);
        plugin.getLogger().warning(message);
        cleanUp();
    }

    private void cleanUp() {
        glider.remove();
        glider.getWorld().removePluginChunkTickets(plugin);
        Runnable callback = onFinished;
        onFinished = null;
        if (callback != null) {
            callback.run();
        }
    }
}
