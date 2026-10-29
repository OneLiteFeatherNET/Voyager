package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.mapsetup.exception.InvalidWorldFolderNameException;

import java.util.regex.Pattern;

/**
 * The name of a world folder in the import root. The name is typed by a builder, so it is checked before any file
 * system call: {@code ..}, a separator or an empty name is refused here, not by the file system.
 *
 * @param value the folder name, {@code [A-Za-z0-9_.-]{1,64}}, and not {@code .} or {@code ..}
 */
public record WorldFolderName(String value) {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    public WorldFolderName {
        if (!SAFE_NAME.matcher(value).matches() || value.equals(".") || value.equals("..")) {
            throw InvalidWorldFolderNameException.refused(value);
        }
    }
}
