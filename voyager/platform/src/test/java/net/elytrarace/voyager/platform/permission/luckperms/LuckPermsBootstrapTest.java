package net.elytrarace.voyager.platform.permission.luckperms;

import net.elytrarace.voyager.platform.permission.luckperms.exception.PermissionBackendStartException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The loader is compile-only in voyager-platform and is not on this module's test class path, so these tests see the
 * backend absent, which is the state the fallback policy serves.
 */
class LuckPermsBootstrapTest {

    @Test
    void theLoaderIsNotDetectedOnTheTestClassPath() {
        assertThat(LuckPermsBootstrap.isPresent()).isFalse();
    }

    @Test
    void startingWithoutTheLoaderReportsAStartFailureInsteadOfALinkageError() {
        assertThatThrownBy(LuckPermsBootstrap::start)
                .isInstanceOf(PermissionBackendStartException.class)
                .hasMessageContaining("LuckPerms did not start");
    }
}
