package net.elytrarace.voyager.platform.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockShapesTest {

    /** {@code piston_head[facing=up]}, the one shape that is large by reaching *below* its cell. */
    private static final Block PISTON_HEAD_UP = Block.PISTON_HEAD.withProperty("facing", "up");

    @Test
    void collisionBoxesOfAFullBlockIsTheUnitCube() {
        List<BoundingBox> boxes = BlockShapes.collisionBoxes(Block.STONE);

        assertThat(boxes).hasSize(1);
        assertThat(boxes.getFirst().minX()).isZero();
        assertThat(boxes.getFirst().minY()).isZero();
        assertThat(boxes.getFirst().minZ()).isZero();
        assertThat(boxes.getFirst().maxX()).isEqualTo(1.0);
        assertThat(boxes.getFirst().maxY()).isEqualTo(1.0);
        assertThat(boxes.getFirst().maxZ()).isEqualTo(1.0);
    }

    @Test
    void collisionBoxesOfAMultiBoxBlockKeepsTheRegistryOrder() {
        // Oak stairs are two boxes: the full lower half, then the upper step over half the depth.
        // Asserted as a sequence, not a set — the order is what MinestomCollisionSpace passes on to
        // MovementResolver, where a short-circuit makes the order of candidates observable.
        List<BoundingBox> boxes = BlockShapes.collisionBoxes(Block.OAK_STAIRS);

        assertThat(boxes).hasSize(2);
        assertThat(corners(boxes.getFirst())).containsExactly(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
        assertThat(corners(boxes.get(1))).containsExactly(0.0, 0.5, 0.0, 1.0, 1.0, 0.5);
    }

    @Test
    void collisionBoxesOfAnEmptyShapeIsEmpty() {
        assertThat(BlockShapes.collisionBoxes(Block.AIR)).isEmpty();
    }

    @Test
    void collisionBoxesOfAMovingPistonIsEmptyInMinestom() {
        // Not an incidental fact: it is why the edge branch of the cell-class filter can never emit
        // anything. Vanilla derives a moving piston's shape from its block entity; Minestom bakes one
        // static shape per block state and has no block entity to derive from, so the shape is empty
        // for all twelve states.
        assertThat(BlockShapes.collisionBoxes(Block.MOVING_PISTON)).isEmpty();
    }

    @Test
    void aFullBlockHasNoLargeCollisionShape() {
        assertThat(BlockShapes.hasLargeCollisionShape(Block.STONE)).isFalse();
    }

    @Test
    void aPartialBlockInsideItsCellHasNoLargeCollisionShape() {
        // A slab and stairs are smaller than a full block, not larger — "large" is about leaving the
        // cell, not about the volume filled. A check written as "is not a full cube" would pass every
        // other test in this class and fail here.
        assertThat(BlockShapes.hasLargeCollisionShape(Block.OAK_SLAB)).isFalse();
        assertThat(BlockShapes.hasLargeCollisionShape(Block.OAK_STAIRS)).isFalse();
    }

    @Test
    void anEmptyShapeHasNoLargeCollisionShape() {
        // Minestom reports (0,0,0)..(0,0,0) for an empty shape where Vanilla reports
        // (+inf)..(-inf); both fall outside the < 0 / > 1 window, so the two agree.
        assertThat(BlockShapes.hasLargeCollisionShape(Block.AIR)).isFalse();
    }

    @Test
    void aBlockReachingAboveItsCellHasALargeCollisionShape() {
        // A fence post is 1.5 blocks tall: relativeEnd().y() == 1.5.
        assertThat(BlockShapes.hasLargeCollisionShape(Block.OAK_FENCE)).isTrue();
        assertThat(BlockShapes.hasLargeCollisionShape(Block.COBBLESTONE_WALL)).isTrue();
    }

    @Test
    void aBlockReachingPastItsCellHorizontallyHasALargeCollisionShape() {
        // piston_head, the only horizontally large shape in the registry: relativeEnd().z() == 1.25
        // facing north, relativeEnd().x() == 1.25 facing west. A check that only looked at Y — which
        // is what Minestom's own tall-block heuristic does — would call both of these false.
        assertThat(BlockShapes.hasLargeCollisionShape(Block.PISTON_HEAD.withProperty("facing", "north")))
                .isTrue();
        assertThat(BlockShapes.hasLargeCollisionShape(Block.PISTON_HEAD.withProperty("facing", "west")))
                .isTrue();
    }

    @Test
    void aBlockReachingBelowItsCellHasALargeCollisionShape() {
        // relativeStart().y() == -0.25. This is the < 0.0 half of Vanilla's formula, which a check
        // written only as "> 1.0 on some axis" would miss entirely.
        assertThat(BlockShapes.hasLargeCollisionShape(PISTON_HEAD_UP)).isTrue();
        assertThat(PISTON_HEAD_UP.collisionShape().relativeStart().y()).isEqualTo(-0.25);
        assertThat(PISTON_HEAD_UP.collisionShape().relativeEnd().y()).isEqualTo(1.0);
    }

    private static List<Double> corners(BoundingBox box) {
        return List.of(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
