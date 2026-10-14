package net.elytrarace.voyager.persistence;

import org.jetbrains.annotations.Nullable;

/**
 * Decides what the writer does with a failed write, from its SQLSTATE. SQLSTATE class 22 (data exception) and
 * class 23 (integrity constraint violation) are data errors: the same completion would fail again, so it is
 * dropped after one attempt. Everything else, including a missing state and a state this class does not know,
 * is retried with backoff.
 */
public final class SqlStateClassifier {

    private static final String DATA_EXCEPTION = "22";
    private static final String INTEGRITY_VIOLATION = "23";

    /** What the writer does with a failed write. */
    public enum Failure {
        /** The completion is dropped after this one attempt. */
        DATA,
        /** The completion stays at the head of the queue and is tried again after the backoff. */
        RETRY
    }

    private SqlStateClassifier() {
    }

    /**
     * @param sqlState the five-character SQLSTATE of the failure, or {@code null} when there is none
     * @return {@link Failure#DATA} for class 22 and 23, {@link Failure#RETRY} otherwise
     */
    public static Failure classify(@Nullable String sqlState) {
        if (sqlState == null || sqlState.length() != 5) {
            return Failure.RETRY;
        }
        String sqlClass = sqlState.substring(0, 2);
        return DATA_EXCEPTION.equals(sqlClass) || INTEGRITY_VIOLATION.equals(sqlClass)
                ? Failure.DATA
                : Failure.RETRY;
    }
}
