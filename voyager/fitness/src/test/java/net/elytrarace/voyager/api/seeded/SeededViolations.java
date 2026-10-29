package net.elytrarace.voyager.api.seeded;

import net.kyori.adventure.text.Component;

/**
 * A deliberate violation of the API rule that Adventure is used only for keys, for {@code ApiPurityRulesSeededTest} to
 * prove the rule is not vacuous. It sits in the API's package so that the rule's scope matches it. The architecture run
 * excludes test classes, so this class is seen by that one test alone.
 */
@SuppressWarnings("unused")
final class SeededViolations {

    private SeededViolations() {
    }

    static Component adventureTextInTheApi() {
        return Component.text("seeded");
    }

    /** A class named as a ring shape that is not a record, for {@code DesignRuleTest}'s ring-shape rule to refuse. */
    static final class Ellipse {
    }
}
