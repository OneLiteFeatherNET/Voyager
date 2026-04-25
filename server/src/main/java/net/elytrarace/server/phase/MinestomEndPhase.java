package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.GameModeComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.persistence.GameResultPersistenceService;
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
import java.util.Comparator;
import java.util.List;

/**
 * End phase for the Minestom server.
 * <p>
 * Counts down from a configurable number of ticks (default 100 ticks = 5 seconds at 20 TPS)
 * while displaying the results and remaining time to all players. When the countdown
 * finishes, the server is stopped.
 * <p>
 * Final scores are read from {@link ScoreComponent} on player entities in the
 * {@link EntityManager}. In {@link GameMode#RACE} mode, position bonuses are
 * applied directly from ECS state (1st: +10, 2nd: +6, 3rd: +3, rest: +1).
 * In {@link GameMode#PRACTICE} mode (and any other) no position bonus is added.
 */
public final class MinestomEndPhase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomEndPhase.class);
    private static final int DEFAULT_END_TICKS = 100;

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
     * and applies position bonuses in {@link GameMode#RACE}. The mode is read
     * from the {@link GameModeComponent} attached to the game entity; if no mode
     * is attached, RACE behaviour is used as the default.
     *
     * @param entityManager source of player entities
     * @return ranked list with bonuses applied (index 0 = 1st place)
     */
    static List<Entity> rankAndApplyBonuses(EntityManager entityManager) {
        List<Entity> ranked = entityManager.getEntitiesWithComponent(ScoreComponent.class).stream()
                .filter(e -> e.hasComponent(PlayerRefComponent.class))
                .sorted(Comparator.<Entity, Integer>comparing(
                        e -> e.getComponent(ScoreComponent.class).getTotal()).reversed())
                .toList();

        GameMode mode = findGameMode(entityManager);
        if (mode == null || mode == GameMode.RACE) {
            applyPositionBonuses(ranked);
        }
        return ranked;
    }

    private static @Nullable GameMode findGameMode(EntityManager entityManager) {
        for (Entity entity : entityManager.getEntities()) {
            if (entity.hasComponent(GameModeComponent.class)) {
                return entity.getComponent(GameModeComponent.class).mode();
            }
        }
        return null; // null → default to RACE behaviour
    }

    private static void applyPositionBonuses(List<Entity> ranked) {
        int[] bonuses = {10, 6, 3};
        for (int i = 0; i < ranked.size(); i++) {
            int bonus = i < bonuses.length ? bonuses[i] : 1;
            ranked.get(i).getComponent(ScoreComponent.class).addPositionBonus(bonus);
        }
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
