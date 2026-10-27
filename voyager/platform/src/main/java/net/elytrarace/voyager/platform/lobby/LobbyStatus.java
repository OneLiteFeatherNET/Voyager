package net.elytrarace.voyager.platform.lobby;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * The numbers the lobby shows beside its cup: how many racers are online out of the maximum, and the time left before the
 * start, when a countdown runs.
 *
 * @param online    how many racers are online
 * @param maximum   how many racers make a full server
 * @param countdown the time left before the start, or {@code null} while the minimum is not met and no countdown runs
 */
@NotNullByDefault
public record LobbyStatus(int online, int maximum, @Nullable Duration countdown) {
}
