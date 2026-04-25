package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.ecs.component.ScoringStrategyComponent;
import net.elytrarace.server.persistence.GameResultPersistenceService;
import net.elytrarace.server.scoring.PlayerScore;
import net.elytrarace.server.scoring.ScoringStrategy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.utils.time.TimeUnit;
import net.theevilreaper.xerus.api.phase.TickDirection;
import net.theevilreaper.xerus.api.phase.TimedPhase;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * End phase for the Minestom server.
 * <p>
 * Counts down from a configurable number of ticks (default 100 ticks = 5 seconds at 20 TPS)
 * while displaying the results and remaining time to all players. When the countdown
 * finishes, the server is stopped.
 * <p>
 * Final scores are read from {@link ScoreComponent} on player entities in the
 * {@link EntityManager}. Position bonuses (1st: 50, 2nd: 30, 3rd: 20, rest: 10)
 * are applied and displayed as a title overlay.
 */
public final class MinestomEndPhase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomEndPhase.class);
    private static final int DEFAULT_END_TICKS = 100;
    private static final int[] POSITION_BONUSES = {50, 30, 20};
    private static final int DEFAULT_BONUS = 10;

    private final int endTicksValue;
    private final @Nullable EntityManager entityManager;
    private final @Nullable GameResultPersistenceService persistenceService;
    private boolean finishing = false;
    private boolean suppressOnFinish = false;

    public MinestomEndPhase() {
        this(DEFAULT_END_TICKS, null, null);
    }

    public MinestomEndPhase(int endTicks, @Nullable EntityManager entityManager) {
        this(endTicks, entityManager, null);
    }

    public MinestomEndPhase(int endTicks,
                            @Nullable EntityManager entityManager,
                            @Nullable GameResultPersistenceService persistenceService) {
        super("end", TimeUnit.SERVER_TICK, 20);
        this.endTicksValue = endTicks;
        this.entityManager = entityManager;
        this.persistenceService = persistenceService;
        setEndTicks(0);
        setCurrentTicks(endTicks);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void finish() {
        if (finishing) {
            return;
        }
        finishing = true;
        super.finish();
    }

    @Override
    public void onStart() {
        finishing = false;
        setCurrentTicks(endTicksValue);
        super.onStart();
        LOGGER.info("End phase started — showing results for {} ticks", endTicksValue);
        displayFinalScores();
    }

    @Override
    public void onUpdate() {
        PhaseUiHelper.broadcastTimeActionBar("phase.end.time", getCurrentTicks());
    }

    @Override
    protected void onFinish() {
        if (suppressOnFinish) {
            LOGGER.info("End phase finished — server stop suppressed (game restart)");
            return;
        }
        LOGGER.info("End phase finished — scheduling server stop");
        // Schedule stop for the next tick so the finish() chain completes cleanly
        // before the server shuts down (avoids "Advance called on not running phase").
        MinecraftServer.getSchedulerManager()
                .buildTask(MinecraftServer::stopCleanly)
                .delay(1, TimeUnit.SERVER_TICK)
                .schedule();
    }

    /**
     * Suppresses the server stop that normally occurs when the end phase finishes.
     * <p>
     * Call this before {@link #finish()} during a game restart to prevent the
     * end phase from shutting down the server.
     *
     * @param suppress {@code true} to suppress the server stop
     */
    public void setSuppressOnFinish(boolean suppress) {
        this.suppressOnFinish = suppress;
    }

    /**
     * Ranks all player entities by {@link ScoreComponent#getTotal()} descending
     * and applies position bonuses.
     * <p>
     * If a {@link ScoringStrategy} is attached to the game entity, the strategy
     * is the source of truth: {@link ScoringStrategy#applyMapResults()} runs
     * first, then position bonuses and medal tiers are propagated back into the
     * {@link ScoreComponent} so the HUD reflects what the strategy decided.
     * Without a strategy (legacy unit-test path) the fallback applies the
     * historical {@code 50 / 30 / 20 / 10} bonuses directly on ECS state.
     *
     * @param entityManager source of player entities and the strategy component
     * @return ranked list with bonuses applied (index 0 = 1st place)
     */
    static List<Entity> rankAndApplyBonuses(EntityManager entityManager) {
        ScoringStrategy strategy = findStrategy(entityManager);
        if (strategy != null) {
            strategy.applyMapResults();
            return rankFromStrategy(entityManager, strategy);
        }
        return rankWithLegacyBonuses(entityManager);
    }

    /**
     * Applies the ranking decided by the strategy to the ECS entities, copying
     * position bonuses and medal tiers into each {@link ScoreComponent}.
     */
    private static List<Entity> rankFromStrategy(EntityManager entityManager, ScoringStrategy strategy) {
        Map<UUID, Entity> byPlayer = new HashMap<>();
        for (Entity entity : entityManager.getEntitiesWithComponent(ScoreComponent.class)) {
            if (entity.hasComponent(PlayerRefComponent.class)) {
                byPlayer.put(entity.getComponent(PlayerRefComponent.class).getPlayerId(), entity);
            }
        }

        List<PlayerScore> ranking = strategy.getRanking();
        List<Entity> ranked = new ArrayList<>(ranking.size());
        for (PlayerScore ps : ranking) {
            Entity entity = byPlayer.get(ps.playerId());
            if (entity == null) {
                continue;
            }
            ScoreComponent score = entity.getComponent(ScoreComponent.class);
            score.setPositionBonus(ps.positionBonus());
            if (ps.medalTier() != null) {
                score.setMedalTier(ps.medalTier());
            }
            ranked.add(entity);
        }
        return List.copyOf(ranked);
    }

    /**
     * Legacy ranking path used when no {@link ScoringStrategy} is attached
     * (unit tests that build entities directly).
     */
    private static List<Entity> rankWithLegacyBonuses(EntityManager entityManager) {
        List<Entity> ranked = entityManager.getEntitiesWithComponent(ScoreComponent.class).stream()
                .filter(e -> e.hasComponent(PlayerRefComponent.class))
                .sorted(Comparator.<Entity, Integer>comparing(
                        e -> e.getComponent(ScoreComponent.class).getTotal()).reversed())
                .toList();

        for (int i = 0; i < ranked.size(); i++) {
            int bonus = i < POSITION_BONUSES.length ? POSITION_BONUSES[i] : DEFAULT_BONUS;
            ranked.get(i).getComponent(ScoreComponent.class).setPositionBonus(bonus);
        }
        return ranked;
    }

    private static @Nullable ScoringStrategy findStrategy(EntityManager entityManager) {
        for (Entity entity : entityManager.getEntities()) {
            if (entity.hasComponent(ScoringStrategyComponent.class)) {
                return entity.getComponent(ScoringStrategyComponent.class).strategy();
            }
        }
        return null;
    }

    /**
     * Calculates position bonuses and displays the final scoreboard as title
     * overlays to all players. Scores are read from {@link ScoreComponent}.
     */
    private void displayFinalScores() {
        if (entityManager == null) {
            LOGGER.warn("No EntityManager provided — skipping final score display");
            return;
        }

        List<Entity> ranked = rankAndApplyBonuses(entityManager);
        persistResultsAsync(ranked);

        var scoreboardBuilder = Component.text()
                .append(Component.translatable("end.final_results"))
                .append(Component.newline());

        for (int i = 0; i < ranked.size(); i++) {
            var ref = ranked.get(i).getComponent(PlayerRefComponent.class);
            var score = ranked.get(i).getComponent(ScoreComponent.class);
            scoreboardBuilder
                    .append(Component.translatable(
                            "end.score_line",
                            Component.text(i + 1),
                            Component.text(ref.getPlayer().getUsername()),
                            Component.text(score.getTotal())))
                    .append(Component.newline());
        }

        var scoreboard = scoreboardBuilder.build();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.showTitle(Title.title(
                    Component.translatable("end.race_complete"),
                    ranked.isEmpty() ? Component.empty()
                            : Component.translatable(
                                    "end.winner",
                                    Component.text(ranked.getFirst()
                                            .getComponent(PlayerRefComponent.class)
                                            .getPlayer().getUsername())),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
            player.sendMessage(scoreboard);
        }

        LOGGER.info("Final scores displayed for {} players", ranked.size());
    }

    /**
     * Dispatches persistence off the tick thread so a slow DB never stalls the
     * game loop. Failures are swallowed inside the persistence service — this
     * method never throws.
     */
    private void persistResultsAsync(List<Entity> ranked) {
        if (persistenceService == null || entityManager == null || ranked.isEmpty()) {
            return;
        }
        String cupName = resolveCupName();
        String mapName = resolveMapName();
        persistenceService.persistResults(cupName, mapName, ranked)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Unexpected error while persisting game results", ex);
                    } else {
                        LOGGER.info("Persisted game results for cup '{}' map '{}' ({} entries)",
                                cupName, mapName, ranked.size());
                    }
                });
    }

    private String resolveCupName() {
        if (entityManager == null) return "unknown";
        return entityManager.getEntitiesWithComponent(CupProgressComponent.class).stream()
                .findFirst()
                .map(e -> e.getComponent(CupProgressComponent.class).getCup().name())
                .orElse("unknown");
    }

    private String resolveMapName() {
        if (entityManager == null) return "unknown";
        return entityManager.getEntitiesWithComponent(ActiveMapComponent.class).stream()
                .findFirst()
                .map(e -> e.getComponent(ActiveMapComponent.class).getCurrentMap())
                .filter(m -> m != null)
                .map(net.elytrarace.server.cup.MapDefinition::name)
                .orElse("unknown");
    }
}
