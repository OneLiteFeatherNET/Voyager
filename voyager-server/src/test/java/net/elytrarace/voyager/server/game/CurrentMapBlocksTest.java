package net.elytrarace.voyager.server.game;

import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The block source the flight simulation reads through, and the one decision in it: an unreadable
 * cell answers {@code null} — "nothing is known here" — rather than {@code Block.AIR}.
 *
 * <p>The two are indistinguishable downstream <em>today</em>, because {@code MinestomCollisionSpace}
 * skips a {@code null} cell and an air block has no collision boxes to add either way. They stop
 * being indistinguishable the moment anything reads a block for a reason other than collision, and
 * the difference is the difference between "there is no floor there" and "nobody has looked". This
 * test is what keeps the answer from drifting to the convenient one.
 */
@EnvTest
class CurrentMapBlocksTest {

    @Test
    void answersNothingBeforeAnyWorldIsFollowed() {
        CurrentMapBlocks blocks = new CurrentMapBlocks();

        assertThat(blocks.hasWorld()).isFalse();
        assertThat(blocks.getBlock(4, 70, 9, Block.Getter.Condition.TYPE)).isNull();
    }

    @Test
    void readsTheWorldItIsFollowing(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(4, 70, 9, Block.GOLD_BLOCK);
        CurrentMapBlocks blocks = new CurrentMapBlocks();

        blocks.follow(instance);

        assertThat(blocks.hasWorld()).isTrue();
        assertThat(blocks.getBlock(4, 70, 9, Block.Getter.Condition.TYPE)).isEqualTo(Block.GOLD_BLOCK);
    }

    /**
     * Two worlds, two different blocks at the same coordinate. A cached chunk that survived the
     * change of world would answer with the previous world's block, and a coordinate assertion
     * against one world alone could not see it.
     */
    @Test
    void readsTheNewWorldAfterTheCupAdvancesRatherThanTheCachedOne(Env env) {
        Instance first = env.createFlatInstance();
        first.loadChunk(0, 0).join();
        first.setBlock(4, 70, 9, Block.GOLD_BLOCK);
        Instance second = env.createFlatInstance();
        second.loadChunk(0, 0).join();
        second.setBlock(4, 70, 9, Block.DIAMOND_BLOCK);

        CurrentMapBlocks blocks = new CurrentMapBlocks();
        blocks.follow(first);
        assertThat(blocks.getBlock(4, 70, 9, Block.Getter.Condition.TYPE)).isEqualTo(Block.GOLD_BLOCK);

        blocks.follow(second);

        assertThat(blocks.getBlock(4, 70, 9, Block.Getter.Condition.TYPE)).isEqualTo(Block.DIAMOND_BLOCK);
    }

    /**
     * The reason this class exists rather than the instance being handed to the collision space
     * directly: {@code Instance#getBlock} throws {@code NullPointerException} for an unloaded chunk,
     * and a physics tick that throws is a server that stops ticking. A racer's collision sweep
     * straddles a chunk edge on any tick the far side has not arrived yet.
     */
    @Test
    void answersNothingForAnUnloadedChunkInsteadOfThrowingTheWayTheInstanceWould(Env env) {
        Instance instance = env.createFlatInstance();
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        blocks.follow(instance);

        assertThatCode(() -> assertThat(blocks.getBlock(6_000, 70, 6_000, Block.Getter.Condition.TYPE)).isNull())
                .doesNotThrowAnyException();
    }

    @Test
    void answersNothingAgainOnceItFollowsNoWorld(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        instance.setBlock(4, 70, 9, Block.GOLD_BLOCK);
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        blocks.follow(instance);

        blocks.follow(null);

        assertThat(blocks.hasWorld()).isFalse();
        assertThat(blocks.getBlock(4, 70, 9, Block.Getter.Condition.TYPE)).isNull();
    }
}
