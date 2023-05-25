package net.elytrarace.utils.extensions

import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.pow


fun bezierCurve(
    segmentCount: Int,
    startPoint: Location,
    controlPoint: Location,
    endPoint: Location,
): List<Location> {
    return (1..segmentCount).map {
        val t = it / segmentCount.toFloat()
        bezierPoint(t, startPoint, controlPoint, endPoint)
    }
}

private fun bezierPoint(delta: Float, startPoint: Location, controlPoint: Location, endPoint: Location): Location {
    val x = (1 - delta).pow(2) * startPoint.x + (1 - delta) * 2 * delta * controlPoint.x + delta * delta * endPoint.x
    val y = (1 - delta).pow(2) * startPoint.y + (1 - delta) * 2 * delta * controlPoint.y + delta * delta * endPoint.y
    val z = (1 - delta).pow(2) * startPoint.z + (1 - delta) * 2 * delta * controlPoint.z + delta * delta * endPoint.z
    return Vector(x, y, z).toLocation(controlPoint.world)
}

private fun quadraticBezier(p1: Double, p2: Double, p3: Double, t: Float): Double {
    return (1.0 - t) * ((1.0 - t) * p1 + t * p2) + t * ((1.0 - t) * p2 + t * p3)
}
/**
 * Given a list of control points, this will create a list of pointsPerSegment
 * points spaced uniformly along the resulting Catmull-Rom curve.
 *
 * @param points The list of control points, leading and ending with a
 * coordinate that is only used for controling the spline and is not visualized.
 * @param index The index of control point p0, where p0, p1, p2, and p3 are
 * used in order to create a curve between p1 and p2.
 * @param pointsPerSegment The total number of uniformly spaced interpolated
 * points to calculate for each segment. The larger this number, the
 * smoother the resulting curve.
 * @param curveType Clarifies whether the curve should use uniform, chordal
 * or centripetal curve types. Uniform can produce loops, chordal can
 * produce large distortions from the original lines, and centripetal is an
 * optimal balance without spaces.
 * @return the list of coordinates that define the CatmullRom curve
 * between the points defined by index+1 and index+2.
 */
fun interpolate(points: List<Location>, index: Int = 0, pointsPerSegment: Int): List<Location> {
    val result = mutableListOf<Location>()
    val x = DoubleArray(4)
    val y = DoubleArray(4)
    val z = DoubleArray(4)
    val time = DoubleArray(4)
    for (i in 0 until 4) {
        x[i] = points[index + i].x + 0.5
        y[i] = points[index + i].y + 0.5
        z[i] = points[index + i].z + 0.5
        time[i] = i.toDouble()
    }
    var tStart = 1.0
    var tEnd = 2.0
    var total = 0.0
    for(i in 1 until 4) {
        val dx = x[i] - x[i - 1]
        val dy = y[i] - y[i - 1]
        val dz = z[i] - z[i - 1]
        total += (dx * dx + dy * dy + dz * dz).pow(0.25)
        time[i] = total
    }
    tStart = time[1]
    tEnd = time[2]
    /*var z1 = 0.0
    var z2 = 0.0
    if (!points[index + 1].z.isNaN()) {
        z1 = points[index + 1].z
    }
    if (!points[index + 2].z.isNaN()) {
        z2 = points[index + 2].z
    }
    val dz = z2 - z1*/
    val world = points.first().world
    val segments = pointsPerSegment - 1
    result.add(points[index + 1])
    for(i in 1 until segments) {
        val xi = interpolate(x, time, tStart + (i * (tEnd - tStart)) / segments)
        val yi = interpolate(y, time, tStart + (i * (tEnd - tStart)) / segments)
        val zi = interpolate(z, time, tStart + (i * (tEnd - tStart)) / segments)
        result.add(Location(world, xi,yi,zi))
    }
    result.add(points[index + 2])
    return result
}

/**
 * Unlike the other implementation here, which uses the default "uniform"
 * treatment of t, this computation is used to calculate the same values but
 * introduces the ability to "parameterize" the t values used in the
 * calculation. This is based on Figure 3 from
 * http://www.cemyuksel.com/research/catmullrom_param/catmullrom.pdf
 *
 * @param p An array of double values of length 4, where interpolation
 * occurs from p1 to p2.
 * @param time An array of time measures of length 4, corresponding to each
 * p value.
 * @param t the actual interpolation ratio from 0 to 1 representing the
 * position between p1 and p2 to interpolate the value.
 * @return
 */
private fun interpolate(p: DoubleArray, time: DoubleArray, t: Double): Double {
    val l01 = p[0] * (time[1] - t) / (time[1] - time[0]) + p[1] * (t - time[0]) / (time[1] - time[0])
    val l12 = p[1] * (time[2] - t) / (time[2] - time[1]) + p[2] * (t - time[1]) / (time[2] - time[1])
    val l23 = p[2] * (time[3] - t) / (time[3] - time[2]) + p[3] * (t - time[2]) / (time[3] - time[2])
    val l012 = l01 * (time[2] - t) / (time[2] - time[0]) + l12 * (t - time[0]) / (time[2] - time[0])
    val l123 = l12 * (time[3] - t) / (time[3] - time[1]) + l23 * (t - time[1]) / (time[3] - time[1])
    return l012 * (time[2] - t) / (time[2] - time[1]) + l123 * (t - time[1]) / (time[2] - time[1])
}