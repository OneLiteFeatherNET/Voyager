package net.elytrarace.voyager.platform.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a world loader actually did, in the numbers that separate a world which loaded from a world
 * which only appeared to.
 *
 * <p>Three of them are the reason this type exists at all. {@code unknownBlocks} counts block names
 * the running server does not know; the loader's default policy turns each of them into air, so on
 * a racetrack an unknown name is a hole in the course that nobody is told about. {@code
 * chunksSkipped} counts chunks that were asked for and had no data behind them, which is exactly
 * what a mistyped world name produces — and a world that is entirely absent is otherwise
 * indistinguishable from a working void world. {@code chunksRefused} counts chunks the loader would
 * not read because of the version they were written by, which is a half-loaded world: some of the
 * course is there and some of it is not.
 *
 * <p>Deliberately free of both Falco and Minestom: it takes plain numbers and a plain map, so the
 * decisions it makes are testable without a server, a world directory or a fixture.
 *
 * @param world           the name of the world directory these counters belong to
 * @param chunksLoaded    how many chunks were read successfully
 * @param chunksSkipped   how many chunks were asked for and had no data behind them
 * @param unknownBlocks   how many distinct block names the server did not know, each replaced by air
 * @param chunksRefused   how many chunks were refused for the data version they carry
 * @param refusedVersions how many chunks were refused per stored data version. Not derivable from
 *                        {@code chunksRefused}, and not the other way round either: the loader caps
 *                        how many distinct versions it tracks, so on a world holding more than that
 *                        the map's values sum to less than the count. Both are carried because the
 *                        count is the size of the problem and the map is what it was
 * @param errors          how many chunks failed to load or save outright
 */
public record WorldHealth(String world, long chunksLoaded, long chunksSkipped, int unknownBlocks,
                          long chunksRefused, Map<String, Long> refusedVersions, long errors) {

    public WorldHealth {
        if (world.isBlank()) {
            throw new IllegalArgumentException("a world health report has to name the world it describes");
        }
        if (chunksLoaded < 0 || chunksSkipped < 0 || unknownBlocks < 0 || chunksRefused < 0 || errors < 0) {
            throw new IllegalArgumentException(
                    ("counters cannot be negative, was loaded=%d skipped=%d unknownBlocks=%d refused=%d "
                            + "errors=%d").formatted(chunksLoaded, chunksSkipped, unknownBlocks, chunksRefused, errors));
        }
        refusedVersions = Map.copyOf(refusedVersions);
    }

    /**
     * Whether this world can be raced on.
     *
     * <p>A world nothing has been read from yet is <em>not</em> sound. An empty world is the failure
     * this type exists to make visible, not a clean bill of health: it is what a mistyped world name
     * looks like, and reporting it as healthy would defeat the whole point of counting.
     *
     * <p>A refused chunk is not sound either. It is not the tolerable gap a never-generated chunk at
     * the edge of a finite map is — it is a piece of the racetrack that exists on disk and did not
     * get read, and an operator has to be told rather than left to notice.
     *
     * <p>Skipped chunks on their own <em>do</em> leave a world sound. A player flying to the edge of
     * a finite racetrack legitimately asks for chunks that were never generated, so the count is
     * worth naming in {@link #describe()} but is not a verdict.
     *
     * @return true when at least one chunk was read and nothing was lost, refused or renamed away
     */
    public boolean isSound() {
        return unknownBlocks == 0 && errors == 0 && chunksRefused == 0 && chunksLoaded > 0;
    }

    /**
     * One line naming every non-zero problem, or the chunk count when there are none.
     *
     * @return a human-readable summary of this report
     */
    public String describe() {
        List<String> problems = new ArrayList<>(5);

        if (chunksLoaded == 0) {
            problems.add("no chunk was read at all");
        }
        if (unknownBlocks > 0) {
            problems.add("%d block name(s) unknown to this server, each replaced by air".formatted(unknownBlocks));
        }
        if (chunksSkipped > 0) {
            problems.add("%d chunk(s) skipped for want of data".formatted(chunksSkipped));
        }
        if (chunksRefused > 0) {
            // The version is the whole content of this report: "chunks were refused" says something
            // is wrong with the world without saying what, and the number that was refused is the
            // one an operator needs to decide whether to convert the world or to fix the loader.
            problems.add(refusedVersions.isEmpty()
                    ? "%d chunk(s) refused for their data version".formatted(chunksRefused)
                    : "%d chunk(s) refused for their data version (%s)"
                            .formatted(chunksRefused, describeRefusedVersions()));
        }
        if (errors > 0) {
            problems.add("%d chunk(s) failed outright".formatted(errors));
        }
        if (problems.isEmpty()) {
            return "world '%s' is sound: %d chunk(s) read".formatted(world, chunksLoaded);
        }
        return "world '%s': %s".formatted(world, String.join("; ", problems));
    }

    private String describeRefusedVersions() {
        // Sorted rather than in map order: this line ends up in a log that somebody compares between
        // two runs, and a breakdown that lists the same versions in a different order every time
        // cannot be compared at all.
        return refusedVersions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "%s x %d".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }
}
