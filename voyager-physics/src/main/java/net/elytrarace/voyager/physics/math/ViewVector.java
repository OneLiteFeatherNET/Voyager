package net.elytrarace.voyager.physics.math;

import net.elytrarace.voyager.api.math.Vec3;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
abstract class ViewVector {

    private ViewVector() {
    }

    public static Vec3 of(float pitchDegrees, float yawDegrees) {
        float realXRot = pitchDegrees * MinecraftMath.DEG_TO_RAD;
        float realYRot = -yawDegrees * MinecraftMath.DEG_TO_RAD;
        float yCos = MinecraftMath.cos(realYRot);
        float ySin = MinecraftMath.sin(realYRot);
        float xCos = MinecraftMath.cos(realXRot);
        float xSin = MinecraftMath.sin(realXRot);
        return new Vec3(ySin * xCos, -xSin, yCos * xCos);
    }
}
