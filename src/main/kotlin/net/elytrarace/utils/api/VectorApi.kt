package net.elytrarace.utils.api

import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.bukkit.Location
import org.bukkit.World

interface VectorApi {

    fun toVector3D(location: Location): Vector3D {
        return Vector3D.of(location.x, location.y, location.z)
    }

    fun toBukkitLocation(vector3D: Vector3D, world: World): Location {
        return Location(world, vector3D.x, vector3D.y,vector3D.z)
    }


}