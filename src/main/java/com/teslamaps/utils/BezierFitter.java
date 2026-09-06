// Ported from SkyHanni's PolynomialFitter.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.utils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Fits a degree-n curve through a run of particle positions and extrapolates where it was
 * headed. Hypixel's guess particles fly along such a curve towards their target, so the
 * target can be recovered from the first few particles.
 */
public class BezierFitter {

    private final int degree;
    private final List<Vec3> points = new ArrayList<>();
    private double[][] coefficients;

    public BezierFitter(int degree) {
        this.degree = degree;
    }

    public void reset() {
        points.clear();
        coefficients = null;
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public int count() {
        return points.size();
    }

    /**
     * @return true when the point extended the curve.
     */
    public boolean tryAdd(Vec3 location, double maxDistanceToLast) {
        if (!Double.isFinite(location.x) || !Double.isFinite(location.y) || !Double.isFinite(location.z)) return false;
        if (points.isEmpty()) {
            add(location);
            return false;
        }
        Vec3 last = points.get(points.size() - 1);
        if (last.distanceTo(location) > maxDistanceToLast) return false;
        // Hypixel repeats positions, sometimes interleaved as A B A B. A repeat stalls the
        // curve parameter and skews the derivative, which can throw the guess thousands of
        // blocks off, so every point is checked and not just the last one.
        for (Vec3 p : points) {
            if (p.distanceToSqr(location) < 0.0001) return false;
        }
        add(location);
        return true;
    }

    private void add(Vec3 location) {
        points.add(location);
        coefficients = null;
    }

    /** @return the extrapolated target, or null while there are too few points. */
    public Vec3 solve() {
        if (points.size() <= degree) return null;
        if (coefficients == null) coefficients = fit();
        if (coefficients == null) return null;

        Vec3 derivative = derivativeAt(0.0);
        double length = derivative.length();
        if (length <= 0) return null;

        double t = 3 * pitchWeight(derivative) / length;
        return at(t);
    }

    private Vec3 at(double t) {
        double[] out = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double result = 0;
            for (int i = coefficients[axis].length - 1; i >= 0; i--) {
                result = result * t + coefficients[axis][i];
            }
            out[axis] = result;
        }
        return new Vec3(out[0], out[1], out[2]);
    }

    private Vec3 derivativeAt(double t) {
        double[] out = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double[] c = coefficients[axis];
            double result = 0;
            for (int i = c.length - 1; i >= 1; i--) {
                result = result * t + c[i] * i;
            }
            out[axis] = result;
        }
        return new Vec3(out[0], out[1], out[2]);
    }

    /** Least squares fit per axis, with the point's index in the list as the parameter. */
    private double[][] fit() {
        int n = degree + 1;
        double[][] result = new double[3][];
        for (int axis = 0; axis < 3; axis++) {
            double[][] normal = new double[n][n + 1];
            for (int row = 0; row < n; row++) {
                for (int col = 0; col < n; col++) {
                    double sum = 0;
                    for (int p = 0; p < points.size(); p++) sum += Math.pow(p, row + col);
                    normal[row][col] = sum;
                }
                double sum = 0;
                for (int p = 0; p < points.size(); p++) sum += Math.pow(p, row) * axis(points.get(p), axis);
                normal[row][n] = sum;
            }
            double[] solved = gauss(normal, n);
            if (solved == null) return null;
            result[axis] = solved;
        }
        return result;
    }

    private static double axis(Vec3 v, int index) {
        return index == 0 ? v.x : index == 1 ? v.y : v.z;
    }

    private static double[] gauss(double[][] m, int n) {
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) pivot = row;
            }
            if (Math.abs(m[pivot][col]) < 1e-12) return null;
            double[] swap = m[col];
            m[col] = m[pivot];
            m[pivot] = swap;

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = m[row][col] / m[col][col];
                for (int k = col; k <= n; k++) m[row][k] -= factor * m[col][k];
            }
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = m[i][n] / m[i][i];
        return out;
    }

    // Hypixel's particles drop as they fly; the pitch at the start says how far out the
    // control point sits, which is what turns the curve into a distance.
    private static double pitchWeight(Vec3 derivative) {
        return Math.sqrt(24 * Math.sin(pitchFromDerivative(derivative) - Math.PI) + 25);
    }

    private static double pitchFromDerivative(Vec3 derivative) {
        double xzLength = Math.sqrt(derivative.x * derivative.x + derivative.z * derivative.z);
        double pitch = -Math.atan2(derivative.y, xzLength);

        // solve y = atan2(sin(x) - 0.75, cos(x)) for x, by bisection
        double guess = pitch;
        double result = Math.atan2(Math.sin(guess) - 0.75, Math.cos(guess));
        double max = Math.PI / 2, min = -Math.PI / 2;
        for (int i = 0; i < 100; i++) {
            if (result < pitch) min = guess; else max = guess;
            guess = (min + max) / 2;
            result = Math.atan2(Math.sin(guess) - 0.75, Math.cos(guess));
            if (result == pitch) return guess;
        }
        return guess;
    }
}
