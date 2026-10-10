package net.elytrarace.voyager.platform.collision;

import net.elytrarace.voyager.platform.collision.exception.UnsupportedBlockShapeException;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * The two facts {@link MinestomCollisionSpace} needs about a block's collision shape, and the only
 * place in this repository that reaches past Minestom's {@code Shape} interface.
 *
 * <p><b>Why a cast is unavoidable.</b> {@code Shape} declares seven methods — {@code isOccluded},
 * {@code isFaceFull}, {@code intersectBox}, {@code intersectBoxSwept}, {@code intersectEntity},
 * {@code relativeStart} and {@code relativeEnd} — and none of them hands back the boxes a shape is
 * made of. {@code boundingBoxes()} exists only on the {@code ShapeImpl} record. Every block in the
 * registry of Minestom {@code 2026.08.28-26.2} carries a {@code ShapeImpl} at runtime, so the cast
 * always succeeds today; it is still an implementation type, which is why it lives behind
 * {@link #collisionBoxes(Block)} alone. A Minestom upgrade that changes this breaks here and
 * nowhere else, as a named {@link UnsupportedBlockShapeException}.
 */
@ApiStatus.Internal
public abstract class BlockShapes {

    private BlockShapes() {
    }

    /**
     * The boxes making up {@code block}'s collision shape, in registry order, each relative to its
     * own block cell. An empty list for a block without collision, air included.
     *
     * <p>The order is Minestom's, taken from the order the boxes appear in the block registry's
     * shape string. Vanilla has no counterpart to compare it against: a Vanilla collision candidate
     * is a whole {@code VoxelShape}, iterated as a voxel bitset, and the decomposition into boxes
     * never surfaces there. The order therefore only has to be <em>stable</em>, which a registry
     * order is; see {@link MinestomCollisionSpace}'s note on flattening for what does not follow
     * from that.
     */
    @Contract(pure = true)
    public static @Unmodifiable List<BoundingBox> collisionBoxes(Block block) {
        Shape shape = block.collisionShape();
        if (!(shape instanceof ShapeImpl impl)) {
            throw new UnsupportedBlockShapeException(block.key().asString(), shape.getClass().getName());
        }
        return impl.boundingBoxes();
    }

    /**
     * Vanilla's {@code BlockState.hasLargeCollisionShape()}: whether the collision shape leaves the
     * unit cell on any axis.
     *
     * <p>Minestom has no such property — {@code largeCollision} and {@code hasLarge} have zero
     * matches across its sources — so it is recomputed here. The formula is transcribed verbatim
     * from where Vanilla caches it, {@code BlockBehaviour.BlockStateBase.Cache}'s constructor:
     *
     * <pre>{@code
     * this.largeCollisionShape = Arrays.stream(Direction.Axis.values())
     *         .anyMatch(dir -> this.collisionShape.min(dir) < 0.0 || this.collisionShape.max(dir) > 1.0);
     * }</pre>
     *
     * <p>{@code collisionShape.min(axis)} / {@code max(axis)} are the shape's overall bounds, which
     * is exactly what Minestom's {@code relativeStart()} / {@code relativeEnd()} hold.
     * {@code ShapeImpl} seeds that reduction at {@code min = 1} and {@code max = 0}, so a
     * {@code relativeStart} is never above {@code 1} and a {@code relativeEnd} never below
     * {@code 0} — neither clamp can change the outcome of a {@code < 0} or {@code > 1} test, and
     * the two agree on an empty shape as well ({@code (0,0)} here, {@code (+inf, -inf)} in Vanilla,
     * both {@code false}).
     *
     * <p><b>Where it is an approximation rather than a translation.</b> The formula matches; the
     * shape it is applied to is a different object. Vanilla computes the cached flag from
     * {@code block.getCollisionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO,
     * CollisionContext.empty())} — a context-free shape that a later, entity-aware
     * {@code getCollisionShape} call may not equal. Minestom bakes one static shape per block state
     * into its registry and has no context at all, so a block whose Vanilla collision shape varies
     * with the querying entity is answered here from the static shape. No block Voyager races over
     * is such a block, and Minestom could not model one anyway.
     */
    @Contract(pure = true)
    public static boolean hasLargeCollisionShape(Block block) {
        Shape shape = block.collisionShape();
        Point start = shape.relativeStart();
        Point end = shape.relativeEnd();
        return start.x() < 0.0 || start.y() < 0.0 || start.z() < 0.0
                || end.x() > 1.0 || end.y() > 1.0 || end.z() > 1.0;
    }
}
