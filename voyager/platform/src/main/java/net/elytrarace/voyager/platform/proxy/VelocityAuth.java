package net.elytrarace.voyager.platform.proxy;

import net.minestom.server.Auth;
import org.jetbrains.annotations.Nullable;

/**
 * Maps the forwarding settings to the Minestom auth the composition roots pass to {@code MinecraftServer.init}. The
 * record itself stays free of Minestom types; this is the one place they meet.
 */
public final class VelocityAuth {

    private VelocityAuth() {
    }

    /**
     * @param settings the forwarding settings
     * @return Velocity modern forwarding with the secret, or {@code null} when the server runs offline
     */
    public static @Nullable Auth of(ProxyForwardingSettings settings) {
        String secret = settings.secret();
        return secret == null ? null : new Auth.Velocity(secret);
    }
}
