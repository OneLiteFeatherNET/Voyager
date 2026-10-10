package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.convert.VelocityExit;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import org.jetbrains.annotations.ApiStatus;

/**
 * What is done to a player so that they can race: the loadout on join, the launch when a map starts,
 * and putting them back on their feet when it ends.
 *
 * <h2>The problem this class exists to solve</h2>
 *
 * <p>Everything in {@code voyager-platform} keys off {@code isFlyingWithElytra()}. A player who
 * joins with no elytra, or who has one and never jumps and holds, produces a flight that never
 * starts and a race in which nothing happens — no ring is ever crossed, no run ever finishes, and
 * the failure looks exactly like a broken collision check. The tree being replaced handed out an
 * elytra and a firework and left the take-off to the player; that is the trick this removes. The
 * firework itself stays — {@link Rockets} is what it does now — but it is the way up a course, not
 * the way into the air.
 *
 * <p>So the server starts the glide. At the first tick of a map's {@code GAME} phase every racer is
 * already standing on the map's spawn (that is {@code MapTransition}'s work), and
 * {@link #launch(Player, MapDefinition)} turns them toward the first ring, sets the gliding flag, and
 * gives them the one impulse that gets them off the ground. The flag is server-side entity metadata
 * and the vanilla client honours it for its own player, which is what makes a server-started glide
 * possible at all.
 *
 * <h2>The launch numbers are seeds, not measurements</h2>
 *
 * <p>{@link #LAUNCH_FORWARD_PER_TICK} and {@link #LAUNCH_UP_PER_TICK} have never been flown by a real
 * client — there is no client in a test environment, and no acceptance run has happened yet. They are
 * named constants in one place precisely so that the first person to fly the course can change two
 * numbers rather than hunt through a launch routine. Task 10's procedure is where they get their
 * first reading.
 *
 * <p>The impulse goes out through {@code VelocityExit}, which is the only door a velocity may leave
 * by ({@code ApiPurityTest.onlyVelocityExitSendsAVelocityToMinestom}). It is one of the external
 * forces that class enumerates, alongside the ring {@code BOOST}/{@code SLOW} effects and the
 * out-of-bounds reset, and it is one for the same reason they are: the server is deliberately
 * overriding a client-authoritative flight, at one named moment, rather than steering it.
 *
 * <p>The firework boost is <em>not</em> one of them, and this is the class whose javadoc used to say
 * it was. A rocket is a real entity the client boosts itself with — see {@link Rockets} — so nothing
 * about it passes through {@code VelocityExit}.
 */
@ApiStatus.Internal
public abstract class Racers {

    /** Horizontal launch speed, in blocks per tick, along the line from the spawn to the first ring. */
    public static final double LAUNCH_FORWARD_PER_TICK = 0.8;

    /** Upward launch speed, in blocks per tick — enough air under the racer for a glide to take. */
    public static final double LAUNCH_UP_PER_TICK = 1.2;

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
     * The loadout, applied once when a player spawns.
     *
     * <p>{@code ADVENTURE} so nobody edits the racetrack, invulnerable so a crash into a wall at
     * elytra speed ends the run rather than the player — collision damage and an out-of-bounds reset
     * are E5's, and until they exist a dead racer is a racer whose run silently stops being advanced.
     */
    public static void prepare(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.getInventory().clear();
        player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.ELYTRA));
        player.getInventory().setItemStack(0, Rockets.item(ROCKETS));
    }

    /**
     * Turns {@code player} toward the map's first ring without touching their flight.
     *
     * <p>Called when a map's start countdown begins, so the racer spends those three seconds looking
     * at the course they are about to fly rather than at whatever direction the teleport left them
     * in — {@code MapDefinition.spawn()} is a {@link Vec3} with no yaw, so that direction is north
     * whichever way the course runs.
     */
    public static void faceCourse(Player player, MapDefinition map) {
        player.lookAt(Vectors.toMinestom(map.rings().getFirst().center()));
    }

    /**
     * Turns {@code player} toward the map's first ring, starts the glide and launches them.
     *
     * <p>The facing comes from the ring data rather than from the spawn, because
     * {@code MapDefinition.spawn()} is a {@link Vec3} and carries no yaw — a racer moved by
     * {@code MapTransition} arrives looking north whatever direction the course runs. Ring 0's centre
     * is the direction the course is entered in, and for the committed map it is the direction the
     * authored spawn already agreed with.
     */
    public static void launch(Player player, MapDefinition map) {
        Ring first = map.rings().getFirst();
        player.lookAt(Vectors.toMinestom(first.center()));
        player.setFlyingWithElytra(true);
        VelocityExit.send(player, launchVelocity(map.spawn(), first));
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
     * The launch impulse: {@link #LAUNCH_FORWARD_PER_TICK} along the horizontal line from the spawn
     * to the first ring, plus {@link #LAUNCH_UP_PER_TICK} straight up.
     *
     * <p>Horizontal, not straight at the ring: the first ring can be well above or below the spawn,
     * and a launch that aimed the whole impulse at it would fire a racer at the floor on a course
     * that starts with a drop. The climb is the elytra's job and the rocket's.
     *
     * <p>A first ring directly above the spawn leaves no horizontal direction to take. That is a
     * launch straight up, which is the honest answer for that geometry, rather than a fabricated
     * compass bearing.
     */
    private static Vec3 launchVelocity(Vec3 spawn, Ring first) {
        Vec3 toRing = first.center().minus(spawn);
        Vec3 horizontal = new Vec3(toRing.x(), 0, toRing.z());
        double length = horizontal.length();
        Vec3 forward = length == 0.0 ? Vec3.ZERO : horizontal.scale(LAUNCH_FORWARD_PER_TICK / length);
        return new Vec3(forward.x(), LAUNCH_UP_PER_TICK, forward.z());
    }
}
