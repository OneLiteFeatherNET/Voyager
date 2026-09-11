package net.elytrarace.voyager.platform.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link CollisionSpace} the simulator runs against on a live server: Vanilla's block-collision
 * iteration, rebuilt over a Minestom {@link Block.Getter}.
 *
 * <p><b>Minestom has no region query to borrow.</b> Nothing in the library turns a region into a
 * list of collision boxes; its own collision is {@code BlockCollision.sweepBlocks}, a private swept
 * AABB that collects nothing, runs from near to far along the velocity vector, and nests X outside
 * and Z inside — the reverse of Vanilla. So the iteration is written out here, which is also what
 * makes {@link CollisionSpace}'s ordering contract satisfiable at all: nothing else decides the
 * order.
 *
 * <p>Four separate things are reproduced, and each of them is a place the candidate list can go
 * wrong on its own:
 *
 * <ol>
 *   <li>The grid's bounds, {@code floor(min - 1e-7) - 1 .. floor(max + 1e-7) + 1} per axis — see
 *       {@link BlockCursor}.</li>
 *   <li>The order, {@code for z { for y { for x } } }} with X running fastest. That is
 *       {@code Cursor3D.advance()}'s {@code x = index % width; y = (index / width) % height;
 *       z = index / height} written as loops. Minestom's {@code BoundingBox.PointIterator.next()}
 *       happens to advance in the same order, but over the wrong bounds, so only the order is
 *       borrowable and it is cheaper to write than to adapt.</li>
 *   <li>The cell-class filter — see {@link #admits(int, Block)}.</li>
 *   <li>The intersection test, applied per box before it is emitted, with
 *       {@link Aabb#intersects(Aabb)}'s strict overlap. Vanilla's equivalents are
 *       {@code AABB.intersects} for the full-block fast path and
 *       {@code Shapes.joinIsNotEmpty(shape, entityShape, AND)} otherwise, both of which treat two
 *       boxes that only touch as disjoint.</li>
 * </ol>
 *
 * <p><b>Where this is not Vanilla, on purpose.</b> A Vanilla candidate is a whole
 * {@code VoxelShape}; {@link CollisionSpace} hands back {@link Aabb}s, so a block made of several
 * boxes becomes several candidates. That matters only because {@code Shapes.collide}'s
 * sub-{@code 1.0E-7} short-circuit fires once per candidate: a two-box block is one short-circuit
 * check in Vanilla and two here. Filtering per box rather than per block keeps the difference as
 * small as this interface allows — a block only contributes more than one candidate when more than
 * one of its boxes genuinely overlaps the region — and the divergence was measured rather than
 * assumed; see {@code .superpowers/sdd/2026-09-11-e4-platform-and-server/task-3-report.md}.
 *
 * <p>Two further Vanilla behaviours are deliberately absent, both because {@link CollisionSpace} is
 * defined over blocks alone: entity collision shapes, which {@code Entity.collectColliders} puts
 * <em>before</em> the block candidates, and the world border, which it puts between the two. A
 * racer that reaches a world border is out of bounds by the game's own rules long before Vanilla's
 * border collision would matter.
 */
public final class MinestomCollisionSpace implements CollisionSpace {

    private final Block.Getter blocks;

    /**
     * @param blocks the world to read; a {@code ChunkCache} over the racer's instance in production,
     *     a fake in tests. A {@code null} block — which {@code ChunkCache} returns for an unloaded
     *     chunk when it was built without a default — is skipped, matching Vanilla's
     *     {@code BlockCollisions.computeNext} skipping a cell whose chunk is {@code null}.
     */
    public MinestomCollisionSpace(Block.Getter blocks) {
        this.blocks = blocks;
    }

    @Override
    public @Unmodifiable List<Aabb> boxesIntersecting(Aabb region) {
        BlockCursor cursor = BlockCursor.over(region);
        List<Aabb> candidates = new ArrayList<>();
        for (int z = cursor.minZ(); z <= cursor.maxZ(); z++) {
            for (int y = cursor.minY(); y <= cursor.maxY(); y++) {
                for (int x = cursor.minX(); x <= cursor.maxX(); x++) {
                    // Vanilla looks the block up only after the corner is ruled out. The lookup is
                    // hinted TYPE — only the block state matters here, never its handler or NBT —
                    // and at most eight cells per query are corners, so the filter is applied in one
                    // place instead of being split for the sake of those eight.
                    @Nullable Block block = blocks.getBlock(x, y, z, Block.Getter.Condition.TYPE);
                    if (block == null || !admits(cursor.cellType(x, y, z), block)) {
                        continue;
                    }
                    for (BoundingBox box : BlockShapes.collisionBoxes(block)) {
                        Aabb candidate = translate(box, x, y, z);
                        if (region.intersects(candidate)) {
                            candidates.add(candidate);
                        }
                    }
                }
            }
        }
        return List.copyOf(candidates);
    }

    /**
     * {@code BlockCollisions.computeNext}'s cell-class filter:
     *
     * <pre>{@code
     * if (nextType != 3) {                                              // corners never
     *     ...
     *     if (... && (nextType != 1 || blockState.hasLargeCollisionShape())   // faces: large only
     *             && (nextType != 2 || blockState.is(Blocks.MOVING_PISTON)))  // edges: piston only
     * }</pre>
     *
     * <p>({@code onlySuffocatingBlocks} is dropped: it is {@code false} for every movement query —
     * only {@code Entity.isSuffocating} passes {@code true}.)
     *
     * <p><b>This filter changes no output that Minestom can currently produce, and is written out
     * anyway.</b> Two independent reasons, both checked against the whole block registry of
     * {@code 2026.08.28-26.2} rather than assumed:
     *
     * <ul>
     *   <li>A cell at a grid extreme is a full block clear of the region on that axis (see
     *       {@link BlockCursor}), so a shape contained in its own cell can never reach back in — the
     *       intersection test rejects it whatever this filter says. Only a large shape can reach,
     *       and reaching in from an edge needs two axes at once, from a corner three. Walking all
     *       {@code 32366} block states: {@code 10929} are large, and every one of them is large on
     *       exactly one axis — {@code 10921} on Y, {@code 4} on X, {@code 4} on Z. No state leaves
     *       its cell by more than {@code 0.25} horizontally or {@code 0.5} vertically. The edge and
     *       corner branches are therefore unreachable in practice, not merely untaken here.</li>
     *   <li>{@code Block.MOVING_PISTON}'s collision shape is <em>empty</em> in Minestom — the
     *       moving piston's Vanilla shape comes from its block entity, and Minestom bakes one static
     *       shape per state — so the edge exception admits a block that contributes no boxes.</li>
     * </ul>
     *
     * <p>Which is why this is a method a test can call directly rather than a condition buried in
     * the loop: the branches are real, they are part of what "Vanilla's candidate order" means, and
     * a mutation to them cannot be caught through {@link #boxesIntersecting(Aabb)}'s output.
     */
    static boolean admits(int cellType, Block block) {
        return switch (cellType) {
            case BlockCursor.TYPE_INSIDE -> true;
            case BlockCursor.TYPE_FACE -> BlockShapes.hasLargeCollisionShape(block);
            case BlockCursor.TYPE_EDGE -> block.compare(Block.MOVING_PISTON);
            default -> false;
        };
    }

    /** {@code collisionShape.move(pos)} — a cell-relative box placed at its block's coordinates. */
    private static Aabb translate(BoundingBox box, int x, int y, int z) {
        return new Aabb(
                new Vec3(box.minX() + x, box.minY() + y, box.minZ() + z),
                new Vec3(box.maxX() + x, box.maxY() + y, box.maxZ() + z));
    }
}
