package net.elytrarace.common.language;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing the language.
 */
public interface LanguageService {

    /**
     * Load the language files.
     *
     * @return A future that completes when the language files are loaded.
     */
    CompletableFuture<Void> loadLanguage();

    /**
     * Create a new instance of the service.
     *
     * @param baseName The base name of the language files.
     * @param key The key of the plugin.
     * @param dataPath The data path for language files.
     * @return The service.
     */
    @Contract(value = "_, _, _ -> new", pure = true)
    static @NotNull LanguageService create(@NotNull String baseName, @NotNull Key key, @NotNull Path dataPath) {
        return new LanguageServiceImpl(baseName, key, dataPath);
    }

}
