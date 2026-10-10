package net.elytrarace.common.game.scoring;

import java.time.Duration;
import java.util.Objects;

/**
 * Time thresholds expressed as multipliers of a reference duration that decide
 * which {@link MedalTier} a player earns when they finish a map.
 *
 * <p>Multipliers must be strictly ordered: {@code diamond < gold < silver < bronze}.
 * The classifier returns {@link MedalTier#FINISH} when the player completed the map
 * but exceeded the bronze multiplier. {@link MedalTier#DNF} is intentionally not
 * returned here — the caller decides when a player did not finish at all.
 */
public record MedalBrackets(double diamond, double gold, double silver, double bronze) {

    public static final MedalBrackets DEFAULT = new MedalBrackets(1.00, 1.10, 1.25, 1.50);

    public MedalBrackets {
        if (diamond <= 0 || gold <= 0 || silver <= 0 || bronze <= 0) {
            throw new IllegalArgumentException(
                    "all bracket multipliers must be positive (diamond=" + diamond
                            + ", gold=" + gold + ", silver=" + silver + ", bronze=" + bronze + ")");
        }
        if (!(diamond < gold && gold < silver && silver < bronze)) {
            throw new IllegalArgumentException(
                    "bracket multipliers must be strictly ordered diamond<gold<silver<bronze (got diamond="
                            + diamond + ", gold=" + gold + ", silver=" + silver + ", bronze=" + bronze + ")");
        }
    }

    public MedalTier classify(Duration elapsed, Duration reference) {
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        Objects.requireNonNull(reference, "reference must not be null");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative: " + elapsed);
        }
        if (reference.isZero() || reference.isNegative()) {
            throw new IllegalArgumentException("reference must be positive: " + reference);
        }

        double elapsedNanos = (double) elapsed.toNanos();
        double referenceNanos = (double) reference.toNanos();

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
