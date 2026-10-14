package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.race.flow.RaceClock;

import java.util.List;
import java.util.UUID;

/** The {@link CompetitorField} of a cup with no computer racers: it prepares nobody, reports nobody and dismisses nobody. */
public enum NoCompetitors implements CompetitorField {

    INSTANCE;

    @Override
    public List<UUID> prepare(int mapIndex, MapDefinition map) {
        return List.of();
    }

    @Override
    public List<Competitor> advance(RaceClock clock) {
        return List.of();
    }

    @Override
    public void dismiss() {
        // Nobody was prepared, so there is nobody to let go.
    }
}
