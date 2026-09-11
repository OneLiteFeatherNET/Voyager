package net.elytrarace.voyager.platform;

import org.jetbrains.annotations.ApiStatus;

/**
 * Identifies this module to the architecture rules; carries no behaviour.
 *
 * <p>voyager-platform is the only rebuild module allowed to import {@code net.minestom..} or
 * {@code net.theevilreaper.xerus..}. It translates between those platform APIs and the domain
 * modules (voyager-api, voyager-physics, voyager-race) — it does not make game decisions itself.
 */
@ApiStatus.Internal
public abstract class PlatformCore {

    private PlatformCore() {
    }
}
