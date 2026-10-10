package net.elytrarace.voyager.physics.math;

import org.jetbrains.annotations.ApiStatus;

/**
 * Vanilla's trigonometry, reproduced exactly.
 *
 * <p>Minecraft does not use {@link Math#sin} for these — it reads a 65536-entry table of
 * {@code float}. The quantisation is part of the behaviour being replicated, not an artefact to be
 * improved away: substituting the JDK functions changes climb behaviour in a way that passes review
 * and fails the trace suite.
 *
 * <p>Transcribed from {@code net/minecraft/util/Mth.java} of Minecraft 26.2.
 */
@ApiStatus.Internal
public abstract class MinecraftMath {

    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private static final double SIN_SCALE = 10430.378350470453;
    private static final double COS_OFFSET = 16384.0;
    private static final int TABLE_MASK = 65535;

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin(i / SIN_SCALE);
        }
    }

    private MinecraftMath() {
    }

    public static float sin(double radians) {
        return SIN[(int) ((long) (radians * SIN_SCALE) & TABLE_MASK)];
    }

    public static float cos(double radians) {
        return SIN[(int) ((long) (radians * SIN_SCALE + COS_OFFSET) & TABLE_MASK)];
    }

    public static double square(double value) {
        return value * value;
    }
}
