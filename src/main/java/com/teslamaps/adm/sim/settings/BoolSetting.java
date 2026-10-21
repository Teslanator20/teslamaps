package com.teslamaps.adm.sim.settings;

public final class BoolSetting extends SimSetting {
    public final boolean def;

    BoolSetting(String name, boolean def, String desc) {
        super(name, desc);
        this.def = def;
    }

    public boolean get() {
        String r = raw();
        return r == null ? def : Boolean.parseBoolean(r);
    }

    public void set(boolean v) { store(Boolean.toString(v)); }

    public void toggle() { set(!get()); }
}
