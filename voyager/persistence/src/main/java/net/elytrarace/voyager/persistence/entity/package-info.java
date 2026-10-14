/**
 * Hibernate mappings of the statistics tables (design D4). One entity per table, raw foreign-key columns and no
 * association annotations (design D2): a relation is read by a query, never followed through a field.
 */
@NotNullByDefault
package net.elytrarace.voyager.persistence.entity;

import org.jetbrains.annotations.NotNullByDefault;
