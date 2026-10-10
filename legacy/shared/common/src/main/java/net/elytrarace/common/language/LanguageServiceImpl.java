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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

class LanguageServiceImpl implements LanguageService {

    private static final String DEFAULT_LANGUAGE_TAG = "en-US";
    private static final String PROPERTIES_SUFFIX = ".properties";

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
            if (Files.exists(langFolder)) {
                languages.addAll(discoverLanguagesFromFolder(langFolder));
                if (languages.isEmpty()) {
                    languages.add(DEFAULT_LANGUAGE_TAG);
                }
                try (var urlClassLoader = new URLClassLoader(new URL[]{langFolder.toUri().toURL()})) {
                    languages.stream().map(Locale::forLanguageTag).forEach(r -> {
                        var bundle = ResourceBundle.getBundle(baseName, r, urlClassLoader, UTF8ResourceBundleControl.get());
                        translationRegistry.registerAll(r, bundle, false);
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                languages.add(DEFAULT_LANGUAGE_TAG);
                languages.stream().map(Locale::forLanguageTag).forEach(r -> {
                    var bundle = ResourceBundle.getBundle(baseName, r, UTF8ResourceBundleControl.get());
                    translationRegistry.registerAll(r, bundle, false);
                });
            }
            GlobalTranslator.translator().addSource(translationRegistry);
        });
    }

    private Set<String> discoverLanguagesFromFolder(Path langFolder) {
        var discovered = new HashSet<String>();
        try (Stream<Path> files = Files.list(langFolder)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(baseName) && name.endsWith(PROPERTIES_SUFFIX))
                    .forEach(name -> {
                        String tag = extractLanguageTag(name);
                        if (tag != null) {
                            discovered.add(tag);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return discovered;
    }

    private String extractLanguageTag(String fileName) {
        String stripped = fileName.substring(0, fileName.length() - PROPERTIES_SUFFIX.length());
        if (stripped.equals(baseName)) {
            return DEFAULT_LANGUAGE_TAG;
        }
        String prefix = baseName + "_";
        if (!stripped.startsWith(prefix)) {
            return null;
        }
        String localePart = stripped.substring(prefix.length());
        if (localePart.isEmpty()) {
            return null;
        }
        return localePart.replace('_', '-');
    }
}
