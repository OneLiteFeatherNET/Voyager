/**
 * The file-system side of the setup server's map authoring: importing a finished world into a map's world folder.
 * {@link net.elytrarace.voyager.platform.mapsetup.FolderWorldImporter} implements the API's {@code WorldImporter}; it
 * sits here, beside the other platform code that depends on {@code api.mapsetup}, so that {@code SliceBoundaryRulesTest}
 * rule R5 holds it in the one package it allows.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.mapsetup;

import org.jetbrains.annotations.NotNullByDefault;
