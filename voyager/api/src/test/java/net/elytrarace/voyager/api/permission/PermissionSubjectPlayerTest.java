package net.elytrarace.voyager.api.permission;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionSubjectPlayerTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void rejectsAnOperatorLevelBelowZero() {
        assertThatThrownBy(() -> new PermissionSubject.Player(ID, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void rejectsAnOperatorLevelAboveFour() {
        assertThatThrownBy(() -> new PermissionSubject.Player(ID, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }

    @Test
    void acceptsOperatorLevelZero() {
        assertThat(new PermissionSubject.Player(ID, 0).operatorLevel()).isZero();
    }

    @Test
    void acceptsOperatorLevelFour() {
        assertThat(new PermissionSubject.Player(ID, 4).operatorLevel()).isEqualTo(4);
    }
}
