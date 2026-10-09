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
     * Nothing named a cup and the cup directory does not hold exactly one cup file, so there is no cup
     * that can be called "the" cup. A file that does not parse counts: it is a cup the operator would be
     * choosing against without knowing it. Both lists are sorted, so the message is the same on every
     * machine.
     *
     * @param cupNames  the cups that parsed, by the name each declares
     * @param cupFiles  every cup file in the directory, parsed or not, by file name
     */
    public static UnresolvedCupException ambiguous(Collection<String> cupNames, Collection<String> cupFiles) {
        return new UnresolvedCupException(
                ("the cup directory holds %s cup file(s) %s and none was chosen; "
                        + "set -Pcup=<name> (Gradle) or -DVOYAGER_CUP=<name> (java -jar); "
                        + "the cups that parsed are %s").formatted(cupFiles.size(), sorted(cupFiles), sorted(cupNames)));
    }

    private static String sorted(Collection<String> names) {
        return names.stream().sorted().toList().toString();
    }
}
