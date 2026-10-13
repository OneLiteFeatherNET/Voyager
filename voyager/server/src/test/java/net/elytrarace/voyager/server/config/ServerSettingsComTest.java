package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.server.config.exception.InvalidSettingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The computer racer settings as the server reads them. Each test reads its own properties map and points the two
 * directories at its own temp folders, so no test depends on another or on the JVM's properties.
 */
class ServerSettingsComTest {

    @TempDir
    Path root;

    @Test
    void anAbsentCountIsZero() throws IOException {
        assertThat(settingsWith(Map.of()).comCount())
                .as("no %s set: the server runs no computer racers", ServerSettings.COM_COUNT_PROPERTY)
                .isZero();
    }

    @Test
    void aCountOfSixteenIsRefusedAndNamesTheRange() throws IOException {
        assertThatThrownBy(() -> settingsWith(Map.of(ServerSettings.COM_COUNT_PROPERTY, "16")))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.COM_COUNT_PROPERTY)
                .hasMessageContaining("0 to 15")
                .hasMessageContaining("16");
    }

    @Test
    void aCountThatIsNotANumberIsRefusedAndNamesTheValue() throws IOException {
        assertThatThrownBy(() -> settingsWith(Map.of(ServerSettings.COM_COUNT_PROPERTY, "five")))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.COM_COUNT_PROPERTY)
                .hasMessageContaining("five");
    }

    @Test
    void anAbsentDifficultyIsMedium() throws IOException {
        assertThat(settingsWith(Map.of()).comDifficulty()).isEqualTo(ComDifficulty.MEDIUM);
    }

    @Test
    void aDifficultyIsCaseInsensitive() throws IOException {
        assertThat(settingsWith(Map.of(ServerSettings.COM_DIFFICULTY_PROPERTY, "hard")).comDifficulty())
                .isEqualTo(ComDifficulty.HARD);
    }

    @Test
    void anUnknownDifficultyIsRefusedAndListsTheThreeNames() throws IOException {
        assertThatThrownBy(() -> settingsWith(Map.of(ServerSettings.COM_DIFFICULTY_PROPERTY, "expert")))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining(ServerSettings.COM_DIFFICULTY_PROPERTY)
                .hasMessageContaining("EASY")
                .hasMessageContaining("MEDIUM")
                .hasMessageContaining("HARD");
    }

    private ServerSettings settingsWith(Map<String, String> extra) throws IOException {
        Path data = Files.createDirectories(root.resolve("catalog"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Map<String, String> properties = new HashMap<>();
        properties.put(ServerSettings.DATA_PATH_PROPERTY, data.toString());
        properties.put(ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
        properties.putAll(extra);
        return ServerSettings.fromProperties(new String[0], properties::get);
    }
}
