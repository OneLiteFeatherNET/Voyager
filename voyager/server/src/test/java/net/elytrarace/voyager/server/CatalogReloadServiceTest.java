package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.catalog.ReloadOutcome;
import net.elytrarace.voyager.server.LogCapture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The operator's reload, without a server: the outcome is applied to the holder or reported, a failure is
 * logged and reported, and the work runs on the executor it was given, never on the caller's thread.
 */
class CatalogReloadServiceTest {

    private static final String PENDING = "voyager.command.reload.pending";
    private static final String FAILED = "voyager.command.reload.failed";
    private static final String REJECTED = "voyager.command.reload.rejected";
    private static final String WARNING = "voyager.command.reload.warning";

    @Test
    void anAppliedReloadIsOfferedToTheHolderAndTheSenderIsToldItWaitsForTheNextRound() {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one"));
        LoadedCatalog changed = catalog("tour-two");
        CatalogReloadService service = service(() -> new ReloadOutcome.Applied(changed, List.of()), holder);
        List<Component> replies = new ArrayList<>();

        service.request(replies::add);

        assertThat(holder.pending()).contains(changed);
        assertThat(holder.current().cup().name()).isEqualTo("tour-one");
        assertThat(replies).singleElement().satisfies(reply -> assertThat(key(reply)).isEqualTo(PENDING));
    }

    @Test
    void aRejectedReloadLeavesTheHolderAloneAndSendsEveryProblem() {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one"));
        CatalogReloadService service = service(
                () -> new ReloadOutcome.Rejected(List.of("ERROR maps/ridge.json file: broken", "ERROR cups/x.json cup: no")),
                holder);
        List<Component> replies = new ArrayList<>();

        service.request(replies::add);

        assertThat(holder.pending()).isEmpty();
        assertThat(key(replies.getFirst())).isEqualTo(REJECTED);
        assertThat(key(replies.get(1))).isEqualTo("voyager.command.reload.problem");
        assertThat(text(replies.get(1))).contains("ridge.json");
        assertThat(text(replies.get(2))).contains("x.json");
    }

    @Test
    void aWarningOfAnAppliedReloadIsSentBeforeThePendingMessage() {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one"));
        CatalogReloadService service = service(
                () -> new ReloadOutcome.Applied(catalog("tour-two"), List.of("restart needed for world 'x'")), holder);
        List<Component> replies = new ArrayList<>();

        service.request(replies::add);

        assertThat(key(replies.getFirst())).isEqualTo(WARNING);
        assertThat(text(replies.getFirst())).contains("restart needed for world 'x'");
        assertThat(key(replies.getLast())).isEqualTo(PENDING);
    }

    @Test
    void anUnexpectedFailureLeavesTheHolderAloneLogsTheCauseAndTellsTheSender() {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one"));
        IllegalStateException cause = new IllegalStateException("disk gone");
        CatalogReloadService service = service(() -> {
            throw cause;
        }, holder);
        List<Component> replies = new ArrayList<>();

        try (LogCapture log = LogCapture.of(CatalogReloadService.class)) {
            service.request(replies::add);

            assertThat(log.errorCauses()).containsExactly(cause);
            assertThat(log.errors()).singleElement().asString().contains("running catalogue is unchanged");
        }
        assertThat(holder.pending()).isEmpty();
        assertThat(replies).singleElement().satisfies(reply -> assertThat(key(reply)).isEqualTo(FAILED));
    }

    @Test
    void theReloadRunsOnTheExecutorAndNotOnTheCaller() {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one"));
        List<Runnable> queued = new ArrayList<>();
        int[] runs = {0};
        CatalogReloadService service = new CatalogReloadService(() -> {
            runs[0]++;
            return new ReloadOutcome.Rejected(List.of());
        }, holder, queued::add);

        service.request(ignored -> { });

        assertThat(runs[0]).isZero();
        assertThat(queued).hasSize(1);
        queued.forEach(Runnable::run);
        assertThat(runs[0]).isEqualTo(1);
    }

    private static CatalogReloadService service(Supplier<ReloadOutcome> reload, CatalogHolder holder) {
        return new CatalogReloadService(reload, holder, Runnable::run);
    }

    private static String key(Component component) {
        return ((TranslatableComponent) component).key();
    }

    /** The line a reload message carries: its one argument, which is the text the operator reads. */
    private static String text(Component component) {
        return ((TextComponent) ((TranslatableComponent) component).arguments().getFirst().asComponent()).content();
    }

    private static LoadedCatalog catalog(String cupName) {
        MapDefinition map = new MapDefinition("ridge", "ridge-world", new Vec3(0, 64, 0),
                List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        CupDefinition cup = new CupDefinition(cupName, List.of("ridge"), GameMode.RACE);
        return new LoadedCatalog(new CatalogSnapshot(Map.of("ridge", map), Map.of(cupName, cup)), cup, Instant.EPOCH);
    }
}
