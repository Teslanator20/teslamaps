package com.teslamaps.features.kuudra;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.StringUtil;

/** Shared bits of the w16-09 Kuudra helpers; phase and run start come from {@link Kuudra}. */
public final class KuudraRunInfo {

    public static final String START = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!";

    private KuudraRunInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        KuudraPileWaypoints.register();
        KuudraDoublePearl.register();
        KuudraStunWaypoint.register();
        Feature.of("KuudraGeneralConfig").config("Kuudra", KuudraRunInfo::config);
    }

    public static boolean inKuudra() {
        return Kuudra.inKuudra();
    }

    public static boolean supplies() {
        return Kuudra.inKuudra() && Kuudra.phase() == Kuudra.Phase.SUPPLIES;
    }

    /** Wall time of Elle's start line, 0 = none this instance. */
    public static long startMs() {
        return Kuudra.runStartMs();
    }

    public static String strip(String raw) {
        return StringUtil.strip(raw).trim();
    }

    /** Rows under KuudraSupply's "Supplies" label. */
    public static void supplyConfig(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("Double Pearl Warning", () -> c.kuudraDoublePearl, v -> c.kuudraDoublePearl = v)
                .desc("While picking up a supply: DOUBLE PEARL title and a red box when your pearl would spawn inside a supply giant");
        b.toggle("  Double Pearl Sound", () -> c.kuudraDoublePearlSound, v -> c.kuudraDoublePearlSound = v);
    }

    private static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.label("General");
        b.toggle("Block Profile Viewer", () -> c.kuudraPvBlocker, v -> c.kuudraPvBlocker = v)
                .desc("Right-clicking a real player in Kuudra no longer opens the profile viewer (item abilities still work)");
        b.toggle("  Also in Dungeons", () -> c.kuudraPvBlockerDungeon, v -> c.kuudraPvBlockerDungeon = v);
    }
}
