package net.elytrarace.server.cup;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe implementation of {@link CupFlowService} that tracks cup progression
 * using atomic state.
 */
public final class CupFlowServiceImpl implements CupFlowService {

    private final AtomicReference<CupDefinition> currentCup = new AtomicReference<>();
    private final AtomicInteger mapIndex = new AtomicInteger(-1);

    @Override
    public void startCup(CupDefinition cup) {
        if (cup.maps().isEmpty()) {
            throw new IllegalArgumentException("Cup must contain at least one map");
        }
        currentCup.set(cup);
        mapIndex.set(0);
    }

    @Override
    public Optional<MapDefinition> getCurrentMap() {
        var cup = currentCup.get();
        int index = mapIndex.get();
        if (cup == null || index < 0) {
            return Optional.empty();
        }
        return Optional.of(cup.maps().get(index));
    }

    @Override
    public boolean hasNextMap() {
        var cup = requireCup();
        return mapIndex.get() < cup.maps().size() - 1;
    }

    @Override
    public void advanceToNextMap() {
        var cup = requireCup();
        int current = mapIndex.get();
        if (current >= cup.maps().size() - 1) {
            throw new IllegalStateException("No more maps in the cup");
        }
        mapIndex.incrementAndGet();
    }

    @Override
    public boolean isCupComplete() {
        var cup = requireCup();
        return mapIndex.get() >= cup.maps().size() - 1;
    }

    @Override
    public int getCurrentMapIndex() {
        requireCup();
        return mapIndex.get();
    }

    @Override
    public int getTotalMaps() {
        return requireCup().maps().size();
    }

    private CupDefinition requireCup() {
        var cup = currentCup.get();
        if (cup == null || mapIndex.get() < 0) {
            throw new IllegalStateException("No cup has been started");
        }
        return cup;
    }
}
