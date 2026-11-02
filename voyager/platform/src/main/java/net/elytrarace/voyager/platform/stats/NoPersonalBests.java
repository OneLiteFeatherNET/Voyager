package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

/** The reader of a server that keeps no statistics: nothing is read, so no best is ever shown. */
@NotNullByDefault
public final class NoPersonalBests implements PersonalBestReader {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public BestAnswer bestOn(UUID playerId, String mapKey, String layoutVersion, GameMode mode) {
        return BestAnswer.unavailable();
    }
}
