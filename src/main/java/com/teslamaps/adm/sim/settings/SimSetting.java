package com.teslamaps.adm.sim.settings;

/** One ADS setting (was an Odin setting on the ADS module); its value lives in TeslaMapsConfig.admSimSettings. */
public abstract class SimSetting {
    public final String name;
    public final String desc;

    protected SimSetting(String name, String desc) {
        this.name = name;
        this.desc = desc == null ? "" : desc;
    }

    protected String raw() { return SimSettings.raw(name); }

    protected void store(String v) { SimSettings.store(name, v); }
}
