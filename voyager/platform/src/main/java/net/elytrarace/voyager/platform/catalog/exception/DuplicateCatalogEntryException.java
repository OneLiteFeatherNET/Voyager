package net.elytrarace.voyager.platform.catalog.exception;

import java.nio.file.Path;

/**
 * Thrown when two files in one catalogue directory declare the same name.
 *
 * <p>A catalogue is keyed by the name inside the file rather than by the filename, so two files can
 * collide without looking alike. Whichever loaded second would win, silently, and the map a cup
 * plays would depend on directory iteration order — which is not the same on two machines. There is
 * no correct answer to pick here, so neither is picked.
 */
public final class DuplicateCatalogEntryException extends RuntimeException {

    private final String name;

    public DuplicateCatalogEntryException(String kind, String name, Path first, Path second) {
        super("two %s definitions are both named '%s': %s and %s".formatted(kind, name, first, second));
        this.name = name;
    }

    /** The name both files declare. */
    public String name() {
        return name;
    }
}
