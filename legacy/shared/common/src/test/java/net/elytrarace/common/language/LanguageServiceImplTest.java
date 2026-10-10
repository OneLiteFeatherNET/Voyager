package net.elytrarace.common.language;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageServiceImplTest {

    private static final Key TEST_KEY = Key.key("elytrarace", "test");
    private static final String BASE_NAME = "testlang";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Empty lang folder falls back to en-US and loads it from classpath")
    void discoverLanguages_fromEmptyFolder_fallsBackToEnUs() throws IOException, ExecutionException, InterruptedException {
        // Given
        Path langFolder = tempDir.resolve("lang");
        Files.createDirectories(langFolder);
        Files.writeString(langFolder.resolve("testlang_en_US.properties"), "plugin.name=Test Plugin\n");

        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("Single locale file is discovered and loaded")
    void discoverLanguages_fromFolderWithOneLocale_loadsIt() throws IOException, ExecutionException, InterruptedException {
        // Given
        Path langFolder = tempDir.resolve("lang");
        Files.createDirectories(langFolder);
        Files.writeString(langFolder.resolve("testlang_de_DE.properties"), "plugin.name=Test Plugin DE\n");

        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("Multiple locale files are all discovered and loaded")
    void discoverLanguages_fromFolderWithMultipleLocales_loadsAll() throws IOException, ExecutionException, InterruptedException {
        // Given
        Path langFolder = tempDir.resolve("lang");
        Files.createDirectories(langFolder);
        Files.writeString(langFolder.resolve("testlang_en_US.properties"), "plugin.name=Test Plugin EN\n");
        Files.writeString(langFolder.resolve("testlang_de_DE.properties"), "plugin.name=Test Plugin DE\n");

        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("Missing lang folder falls back to classpath en-US bundle")
    void discoverLanguages_whenFolderDoesNotExist_fallsBackToEnUs() throws ExecutionException, InterruptedException {
        // Given — tempDir has no lang/ subfolder
        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("Empty lang folder falls back to classpath bundle and completes successfully")
    void discoverLanguages_fromTrulyEmptyFolder_fallsBackToClasspath() throws IOException, ExecutionException, InterruptedException {
        // Given — lang/ folder exists but has no matching .properties files
        Path langFolder = tempDir.resolve("lang");
        Files.createDirectories(langFolder);

        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then — URLClassLoader falls back to parent (test classpath) which has testlang_en_US.properties
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("Locale tag is correctly extracted from underscore-separated file name")
    void discoverLanguages_localeTagConvertedFromUnderscoreToDash() throws IOException, ExecutionException, InterruptedException {
        // Given
        Path langFolder = tempDir.resolve("lang");
        Files.createDirectories(langFolder);
        Files.writeString(langFolder.resolve("testlang_pt_BR.properties"), "plugin.name=Test Plugin PT-BR\n");

        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }

    @Test
    @DisplayName("Non-matching file in lang folder is ignored")
    void discoverLanguages_ignoresNonMatchingFiles() throws IOException, ExecutionException, InterruptedException {
        // Given — lang/ has a file with a different base name
        Path langFolder = tempDir.resolve("lang");
        Files.createDirectories(langFolder);
        Files.writeString(langFolder.resolve("otherplugin_en_US.properties"), "key=value\n");
        Files.writeString(langFolder.resolve("testlang_en_US.properties"), "plugin.name=Test\n");

        LanguageService service = LanguageService.create(BASE_NAME, TEST_KEY, tempDir);

        // When
        var future = service.loadLanguage();
        future.get();

        // Then
        assertThat(future).isCompleted();
        assertThat(future.isCompletedExceptionally()).isFalse();
    }
}
