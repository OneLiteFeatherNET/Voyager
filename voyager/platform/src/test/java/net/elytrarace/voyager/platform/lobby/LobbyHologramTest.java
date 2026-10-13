package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby hologram: one text display at the configured position in the lobby instance, whose text follows the board. Each test
 * builds its own instance.
 */
@EnvTest
class LobbyHologramTest {

    private static final Pos POSITION = new Pos(0.5, 70.0, 0.5);

    private static final LobbyBoard GRAND_TOUR = LobbyBoard.of(
            new CupDefinition("Grand Tour", List.of("alpha", "bravo"), GameMode.RACE));
    private static final LobbyBoard GRAND_TOUR_II = LobbyBoard.of(
            new CupDefinition("Grand Tour II", List.of("delta", "echo"), GameMode.RACE));

    @Test
    void theHologramStandsAtTheConfiguredPositionInTheLobbyInstance(Env env) {
        Instance lobby = env.createFlatInstance();

        Optional<LobbyHologram> hologram = LobbyHologram.spawnAt(Optional.of(POSITION), lobby, GRAND_TOUR);

        assertThat(hologram).isPresent();
        Entity display = hologram.get().entity();
        assertThat(display.getInstance()).isSameAs(lobby);
        assertThat(display.getPosition()).isEqualTo(POSITION);
    }

    @Test
    void itsTextFollowsTheBoardWhenTheCupChanges(Env env) {
        Instance lobby = env.createFlatInstance();
        LobbyHologram hologram = LobbyHologram.spawnAt(Optional.of(POSITION), lobby, GRAND_TOUR).orElseThrow();

        hologram.update(GRAND_TOUR_II);

        assertThat(text(hologram.entity())).contains("Grand Tour II").contains("delta").contains("echo");
    }

    @Test
    void noHologramIsSpawnedWithoutAPosition(Env env) {
        Instance lobby = env.createFlatInstance();

        assertThat(LobbyHologram.spawnAt(Optional.empty(), lobby, GRAND_TOUR)).isEmpty();
        assertThat(lobby.getEntities())
                .noneMatch(entity -> entity.getEntityType() == EntityType.TEXT_DISPLAY);
    }

    private static String text(Entity display) {
        return PlainTextComponentSerializer.plainText()
                .serialize(((TextDisplayMeta) display.getEntityMeta()).getText());
    }
}
