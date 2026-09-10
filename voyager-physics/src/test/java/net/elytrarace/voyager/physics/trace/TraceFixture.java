package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.physics.trace.exception.InvalidTraceFixtureException;

import java.util.List;

/**
 * A recorded Vanilla glide, mirroring E2a's {@code TraceFile} format on the reading side.
 *
 * <p>Field names are the file format's contract, not an implementation detail: {@link Metadata},
 * {@link Tick} and {@link BlockBox} reuse E2a's field names exactly — {@code metadata}, {@code
 * ticks}, {@code index}, {@code posX}/{@code posY}/{@code posZ}, {@code velX}/{@code velY}/{@code
 * velZ}, {@code yaw}/{@code pitch}, {@code onGround}, {@code fireworkBoostActive}, {@code
 * fireworkTicksRemaining}, {@code minecraftVersion}, {@code profile}, {@code gravity}, {@code
 * formatVersion}, {@code worldSlice}, {@code minX}/{@code minY}/{@code minZ}/{@code maxX}/{@code
 * maxY}/{@code maxZ} — so a rename here does not silently stop this harness from reading a real
 * fixture while every synthetic test built from this same type keeps passing. See
 * {@code docs/superpowers/plans/2026-09-10-e2a-trace-recorder.md}, Task 2.
 *
 * <p>{@link Metadata}, {@link Tick} and {@link BlockBox} are nested here rather than split into
 * their own top-level types the way E2a's {@code TraceMetadata}/{@code TraceTick}/{@code BlockBox}
 * are: the JSON contract lives in field names, not Java type names, and this harness has no reason
 * to expose these shapes beyond {@code voyager-physics}'s test source set.
 */
public record TraceFixture(Metadata metadata, List<Tick> ticks) {

    public TraceFixture {
        if (metadata == null) {
            throw new InvalidTraceFixtureException("a trace fixture must carry metadata");
        }
        if (ticks == null || ticks.isEmpty()) {
            throw new InvalidTraceFixtureException("a trace fixture must contain at least one tick");
        }
        for (int i = 0; i < ticks.size(); i++) {
            Tick tick = ticks.get(i);
            if (tick == null) {
                throw new InvalidTraceFixtureException("tick at position %s must not be null".formatted(i));
            }
            if (tick.index() != i) {
                throw new InvalidTraceFixtureException(
                        "tick indices must be consecutive from zero; position %s holds index %s"
                                .formatted(i, tick.index()));
            }
        }
        ticks = List.copyOf(ticks);
    }

    /**
     * What a replay needs to know about a recording beyond the samples themselves. {@code gravity}
     * is the entity's effective gravity attribute at record time — a replay that assumes a fixed
     * default silently diverges whenever the recording used a different value.
     */
    public record Metadata(
            String minecraftVersion,
            String profile,
            double gravity,
            int formatVersion,
            List<BlockBox> worldSlice) {

        public Metadata {
            if (minecraftVersion == null || minecraftVersion.isBlank()) {
                throw new InvalidTraceFixtureException("minecraftVersion must not be blank");
            }
            if (profile == null || profile.isBlank()) {
                throw new InvalidTraceFixtureException("profile must not be blank");
            }
            if (!Double.isFinite(gravity) || gravity <= 0.0) {
                throw new InvalidTraceFixtureException(
                        "gravity must be finite and > 0, was %s".formatted(gravity));
            }
            if (formatVersion < 1) {
                throw new InvalidTraceFixtureException(
                        "formatVersion must be >= 1, was %s".formatted(formatVersion));
            }
            worldSlice = worldSlice == null ? List.of() : List.copyOf(worldSlice);
        }
    }

    /**
     * One tick of a recorded glide, sampled after the entity has been ticked. Velocity is the
     * entity's real internal delta movement, not a position difference.
     */
    public record Tick(
            int index,
            double posX, double posY, double posZ,
            double velX, double velY, double velZ,
            float yaw, float pitch,
            boolean onGround,
            boolean fireworkBoostActive,
            int fireworkTicksRemaining) {

        public Tick {
            if (index < 0) {
                throw new InvalidTraceFixtureException("tick index must be >= 0, was %s".formatted(index));
            }
            if (!Double.isFinite(posX) || !Double.isFinite(posY) || !Double.isFinite(posZ)
                    || !Double.isFinite(velX) || !Double.isFinite(velY) || !Double.isFinite(velZ)) {
                throw new InvalidTraceFixtureException(
                        "tick %s carries a non-finite sample: pos=(%s, %s, %s) vel=(%s, %s, %s)"
                                .formatted(index, posX, posY, posZ, velX, velY, velZ));
            }
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new InvalidTraceFixtureException(
                        "tick %s carries a non-finite rotation: yaw=%s pitch=%s".formatted(index, yaw, pitch));
            }
            if (fireworkTicksRemaining < 0) {
                throw new InvalidTraceFixtureException(
                        "tick %s has a negative firework tick count: %s"
                                .formatted(index, fireworkTicksRemaining));
            }
        }
    }

    /** One solid collision box from the recorded world slice, in world coordinates. */
    public record BlockBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

        public BlockBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new InvalidTraceFixtureException(
                        "block box minimum exceeds its maximum: (%s, %s, %s) to (%s, %s, %s)"
                                .formatted(minX, minY, minZ, maxX, maxY, maxZ));
            }
        }
    }
}
