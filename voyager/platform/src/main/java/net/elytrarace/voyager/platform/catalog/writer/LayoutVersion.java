package net.elytrarace.voyager.platform.catalog.writer;

import net.elytrarace.voyager.api.race.MapDefinition;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The layout identity of a map definition: the first sixteen lowercase hex characters of the SHA-256 digest of its
 * canonical form ({@link MapDefinitionCanonicalWriter}), in UTF-8. A changed ring, spawn or any other component changes
 * it, the name and the world included, and the file the definition was read from does not take part at all.
 */
@ApiStatus.Internal
public abstract class LayoutVersion {

    /** Sixteen hex characters are eight bytes of the digest. */
    private static final int DIGEST_BYTES = 8;

    private LayoutVersion() {
    }

    /**
     * @param map the map definition whose layout is named
     * @return sixteen lowercase hex characters
     */
    @Contract(pure = true)
    public static String of(MapDefinition map) {
        byte[] canonical = MapDefinitionCanonicalWriter.toCanonicalJson(map).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest, 0, DIGEST_BYTES);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is a required algorithm of every Java platform, so a JVM without it is not one Voyager runs on.
            throw new IllegalStateException("SHA-256 is not available on this JVM", exception);
        }
    }
}
