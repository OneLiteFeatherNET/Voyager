package net.elytrarace.voyager.api.race;

/**
 * How a player's completion time on a map compares to its reference time, and the points that
 * comparison is worth.
 *
 * <p>{@code DNF} is never produced by {@link MedalBrackets#classify}: it is the tier a scorer assigns
 * to a player who never finished, not a band a completion time can fall into.
 */
public enum MedalTier {
    DIAMOND(60),
    GOLD(45),
    SILVER(30),
    BRONZE(15),
    FINISH(5),
    DNF(0);

    private final int medalPoints;

    MedalTier(int medalPoints) {
        this.medalPoints = medalPoints;
    }

    public int medalPoints() {
        return medalPoints;
    }
}
