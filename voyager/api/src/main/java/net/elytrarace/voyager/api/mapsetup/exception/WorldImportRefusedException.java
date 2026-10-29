package net.elytrarace.voyager.api.mapsetup.exception;

import java.util.List;

/**
 * Thrown when an import is refused before or after its copy. The {@link Reason} says why, and the arguments fill the
 * placeholders of the message the setup server shows, so the text stays translatable and names the values that
 * refused the import.
 */
public final class WorldImportRefusedException extends RuntimeException {

    /** Why an import was refused. */
    public enum Reason {
        /** The import root does not exist; the argument is its absolute path. */
        ROOT_MISSING,
        /** The world folder name or its resolved place is outside the rule; the argument is the name. */
        FOLDER_INVALID,
        /** The world folder does not exist under the import root; the argument is its absolute path. */
        FOLDER_MISSING,
        /** Neither accepted layout holds region files. No arguments. */
        LAYOUT_UNSUPPORTED,
        /** Both accepted layouts hold region files; the arguments are the two folders. */
        LAYOUT_AMBIGUOUS,
        /** A region file is a symbolic link; the argument is its file name. */
        LINK,
        /** The region data is above a limit; the arguments are the measured value, the limit and the setting's name. */
        TOO_LARGE,
        /** A world or chunk states a data version above the one this server reads; the arguments are the version
         *  found and the highest supported version. */
        DATA_VERSION_NEWER,
        /** The world folder already exists; the argument is the map id. */
        WORLD_EXISTS
    }

    private final Reason reason;
    private final List<String> arguments;

    private WorldImportRefusedException(Reason reason, List<String> arguments) {
        super(reason + " " + arguments);
        this.reason = reason;
        this.arguments = List.copyOf(arguments);
    }

    /**
     * @param reason    why the import was refused
     * @param arguments the values the message names, in placeholder order
     * @return the exception to throw
     */
    public static WorldImportRefusedException because(Reason reason, String... arguments) {
        return new WorldImportRefusedException(reason, List.of(arguments));
    }

    public Reason reason() {
        return reason;
    }

    /** @return the values the message names, in placeholder order */
    public List<String> arguments() {
        return arguments;
    }
}
