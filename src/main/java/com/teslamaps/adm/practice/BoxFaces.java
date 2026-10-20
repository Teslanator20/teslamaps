package com.teslamaps.adm.practice;

// Ported from ADM's BoxFaces.kt: which face of a box the view selects, and moving a face.
final class BoxFaces {
    private BoxFaces() {}

    enum Face { EAST, WEST, SOUTH, NORTH, UP }

    static final float TOP_PITCH = -10f;
    private static final double UP = 0.17364817766693033;

    static final int MIN_X = 0, MIN_Y = 1, MIN_Z = 2, MAX_X = 3, MAX_Y = 4, MAX_Z = 5;
    private static final int X = 0, Y = 1, Z = 2;

    private record Crossing(double enter, int enterAxis, boolean enterHigh, int exitAxis, boolean exitHigh) {}

    static Double distance(double[] eye, double[] dir, double[] min, double[] max) {
        Crossing c = cross(eye, dir, min, max);
        return c == null ? null : Math.max(c.enter, 0.0);
    }

    static Face select(double[] eye, double[] dir, double[] min, double[] max) {
        Crossing hit = cross(eye, dir, min, max);
        if (hit == null) return null;
        double len = Math.sqrt(dir[X] * dir[X] + dir[Y] * dir[Y] + dir[Z] * dir[Z]);
        if (hit.exitAxis == Y && hit.exitHigh && len > 0 && dir[Y] / len > UP) return Face.UP;
        double exit = Double.POSITIVE_INFINITY;
        Face face = null;
        for (int axis : new int[]{X, Z}) {
            double d = dir[axis];
            if (Math.abs(d) < 1e-6) continue;
            double far = Math.max((min[axis] - eye[axis]) / d, (max[axis] - eye[axis]) / d);
            if (far < exit) {
                exit = far;
                face = axis == X ? (d > 0 ? Face.EAST : Face.WEST) : (d > 0 ? Face.SOUTH : Face.NORTH);
            }
        }
        return face;
    }

    static boolean editsTop(double[] feet, float pitch, double[] min, double[] max) {
        if (pitch >= TOP_PITCH) return false;
        for (int i = 0; i < 3; i++) if (feet[i] < min[i] || feet[i] >= max[i]) return false;
        return true;
    }

    static boolean move(int[] c, Face face, int by) {
        int corner, sign, other;
        switch (face) {
            case EAST -> { corner = MAX_X; sign = 1; other = MIN_X; }
            case WEST -> { corner = MIN_X; sign = -1; other = MAX_X; }
            case SOUTH -> { corner = MAX_Z; sign = 1; other = MIN_Z; }
            case NORTH -> { corner = MIN_Z; sign = -1; other = MAX_Z; }
            default -> { corner = MAX_Y; sign = 1; other = MIN_Y; }
        }
        int next = c[corner] + sign * by;
        int size = sign > 0 ? next - c[other] : c[other] - next;
        if (size < 1) return false;
        c[corner] = next;
        return true;
    }

    private static Crossing cross(double[] eye, double[] dir, double[] min, double[] max) {
        double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        int enterAxis = -1, exitAxis = -1;
        boolean enterHigh = false, exitHigh = false;
        for (int axis = 0; axis <= 2; axis++) {
            double o = eye[axis], d = dir[axis];
            if (d == 0.0) {
                if (o < min[axis] || o > max[axis]) return null;
                continue;
            }
            double near = d > 0 ? (min[axis] - o) / d : (max[axis] - o) / d;
            double far = d > 0 ? (max[axis] - o) / d : (min[axis] - o) / d;
            if (near > enter) { enter = near; enterAxis = axis; enterHigh = d < 0; }
            if (far < exit) { exit = far; exitAxis = axis; exitHigh = d > 0; }
        }
        if (exit < Math.max(enter, 0.0)) return null;
        return new Crossing(enter, enterAxis, enterHigh, exitAxis, exitHigh);
    }
}
