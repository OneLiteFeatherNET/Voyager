package net.elytrarace.voyager.api.race.exception;

/** Thrown when a {@code BoostConfig} is constructed with values a race cannot be run on. */
public final class InvalidBoostConfigException extends RuntimeException {

    private InvalidBoostConfigException(String message) {
        super(message);
    }

    public static InvalidBoostConfigException nonPositiveBurn(int burnDurationTicks) {
        return new InvalidBoostConfigException(
                "boost burn duration must be positive, was %s tick(s)".formatted(burnDurationTicks));
    }

    /** A stack a racer cannot hold: the item stack limit is {@code BoostConfig.MAX_ROCKETS_PER_RACER}. */
    public static InvalidBoostConfigException rocketsOutOfRange(int rocketsPerRacer) {
        return new InvalidBoostConfigException(
                "boost field rocketsPerRacer must be between 1 and 64, was %s".formatted(rocketsPerRacer));
    }

    /** A cap that is not a speed: it must be finite and greater than zero, or it is left out to mean no cap. */
    public static InvalidBoostConfigException nonPositiveSpeedCap(double maxSpeedBlocksPerTick) {
        return new InvalidBoostConfigException(
                "boost field maxSpeedBlocksPerTick must be finite and greater than zero, was %s"
                        .formatted(maxSpeedBlocksPerTick));
    }

    /**
     * The refusal that exists for a reason outside the config: the simulation's boost input is a
     * boolean, so two rockets burning on one player at the same time cannot be told from one. The
     * message says that rather than only stating the arithmetic, because the obvious "fix" — lowering
     * the cooldown so boosts come faster — is the one that reintroduces it.
     */
    public static InvalidBoostConfigException cooldownNotLongerThanBurn(int burnDurationTicks, int cooldownTicks) {
        return new InvalidBoostConfigException(
                ("boost cooldown must be longer than the burn it is measured from the start of, so two "
                        + "rockets can never burn on one racer at once; the simulation's boost input is "
                        + "a boolean and cannot express the overlap. Was a cooldown of %s tick(s) "
                        + "against a burn of %s.").formatted(cooldownTicks, burnDurationTicks));
    }
}
