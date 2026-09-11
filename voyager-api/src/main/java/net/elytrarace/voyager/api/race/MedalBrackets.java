package net.elytrarace.voyager.api.race;

import java.time.Duration;

/**
 * The multipliers of a map's reference time that mark off each {@link MedalTier} band.
 *
 * <p>A completion time is classified by scaling the map's own reference time rather than assuming
 * one fixed duration for every map — the same brackets apply whether the reference time is thirty
 * seconds or five minutes.
 */
public record MedalBrackets(double diamond, double gold, double silver, double bronze) {

    /** The bracket multipliers read out of the tree this rebuild replaces: 1.00 / 1.10 / 1.25 / 1.50. */
    public static final MedalBrackets DEFAULT = new MedalBrackets(1.00, 1.10, 1.25, 1.50);

    public MedalBrackets {
        if (!(0 < diamond && diamond < gold && gold < silver && silver < bronze)) {
            throw new IllegalArgumentException(
                    "bracket multipliers must be positive and strictly increasing, was %s, %s, %s, %s"
                            .formatted(diamond, gold, silver, bronze));
        }
    }

    /**
     * Classifies {@code elapsed} against {@code reference} scaled by each bracket in turn — diamond,
     * gold, silver, bronze — returning the first band {@code elapsed} fits inside, each boundary
     * inclusive. Returns {@link MedalTier#FINISH} when {@code elapsed} exceeds every band; never
     * returns {@link MedalTier#DNF}, which a scorer assigns for a player who did not finish at all.
     */
    public MedalTier classify(Duration elapsed, Duration reference) {
        long elapsedNanos = elapsed.toNanos();
        long referenceNanos = reference.toNanos();
        if (elapsedNanos <= referenceNanos * diamond) {
            return MedalTier.DIAMOND;
        }
        if (elapsedNanos <= referenceNanos * gold) {
            return MedalTier.GOLD;
        }
        if (elapsedNanos <= referenceNanos * silver) {
            return MedalTier.SILVER;
        }
        if (elapsedNanos <= referenceNanos * bronze) {
            return MedalTier.BRONZE;
        }
        return MedalTier.FINISH;
    }
}
