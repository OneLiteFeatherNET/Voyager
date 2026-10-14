package net.elytrarace.voyager.seeded;

import net.elytrarace.voyager.api.handoff.PlayerHandoff;
import net.elytrarace.voyager.api.handoff.PlayerHandoffs;

/**
 * A deliberate install of the hub handoff outside the bridge, for {@code HandoffRuleSeededTest} to prove the install rule is
 * not vacuous. This package is imported by that test alone: the architecture run excludes test classes.
 */
@SuppressWarnings("unused")
final class SeededHandoffInstall {

    private SeededHandoffInstall() {
    }

    static void installFromTheServer() {
        PlayerHandoffs.SLOT.install(playerId -> { });
    }

    static PlayerHandoff unused() {
        return playerId -> { };
    }
}
