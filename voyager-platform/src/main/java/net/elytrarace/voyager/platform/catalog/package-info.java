/**
 * Reading the racecourses and cups off disk. {@link net.elytrarace.voyager.platform.catalog.CatalogLoader}
 * is the one entry point: it turns a data directory into a {@code CatalogSnapshot}, which answers the
 * {@code MapCatalog} and {@code CupCatalog} ports, and it runs the one cross-check neither directory
 * can make alone.
 *
 * <p>Gson lives here and in the adapter package beside it, and nowhere else in the rebuild. The
 * modules that model a race must be able to play one out in JUnit with hand-built definitions, so
 * {@code ApiPurityTest} holds every module outside {@code voyager-platform} free of it.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.catalog;

import org.jetbrains.annotations.NotNullByDefault;
