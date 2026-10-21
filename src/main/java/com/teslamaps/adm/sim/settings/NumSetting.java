package com.teslamaps.adm.sim.settings;

public final class NumSetting extends SimSetting {
    public final double def, min, max, step;
    public final String unit;

    NumSetting(String name, double def, double min, double max, double step, String unit, String desc) {
        super(name, desc);
        this.def = def;
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit == null ? "" : unit;
    }

    public double get() {
        String r = raw();
        if (r == null) return def;
        try {
            return Math.clamp(Double.parseDouble(r), min, max);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getInt() { return (int) get(); }

    public float getFloat() { return (float) get(); }

    public void set(double v) {
        double c = Math.clamp(v, min, max);
        if (step > 0) c = Math.round((c - min) / step) * step + min;
        store(Double.toString(Math.clamp(c, min, max)));
    }

    /** Whole numbers only, as Odin's Int NumberSetting. */
    public boolean integral() {
        return def == Math.rint(def) && min == Math.rint(min) && step == Math.rint(step);
    }
}
