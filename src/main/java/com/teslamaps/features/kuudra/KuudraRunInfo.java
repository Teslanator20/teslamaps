package com.teslamaps.features.kuudra;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.StringUtil;

/** Shared bits of the w16-09 Kuudra helpers; phase and run start come from KuudraBuild's state tracker. */
public final class KuudraRunInfo {

    public static final String START = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!";

    private KuudraRunInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    /** KuudraBuild.stateNeeded: these helpers read the phase. */
    public static boolean needsState() {
        TeslaMapsConfig c = cfg();
        return c.kuudraPileWaypoints || c.kuudraShopHelper;
    }

    public static void register() {
        FeatureRegistry.category("Kuudra", "Skyblock", true);
        KuudraPileWaypoints.register();
        KuudraDoublePearl.register();
        KuudraShopHelper.register();
        KuudraStunWaypoint.register();
        KuudraLagTracker.register();
        Feature.of("KuudraGeneralConfig").config("Kuudra", KuudraRunInfo::config);
    }

    public static boolean inKuudra() {
        return KuudraBuild.inKuudra();
    }

    /** 0 = not started, 1 supplies, 2 build, 3 stun/dps, 4 kill. */
    public static int phase() {
        return inKuudra() ? KuudraBuild.phase : 0;
    }

    /** Wall time of Elle's start line, 0 = none this instance. */
    public static long startMs() {
        return KuudraSupply.runStart;
    }

    public static String strip(String raw) {
        return StringUtil.strip(raw).trim();
    }

    /** Rows under KuudraSupply's "Supplies" label. */
    public static void supplyConfig(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("Pile Waypoints", () -> c.kuudraPileWaypoints, v -> c.kuudraPileWaypoints = v)
                .desc("Beam boxes on the crate piles during supplies; the pile of a \"No <pre>!\" party callout gets the No-Pre color. Positions in config/teslamaps/kuudra_piles.json");
        b.toggle("  Pile Names", () -> c.kuudraPileNames, v -> c.kuudraPileNames = v);
        b.color("  Pile Color", () -> c.kuudraPileColor, v -> c.kuudraPileColor = v);
        b.color("  No-Pre Pile Color", () -> c.kuudraPileNoPreColor, v -> c.kuudraPileNoPreColor = v);
        b.button("  Reload Pile File", KuudraPileWaypoints::load);
        b.toggle("Double Pearl Warning", () -> c.kuudraDoublePearl, v -> c.kuudraDoublePearl = v)
                .desc("While picking up a supply: DOUBLE PEARL title and a red box when your pearl would spawn inside a supply giant");
        b.toggle("  Double Pearl Sound", () -> c.kuudraDoublePearlSound, v -> c.kuudraDoublePearlSound = v);
        b.toggle("Shop Helper", () -> c.kuudraShopHelper, v -> c.kuudraShopHelper = v)
                .desc("Shop / X Cannon: tentacles spawning 9-15s into supplies get matched to S1-S5 / XC1-XC3, boxed, called out in chat, and the saved safe spot for that combo is shown");
        b.toggle("  Draw Tentacles", () -> c.kuudraShopTentacles, v -> c.kuudraShopTentacles = v);
        b.color("  Tentacle Color", () -> c.kuudraShopTentacleColor, v -> c.kuudraShopTentacleColor = v);
        b.color("  Safe Spot Color", () -> c.kuudraShopSafeColor, v -> c.kuudraShopSafeColor = v);
        b.dropdown("  Spawn Point to Set", KuudraShopHelper.TARGETS, () -> c.kuudraShopTarget, v -> c.kuudraShopTarget = v);
        b.button("  Set Spawn Point Here", KuudraShopHelper::setTarget);
        b.button("  Save Safe Spot (Current Combo)", KuudraShopHelper::saveComboSpot);
        b.button("  Clear Safe Spots (Current Combo)", KuudraShopHelper::clearCombo);
    }

    private static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.label("General");
        b.toggle("Block Profile Viewer", () -> c.kuudraPvBlocker, v -> c.kuudraPvBlocker = v)
                .desc("Right-clicking a real player in Kuudra no longer opens the profile viewer (item abilities still work)");
        b.toggle("  Also in Dungeons", () -> c.kuudraPvBlockerDungeon, v -> c.kuudraPvBlockerDungeon = v);
        b.toggle("Lag Tracker", () -> c.kuudraLagTracker, v -> c.kuudraLagTracker = v)
                .desc("At KUUDRA DOWN / DEFEAT: seconds lost to server lag (server ticks vs real time since Elle's start line)");
        b.toggle("  Send to Party", () -> c.kuudraLagTrackerParty, v -> c.kuudraLagTrackerParty = v);
    }
}
