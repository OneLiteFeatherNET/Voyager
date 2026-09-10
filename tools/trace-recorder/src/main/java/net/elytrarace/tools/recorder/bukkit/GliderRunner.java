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
 */
public final class GliderRunner {

    private static final int CHUNK_RADIUS = 12;
    private static final double SPAWN_HEIGHT_OFFSET = 2.0;
    private static final double WORLD_SLICE_RADIUS = 3.0;
    private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private final Plugin plugin;
    private final Mob glider;

    private @Nullable Firework activeFirework;

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

    /** Drives {@code collector}'s script to completion, then writes {@code traces/<profile>.json}. */
    public void start(TraceCollector collector, String profile, CommandSender sender) {
        driveTick(collector, profile, sender);
    }

    private static Mob spawn(Plugin plugin, Location above) {
        Location spawnLocation = above.clone().add(0.0, SPAWN_HEIGHT_OFFSET, 0.0);
        forceLoadCorridor(plugin, spawnLocation);
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

    private static void forceLoadCorridor(Plugin plugin, Location center) {
        World world = center.getWorld();
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                world.addPluginChunkTicket(centerChunkX + dx, centerChunkZ + dz, plugin);
            }
        }
    }

    private void driveTick(TraceCollector collector, String profile, CommandSender sender) {
        ScriptedInput input = collector.nextInput();
        // Rotation is written now, for the world tick that runs after this method returns — not
        // read back until the sample taken one tick later, per the spike's write/read offset.
        glider.setRotation(input.yaw(), input.pitch());
        if (input.igniteFirework()) {
            igniteFirework();
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            recordSample(collector);
            if (collector.isComplete()) {
                finish(collector, profile, sender);
            } else {
                driveTick(collector, profile, sender);
            }
        }, 1L);
    }

    private void igniteFirework() {
        Location location = glider.getLocation();
        Firework firework = location.getWorld().spawn(location, Firework.class, f -> {
            f.setAttachedTo(glider);
            f.setShotAtAngle(true);
        });
        this.activeFirework = firework;
    }

    private void recordSample(TraceCollector collector) {
        Location location = glider.getLocation();
        Vector velocity = glider.getVelocity();
        boolean boostActive = activeFirework != null && activeFirework.isValid() && !activeFirework.isDead();
        int ticksRemaining = boostActive
                ? Math.max(0, activeFirework.getTicksToDetonate() - activeFirework.getTicksFlown())
                : 0;
        if (!boostActive) {
            activeFirework = null;
        }
        GliderSample sample = new GliderSample(
                location.getX(), location.getY(), location.getZ(),
                velocity.getX(), velocity.getY(), velocity.getZ(),
                location.getYaw(), location.getPitch(),
                glider.isOnGround(),
                boostActive,
                ticksRemaining);
        collector.record(sample);
    }

    private void finish(TraceCollector collector, String profile, CommandSender sender) {
        BukkitSolidBlockSource source = new BukkitSolidBlockSource(glider.getWorld());
        TraceFile trace = collector.finish(source, WORLD_SLICE_RADIUS);

        Path output = plugin.getDataFolder().toPath().resolve("traces").resolve("%s.json".formatted(profile));
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, GSON.toJson(trace));
        } catch (IOException e) {
            sender.sendMessage("Recorded %s ticks but failed to write %s: %s"
                    .formatted(trace.ticks().size(), output, e.getMessage()));
            cleanUp();
            return;
        }

        cleanUp();
        sender.sendMessage("Wrote %s tick(s) to %s".formatted(trace.ticks().size(), output));
    }

    private void cleanUp() {
        glider.remove();
        glider.getWorld().removePluginChunkTickets(plugin);
    }
}
