package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.platform.stats.StatsRequests;
import net.elytrarace.voyager.server.config.PersistenceSettings;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.persistence.StatsBinding;

import java.time.Clock;

/**
 * The statistics wiring: the recorder the cup calls and the reader the commands call, both taken from the one binding
 * that {@link StatsBinding#of} decides at boot from the persistence settings.
 *
 * <p>The binding is built here, when the graph is built, so the one opening attempt happens at boot and never on the tick.
 */
@Factory
public final class PersistenceBeans {

    @Bean
    StatsBinding statsBinding(@External PersistenceSettings persistence, @External ServerSettings server, Clock clock) {
        return StatsBinding.of(persistence.database(), clock, StatsBinding.writerLog(),
                StatsBinding.databaseOpener(server.devMode()));
    }

    @Bean
    RaceRecorder raceRecorder(StatsBinding binding) {
        return binding.recorder();
    }

    @Bean
    StatsReader statsReader(StatsBinding binding) {
        return binding.reader();
    }

    /** The read queue behind {@code /stats} and {@code /leaderboard}: reads on the reader pool, replies from the tick. */
    @Bean
    StatsRequests statsRequests(Clock clock) {
        return new StatsRequests(StatsRequests.readerPool(), clock);
    }
}
