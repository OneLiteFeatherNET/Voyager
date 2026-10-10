package net.elytrarace.voyager.race.reset.exception;

/**
 * Thrown when a reset is planned for a run that has already finished.
 *
 * <p>A finished run is never reset, whatever its racer does afterwards, so asking for one is a
 * programming error in the caller: the caller must test {@code RaceRun#finished()} first, as
 * {@code CupSession} does, rather than catch this.
 */
public final class RunNotResettableException extends RuntimeException {

    private RunNotResettableException(String message) {
        super(message);
    }

    public static RunNotResettableException finishedRun() {
        return new RunNotResettableException(
                "a finished run is never reset: it has passed every ring of its map");
    }
}
