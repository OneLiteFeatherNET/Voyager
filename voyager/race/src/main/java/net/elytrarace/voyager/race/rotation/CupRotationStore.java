package net.elytrarace.voyager.race.rotation;

import java.util.Optional;

/**
 * Where the armed cup is kept across a restart: the port the rotation rule needs, implemented by the platform.
 *
 * <p>The rule owns the port because it is the rule that decides what is worth keeping. A store never throws for a
 * file that cannot be read or written: a failure is logged by the adapter, and the rotation carries on in memory.
 */
public interface CupRotationStore {

    /** A store that keeps nothing: a restart begins again at the first cup. */
    CupRotationStore NONE = new CupRotationStore() {
        @Override
        public Optional<String> read() {
            return Optional.empty();
        }

        @Override
        public void write(String cup) {
            // Nothing is kept: the position lives in the JVM only.
        }
    };

    /**
     * @return the canonical name of the cup that was armed when it was last written, or empty when nothing was kept or
     *     it cannot be read
     */
    Optional<String> read();

    /**
     * Keeps the name of the armed cup.
     *
     * @param cup the canonical name of the cup that is now armed
     */
    void write(String cup);
}
