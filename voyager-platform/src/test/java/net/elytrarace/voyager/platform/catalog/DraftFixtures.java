package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import java.util.List;

/** Drafts for the storage tests: an empty skeleton, and a complete draft that the game server can load. */
public final class DraftFixtures {

    private static final double RADIUS = Math.sqrt(13);

    private DraftFixtures() {
    }

    public static MapDraft skeleton(MapId id) {
        return new MapDraft(id, id.value(), null, List.of(), 60.0, new BoostConfig(30, 40),
                new GuideLine(List.of(), 2, 1.0));
    }

    /** A spawn, two rings and one guide point: every key the game's adapter reads is present. */
    public static MapDraft complete(MapId id) {
        List<Ring> rings = List.of(
                new Ring(0, new Vec3(85, -54, 54), new Vec3(-1, 0, 0), RADIUS, 10, RingType.STANDARD),
                new Ring(1, new Vec3(80, -54, 54), new Vec3(-1, 0, 0), RADIUS, 10, RingType.BOOST));
        GuideLine guide = new GuideLine(List.of(new GuidePoint(0, new Vec3(84, -54, 54))), 2, 1.0);
        return new MapDraft(id, id.value(), new Vec3(109, -62, 54), rings, 60.0, new BoostConfig(30, 40), guide);
    }
}
