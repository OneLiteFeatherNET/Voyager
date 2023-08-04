package net.elytrarace.model

import org.apache.commons.geometry.euclidean.threed.Vector3D
import java.util.concurrent.ArrayBlockingQueue

data class ElytraPlayer(
    val positionQueue: ArrayList<Vector3D> = ArrayList()
)
