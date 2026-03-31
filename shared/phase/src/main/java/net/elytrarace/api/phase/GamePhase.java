package net.elytrarace.api.phase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 14.01.2020 08:51
 */
public abstract class GamePhase extends Phase {

    private final EventRegistrar eventRegistrar;
    private List<Object> phaseListeners;

    public GamePhase(EventRegistrar eventRegistrar) {
        this.eventRegistrar = eventRegistrar;
    }

    public GamePhase(String name, EventRegistrar eventRegistrar) {
        super(name);
        this.eventRegistrar = eventRegistrar;
    }

    @Override
    public void start() {
        super.start();

        eventRegistrar.registerListener(this);
        if (phaseListeners != null && !phaseListeners.isEmpty())
            phaseListeners.forEach(eventRegistrar::registerListener);
    }

    @Override
    public void finish() {
        eventRegistrar.unregisterListener(this);
        if (phaseListeners != null)
            phaseListeners.forEach(eventRegistrar::unregisterListener);

        super.finish();
    }

    public EventRegistrar getEventRegistrar() {
        return eventRegistrar;
    }

    public void addPhaseListener(Object listener) {
        if (phaseListeners == null)
            phaseListeners = new ArrayList<>(3);

        phaseListeners.add(listener);
    }

    public void removePhaseListener(Object listener) {
        if (phaseListeners != null)
            phaseListeners.remove(listener);
    }
}
