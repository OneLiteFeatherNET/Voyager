package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;

/**
 * The same set of adapters {@code CatalogDirectory} registers, built here so each adapter can be
 * tested against a JSON fragment rather than only through a file on disk.
 */
final class Adapters {

    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3.class, new Vec3Adapter())
            .registerTypeAdapter(MapDefinition.class, new MapDefinitionAdapter())
            .registerTypeAdapter(CupDefinition.class, new CupDefinitionAdapter())
            .create();

    private Adapters() {
    }
}
