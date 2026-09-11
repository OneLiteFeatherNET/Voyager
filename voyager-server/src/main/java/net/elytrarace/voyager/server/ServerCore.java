package net.elytrarace.voyager.server;

import org.jetbrains.annotations.ApiStatus;

/**
 * Identifies this module to the architecture rules; carries no behaviour.
 *
 * <p>voyager-server is the composition root: it depends on every other rebuild module and is the
 * only place — alongside voyager-setup once that module exists — permitted to carry
 * {@code com.google.inject..} / {@code io.airlift..} dependency-injection annotations.
 */
@ApiStatus.Internal
public abstract class ServerCore {

    private ServerCore() {
    }
}
