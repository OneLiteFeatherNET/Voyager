package net.elytrarace.spline;

import org.apache.commons.geometry.euclidean.threed.Vector3D;

/**
 * Pre-computed data for one Catmull-Rom spline segment (between p1 and p2).
 * Stores the four control points and their centripetal knot values.
 */
public record SplineSegment(
        Vector3D p0, Vector3D p1, Vector3D p2, Vector3D p3,
        double t0, double t1, double t2, double t3
) {

    private static final double EPSILON = 1e-10;

    /**
     * Evaluates a point on this segment using the Barry-Goldman pyramid.
     *
     * @param localT parameter in [0, 1] where 0 = p1 and 1 = p2
     * @return the interpolated point on the curve
     */
    public Vector3D evaluate(double localT) {
        double t = t1 + localT * (t2 - t1);

        double dt10 = Math.max(t1 - t0, EPSILON);
        double dt21 = Math.max(t2 - t1, EPSILON);
        double dt32 = Math.max(t3 - t2, EPSILON);
        double dt20 = Math.max(t2 - t0, EPSILON);
        double dt31 = Math.max(t3 - t1, EPSILON);

        // First level
        Vector3D a1 = p0.multiply((t1 - t) / dt10).add(p1.multiply((t - t0) / dt10));
        Vector3D a2 = p1.multiply((t2 - t) / dt21).add(p2.multiply((t - t1) / dt21));
        Vector3D a3 = p2.multiply((t3 - t) / dt32).add(p3.multiply((t - t2) / dt32));

        // Second level
        Vector3D b1 = a1.multiply((t2 - t) / dt20).add(a2.multiply((t - t0) / dt20));
        Vector3D b2 = a2.multiply((t3 - t) / dt31).add(a3.multiply((t - t1) / dt31));

        // Third level
        return b1.multiply((t2 - t) / dt21).add(b2.multiply((t - t1) / dt21));
    }

    /**
     * Estimates the arc length of this segment by sampling.
     */
    public double estimateLength(int samples) {
        double length = 0;
        Vector3D prev = evaluate(0.0);
        for (int i = 1; i <= samples; i++) {
            Vector3D curr = evaluate((double) i / samples);
            length += prev.distance(curr);
            prev = curr;
        }
        return length;
    }
}
