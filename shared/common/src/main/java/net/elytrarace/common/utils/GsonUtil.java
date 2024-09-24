package net.elytrarace.common.utils;

import com.google.gson.Gson;

/**
 * Utility class for Gson
 */
public final class GsonUtil {

    /**
     * Gson instance
     */
    public static final Gson GSON;

    static {
        // Add custom Gson configuration here
        GSON = new Gson();
    }

    private GsonUtil() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }
}
