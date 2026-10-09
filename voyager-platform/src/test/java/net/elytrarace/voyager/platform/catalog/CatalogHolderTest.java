package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogHolderTest {

    private static final Instant EPOCH = Instant.EPOCH;

    @Test
    void currentIsTheInitialCatalogUntilARoundPromotes() {
        LoadedCatalog first = catalog("first");
        CatalogHolder holder = new CatalogHolder(first);

        holder.offer(catalog("second"));

        assertThat(holder.current()).isEqualTo(first);
    }

    @Test
    void offerLeavesAPendingCatalogWithoutChangingTheCurrentOne() {
        LoadedCatalog second = catalog("second");
        CatalogHolder holder = new CatalogHolder(catalog("first"));

        holder.offer(second);

        assertThat(holder.pending()).contains(second);
    }

    @Test
    void promoteAppliesThePendingCatalogOnce() {
        LoadedCatalog second = catalog("second");
        CatalogHolder holder = new CatalogHolder(catalog("first"));
        holder.offer(second);

        LoadedCatalog promoted = holder.promoteForNewRound();
        LoadedCatalog again = holder.promoteForNewRound();

        assertThat(promoted).isEqualTo(second);
        assertThat(again).isEqualTo(second);
        assertThat(holder.current()).isEqualTo(second);
        assertThat(holder.pending()).isEmpty();
    }

    @Test
    void promoteWithNothingPendingKeepsTheCurrentCatalog() {
        LoadedCatalog first = catalog("first");
        CatalogHolder holder = new CatalogHolder(first);

        assertThat(holder.promoteForNewRound()).isEqualTo(first);
    }

    @Test
    void twoOffersBeforeAPromoteLeaveTheLaterOne() {
        LoadedCatalog later = catalog("later");
        CatalogHolder holder = new CatalogHolder(catalog("first"));

        holder.offer(catalog("earlier"));
        holder.offer(later);

        assertThat(holder.promoteForNewRound()).isEqualTo(later);
    }

    @Test
    void aCatalogAlreadyPinnedByARoundDoesNotChangeWhenALaterOneIsPromoted() {
        LoadedCatalog first = catalog("first");
        CatalogHolder holder = new CatalogHolder(first);
        LoadedCatalog pinnedByRoundOne = holder.promoteForNewRound();

        holder.offer(catalog("second"));
        holder.promoteForNewRound();

        assertThat(pinnedByRoundOne).isEqualTo(first);
        assertThat(holder.current()).isNotEqualTo(first);
    }

    @Test
    void concurrentOffersLeaveOneOfTheOfferedCatalogsPending() throws InterruptedException {
        List<LoadedCatalog> offered = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            offered.add(catalog("offer-" + index));
        }
        CatalogHolder holder = new CatalogHolder(catalog("first"));
        List<Thread> threads = new CopyOnWriteArrayList<>();
        for (LoadedCatalog candidate : offered) {
            threads.add(Thread.ofPlatform().start(() -> holder.offer(candidate)));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        Optional<LoadedCatalog> pending = holder.pending();

        assertThat(pending).isPresent();
        assertThat(offered).contains(pending.get());
    }

    private static LoadedCatalog catalog(String tag) {
        MapDefinition map = CatalogFixtures.mapNamed("map-" + tag);
        CupDefinition cup = CatalogFixtures.cupNamed("cup-" + tag, map.name());
        return new LoadedCatalog(new CatalogSnapshot(Map.of(map.name(), map), Map.of(cup.name(), cup)), cup, EPOCH);
    }
}
