package net.elytrarace.common.language;

import net.elytrarace.common.utils.PluginTranslationRegistry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

class LanguageServiceImpl implements LanguageService {

    private volatile Path dataPath;
    private volatile Key key;
    private volatile String baseName;

    LanguageServiceImpl(String baseName, Key key, Path dataPath) {
        this.baseName = baseName;
        this.key = key;
        this.dataPath = dataPath;
    }

    @Override
    public CompletableFuture<Void> loadLanguage() {
        return CompletableFuture.runAsync(() -> {
            final TranslationRegistry translationRegistry = new PluginTranslationRegistry(TranslationRegistry.create(key));
            translationRegistry.defaultLocale(Locale.US);
            Path langFolder = dataPath.resolve("lang");
            var languages = new HashSet<String>();
            languages.add("en-US");
            if (Files.exists(langFolder)) {
                try (var urlClassLoader = new URLClassLoader(new URL[]{langFolder.toUri().toURL()})) {
                    languages.stream().map(Locale::forLanguageTag).forEach(r -> {
                        var bundle = ResourceBundle.getBundle(baseName, r, urlClassLoader, UTF8ResourceBundleControl.get());
                        translationRegistry.registerAll(r, bundle, false);
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                languages.stream().map(Locale::forLanguageTag).forEach(r -> {
                    var bundle = ResourceBundle.getBundle(baseName, r, UTF8ResourceBundleControl.get());
                    translationRegistry.registerAll(r, bundle, false);
                });
            }
            GlobalTranslator.translator().addSource(translationRegistry);
        });
    }
}
