package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;

/**
 * Tracks the elytra flight state of a player entity.
 * Updated each tick by {@link net.elytrarace.server.ecs.system.ElytraPhysicsSystem}.
 */
public class ElytraFlightComponent implements Component {

    private Vec velocity = Vec.ZERO;
    private boolean flying;
    private double pitch;
    private double yaw;
    private Pos previousPosition;

    public Vec getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec velocity) {
        this.velocity = velocity;
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public double getPitch() {
        return pitch;
    }

    public void setPitch(double pitch) {
        this.pitch = pitch;
    }

    public double getYaw() {
        return yaw;
    }

    public void setYaw(double yaw) {
        this.yaw = yaw;
    }

    public Pos getPreviousPosition() {
        return previousPosition;
    }

    public void setPreviousPosition(Pos previousPosition) {
        this.previousPosition = previousPosition;
    }

    /**
     * Returns the current speed in blocks per second (velocity length * 20 TPS).
     */
    public double getSpeedBlocksPerSecond() {
        return velocity.length() * 20.0;
    }
}
