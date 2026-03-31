package net.elytrarace.server.physics;

import net.minestom.server.coordinate.Vec;

/**
 * Stateless utility class implementing vanilla Elytra flight physics.
 * <p>
 * All formulas are based on decompiled vanilla code (verified up to 1.21.x).
 * This class has no server dependencies — it operates purely on vectors and angles.
 * <p>
 * Angles in the public API are in degrees; internally converted to radians.
 */
public final class ElytraPhysics {

    // -- Elytra flight constants --
    public static final double GRAVITY = -0.08;
    public static final double PITCH_LIFT = 0.06;
    public static final double DOWNWARD_GLIDE_FACTOR = -0.1;
    public static final double UPWARD_PITCH_BASE = 0.04;
    public static final double UPWARD_PITCH_MULTIPLIER = 3.2;
    public static final double DIRECTION_ALIGNMENT_RATE = 0.1;
    public static final double DRAG_HORIZONTAL = 0.99;
    public static final double DRAG_VERTICAL = 0.98;
    public static final double TICKS_PER_SECOND = 20.0;

    // -- Firework boost constants --
    public static final double FIREWORK_BOOST_STRENGTH = 1.5;
    public static final double FIREWORK_BOOST_BLEND = 0.5;
    public static final double FIREWORK_BOOST_BASE = 0.1;

    private ElytraPhysics() {
        // utility class
    }

    /**
     * Computes the next velocity vector after one physics tick.
     *
     * @param currentVelocity the current velocity in blocks/tick
     * @param pitchDeg        the player's pitch in degrees (negative = looking up, positive = looking down)
     * @param yawDeg          the player's yaw in degrees
     * @return the new velocity vector after applying one tick of elytra physics
     */
    public static Vec computeNextVelocity(Vec currentVelocity, double pitchDeg, double yawDeg) {
        double pitchRad = Math.toRadians(pitchDeg);
        double yawRad = Math.toRadians(yawDeg);

        double pitchCos = Math.cos(pitchRad);
        double pitchSin = Math.sin(pitchRad);
        double sqrPitchCos = pitchCos * pitchCos;

        // Look direction vector (vanilla convention: yaw 0 = south, increases counter-clockwise)
        double yawCos = Math.cos(-yawRad - Math.PI);
        double yawSin = Math.sin(-yawRad - Math.PI);
        double lookX = yawSin * (-pitchCos);
        double lookY = -pitchSin;
        double lookZ = yawCos * (-pitchCos);

        double velX = currentVelocity.x();
        double velY = currentVelocity.y();
        double velZ = currentVelocity.z();

        double hVel = Math.sqrt(velX * velX + velZ * velZ);
        double hLook = pitchCos;

        // Step 4: Gravity + Lift
        velY += GRAVITY + sqrPitchCos * PITCH_LIFT;

        // Step 5: Downward glide damping — convert sinking into forward movement
        if (velY < 0 && hLook > 0) {
            double yAcc = velY * DOWNWARD_GLIDE_FACTOR * sqrPitchCos;
            velY += yAcc;
            velX += lookX * yAcc / hLook;
            velZ += lookZ * yAcc / hLook;
        }

        // Step 6: Upward pitch boost — convert horizontal speed into altitude
        if (pitchSin < 0 && hLook > 1e-8) {  // Guard against division by zero at extreme pitch
            double yAcc = hVel * (-pitchSin) * UPWARD_PITCH_BASE;
            velY += yAcc * UPWARD_PITCH_MULTIPLIER;
            velX -= lookX * yAcc / hLook;
            velZ -= lookZ * yAcc / hLook;
        }

        // Step 7: Direction alignment — steer horizontal velocity towards look direction
        if (hLook > 0) {
            velX += (lookX / hLook * hVel - velX) * DIRECTION_ALIGNMENT_RATE;
            velZ += (lookZ / hLook * hVel - velZ) * DIRECTION_ALIGNMENT_RATE;
        }

        // Step 8: Drag
        velX *= DRAG_HORIZONTAL;
        velY *= DRAG_VERTICAL;
        velZ *= DRAG_HORIZONTAL;

        return new Vec(velX, velY, velZ);
    }

    /**
     * Applies a firework boost to the given velocity, accelerating the player in their look direction.
     *
     * @param currentVelocity the current velocity in blocks/tick
     * @param pitchDeg        the player's pitch in degrees
     * @param yawDeg          the player's yaw in degrees
     * @return the velocity after applying one tick of firework boost
     */
    public static Vec applyFireworkBoost(Vec currentVelocity, double pitchDeg, double yawDeg) {
        double pitchRad = Math.toRadians(pitchDeg);
        double yawRad = Math.toRadians(yawDeg);

        double pitchCos = Math.cos(pitchRad);
        double pitchSin = Math.sin(pitchRad);

        double yawCos = Math.cos(-yawRad - Math.PI);
        double yawSin = Math.sin(-yawRad - Math.PI);
        double lookX = yawSin * (-pitchCos);
        double lookY = -pitchSin;
        double lookZ = yawCos * (-pitchCos);

        double velX = currentVelocity.x();
        double velY = currentVelocity.y();
        double velZ = currentVelocity.z();

        velX += lookX * FIREWORK_BOOST_BASE + (lookX * FIREWORK_BOOST_STRENGTH - velX) * FIREWORK_BOOST_BLEND;
        velY += lookY * FIREWORK_BOOST_BASE + (lookY * FIREWORK_BOOST_STRENGTH - velY) * FIREWORK_BOOST_BLEND;
        velZ += lookZ * FIREWORK_BOOST_BASE + (lookZ * FIREWORK_BOOST_STRENGTH - velZ) * FIREWORK_BOOST_BLEND;

        return new Vec(velX, velY, velZ);
    }
}
