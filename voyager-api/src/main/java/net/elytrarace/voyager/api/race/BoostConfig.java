package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidBoostConfigException;

/**
 * How a firework boost behaves on one racecourse: how long a rocket burns, and how long a racer
 * waits before the next one.
 *
 * <h2>Why there is no speed in here</h2>
 *
 * <p>The old tree's {@code BoostConfig} carried {@code speedBlocksPerTick} and
 * {@code maxSpeedBlocksPerTick} because it computed the boost itself and pushed the result with
 * {@code setVelocity}. The rebuild does not: the impulse is Vanilla's own
 * ({@code velocity += look * 0.1 + (look * 1.5 - velocity) * 0.5}, applied by the rocket entity and
 * transcribed bit-exactly in {@code ElytraSimulator}), so its strength is not a tuning knob and a
 * field for it would be a number the game reads from data and then ignores. What the server does
 * decide is <em>how long</em> the rocket exists and <em>how often</em> one may be used, and those
 * are the two fields.
 *
 * <h2>Why the server owns the burn</h2>
 *
 * <p>Vanilla's rocket lifetime is {@code 10 * flightDuration + random(6) + random(7)} — between 10
 * and 21 ticks for a one-gunpowder rocket. A race in which two identical boosts are worth different
 * amounts of speed is not a race, so the random part is dropped and the server removes the rocket
 * after exactly {@link #burnDurationTicks()} ticks. {@link #VANILLA_BURN_TICKS} is the deterministic
 * core of that formula for the strongest rocket a player can craft; see its javadoc for why three.
 *
 * <h2>Why the cooldown must outlast the burn, and is checked here</h2>
 *
 * <p>{@code FlightInput.fireworkBoostActive} is a {@code boolean}, and Vanilla applies the impulse
 * once <em>per rocket</em>. Two rockets burning at once on the same player is therefore a case the
 * simulation's input type cannot express — it would apply one impulse where Vanilla applied two, and
 * the server's shadow flight would silently fall behind the client's. A cooldown longer than the
 * burn makes that overlap impossible for one player, which turns a modelling gap into a case that
 * cannot arise. So {@code cooldownTicks <= burnDurationTicks} is refused at construction, by the
 * only type that can see both numbers, rather than left to whoever edits the map file.
 *
 * <p>The cooldown is measured <strong>from the tick the burn starts</strong>, not from the tick it
 * ends. That is what makes the comparison above the whole guarantee: with the two counters starting
 * together, the next boost is possible at {@code start + cooldownTicks}, which is strictly after the
 * burn ended at {@code start + burnDurationTicks}. Measured from the end instead, any positive
 * cooldown would satisfy the rule and the comparison here would mean nothing.
 *
 * @param burnDurationTicks how many ticks one rocket boosts for; the rocket entity is removed after
 *     exactly this many, with nothing random in it
 * @param cooldownTicks how many ticks after a boost <em>starts</em> before another may be used
 */
public record BoostConfig(int burnDurationTicks, int cooldownTicks) {

    /**
     * Vanilla's deterministic burn for a three-gunpowder firework rocket: {@code 10 * flightDuration}
     * with {@code flightDuration = 3}, which is 30 ticks, or 1.5 seconds at 20 TPS.
     *
     * <p>Three, not one or two, for two reasons. It is the longest flight duration a player can
     * craft, so a racing server handing out anything shorter would be handing out a deliberately
     * weaker rocket than the game's own maximum. And the rocket the racer holds carries
     * {@code flight_duration: 3} in its item data, so what the item claims and what the server does
     * agree — a rocket that says three and burns for one is the kind of discrepancy a player notices
     * and cannot explain.
     *
     * <p>It is a derivation, not a measurement of what plays well. A balancing pass edits the map
     * file, which is where the value actually lives.
     */
    public static final int VANILLA_BURN_TICKS = 30;

    public BoostConfig {
        if (burnDurationTicks <= 0) {
            throw InvalidBoostConfigException.nonPositiveBurn(burnDurationTicks);
        }
        if (cooldownTicks <= burnDurationTicks) {
            throw InvalidBoostConfigException.cooldownNotLongerThanBurn(burnDurationTicks, cooldownTicks);
        }
    }
}
