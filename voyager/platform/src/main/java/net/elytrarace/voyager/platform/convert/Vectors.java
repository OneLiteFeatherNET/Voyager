package net.elytrarace.voyager.platform.convert;

import net.elytrarace.voyager.api.math.Vec3;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Converts a plain coordinate between {@link Vec3}, the domain's finite-only vector, and
 * Minestom's {@link Vec} / {@link Point}.
 *
 * <p>This is a coordinate conversion only — no unit scaling. Velocity carries a unit conversion on
 * top of the coordinate one (blocks per tick, the domain's unit for a simulated velocity, to blocks
 * per second, what {@code Entity#setVelocity} expects), so it does not go through here; see
 * {@link VelocityExit}, the single place voyager-platform is allowed to send a velocity to
 * Minestom.
 *
 * <p>{@link Vec3#Vec3(double, double, double) Vec3's compact constructor} rejects a non-finite
 * component, so {@link #toDomain(Point)} re-asserts finiteness on every value coming in from
 * Minestom — a {@link Point} arriving from Minestom has never been through that constructor before
 * this call.
 */
@ApiStatus.Internal
public abstract class Vectors {

    private Vectors() {
    }

    @Contract(pure = true, value = "_ -> new")
    public static Vec toMinestom(Vec3 vector) {
        return new Vec(vector.x(), vector.y(), vector.z());
    }

    @Contract(pure = true, value = "_ -> new")
    public static Vec3 toDomain(Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }
}
