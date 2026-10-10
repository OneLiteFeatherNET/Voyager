package net.elytrarace.voyager.platform.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinestomCollisionSpaceTest {

    /**
     * Different on all six faces, straddling zero on x and z, and not symmetric under swapping x
     * with z — so neither a transposed loop nor a lost sign can pass unnoticed.
     *
     * <p>Grid: x {@code -3..1}, y {@code 1..4}, z {@code -4..1}.
     */
    private static final Aabb ORDER_REGION =
            new Aabb(new Vec3(-1.4, 2.3, -2.4), new Vec3(0.7, 3.1, 0.9));

    /** {@code piston_head[facing=up]} — the one shape reaching *below* its own cell, by 0.25. */
    private static final Block PISTON_HEAD_UP = Block.PISTON_HEAD.withProperty("facing", "up");

    @Test
    void candidatesComeBackInVanillasZThenYThenXOrder() {
        // Four full blocks, placed so that every one of the three possible loop transpositions
        // produces a different sequence from the one asserted:
        //   as written (z, y, x):  B, C, A, D
        //   x outermost (x, y, z): D, C, B, A
        //   x in the middle (z, x, y): B, C, D, A
        //   y outermost (y, z, x): B, A, C, D
        // No two of the four share a z, a y and an x with each other in a way that would let two of
        // those orders coincide.
        FakeBlockGetter world = FakeBlockGetter.ofAir()
                .with(0, 2, -3, Block.STONE)    // B
                .with(-1, 3, -1, Block.STONE)   // C
                .with(0, 2, 0, Block.STONE)     // A
                .with(-2, 3, 0, Block.STONE)    // D
                // In the grid but not in the region: its cell sits a full block outside on x. Two
                // independent mechanisms reject it — the face branch of the cell filter reaches it
                // first, and the intersection test would reject it too if that branch were removed.
                // Verified by mutation rather than assumed: neutering the face branch leaves this
                // assertion green, and so does dropping the intersection test.
                .with(-3, 2, -2, Block.STONE);

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(ORDER_REGION);

        assertThat(candidates).containsExactly(
                cube(0, 2, -3),
                cube(-1, 3, -1),
                cube(0, 2, 0),
                cube(-2, 3, 0));
    }

    @Test
    void theGridSpansOneBlockOfMarginOnEveryFaceAndIsWalkedXFastest() {
        // The emitted candidates only show the cells where a block happens to stand. The cells the
        // iteration actually visits are what the bounds and the nesting are made of, and an empty
        // world exposes all of them.
        FakeBlockGetter world = FakeBlockGetter.ofAir();

        new MinestomCollisionSpace(world).boxesIntersecting(ORDER_REGION);

        List<FakeBlockGetter.Cell> visited = world.visited();
        // 5 * 4 * 6: floor(min - 1e-7) - 1 .. floor(max + 1e-7) + 1 on each axis, which is two cells
        // wider per axis than Minestom's own floor(min)..floor(max).
        assertThat(visited).hasSize(120);
        assertThat(visited.getFirst()).isEqualTo(new FakeBlockGetter.Cell(-3, 1, -4));
        assertThat(visited.getLast()).isEqualTo(new FakeBlockGetter.Cell(1, 4, 1));
        // X advances first, then Y once X wraps after five cells, then Z once Y wraps after four
        // rows of five.
        assertThat(visited.get(1)).isEqualTo(new FakeBlockGetter.Cell(-2, 1, -4));
        assertThat(visited.get(5)).isEqualTo(new FakeBlockGetter.Cell(-3, 2, -4));
        assertThat(visited.get(20)).isEqualTo(new FakeBlockGetter.Cell(-3, 1, -3));
    }

    @Test
    void aBlockReachingUpFromTheMarginBelowIsIncludedFromAFaceCell() {
        // Region y starts at 2.3, so the grid runs y 1..4 and the cell at y = 1 exists only because
        // of the -1 margin. A fence post there is 1.5 blocks tall and reaches 2.5 — into the region.
        // Minestom's own floor(min)..floor(max) bounds would start at y = 2 and never look.
        Aabb region = new Aabb(new Vec3(-0.4, 2.3, 0.2), new Vec3(0.4, 3.1, 0.8));
        FakeBlockGetter world = FakeBlockGetter.ofAir().with(0, 1, 0, Block.OAK_FENCE);

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(region);

        assertThat(candidates).containsExactly(
                box(0.375, 1.0, 0.375, 0.625, 2.5, 0.625));
    }

    @Test
    void aFullBlockInThatSameFaceCellIsNotIncluded() {
        // The companion to the test above, and deliberately a weak one: a unit cube in a margin cell
        // is a full block clear of the region, so the intersection test rejects it on its own and
        // this cannot tell the face filter apart from that. Nothing can — see
        // MinestomCollisionSpace#admits. The cell-class filter is pinned in admitsOnly* below.
        Aabb region = new Aabb(new Vec3(-0.4, 2.3, 0.2), new Vec3(0.4, 3.1, 0.8));
        FakeBlockGetter world = FakeBlockGetter.ofAir().with(0, 1, 0, Block.STONE);

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(region);

        assertThat(candidates).isEmpty();
    }

    @Test
    void aBlockReachingDownFromTheMarginAboveIsIncludedFromAFaceCell() {
        // The mirror image, which the +1 margin is responsible for: region y ends at 2.9, the grid
        // runs y 1..3, and the cell at y = 3 exists only because of the margin. A piston head facing
        // up starts at y = -0.25 and so reaches down to 2.75. Only its first box does; the other
        // four sit at 3.75 and up, and are filtered out per box rather than per block.
        Aabb region = new Aabb(new Vec3(-0.4, 2.1, 0.2), new Vec3(0.7, 2.9, 0.8));
        FakeBlockGetter world = FakeBlockGetter.ofAir().with(0, 3, 0, PISTON_HEAD_UP);

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(region);

        assertThat(candidates).containsExactly(
                box(0.375, 2.75, 0.375, 0.625, 4.0, 0.625));
    }

    @Test
    void aMultiBoxBlockContributesEveryIntersectingBoxInRegistryOrder() {
        Aabb region = new Aabb(new Vec3(-0.2, 2.2, 0.1), new Vec3(0.9, 2.9, 0.4));
        FakeBlockGetter world = FakeBlockGetter.ofAir().with(0, 2, 0, Block.OAK_STAIRS);

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(region);

        // Lower half first, then the upper step — the order BlockShapes reports, asserted as a
        // sequence because MovementResolver's short-circuit consumes candidates in order.
        assertThat(candidates).containsExactly(
                box(0.0, 2.0, 0.0, 1.0, 2.5, 1.0),
                box(0.0, 2.5, 0.0, 1.0, 3.0, 0.5));
    }

    @Test
    void aMultiBoxBlockContributesOnlyTheBoxesThatIntersect() {
        // The same stairs, with the region moved to z 0.6..0.9. The lower half spans the full depth
        // and reaches it; the upper step stops at z = 0.5 and does not. A per-block filter — "any
        // box intersects, so emit them all" — would return two candidates here, and would hand
        // MovementResolver one more short-circuit check than Vanilla performs.
        Aabb region = new Aabb(new Vec3(-0.2, 2.2, 0.6), new Vec3(0.9, 2.9, 0.9));
        FakeBlockGetter world = FakeBlockGetter.ofAir().with(0, 2, 0, Block.OAK_STAIRS);

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(region);

        assertThat(candidates).containsExactly(box(0.0, 2.0, 0.0, 1.0, 2.5, 1.0));
    }

    @Test
    void anAirWorldYieldsNothing() {
        List<Aabb> candidates =
                new MinestomCollisionSpace(FakeBlockGetter.ofAir()).boxesIntersecting(ORDER_REGION);

        assertThat(candidates).isEmpty();
    }

    @Test
    void anUnloadedWorldYieldsNothingRatherThanFailing() {
        // ChunkCache hands back null for an unloaded chunk when it was built without a default
        // block. Vanilla's own iteration skips a cell whose chunk is null, so this must not throw.
        FakeBlockGetter world = FakeBlockGetter.unloaded();

        List<Aabb> candidates = new MinestomCollisionSpace(world).boxesIntersecting(ORDER_REGION);

        assertThat(candidates).isEmpty();
        assertThat(world.visited()).hasSize(120);
    }

    @Test
    void admitsEveryBlockInsideTheGrid() {
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_INSIDE, Block.STONE)).isTrue();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_INSIDE, Block.OAK_FENCE)).isTrue();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_INSIDE, Block.MOVING_PISTON)).isTrue();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_INSIDE, Block.AIR)).isTrue();
    }

    @Test
    void admitsOnlyALargeShapeOnAFace() {
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_FACE, Block.OAK_FENCE)).isTrue();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_FACE, PISTON_HEAD_UP)).isTrue();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_FACE, Block.STONE)).isFalse();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_FACE, Block.OAK_SLAB)).isFalse();
        // A moving piston is not large, so the edge exception does not carry over to a face.
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_FACE, Block.MOVING_PISTON)).isFalse();
    }

    @Test
    void admitsOnlyAMovingPistonOnAnEdge() {
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_EDGE, Block.MOVING_PISTON)).isTrue();
        // A large shape is admitted on a face but not on an edge — the two branches are distinct
        // conditions, not one "is this block interesting" test reused.
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_EDGE, Block.OAK_FENCE)).isFalse();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_EDGE, PISTON_HEAD_UP)).isFalse();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_EDGE, Block.STONE)).isFalse();
    }

    @Test
    void admitsNothingOnACorner() {
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_CORNER, Block.MOVING_PISTON)).isFalse();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_CORNER, Block.OAK_FENCE)).isFalse();
        assertThat(MinestomCollisionSpace.admits(BlockCursor.TYPE_CORNER, Block.STONE)).isFalse();
    }

    private static Aabb cube(int x, int y, int z) {
        return box(x, y, z, x + 1.0, y + 1.0, z + 1.0);
    }

    private static Aabb box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new Aabb(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }
}
