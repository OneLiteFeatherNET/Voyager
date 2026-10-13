/**
 * Server configuration: the one loader that reads the environment, the system properties and the configuration files, and
 * the precedence they follow (ADR-0036). Nothing else in the rebuild reads the JVM for configuration; the fitness rules
 * {@code environmentAccessIsConfinedToTheEdge} and {@code avajeConfigIsConfinedToTheEdge} enforce it.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.config;

import org.jetbrains.annotations.NotNullByDefault;
