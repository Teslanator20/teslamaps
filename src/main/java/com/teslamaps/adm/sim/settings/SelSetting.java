package com.teslamaps.adm.sim.settings;

import java.util.List;

public final class SelSetting extends SimSetting {
    public final List<String> options;
    public final int defIndex;

    SelSetting(String name, int defIndex, List<String> options, String desc) {
        super(name, desc);
        this.options = List.copyOf(options);
        this.defIndex = Math.max(0, defIndex);
    }

    public int index() {
        String r = raw();
        if (r == null) return defIndex;
        int i = options.indexOf(r);
        return i < 0 ? defIndex : i;
    }

    public void setIndex(int i) {
        if (options.isEmpty()) return;
        store(options.get(Math.floorMod(i, options.size())));
    }

    public String get() { return options.isEmpty() ? "" : options.get(index()); }

    public void set(String option) {
        int i = options.indexOf(option);
        if (i >= 0) setIndex(i);
    }
}
