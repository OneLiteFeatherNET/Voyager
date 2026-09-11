package net.elytrarace.tools.recorder.world;

/** Answers whether a block coordinate is solid. The seam that keeps {@link WorldSliceCollector} testable without a server. */
@FunctionalInterface
public interface SolidBlockSource {

    boolean isSolid(int x, int y, int z);
}
