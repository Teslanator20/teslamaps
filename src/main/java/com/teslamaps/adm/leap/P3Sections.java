package com.teslamaps.adm.leap;

// Ported from ADM's rotation/P3Sections.kt
public final class P3Sections {

    private P3Sections() {}

    private static final double[][] BOXES = {
            {113.0, 160.0, 48.0, 89.0, 100.0, 122.0},
            {91.0, 160.0, 145.0, 19.0, 100.0, 121.0},
            {-6.0, 160.0, 123.0, 19.0, 100.0, 50.0},
            {17.0, 160.0, 27.0, 90.0, 100.0, 50.0},
    };

    /** Which section (1-4) contains this point, or 0. */
    public static int sectionAt(double x, double y, double z) {
        for (int i = 0; i < BOXES.length; i++) {
            double[] b = BOXES[i];
            if (within(x, b[0], b[3]) && within(y, b[1], b[4]) && within(z, b[2], b[5])) return i + 1;
        }
        return 0;
    }

    public static boolean isInSection(int section, double x, double y, double z) {
        return section >= 1 && section <= BOXES.length && sectionAt(x, y, z) == section;
    }

    private static boolean within(double v, double a, double b) {
        return v >= Math.min(a, b) && v <= Math.max(a, b);
    }
}
