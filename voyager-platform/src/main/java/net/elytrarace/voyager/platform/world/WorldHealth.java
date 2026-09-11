package net.elytrarace.voyager.platform.world;

import java.util.ArrayList;
import java.util.List;

/**
 * What a world loader actually did, in the four numbers that separate a world which loaded from a
 * world which only appeared to.
 *
 * <p>Two of them are the reason this type exists at all. {@code unknownBlocks} counts block names
 * the running server does not know; the loader's default policy turns each of them into air, so on
 * a racetrack an unknown name is a hole in the course that nobody is told about. {@code
 * chunksSkipped} counts chunks that were asked for and had no data behind them, which is exactly
 * what a mistyped world name produces — and a world that is entirely absent is otherwise
 * indistinguishable from a working void world.
 *
 * <p>Deliberately free of both Falco and Minestom: it takes plain numbers, so the decisions it makes
 * are testable without a server, a world directory or a fixture.
 *
 * @param world        the name of the world directory these counters belong to
 * @param chunksLoaded how many chunks were read successfully
 * @param chunksSkipped how many chunks were asked for and had no data behind them
 * @param unknownBlocks how many distinct block names the server did not know, each replaced by air
 * @param errors       how many chunks failed to load or save outright
 */
public record WorldHealth(String world, long chunksLoaded, long chunksSkipped, int unknownBlocks, long errors) {

    public WorldHealth {
        if (world.isBlank()) {
            throw new IllegalArgumentException("a world health report has to name the world it describes");
        }
        if (chunksLoaded < 0 || chunksSkipped < 0 || unknownBlocks < 0 || errors < 0) {
            throw new IllegalArgumentException(
                    "counters cannot be negative, was loaded=%d skipped=%d unknownBlocks=%d errors=%d"
                            .formatted(chunksLoaded, chunksSkipped, unknownBlocks, errors));
        }
    }

    /**
     * Whether this world can be raced on.
     *
     * <p>A world nothing has been read from yet is <em>not</em> sound. An empty world is the failure
     * this type exists to make visible, not a clean bill of health: it is what a mistyped world name
     * looks like, and reporting it as healthy would defeat the whole point of counting.
     *
     * <p>Skipped chunks on their own do not make a world unsound. A player flying to the edge of a
     * finite racetrack legitimately asks for chunks that were never generated, so the count is worth
     * naming in {@link #describe()} but is not a verdict.
     *
     * @return true when at least one chunk was read and nothing was lost or renamed away
     */
    public boolean isSound() {
        return unknownBlocks == 0 && errors == 0 && chunksLoaded > 0;
    }

    /**
     * One line naming every non-zero problem, or the chunk count when there are none.
     *
     * @return a human-readable summary of this report
     */
    public String describe() {
        List<String> problems = new ArrayList<>(4);

        if (chunksLoaded == 0) {
            problems.add("no chunk was read at all");
        }
        if (unknownBlocks > 0) {
            problems.add("%d block name(s) unknown to this server, each replaced by air".formatted(unknownBlocks));
        }
        if (chunksSkipped > 0) {
            problems.add("%d chunk(s) skipped for want of data".formatted(chunksSkipped));
        }
        if (errors > 0) {
            problems.add("%d chunk(s) failed outright".formatted(errors));
        }
        if (problems.isEmpty()) {
            return "world '%s' is sound: %d chunk(s) read".formatted(world, chunksLoaded);
        }
        return "world '%s': %s".formatted(world, String.join("; ", problems));
    }
}
