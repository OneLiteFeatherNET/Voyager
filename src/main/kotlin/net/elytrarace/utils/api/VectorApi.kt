package net.elytrarace.utils.api

import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.bukkit.Location

interface VectorApi {

    fun toVector3D(location: Location): Vector3D {
        return Vector3D.of(location.x, location.y, location.z)
    }

}