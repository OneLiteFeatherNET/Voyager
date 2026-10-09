package net.elytrarace.voyager.server.game.exception;

import java.util.Collection;

/**
 * The cup this server was told to play cannot be resolved from the catalogue.
 *
 * <p>Two shapes, because the two are fixed differently: a named cup that is not in the catalogue is
 * a typo or a missing file, and an unnamed cup in a catalogue that holds anything other than exactly
 * one is a server that has to be told which one. Both list the names that <em>are</em> there, so the
 * answer is in the message rather than in a second command.
 */
public final class UnresolvedCupException extends RuntimeException {

    private UnresolvedCupException(String message) {
        super(message);
    }

    /** No cup in the catalogue carries {@code name}. */
    public static UnresolvedCupException noSuchCup(String name, Collection<String> available) {
        return new UnresolvedCupException(
                "no cup named '%s' in the catalogue; it holds %s".formatted(name, sorted(available)));
    }

    /**
     * Nothing named a cup and the catalogue does not hold exactly one, so there is no cup that can be
     * called "the" cup. An empty catalogue cannot reach this: the catalogue itself refuses a
     * directory with no definitions in it.
     */
    public static UnresolvedCupException ambiguous(Collection<String> available) {
        return new UnresolvedCupException(
                "the catalogue holds %s cups %s and none was chosen; set -Pcup=<name> (Gradle) or -DVOYAGER_CUP=<name> (java -jar)"
                        .formatted(available.size(), sorted(available)));
    }

    private static String sorted(Collection<String> names) {
        return names.stream().sorted().toList().toString();
    }
}
