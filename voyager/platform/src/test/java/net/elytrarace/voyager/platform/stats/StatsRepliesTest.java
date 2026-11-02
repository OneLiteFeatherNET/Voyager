package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.api.stats.PlayerProfile;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.writer.LayoutVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The best-time line of {@code /stats}: the map is named by its catalogue display name, and a map that is no
 * longer in the catalogue is named by its plain key. No server and no clock: the profile and the catalogue are
 * given directly.
 */
class StatsRepliesTest {

    private static final UUID RACER = UUID.fromString("00000000-0000-0000-0000-00000000a1ce");
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final String KEY = "elytrarace:memoria";
    private static final MapDefinition MEMORIA = new MapDefinition("memoria", "memoria-world", new Vec3(0, 64, 0),
            List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
            Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0),
            RingEffectTuning.DEFAULT, "Memoria");

    @Test
    void theBestTimeLineNamesTheMapByItsCatalogueDisplayName() {
        CatalogSnapshot catalog = new CatalogSnapshot(Map.of("memoria", MEMORIA), Map.of());

        TranslatableComponent best = bestLine(StatsReplies.profile(profileOn(KEY, 62_300), "Alex", catalog));

        assertThat(plain(best.arguments().getFirst().asComponent()))
                .as("the first argument of the best-time line")
                .isEqualTo("Memoria");
    }

    @Test
    void aMapNoLongerInTheCatalogueIsNamedByItsPlainKey() {
        CatalogSnapshot catalog = new CatalogSnapshot(Map.of(), Map.of());

        TranslatableComponent best = bestLine(StatsReplies.profile(profileOn(KEY, 62_300), "Alex", catalog));

        assertThat(plain(best.arguments().getFirst().asComponent()))
                .as("the plain key stands in for the missing map")
                .isEqualTo(KEY);
    }

    @Test
    void theBestTimeLineStillShowsTheTimeWhenTheMapIsGone() {
        CatalogSnapshot catalog = new CatalogSnapshot(Map.of(), Map.of());

        TranslatableComponent best = bestLine(StatsReplies.profile(profileOn(KEY, 62_300), "Alex", catalog));

        assertThat(plain(best.arguments().get(1).asComponent()))
                .as("the time is still formatted for the reader")
                .isEqualTo("62.300");
    }

    private static PlayerProfile profileOn(String mapKey, int bestTimeMillis) {
        return new PlayerProfile(RACER, "Alex", 1, 0, 0, 0, 0, 1, 90_000L,
                List.of(new PlayerProfile.PersonalBest(mapKey, LayoutVersion.of(MEMORIA), GameMode.RACE,
                        bestTimeMillis, NOW)));
    }

    private static TranslatableComponent bestLine(List<Component> lines) {
        return lines.stream()
                .filter(line -> line instanceof TranslatableComponent translatable
                        && translatable.key().equals("stats.profile.best"))
                .map(TranslatableComponent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no best-time line in %s".formatted(lines)));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
