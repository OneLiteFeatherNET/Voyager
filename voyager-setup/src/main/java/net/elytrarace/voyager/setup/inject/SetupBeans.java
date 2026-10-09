package net.elytrarace.voyager.setup.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.mapsetup.DraftStore;
import net.elytrarace.voyager.platform.catalog.JsonDraftStore;
import net.elytrarace.voyager.setup.config.SetupSettings;

/**
 * The beans of the setup server. Constructors are called here, not annotated: no class under {@code setup.adapter} or
 * {@code setup.mapsetup} carries a DI annotation, so a constructor change is a compile error in this file.
 */
@Factory
public final class SetupBeans {

    @Bean
    DraftStore draftStore(@External SetupSettings settings) {
        return new JsonDraftStore(settings.dataPath(), settings.worldsPath());
    }
}
