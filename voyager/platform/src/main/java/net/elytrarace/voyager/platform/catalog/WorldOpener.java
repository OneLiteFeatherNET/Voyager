package net.elytrarace.voyager.platform.catalog;

/**
 * The world side of a catalogue reload: what the reloader may ask of the worlds the maps name.
 *
 * <p>A port, because the reloader decides <em>whether</em> a world may be opened, and the world layer decides
 * <em>how</em>. {@code MapInstances} implements it. The reloader opens only worlds that are not open yet, and
 * discards only the ones its own attempt opened; a world that was open before a reload is never reread.
 */
public interface WorldOpener {

    /** Whether region data sits behind {@code world}. Cheap, and does not open the world. */
    boolean holdsRegionData(String world);

    /** Whether {@code world} is open, which is what {@link #open} leaves it as. */
    boolean isOpen(String world);

    /**
     * Opens {@code world} and registers its instance.
     *
     * @throws RuntimeException if no region data sits behind the name or the loader cannot be built
     */
    void open(String world);

    /**
     * Closes and unregisters a world this attempt opened, so a rejected reload leaves nothing behind.
     * Does nothing for a world that is not open.
     */
    void discard(String world);

    /**
     * Whether the region files of an open world differ from what they were when the world was opened. A
     * world that is not open reports {@code false}. A {@code true} means the change is not in the running
     * world and needs a restart.
     */
    boolean regionDataChanged(String world);
}
