package com.teslamaps.adm.sim.settings;

public final class KeySetting extends SimSetting {
    public final int def;
    Runnable onPress;
    boolean down;

    KeySetting(String name, int def, String desc) {
        super(name, desc);
        this.def = def;
    }

    public int key() {
        String r = raw();
        if (r == null) return def;
        try {
            return Integer.parseInt(r);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void setKey(int k) { store(Integer.toString(k)); }

    public KeySetting onPress(Runnable r) {
        this.onPress = r;
        return this;
    }
}
