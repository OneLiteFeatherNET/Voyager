package net.elytrarace.voyager.setup.mapsetup;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The edit claims of the setup server: one holder for each key, kept in memory only, so a start begins with none. A
 * key is {@code map:<id>} for a map; a cup uses {@code cup:<id>}, so the two namespaces do not block each other.
 *
 * <p>Player events run on Minestom's main thread, so the registry needs no lock, as {@code MapSession} needs none.
 */
public final class EditClaims {

    private final Map<String, Holder> holders = new HashMap<>();

    /**
     * A builder who holds a claim, under the name the builder had when the claim was taken.
     *
     * @param builder the builder's UUID
     * @param name    the builder's name at the claim
     */
    public record Holder(UUID builder, String name) {
    }

    /**
     * Grants the key to the builder. A key that the same builder already holds is granted again, and its holder is not
     * changed.
     *
     * @param key     the claim key, such as {@code map:skyfortress}
     * @param builder the builder who asks
     * @param name    the builder's name, recorded only when the claim is new
     * @return the holder who refused the claim, or empty when the claim is granted
     */
    public Optional<Holder> claim(String key, UUID builder, String name) {
        Holder held = holders.get(key);
        if (held == null) {
            holders.put(key, new Holder(builder, name));
            return Optional.empty();
        }
        return held.builder().equals(builder) ? Optional.empty() : Optional.of(held);
    }

    /**
     * Releases the key when the builder holds it. A release by any other builder changes nothing.
     *
     * @param key     the claim key
     * @param builder the builder who releases
     */
    public void release(String key, UUID builder) {
        holders.computeIfPresent(key, (ignored, held) -> held.builder().equals(builder) ? null : held);
    }

    /**
     * Releases every key the builder holds, and no key of any other builder.
     *
     * @param builder the builder who leaves
     */
    public void releaseAll(UUID builder) {
        holders.values().removeIf(held -> held.builder().equals(builder));
    }

    /**
     * Releases the key whoever holds it. Only the administrator's unlock calls this.
     *
     * @param key the claim key
     * @return the holder that was released, or empty when no builder held the key
     */
    public Optional<Holder> unlock(String key) {
        return Optional.ofNullable(holders.remove(key));
    }

    /**
     * @param key the claim key
     * @return the holder of the key, or empty when no builder holds it
     */
    public Optional<Holder> holder(String key) {
        return Optional.ofNullable(holders.get(key));
    }
}
