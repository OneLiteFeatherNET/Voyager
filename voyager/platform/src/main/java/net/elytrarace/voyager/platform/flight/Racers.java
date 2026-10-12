package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.VelocityExit;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import org.jetbrains.annotations.ApiStatus;

/**
 * What is done to a player so that they can race: the loadout on join, the equipment while a map is
 * counted down, the release at GO, the placement after a reset, and putting them back on their feet
 * when a map ends.
 *
 * <h2>The racer takes off by themselves</h2>
 *
 * <p>Everything in {@code voyager-platform} keys off {@code isFlyingWithElytra()}. A player who
 * joins with no elytra, or who has one and never jumps and holds, produces a flight that never
 * starts and a race in which nothing happens. So the racer is given the elytra and the rockets
 * before the count ({@link #faceCourse}), and the flight starts when they deploy the elytra
 * themselves, as they did in the tree being replaced. The server does not start the glide and does
 * not push the racer off the spawn: the gliding flag and the velocity are the client's, as in
 * Vanilla. The flag is server-side entity metadata and the client honours it for its own player, so
 * the server sees the deployment through {@code isFlyingWithElytra()} and the race counts it.
 *
 * <p>The firework itself stays — {@link Rockets} is what it does now — and it is used once the
 * elytra is deployed, because a boost is refused while the racer is not gliding.
 *
 * <h2>Where a velocity may leave the server</h2>
 *
 * <p>Nothing here sends a velocity. The only two places that do are {@link #standDown} (a zero, when a
 * map ends) and {@code RunResetter} (a zero, when a racer is reset); both go through
 * {@code VelocityExit}, the only door a velocity may leave by
 * ({@code ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom}). The ring {@code BOOST}/{@code SLOW}
 * effects are the one velocity that is not a zero, and they are not built yet; when they are, they go through
 * {@code VelocityExit} for the same reason a zero does: the server is deliberately overriding a
 * client-authoritative flight at one named moment, rather than steering it.
 *
 * <p>The firework boost is <em>not</em> one of them. A rocket is a real entity the client boosts itself
 * with — see {@link Rockets} — so nothing about it passes through {@code VelocityExit}.
 */
@ApiStatus.Internal
public abstract class Racers {

    /**
     * How many rockets a racer carries. The committed course climbs from y = -62 to y = 319, so a
     * boost is not a luxury on it; it is the only way up.
     *
     * <p>A full stack rather than a budget, and it is never spent — see {@link Rockets#item(int)}.
     * What limits a racer is the map's cooldown, which is a rule the game states, rather than a count
     * that runs out at a moment nothing announced.
     */
    private static final int ROCKETS = 64;

    private Racers() {
    }

    /**
     * The loadout of a waiting racer, applied once when a player spawns.
     *
     * <p>{@code ADVENTURE} so nobody edits the racetrack, invulnerable so a crash into a wall at
     * elytra speed ends the run rather than the player — collision damage and an out-of-bounds reset
     * are E5's, and until they exist a dead racer is a racer whose run silently stops being advanced.
     *
     * <p>No elytra and no rockets: a waiting racer cannot fly off the spawn. {@link #equip} gives them
     * when a map begins.
     */
    public static void prepare(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.getInventory().clear();
    }

    /**
     * Gives the racer the elytra and the rockets of a map, in place of the nothing {@link #prepare} left them.
     */
    public static void equip(Player player) {
        player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.ELYTRA));
        player.getInventory().setItemStack(0, Rockets.item(ROCKETS));
    }

    /**
     * Takes the elytra and the rockets away again, for a racer who is waiting: on joining while no cup runs, when
     * a cup is aborted, and when a cup finishes.
     */
    public static void hold(Player player) {
        player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.AIR);
        player.getInventory().setItemStack(0, ItemStack.AIR);
    }

    /**
     * Turns {@code player} toward the map's first ring without touching their flight, and gives them their equipment.
     *
     * <p>Called when a map's start countdown begins, so the racer spends those three seconds looking
     * at the course they are about to fly rather than at whatever direction the teleport left them
     * in — {@code MapDefinition.spawn()} is a {@link Vec3} with no yaw, so that direction is north
     * whichever way the course runs. The elytra is given here so the racer sees it in hand during the
     * count, and it is in hand at GO, when the racer is released.
     */
    public static void faceCourse(Player player, MapDefinition map) {
        equip(player);
        player.lookAt(Vectors.toMinestom(map.rings().getFirst().center()));
    }

    /**
     * Places a reset racer: gives them their equipment, turns them toward {@code toward}, and ends any glide
     * they were in.
     *
     * <p>Called by a course reset after the racer has been moved to the target. The racer is not gliding
     * afterwards, whatever they were doing before: a reset out of bounds in mid-flight ends the glide, and
     * the racer deploys the elytra again to take off. No velocity is sent here; {@code RunResetter} zeroes it
     * through {@code VelocityExit}.
     *
     * @param toward a point the racer faces, one step along the flow normal of the ring they are placed at
     */
    public static void place(Player player, Vec3 toward) {
        equip(player);
        player.lookAt(Vectors.toMinestom(toward));
        player.setFlyingWithElytra(false);
    }

    /**
     * Ends the glide and puts the racer back in a state a lobby can hold them in. Called when a
     * map's {@code GAME} phase ends, whether it ended on the limit or because everybody finished.
     */
    public static void standDown(Player player) {
        player.setFlyingWithElytra(false);
        VelocityExit.send(player, Vec3.ZERO);
    }
}
