package net.elytrarace.setup.testfly;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active test flights. Runs a repeating task checking portal collisions.
 */
public final class TestflyManager {

    private static final long CHECK_INTERVAL_TICKS = 2L; // every 100ms

    private final Map<UUID, TestflySession> sessions = new ConcurrentHashMap<>();
    private BukkitTask task;

    public void start(Plugin plugin) {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sessions.clear();
    }

    public void addSession(TestflySession session) {
        sessions.put(session.playerId(), session);
    }

    public boolean isFlying(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Ends a test flight, restores inventory, and returns the session for results display.
     */
    public Optional<TestflySession> endFlight(UUID playerId) {
        var session = sessions.remove(playerId);
        if (session == null) return Optional.empty();

        var player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.getInventory().setContents(session.savedInventory());
            player.getInventory().setArmorContents(session.savedArmor());
        }
        return Optional.of(session);
    }

    public void remove(UUID playerId) {
        endFlight(playerId);
    }

    private void tick() {
        for (var entry : sessions.entrySet()) {
            var playerId = entry.getKey();
            var session = entry.getValue();
            var player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                sessions.remove(playerId);
                continue;
            }

            // Update position
            var loc = player.getLocation();
            session.updatePosition(loc.getX(), loc.getY(), loc.getZ());

            // Check collisions
            int hitIndex = session.checkCollisions();
            if (hitIndex >= 0) {
                player.sendActionBar(Component.translatable("testfly.portal_hit")
                        .arguments(
                                Component.text(hitIndex),
                                Component.text(session.hitCount()),
                                Component.text(session.totalPortals())
                        ));
                // Play pling sound
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            }

            // Check if complete
            if (session.isComplete()) {
                sessions.remove(playerId);
                showResults(player, session);
                // Restore inventory
                player.getInventory().setContents(session.savedInventory());
                player.getInventory().setArmorContents(session.savedArmor());
            }
        }
    }

    private void showResults(Player player, TestflySession session) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("TEST FLY RESULTS", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Time:    " + session.elapsedFormatted(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("Portals: " + session.hitCount() + "/" + session.totalPortals() + " hit",
                NamedTextColor.GREEN));

        var missed = session.missedPortalIndices();
        if (!missed.isEmpty()) {
            player.sendMessage(Component.text("Missed:  #" + String.join(", #",
                    missed.stream().map(String::valueOf).toList()), NamedTextColor.RED));
        }
        player.sendMessage(Component.empty());
    }
}
