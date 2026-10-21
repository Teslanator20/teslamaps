package com.teslamaps.adm.sim.settings;

public final class StrSetting extends SimSetting {
    public final String def;
    public final int maxLength;

    StrSetting(String name, String def, int maxLength, String desc) {
        super(name, desc);
        this.def = def == null ? "" : def;
        this.maxLength = maxLength;
    }

    public String get() {
        String r = raw();
        return r == null ? def : r;
    }

    public void set(String v) {
        if (v == null) v = "";
        if (maxLength > 0 && v.length() > maxLength) v = v.substring(0, maxLength);
        store(v);
    }
}
