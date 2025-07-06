package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;

/**
 * Component that stores the current phase information.
 * This is part of the flattened architecture, replacing the PhaseComponent that wraps a LinearPhaseSeries.
 */
public record SimplePhaseComponent(
    String phaseName,
    PhaseState phaseState,
    int currentTicks,
    int maxTicks
) implements Component {
    
    /**
     * Creates a new SimplePhaseComponent with the given phase name and state.
     */
    public static SimplePhaseComponent create(String phaseName, PhaseState phaseState, int currentTicks, int maxTicks) {
        return new SimplePhaseComponent(phaseName, phaseState, currentTicks, maxTicks);
    }
    
    /**
     * Creates a new SimplePhaseComponent for a preparation phase.
     */
    public static SimplePhaseComponent createPreparation() {
        return new SimplePhaseComponent("Preparation", PhaseState.RUNNING, 0, 100);
    }
    
    /**
     * Creates a new SimplePhaseComponent for a lobby phase.
     */
    public static SimplePhaseComponent createLobby() {
        return new SimplePhaseComponent("Lobby", PhaseState.RUNNING, 0, 600);
    }
    
    /**
     * Creates a new SimplePhaseComponent for a game phase.
     */
    public static SimplePhaseComponent createGame() {
        return new SimplePhaseComponent("Game", PhaseState.RUNNING, 0, 0);
    }
    
    /**
     * Creates a new SimplePhaseComponent for an end phase.
     */
    public static SimplePhaseComponent createEnd() {
        return new SimplePhaseComponent("End", PhaseState.RUNNING, 0, 200);
    }
    
    /**
     * Creates a new SimplePhaseComponent with updated ticks.
     */
    public SimplePhaseComponent withTicks(int currentTicks) {
        return new SimplePhaseComponent(phaseName, phaseState, currentTicks, maxTicks);
    }
    
    /**
     * Creates a new SimplePhaseComponent with updated state.
     */
    public SimplePhaseComponent withState(PhaseState phaseState) {
        return new SimplePhaseComponent(phaseName, phaseState, currentTicks, maxTicks);
    }
    
    /**
     * Checks if the phase is finished.
     */
    public boolean isFinished() {
        return phaseState == PhaseState.FINISHED;
    }
    
    /**
     * Checks if the phase is running.
     */
    public boolean isRunning() {
        return phaseState == PhaseState.RUNNING;
    }
    
    /**
     * Checks if the phase is skipping.
     */
    public boolean isSkipping() {
        return phaseState == PhaseState.SKIPPING;
    }
    
    /**
     * Enum representing the state of a phase.
     */
    public enum PhaseState {
        RUNNING,
        FINISHED,
        SKIPPING
    }
}