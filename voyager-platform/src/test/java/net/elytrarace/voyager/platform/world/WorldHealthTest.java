package net.elytrarace.voyager.platform.world;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldHealthTest {

    private static final String WORLD = "ElytraraceBlueAndRed";

    // Mutually distinct and none of them zero, so a report built from the wrong counter is visible:
    // a suite where every number is 1 (or where skipped happens to equal errors) cannot tell
    // "reads chunksSkipped" from "reads errors". The verdict cases below override one field each.
    private static final long CHUNKS_LOADED = 412L;
    private static final long CHUNKS_SKIPPED = 17L;
    private static final int UNKNOWN_BLOCKS = 3;
    private static final long CHUNKS_REFUSED = 11L;
    private static final long ERRORS = 5L;

    // Sums to less than CHUNKS_REFUSED on purpose: the loader caps how many distinct versions it
    // tracks, so the breakdown and the count legitimately disagree on a world holding many. A
    // fixture where they agreed could not tell a report that derived one from the other.
    private static final Map<String, Long> REFUSED_VERSIONS = Map.of("2566", 6L, "1976", 2L);

    private static WorldHealth sound() {
        return new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, 0L, Map.of(), 0L);
    }

    @Test
    void isSoundWhenChunksWereReadAndNothingWasLost() {
        assertThat(sound().isSound()).isTrue();
    }

    @Test
    void isNotSoundWhenNoChunkWasReadAtAll() {
        // The whole reason this type exists. A world nothing has been read from is what a mistyped
        // world name looks like from the inside, and it is indistinguishable from a working void
        // world unless an empty read counts as a failure.
        WorldHealth empty = new WorldHealth(WORLD, 0L, 0L, 0, 0L, Map.of(), 0L);

        assertThat(empty.isSound()).isFalse();
    }

    @Test
    void isNotSoundWhenABlockNameWasUnknown() {
        WorldHealth withUnknownBlocks = new WorldHealth(WORLD, CHUNKS_LOADED, 0L, UNKNOWN_BLOCKS, 0L, Map.of(), 0L);

        assertThat(withUnknownBlocks.isSound()).isFalse();
    }

    @Test
    void isNotSoundWhenAChunkFailedOutright() {
        WorldHealth withErrors = new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, 0L, Map.of(), ERRORS);

        assertThat(withErrors.isSound()).isFalse();
    }

    @Test
    void isStillSoundWhenChunksWereSkipped() {
        // Deliberate, not an oversight: a player flying to the edge of a finite racetrack asks for
        // chunks that were never generated, and that is normal. describe() still names them.
        WorldHealth withSkippedChunks = new WorldHealth(WORLD, CHUNKS_LOADED, CHUNKS_SKIPPED, 0, 0L, Map.of(), 0L);

        assertThat(withSkippedChunks.isSound()).isTrue();
    }

    @Test
    void describeNamesTheWorldAndTheChunkCountWhenThereIsNoProblem() {
        assertThat(sound().describe())
                .isEqualTo("world 'ElytraraceBlueAndRed' is sound: 412 chunk(s) read");
    }

    @Test
    void describeReportsAnEmptyWorldRatherThanACleanBillOfHealth() {
        WorldHealth empty = new WorldHealth(WORLD, 0L, 0L, 0, 0L, Map.of(), 0L);

        assertThat(empty.describe()).isEqualTo("world 'ElytraraceBlueAndRed': no chunk was read at all");
    }

    @Test
    void describeNamesUnknownBlocksAndSaysWhatBecameOfThem() {
        WorldHealth withUnknownBlocks = new WorldHealth(WORLD, CHUNKS_LOADED, 0L, UNKNOWN_BLOCKS, 0L, Map.of(), 0L);

        assertThat(withUnknownBlocks.describe())
                .isEqualTo("world 'ElytraraceBlueAndRed': 3 block name(s) unknown to this server, "
                        + "each replaced by air");
    }

    @Test
    void describeNamesSkippedChunksEvenThoughTheyDoNotMakeTheWorldUnsound() {
        WorldHealth withSkippedChunks = new WorldHealth(WORLD, CHUNKS_LOADED, CHUNKS_SKIPPED, 0, 0L, Map.of(), 0L);

        assertThat(withSkippedChunks.describe())
                .isEqualTo("world 'ElytraraceBlueAndRed': 17 chunk(s) skipped for want of data");
    }

    @Test
    void describeNamesFailedChunks() {
        WorldHealth withErrors = new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, 0L, Map.of(), ERRORS);

        assertThat(withErrors.describe()).isEqualTo("world 'ElytraraceBlueAndRed': 5 chunk(s) failed outright");
    }

    @Test
    void describeNamesEveryProblemAtOnce() {
        // The four counters carry four different values here, so a describe() that read the same
        // counter twice, or read skipped where it meant errors, produces a different line.
        WorldHealth broken = new WorldHealth(WORLD, 0L, CHUNKS_SKIPPED, UNKNOWN_BLOCKS, CHUNKS_REFUSED, REFUSED_VERSIONS, ERRORS);

        assertThat(broken.describe())
                .isEqualTo("world 'ElytraraceBlueAndRed': no chunk was read at all; "
                        + "3 block name(s) unknown to this server, each replaced by air; "
                        + "17 chunk(s) skipped for want of data; "
                        + "11 chunk(s) refused for their data version (1976 x 2, 2566 x 6); "
                        + "5 chunk(s) failed outright");
    }

    @Test
    void isNotSoundWhenAChunkWasRefusedForItsDataVersion() {
        // Deliberately without an error alongside it, because the loader happens to count a refusal
        // as an error too: with errors at zero this asserts the refused term itself and not Falco's
        // error accounting standing in for it.
        WorldHealth withRefusedChunks =
                new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, CHUNKS_REFUSED, REFUSED_VERSIONS, 0L);

        assertThat(withRefusedChunks.isSound()).isFalse();
    }

    @Test
    void describeNamesRefusedChunksWithTheVersionsTheyCameFrom() {
        WorldHealth withRefusedChunks =
                new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, CHUNKS_REFUSED, REFUSED_VERSIONS, 0L);

        // Ascending by version, not in map order: this line is compared between two runs.
        assertThat(withRefusedChunks.describe())
                .isEqualTo("world 'ElytraraceBlueAndRed': 11 chunk(s) refused for their data version "
                        + "(1976 x 2, 2566 x 6)");
    }

    @Test
    void describeStillNamesRefusedChunksWhenNoVersionSurvivedTheLoadersCap() {
        // The loader stops tracking distinct versions past its cap while still counting the chunks,
        // so a breakdown can be empty with the count non-zero. The line must not then trail off into
        // an empty bracket.
        WorldHealth withRefusedChunks = new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, CHUNKS_REFUSED, Map.of(), 0L);

        assertThat(withRefusedChunks.describe())
                .isEqualTo("world 'ElytraraceBlueAndRed': 11 chunk(s) refused for their data version");
    }

    @Test
    void keepsItsOwnCopyOfTheRefusedVersionBreakdown() {
        Map<String, Long> mutable = new HashMap<>(REFUSED_VERSIONS);
        WorldHealth health = new WorldHealth(WORLD, CHUNKS_LOADED, 0L, 0, CHUNKS_REFUSED, mutable, 0L);

        mutable.clear();

        assertThat(health.refusedVersions()).containsExactlyInAnyOrderEntriesOf(REFUSED_VERSIONS);
    }

    @Test
    void rejectsAReportThatNamesNoWorld() {
        assertThatThrownBy(() -> new WorldHealth(" ", CHUNKS_LOADED, 0L, 0, 0L, Map.of(), 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name the world");
    }

    @Test
    void rejectsANegativeCounter() {
        // One case per counter: a guard that checked only three of the four would pass a suite that
        // exercised a single field, and the one it forgot is the one that would go negative.
        assertThatThrownBy(() -> new WorldHealth(WORLD, -1L, CHUNKS_SKIPPED, UNKNOWN_BLOCKS, CHUNKS_REFUSED, REFUSED_VERSIONS, ERRORS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loaded=-1 skipped=17 unknownBlocks=3 refused=11 errors=5");
        assertThatThrownBy(() -> new WorldHealth(WORLD, CHUNKS_LOADED, -1L, UNKNOWN_BLOCKS, CHUNKS_REFUSED, REFUSED_VERSIONS, ERRORS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loaded=412 skipped=-1 unknownBlocks=3 refused=11 errors=5");
        assertThatThrownBy(() -> new WorldHealth(WORLD, CHUNKS_LOADED, CHUNKS_SKIPPED, -1, CHUNKS_REFUSED, REFUSED_VERSIONS, ERRORS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loaded=412 skipped=17 unknownBlocks=-1 refused=11 errors=5");
        assertThatThrownBy(() -> new WorldHealth(WORLD, CHUNKS_LOADED, CHUNKS_SKIPPED, UNKNOWN_BLOCKS, -1L, REFUSED_VERSIONS, ERRORS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loaded=412 skipped=17 unknownBlocks=3 refused=-1 errors=5");
        assertThatThrownBy(() -> new WorldHealth(WORLD, CHUNKS_LOADED, CHUNKS_SKIPPED, UNKNOWN_BLOCKS, CHUNKS_REFUSED, REFUSED_VERSIONS, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loaded=412 skipped=17 unknownBlocks=3 refused=11 errors=-1");
    }
}
