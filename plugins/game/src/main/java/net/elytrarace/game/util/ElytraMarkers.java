package net.elytrarace.game.util;


import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class ElytraMarkers {

    private ElytraMarkers() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    public static final Marker EXCEPTION = MarkerFactory.getMarker("EXCEPTION");
    public static final Marker MAP = MarkerFactory.getMarker("MAP");
    public static final Marker CUP = MarkerFactory.getMarker("CUP");
}
