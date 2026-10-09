package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.mapsetup.MapDraft;

import java.util.ArrayList;
import java.util.List;

/**
 * What the status command reports about a draft: whether a spawn is set, how many rings it has, and the problems that
 * keep it from being game-loadable.
 *
 * @param spawnSet         whether the draft has a spawn
 * @param ringCount        the number of rings
 * @param blockingProblems the problems that keep the draft from being game-loadable, in a fixed order
 */
public record MapStatus(boolean spawnSet, int ringCount, List<Problem> blockingProblems) {

    /** A problem that keeps a draft from being game-loadable. The message is the adapter's to render. */
    public enum Problem {
        /** No spawn is set. */
        NO_SPAWN,
        /** The draft has no ring. */
        NO_RINGS
    }

    public MapStatus {
        blockingProblems = List.copyOf(blockingProblems);
    }

    /**
     * @param draft the draft as last saved
     * @return the status of that draft
     */
    public static MapStatus of(MapDraft draft) {
        List<Problem> problems = new ArrayList<>(2);
        if (draft.spawn() == null) {
            problems.add(Problem.NO_SPAWN);
        }
        if (draft.rings().isEmpty()) {
            problems.add(Problem.NO_RINGS);
        }
        return new MapStatus(draft.spawn() != null, draft.rings().size(), problems);
    }
}
