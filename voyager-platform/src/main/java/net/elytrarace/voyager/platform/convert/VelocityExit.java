package net.elytrarace.voyager.platform.convert;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.math.exception.NonFiniteVectorException;
import net.minestom.server.ServerFlag;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/**
 * The single place voyager-platform is allowed to hand a velocity to Minestom.
 *
 * <p>Normal elytra flight is client-authoritative, matching Vanilla: the client flies, and the
 * server tracks its own copy of the physics result silently alongside it, never calling
 * {@code Player#setVelocity} for it. A velocity reaches Minestom only for an external force that
 * legitimately overrides the client's own flight. Nothing else in the rebuild converts or sends a
 * velocity; that restriction is the entire reason this is one class instead of an inline call at
 * each site — a single place to look when a velocity turns up that should not have.
 *
 * <p><strong>The list of those forces has changed twice and is written here rather than guessed.</strong>
 * It is currently three:
 *
 * <ol>
 *   <li>the launch that starts a map, which turns a standing racer into a gliding one
 *       ({@code Racers.launch});</li>
 *   <li>a ring {@code BOOST}/{@code SLOW} effect;</li>
 *   <li>an out-of-bounds reset.</li>
 * </ol>
 *
 * <p>The <strong>firework boost is deliberately not on that list</strong>, and it used to be. The
 * tree being replaced computed the boost server-side and pushed it with {@code setVelocity}; the
 * rebuild spawns a real firework rocket entity with the racer as its shooter and lets the client
 * boost itself with Vanilla's own impulse, exactly as Vanilla does ({@code Rockets} in
 * voyager-server). No velocity is sent for it at all. If a real client ever turns out not to respond
 * to the entity, this class is the sanctioned fallback and the boost becomes a fourth entry — but
 * that is a measurement nobody has taken, not a plan.
 *
 * <p>The domain simulates velocity in blocks per tick, matching Vanilla's per-tick movement
 * formulas (see voyager-physics). {@code Player#setVelocity} expects blocks per second, so
 * {@link #send(Player, Vec3)} multiplies by {@link ServerFlag#SERVER_TICKS_PER_SECOND} — never a
 * literal {@code 20} — because that flag is configurable through {@code -Dminestom.tps} and the
 * literal would silently stop matching it.
 *
 * <p>{@link Vec3} cannot hold a non-finite component — its compact constructor rejects one — but
 * the multiplication above can still turn a large, perfectly finite component into infinity, so
 * finiteness is asserted again here, after the multiplication and before the value ever reaches
 * Minestom. That second assertion runs by construction: the scaled result is built as a new
 * {@link Vec3}, so the same compact constructor that guarded the input guards the output too.
 */
@ApiStatus.Internal
public abstract class VelocityExit {

    private VelocityExit() {
    }

    /**
     * Sends {@code velocityPerTick}, converted to blocks per second, as {@code player}'s velocity.
     *
     * @throws NonFiniteVectorException if scaling {@code velocityPerTick} by
     *         {@link ServerFlag#SERVER_TICKS_PER_SECOND} produces a non-finite component; the
     *         player's velocity is left untouched when this happens.
     */
    public static void send(Player player, Vec3 velocityPerTick) {
        double factor = ServerFlag.SERVER_TICKS_PER_SECOND;
        Vec3 velocityPerSecond = new Vec3(
                velocityPerTick.x() * factor,
                velocityPerTick.y() * factor,
                velocityPerTick.z() * factor);
        player.setVelocity(Vectors.toMinestom(velocityPerSecond));
    }
}
