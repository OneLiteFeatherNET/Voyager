package net.elytrarace.common.utils;

import org.apache.commons.geometry.euclidean.threed.Vector3D;

import java.util.ArrayList;
import java.util.List;

public class SplineAPI {

    /**
     * Given a list of control points, this will create a list of pointsPerSegment
     * points spaced uniformly along the resulting Catmull-Rom curve.
     *
     * @param points The list of control points, leading and ending with a
     * coordinate that is only used for controlling the spline and is not visualized.
     * @param index The index of control point p0, where p0, p1, p2, and p3 are
     * used in order to create a curve between p1 and p2.
     * @param pointsPerSegment The total number of uniformly spaced interpolated
     * points to calculate for each segment. The larger this number, the
     * smoother the resulting curve.
     * or centripetal curve types. Uniform can produce loops, chordal can
     * produce large distortions from the original lines, and centripetal is an
     * optimal balance without spaces.
     * @return the list of coordinates that define the CatmullRom curve
     * between the points defined by index+1 and index+2.
     */
    public static List<Vector3D> interpolate(List<Vector3D> points, int index, int pointsPerSegment) {
        List<Vector3D> result = new ArrayList<>();
        double[] x = new double[4];
        double[] y = new double[4];
        double[] z = new double[4];
        double[] time = new double[4];
        for (int i = 0; i < 4; i++) {
            x[i] = points.get(index + i).getX();
            y[i] = points.get(index + i).getY();
            z[i] = points.get(index + i).getZ();
            time[i] = i;
        }
        double tStart = 1.0;
        double tEnd = 2.0;
        double total = 0.0;
        for (int i = 1; i < 4; i++) {
            double dx = x[i] - x[i - 1];
            double dy = y[i] - y[i - 1];
            double dz = z[i] - z[i - 1];
            total += Math.pow(dx * dx + dy * dy + dz * dz, 0.25);
            time[i] = total;
        }
        tStart = time[1];
        tEnd = time[2];
        int segments = pointsPerSegment - 1;
        result.add(points.get(index + 1));
        for (int i = 1; i < segments; i++) {
            double xi = interpolate(x, time, tStart + (i * (tEnd - tStart)) / segments);
            double yi = interpolate(y, time, tStart + (i * (tEnd - tStart)) / segments);
            double zi = interpolate(z, time, tStart + (i * (tEnd - tStart)) / segments);
            result.add(Vector3D.of(xi, yi, zi));
        }
        result.add(points.get(index + 2));
        return result;
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
    private static double interpolate(double[] p, double[] time, double t) {
        double l01 = p[0] * (time[1] - t) / (time[1] - time[0]) + p[1] * (t - time[0]) / (time[1] - time[0]);
        double l12 = p[1] * (time[2] - t) / (time[2] - time[1]) + p[2] * (t - time[1]) / (time[2] - time[1]);
        double l23 = p[2] * (time[3] - t) / (time[3] - time[2]) + p[3] * (t - time[2]) / (time[3] - time[2]);
        double l012 = l01 * (time[2] - t) / (time[2] - time[0]) + l12 * (t - time[0]) / (time[2] - time[0]);
        double l123 = l12 * (time[3] - t) / (time[3] - time[1]) + l23 * (t - time[1]) / (time[3] - time[1]);
        return l012 * (time[2] - t) / (time[2] - time[1]) + l123 * (t - time[1]) / (time[2] - time[1]);
    }
}
