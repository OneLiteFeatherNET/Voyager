package net.elytrarace.voyager.platform.permission;

import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class PlayerSubjectsTest {

    @Test
    void theSubjectCarriesThePlayersUuid(Env env) {
        Instance instance = env.createFlatInstance();
        Player player = env.createPlayer(instance, new Pos(0, 40, 0));

        PermissionSubject.Player subject = PlayerSubjects.of(player);

        assertThat(subject.id()).isEqualTo(player.getUuid());
    }

    @Test
    void theSubjectCarriesThePlayersOperatorLevel(Env env) {
        Instance instance = env.createFlatInstance();
        Player player = env.createPlayer(instance, new Pos(0, 40, 0));
        player.setPermissionLevel(3);

        PermissionSubject.Player subject = PlayerSubjects.of(player);

        assertThat(subject.operatorLevel()).isEqualTo(3);
    }
}
