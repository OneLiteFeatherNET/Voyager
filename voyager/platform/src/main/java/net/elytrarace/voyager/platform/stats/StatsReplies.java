package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.stats.LeaderboardEntry;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.text.DisplayName;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The lines {@code /stats} and {@code /leaderboard} send, one component per chat line. Pure: a profile and a list of entries
 * in, the lines out, so the wording and the selection rules can be read without a server.
 */
public final class StatsReplies {

    /** The number of entries a leaderboard lists. */
    public static final int LEADERBOARD_LENGTH = 10;

    private StatsReplies() {
    }

    /**
     * The profile of one player. A player with no ranked run gets one line saying so. Otherwise the header, cups played, cup
     * wins, medals per tier, total flight time, then the fastest time of the player on each map, in map order. Practice runs
     * are not in the profile, so a player who only practised has no ranked record.
     *
     * @param profile the player's profile, as the reader gives it
     * @param name    the name to show in the header
     * @param catalog the catalogue the map names are read from; a map no longer in it is named by its plain key
     * @return the lines, never empty
     */
    public static List<Component> profile(PlayerProfile profile, String name, CatalogSnapshot catalog) {
        if (!hasRankedRecord(profile)) {
            return List.of(Messages.statsProfileNone(name));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Messages.statsProfileHeader(name));
        lines.add(Messages.statsProfileCups(profile.cupsPlayed()));
        lines.add(Messages.statsProfileWins(profile.cupsWon()));
        lines.add(Messages.statsProfileMedals(profile.diamonds(), profile.golds(), profile.silvers(), profile.bronzes()));
        lines.add(Messages.statsProfileFlight(profile.flightMillis()));
        Map<String, Integer> fastest = new TreeMap<>();
        for (PlayerProfile.PersonalBest best : profile.personalBests()) {
            fastest.merge(best.mapKey(), best.bestTimeMillis(), Math::min);
        }
        fastest.forEach((mapKey, millis) -> lines.add(Messages.statsProfileBest(mapName(catalog, mapKey), millis)));
        return List.copyOf(lines);
    }

    /**
     * The name a best-time line shows for a map: the catalogue's display name, or the plain key when the catalogue
     * no longer holds the map, which happens when a stored time outlives its map file.
     */
    private static Component mapName(CatalogSnapshot catalog, String mapKey) {
        return DisplayName.orPlainKey(catalog.mapByName(mapKey), mapKey);
    }

    /**
     * The leaderboard of one map: the entries as the reader gives them, which is fastest first, at most
     * {@link #LEADERBOARD_LENGTH} of them. A map with no entries gets one line saying it has no times yet.
     *
     * @param mapName the map's coloured display name
     * @param entries the entries, fastest first
     * @return the lines, never empty
     */
    public static List<Component> leaderboard(Component mapName, List<LeaderboardEntry> entries) {
        if (entries.isEmpty()) {
            return List.of(Messages.statsLeaderboardEmpty(mapName));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Messages.statsLeaderboardHeader(mapName));
        entries.stream().limit(LEADERBOARD_LENGTH).forEach(entry -> lines.add(
                Messages.statsLeaderboardEntry(entry.rank(), entry.name(), entry.bestTimeMillis())));
        return List.copyOf(lines);
    }

    /** A ranked record is any cup, any flight, any medal or any personal best: the things {@code /stats} counts. */
    private static boolean hasRankedRecord(PlayerProfile profile) {
        int medals = profile.diamonds() + profile.golds() + profile.silvers() + profile.bronzes();
        return profile.cupsPlayed() > 0
                || profile.flightMillis() > 0
                || medals > 0
                || !profile.personalBests().isEmpty();
    }
}
