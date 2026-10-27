package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.minestom.server.coordinate.Pos;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Where a racer stands at the start of a map, facing the first ring.
 *
 * <p>{@code MapDefinition.spawn()} is a position with no facing, so a placement built from it alone looks north (yaw 0,
 * pitch 0). Every placement at a map start is built here instead, so the facing is the same whether the racer arrives
 * by a join, a map transition, the countdown or the GO release. The function is pure: plain numbers in, a {@link Pos}
 * out.
 */
@ApiStatus.Internal
public abstract class ArrivalFacing {

    private ArrivalFacing() {
    }

    /**
     * The spawn of {@code map}, looking at the centre of its first ring.
     *
     * @param map the course being entered; it has at least one ring
     * @return the spawn position, with the yaw and pitch that look at ring 0
     */
    @Contract(pure = true, value = "_ -> new")
    public static Pos at(MapDefinition map) {
        return at(map.spawn(), map.rings().getFirst().center());
    }

    /**
     * The position {@code spawn}, with the yaw and pitch that look at {@code firstRing}.
     *
     * <p>A ring directly above or below the spawn has no horizontal direction, so its yaw is the one Minestom's look
     * computes for a vertical look; the pitch is then straight up or down.
     *
     * @param spawn     where the racer stands
     * @param firstRing the centre of ring 0
     * @return the spawn position with the facing of ring 0
     */
    @Contract(pure = true, value = "_, _ -> new")
    public static Pos at(Vec3 spawn, Vec3 firstRing) {
        Pos facing = Vectors.toMinestom(spawn).asPos().withLookAt(Vectors.toMinestom(firstRing));
        // Adding +0.0 turns a negative zero into a positive one: a level look is pitch 0.0, which Pos equality
        // tells apart from -0.0.
        return facing.withView(facing.yaw() + 0.0f, facing.pitch() + 0.0f);
    }
}
