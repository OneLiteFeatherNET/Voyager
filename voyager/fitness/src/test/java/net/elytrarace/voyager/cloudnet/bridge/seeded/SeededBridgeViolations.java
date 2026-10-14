package net.elytrarace.voyager.cloudnet.bridge.seeded;

import net.elytrarace.voyager.api.race.CupDefinition;

/**
 * A deliberate violation of the bridge's dependency rule, for {@code CloudNetBoundaryRulesSeededTest} to prove the rule is
 * not vacuous. It names a race type, which the bridge may not name. This package is imported by that test alone: the
 * architecture run excludes test classes.
 */
@SuppressWarnings("unused")
final class SeededBridgeViolations {

    private SeededBridgeViolations() {
    }

    static Class<?> raceTypeNamedFromTheBridge() {
        return CupDefinition.class;
    }
}
