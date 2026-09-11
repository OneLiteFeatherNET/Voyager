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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Spawns a scripted glider above the command's initiator, drives it tick by tick from a
 * {@link TraceCollector}, and writes the finished recording.
 *
 * <p>Every rule about what makes a recording valid lives in {@link TraceCollector} and the rest of
 * the server-free core; this class only exists to reach a live entity from Bukkit — spawn, drive,
 * sample, hand off. It applies the AI switch the Task 1 spike established: {@code setAware(false)}
 * keeps the glide running while suppressing look-control, and a scripted rotation only takes effect
 * on the world tick <em>after</em> it is written.
 *
 * <p><b>How a tick is measured.</b> Every sample carries {@code glider.getTicksLived()} — the
 * entity's own tick counter, not {@code Bukkit.getCurrentTick()}. The two are not interchangeable:
 * the Bukkit scheduler is driven by the same server tick loop that ticks the world, so exactly one
 * world tick elapses between the heartbeat that schedules a {@code runTaskLater(…, 1L)} callback and
 * the heartbeat that runs it, unconditionally — a GC pause can delay that in wall-clock time, it
 * cannot remove it. The server's global tick counter would therefore always show a delta of exactly
 * 1 between two consecutive samples, regardless of whether <em>this</em> entity was actually ticked
 * in between (its chunk falling out of the entity-ticking level is the most likely way for that to
 * happen), so it cannot be the check. {@link TraceCollector#record} instead refuses to accept two
 * consecutively recorded samples whose {@code entityTick} is not exactly one apart — a check that
 * holds regardless of what this class's own retry logic decides.
 *
 * <p><b>{@code getTicksLived()} alone is not enough, measured live, not assumed.</b> That counter
 * increments in {@code Entity#baseTick()}, which runs independently of
 * {@code LivingEntity#travel()} — a recorded 200-tick trace showed {@code entityTick} climbing
 * 199 → 200 → 201 while position and velocity held bit-for-bit identical across all three. The
 * counter proves the entity's base tick ran, not that its fall-flying movement specifically did.
 * {@link #isStalled} therefore also compares position and velocity — never rotation, which a
 * changed scripted rotation could move without a world tick doing anything, the exact blind spot a
 * same-sample equality check had — between the sample about to be recorded and the last one that
 * was. {@link #tickAndContinue} retries the wait, bounded by {@link #STALL_TICK_LIMIT}, whenever
 * either signal says the entity was not really ticked.
 *
 * <p><b>Why tick 0 is not the first tick after spawn.</b> A one-step residual check against the
 * ported physics simulator (PR #238) found the formula reproduces every recorded transition from
 * tick 1 onward bit-for-bit, but not tick 0 → tick 1: the very first {@code travelFallFlying} call
 * from a freshly spawned, zero-velocity entity is not the same computation as a later one, in a way
 * the formula itself does not special-case but Vanilla's surrounding state evidently does. {@link
 * #start} therefore lets the glider fly under {@code input[0]}'s rotation for {@link #SETTLE_TICKS}
 * real, unrecorded ticks before {@link #driveTick} for index 0 ever runs — establishing steady
 * gliding before the trace begins, rather than discarding the first recorded tick after the fact,
 * which would only hide the transient instead of recording past it.
 */
public final class GliderRunner {

    private static final int SPAWN_CHUNK_RADIUS = 3;
    private static final int FOLLOW_CHUNK_RADIUS = 6;
    private static final int STALL_TICK_LIMIT = 30;
    private static final int SETTLE_TICKS = 2;
    private static final double SPAWN_HEIGHT_OFFSET = 2.0;
    private static final double WORLD_SLICE_RADIUS = 3.0;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private final Plugin plugin;
    private final Mob glider;
    private final List<Firework> activeFireworks = new ArrayList<>();

    private @Nullable GliderSample lastRecordedSample;
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
     * Removes the spawned glider and releases its chunk tickets without ever having started a
     * recording. For a failure between construction and {@link #start} — {@link #onFinished} is not
     * set yet at that point, so this must not (and does not) go through it.
     */
    public void disposeWithoutStarting() {
        cleanUp();
    }

    /**
     * Drives {@code collector}'s script to completion and writes {@code traces/<profile>.json}, or
     * aborts without writing anything if the glider stops being a trustworthy source of samples.
     * {@code onFinished} runs exactly once, on every terminal path — success, a write failure, or an
     * abort — so a caller can use it to release a "one recording at a time" guard.
     */
    public void start(TraceCollector collector, String profile, CommandSender sender, Runnable onFinished) {
        this.onFinished = onFinished;
        try {
            ScriptedInput firstInput = collector.nextInput();
            glider.setRotation(firstInput.yaw(), firstInput.pitch());
            settle(collector, profile, sender, SETTLE_TICKS);
        } catch (RuntimeException e) {
            handleFailure(profile, sender, e);
        }
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
     * profile can travel further than any one guessed radius comfortably covers. The follow radius
     * is deliberately larger than the spawn radius: a glider diving at a few blocks per tick can
     * reach the edge of a too-small trailing corridor in well under twenty ticks.
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

    /**
     * Lets the glider fly under whatever rotation is already set for {@link #SETTLE_TICKS} real
     * ticks before any recording begins. See the class Javadoc for why tick 0 needs this.
     */
    private void settle(TraceCollector collector, String profile, CommandSender sender, int remaining) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (!glider.isValid()) {
                    abort("the glider is no longer valid (removed, killed, or despawned)", sender);
                    return;
                }
                if (remaining > 1) {
                    settle(collector, profile, sender, remaining - 1);
                } else {
                    driveTick(collector, profile, sender);
                }
            } catch (RuntimeException e) {
                handleFailure(profile, sender, e);
            }
        }, 1L);
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
            int entityTick = glider.getTicksLived();
            GliderSample sample = sampleGlider(entityTick);
            if (isStalled(sample, entityTick)) {
                stallTicks++;
                if (stallTicks >= STALL_TICK_LIMIT) {
                    abort(("the glider has not produced a fresh sample for %s consecutive world ticks "
                            + "(stuck at entity tick %s) — treated as not being ticked, most likely "
                            + "outside the force-loaded corridor, rather than genuinely idle")
                            .formatted(stallTicks, entityTick),
                            sender);
                    return;
                }
                plugin.getServer().getScheduler()
                        .runTaskLater(plugin, () -> tickAndContinue(collector, profile, sender), 1L);
                return;
            }
            stallTicks = 0;
            lastRecordedSample = sample;
            collector.record(sample);

            if (collector.isComplete()) {
                finish(collector, profile, sender);
            } else {
                driveTick(collector, profile, sender);
            }
        } catch (RuntimeException e) {
            handleFailure(profile, sender, e);
        }
    }

    /**
     * Whether {@code sample} is not a trustworthy response to the tick's input: either the entity's
     * own tick counter did not advance since the last recorded sample, or — measured live, and the
     * reason this is two checks and not one — the counter <em>did</em> advance while position and
     * velocity stayed bit-for-bit identical anyway. {@code Entity#getTicksLived()} increments in
     * {@code Entity#baseTick()}, a step that runs independently of {@code LivingEntity#travel()};
     * a live 200-tick recording showed exactly this — {@code entityTick} climbing 199, 200, 201 while
     * position and velocity held at the same values for all three — so the tick counter proves the
     * entity's base tick ran, not that its fall-flying movement specifically did. Comparing position
     * and velocity only (never rotation, never the firework flags) is what keeps this from
     * reintroducing the equality bug a rotation change would otherwise hide: those two fields are
     * exactly the ones a stalled {@code travel()} cannot have changed, and exactly the ones a changed
     * scripted rotation cannot affect on its own.
     *
     * <p>Landed gliders are exempt (a resting glider is supposed to stop changing) — the same
     * exemption the review found adequate for {@code landing}, with the same known residual: a
     * glider wedged against geometry while still airborne (only the {@code wall-graze} profile does
     * this deliberately) could in principle repeat legitimately. Low probability, and not one this
     * round's live recordings exercised.
     */
    private boolean isStalled(GliderSample sample, int entityTick) {
        if (lastRecordedSample == null || sample.onGround()) {
            return false;
        }
        boolean entityTickStalled = entityTick == lastRecordedSample.entityTick();
        boolean physicsStalled = sample.posX() == lastRecordedSample.posX()
                && sample.posY() == lastRecordedSample.posY()
                && sample.posZ() == lastRecordedSample.posZ()
                && sample.velX() == lastRecordedSample.velX()
                && sample.velY() == lastRecordedSample.velY()
                && sample.velZ() == lastRecordedSample.velZ();
        return entityTickStalled || physicsStalled;
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

    private GliderSample sampleGlider(int entityTick) {
        Location location = glider.getLocation();
        Vector velocity = glider.getVelocity();

        activeFireworks.removeIf(firework -> !firework.isValid() || firework.isDead());
        // No seed-at-zero fold: 0 is not a floor here, it is only the value reported when the list
        // is empty (boostActive is false in that case too, so the number is moot). Every firework
        // still in the list just passed the isValid/isDead filter above, so its own remaining-tick
        // count is a Vanilla invariant, not a reading that needs coercing into something plausible
        // — if one ever goes negative regardless, .max() carries that raw value through instead of
        // a Math.max(0, …) fold silently flooring it, so GliderSample's own guard below can catch it.
        int ticksRemaining = activeFireworks.stream()
                .mapToInt(firework -> firework.getTicksToDetonate() - firework.getTicksFlown())
                .max()
                .orElse(0);

        return new GliderSample(
                location.getX(), location.getY(), location.getZ(),
                velocity.getX(), velocity.getY(), velocity.getZ(),
                location.getYaw(), location.getPitch(),
                glider.isOnGround(),
                !activeFireworks.isEmpty(),
                ticksRemaining,
                entityTick);
    }

    private void finish(TraceCollector collector, String profile, CommandSender sender) {
        try {
            BukkitSolidBlockSource source = new BukkitSolidBlockSource(glider.getWorld());
            TraceFile trace = collector.finish(source, WORLD_SLICE_RADIUS);

            Path output = plugin.getDataFolder().toPath().resolve("traces").resolve("%s.json".formatted(profile));
            // Written to a sibling temp file and moved into place, not written directly: nine
            // profiles are recorded one command at a time over this module's lifetime, and a
            // re-recording that fails partway through must not truncate the previously good
            // fixture of the same profile. Files.writeString(output, …) alone would do exactly that.
            Path tempFile = output.resolveSibling("%s.tmp".formatted(output.getFileName()));
            try {
                Files.createDirectories(output.getParent());
                Files.writeString(tempFile, GSON.toJson(trace));
                Files.move(tempFile, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupFailure) {
                    plugin.getLogger().log(Level.WARNING, "Failed to clean up %s".formatted(tempFile), cleanupFailure);
                }
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

    private void handleFailure(String profile, CommandSender sender, RuntimeException e) {
        plugin.getLogger().log(Level.SEVERE, "Recording of '%s' failed".formatted(profile), e);
        sender.sendMessage("Recording failed: %s".formatted(e.getMessage()));
        cleanUp();
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
