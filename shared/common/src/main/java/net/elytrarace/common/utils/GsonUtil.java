package net.elytrarace.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;

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
        GSON = new GsonBuilder()
                .registerTypeAdapter(UUID.class, new UUIDGsonAdapter())
                .create();
    }

    private GsonUtil() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }
}
