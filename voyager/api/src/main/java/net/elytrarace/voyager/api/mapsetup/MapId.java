package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.mapsetup.exception.InvalidMapIdException;

import java.util.regex.Pattern;

/**
 * The name of a map under construction. It is also a folder and file name on disk, so it is checked before any file
 * system call: a builder typing {@code ../x} is refused here, not by the file system.
 *
 * @param value the id, {@code [a-z0-9][a-z0-9_-]{0,31}}
 */
public record MapId(String value) {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public MapId {
        if (!SAFE_NAME.matcher(value).matches()) {
            throw InvalidMapIdException.refused(value);
        }
    }
}
