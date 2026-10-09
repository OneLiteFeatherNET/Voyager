package net.elytrarace.voyager.setup.spike;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike 1.1: which Minestom 26.2 API sets the transform of a display entity, and whether the values
 * read back after a tick. Not part of the test task; run with {@code ./gradlew :voyager-setup:spike}.
 */
@EnvTest
class DisplayTransformSpikeTest {

    private static final float HALF_SQRT2 = 0.70710677f;

    @Test
    void theTransformSettersStoreWhatTheyAreGivenAndSurviveATick(Env env) {
        Instance instance = env.createFlatInstance();
        Entity display = new Entity(EntityType.BLOCK_DISPLAY);
        display.setInstance(instance, new Pos(0, 64, 0)).join();
        BlockDisplayMeta meta = (BlockDisplayMeta) display.getEntityMeta();

        meta.setBlockState(Block.GLASS);
        meta.setScale(new Vec(4.0, 4.0, 0.05));
        float[] leftRotation = {0f, 0f, HALF_SQRT2, HALF_SQRT2};
        meta.setLeftRotation(leftRotation);
        meta.setTranslation(new Vec(-2.0, -2.0, -0.025));

        assertThat(meta.getScale()).isEqualTo(new Vec(4.0, 4.0, 0.05));
        assertThat(meta.getLeftRotation()).containsExactly(leftRotation);
        assertThat(meta.getTranslation()).isEqualTo(new Vec(-2.0, -2.0, -0.025));

        env.tick();

        assertThat(meta.getScale()).isEqualTo(new Vec(4.0, 4.0, 0.05));
        assertThat(meta.getLeftRotation()).containsExactly(leftRotation);
        assertThat(meta.getTranslation()).isEqualTo(new Vec(-2.0, -2.0, -0.025));
        assertThat(meta.getBlockStateId()).isEqualTo(Block.GLASS);
    }
}
