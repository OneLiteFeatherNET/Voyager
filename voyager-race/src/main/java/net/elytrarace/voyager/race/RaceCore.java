package net.elytrarace.voyager.race;

import org.jetbrains.annotations.ApiStatus;

/** Identifies this module to the architecture rules; carries no behaviour. */
@ApiStatus.Internal
public abstract class RaceCore {

    /** The race model version a persisted score or replay was produced against. */
    public static final int MODEL_VERSION = 1;

    private RaceCore() {
    }
}
