package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.VelocityExit;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.minestom.server.coordinate.Pos;
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
 * themselves, as they did in the tree being replaced. The server does not start the glide at GO and does
 * not push the racer off the spawn: the gliding flag and the velocity are the client's, as in
 * Vanilla. The flag is server-side entity metadata and the client honours it for its own player, so
 * the server sees the deployment through {@code isFlyingWithElytra()} and the race counts it.
 *
 * <p>A checkpoint reset is the one place the server sets the flag itself: {@link #place} starts the glide
 * of a racer it puts back behind a checkpoint, with no impulse, so the racer does not have to jump and
 * deploy again. A spawn reset does not glide, and the racer takes off as at GO.
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
 * effects are the one velocity that is not a zero. They are written by the cup's ring step through the same
 * {@code VelocityExit}, for the same reason a zero is: the server overrides a client-authoritative flight at one
 * named moment, rather than steering it.
 *
 * <p>The firework boost is <em>not</em> one of them. A rocket is a real entity the client boosts itself
 * with — see {@link Rockets} — so nothing about it passes through {@code VelocityExit}.
 */
@ApiStatus.Internal
public abstract class Racers {

    /** How far a held racer may drift from the point they are held at before they are returned to it, in blocks. */
    private static final double PIN_TOLERANCE_BLOCKS = 0.1;

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
     *
     * <p>The count is the map's {@code rocketsPerRacer}, which the stack is never spent from; see {@link Rockets#item}.
     *
     * @param config the tuning of the map being raced
     */
    public static void equip(Player player, BoostConfig config) {
        player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.ELYTRA));
        player.getInventory().setItemStack(0, Rockets.item(config));
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
        equip(player, map.boostConfig());
        player.lookAt(Vectors.toMinestom(map.rings().getFirst().center()));
    }

    /**
     * Places a reset racer: gives them their equipment, turns them toward {@code toward}, and sets whether they
     * are gliding.
     *
     * <p>Called by a course reset after the racer has been moved to the target. A checkpoint reset places the
     * racer gliding, with no impulse: the glide is the server's flag, which the client honours for its own
     * player. A spawn reset places the racer on foot, as GO does, so the racer deploys the elytra to take off.
     * No velocity is sent here; {@code RunResetter} zeroes it through {@code VelocityExit}.
     *
     * @param toward a point the racer faces, one step along the flow normal of the checkpoint they are placed
     *     at, or the first ring's centre for a spawn
     * @param config the tuning of the map being raced, so a reset gives the map's count of rockets again
     * @param gliding whether the racer is placed gliding
     */
    public static void place(Player player, Vec3 toward, BoostConfig config, boolean gliding) {
        equip(player, config);
        player.lookAt(Vectors.toMinestom(toward));
        player.setFlyingWithElytra(gliding);
    }

    /**
     * Ends the glide and puts the racer back in a state a lobby can hold them in. Called when a
     * map's {@code GAME} phase ends, whether it ended on the limit or because everybody finished.
     */
    public static void standDown(Player player) {
        player.setFlyingWithElytra(false);
        VelocityExit.send(player, Vec3.ZERO);
    }

    /**
     * Holds a racer at {@code at} until they are released: not gliding, and back at {@code at} with no velocity if they
     * have moved more than a tenth of a block from it.
     *
     * <p>Called every tick for a racer who has glided out and is waiting to be placed on the next map, and for a racer
     * placed on a map who waits there for GO. A jump and a deployed elytra are turned off on the same tick, so the racer
     * is not lifted off the spawn. The elytra stays in the racer's hand, as {@link #faceCourse} gave it.
     *
     * @param at where the racer is held
     */
    public static void pin(Player player, Pos at) {
        player.setFlyingWithElytra(false);
        if (player.getPosition().distance(at) > PIN_TOLERANCE_BLOCKS) {
            player.teleport(at);
            VelocityExit.send(player, Vec3.ZERO);
        }
    }
}
