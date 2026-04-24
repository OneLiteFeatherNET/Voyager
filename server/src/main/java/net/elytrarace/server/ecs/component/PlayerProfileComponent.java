package net.elytrarace.server.ecs.component;

import net.elytrarace.api.database.entity.ElytraPlayerEntity;
import net.elytrarace.common.ecs.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the persisted {@link ElytraPlayerEntity} for a player so gameplay systems
 * can read career stats (total games, wins, rings passed) without touching the
 * database on the tick thread.
 * <p>
 * The reference may be {@code null} briefly between login and the async profile
 * load completing, or indefinitely if the DB was unreachable at join time.
 */
public final class PlayerProfileComponent implements Component {

    private volatile @Nullable ElytraPlayerEntity profile;

    public PlayerProfileComponent() {
        this.profile = null;
    }

    public PlayerProfileComponent(@Nullable ElytraPlayerEntity profile) {
        this.profile = profile;
    }

    public @Nullable ElytraPlayerEntity getProfile() {
        return profile;
    }

    public void setProfile(@Nullable ElytraPlayerEntity profile) {
        this.profile = profile;
    }

    public boolean isLoaded() {
        return profile != null;
    }
}
