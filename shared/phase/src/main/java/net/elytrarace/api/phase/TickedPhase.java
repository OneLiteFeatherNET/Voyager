package net.elytrarace.api.phase;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:40
 */

public abstract class TickedPhase extends GamePhase {

    public TickedPhase(EventRegistrar eventRegistrar) {
        super(eventRegistrar);
    }

    public TickedPhase(String name, EventRegistrar eventRegistrar) {
        super(name, eventRegistrar);
    }

    public abstract void onUpdate();
}
