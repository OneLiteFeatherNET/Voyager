package net.elytrarace.voyager.setup.config;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * The settings of {@code /map import}: the folder world folders are imported from, and the two limits of one import.
 * The composition root reads them through {@code ConfigLoader::get}; this record never reads the JVM itself.
 *
 * @param root       the import folder, {@code imports} under the working directory by default; the server never creates it
 * @param maxBytes   the largest total of region bytes one import may copy; 256 MiB by default
 * @param maxRegions the largest number of region files one import may copy; 1024 by default
 */
public record ImportSettings(Path root, long maxBytes, int maxRegions) {

    /** The import folder. */
    public static final String ROOT_KEY = "VOYAGER_IMPORT_PATH";
    /** The byte limit of one import. */
    public static final String MAX_BYTES_KEY = "VOYAGER_IMPORT_MAX_BYTES";
    /** The region file limit of one import. */
    public static final String MAX_REGIONS_KEY = "VOYAGER_IMPORT_MAX_REGIONS";

    static final String DEFAULT_ROOT = "imports";
    static final long DEFAULT_MAX_BYTES = 268_435_456L;
    static final int DEFAULT_MAX_REGIONS = 1024;

    public ImportSettings {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("%s must be at least 1, was %d".formatted(MAX_BYTES_KEY, maxBytes));
        }
        if (maxRegions < 1) {
            throw new IllegalArgumentException("%s must be at least 1, was %d".formatted(MAX_REGIONS_KEY, maxRegions));
        }
    }

    /**
     * Reads the three keys.
     *
     * @param lookup the source of a key's value, {@code ConfigLoader::get} in the composition root; {@code null} for
     *               an unset key
     * @return the settings, with the defaults for every unset key
     * @throws IllegalArgumentException if a limit is not a number of at least 1; the message names the key
     */
    public static ImportSettings from(Function<String, @Nullable String> lookup) {
        return new ImportSettings(
                Path.of(valueOr(lookup.apply(ROOT_KEY), DEFAULT_ROOT)),
                longOr(lookup.apply(MAX_BYTES_KEY), MAX_BYTES_KEY, DEFAULT_MAX_BYTES),
                (int) Math.min(Integer.MAX_VALUE, longOr(lookup.apply(MAX_REGIONS_KEY), MAX_REGIONS_KEY, DEFAULT_MAX_REGIONS)));
    }

    private static String valueOr(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static long longOr(@Nullable String value, String key, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("%s must be a number, was '%s'".formatted(key, value), exception);
        }
    }
}
