package net.elytrarace.voyager.platform.catalog.writer;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;

import java.time.Duration;
import java.util.List;

/**
 * Two map definitions that differ in one ring, and nothing else. Every component of the definition is set to a value
 * that is not its default, so a canonical form that drops or reorders a component cannot pass by accident.
 */
final class CanonicalFixtures {

    private CanonicalFixtures() {
    }

    /** A two-ring course with a guide point between the rings, a boost cap and non-default ring effects. */
    static MapDefinition course() {
        return course(new Vec3(-22.0, 90.5, 40.0));
    }

    /** The course of {@link #course()} with its second ring moved half a block along x. */
    static MapDefinition courseWithSecondRingMoved() {
        return course(new Vec3(-21.5, 90.5, 40.0), new BoostConfig(4, 9, 3, 1.8));
    }

    /** The course of {@link #course()} with every boost value retuned, and nothing else changed. */
    static MapDefinition courseWithRetunedBoost() {
        return course(new Vec3(-22.0, 90.5, 40.0), new BoostConfig(6, 12, 5, 2.4));
    }

    private static MapDefinition course(Vec3 secondRingCenter) {
        return course(secondRingCenter, new BoostConfig(4, 9, 3, 1.8));
    }

    private static MapDefinition course(Vec3 secondRingCenter, BoostConfig boost) {
        List<Ring> rings = List.of(
                new Ring(0, new Vec3(10.5, 78.0, 6.0), new Vec3(0, 0, 1), 6.0, 3, RingType.STANDARD),
                new Ring(1, secondRingCenter, new Vec3(0.6, 0, 0.8), 4.5, 7, RingType.BOOST));
        GuideLine guide = new GuideLine(List.of(new GuidePoint(50, new Vec3(0.5, 82.0, 20.0))), 2, 0.75,
                GuideLineMode.WINDOW, GuideParticle.DUST, 0x3366FF);
        return new MapDefinition("ridge-run", "world_ridge", new Vec3(10.5, 70.0, -4.25), rings,
                Duration.ofMillis(41_250), boost, guide, new RingEffectTuning(2.0, 0.25));
    }
}
