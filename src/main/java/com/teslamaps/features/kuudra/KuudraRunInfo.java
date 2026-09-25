package com.teslamaps.features.kuudra;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Area + phase from Elle's lines, shared by the w16-09 Kuudra helpers. */
public final class KuudraRunInfo {

    public static final String START = "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!";
    private static final String P2 = "[NPC] Elle: OMG! Great work collecting my supplies!";
    private static final String P3 = "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!";
    private static final String P4 = "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!";

    private static int phase;
    private static long startMs;
    private static boolean inKuudra;
    private static long areaCheck;
    private static Object lastLevel;

    private KuudraRunInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Kuudra", "Skyblock", true);
        Feature.of("KuudraRunInfo").section("Kuudra")
                .when(() -> cfg().kuudraPileWaypoints || cfg().kuudraShopHelper)
                .serverChat(raw -> onChat(strip(raw)))
                .config("Kuudra", KuudraRunInfo::config);
        KuudraPileWaypoints.register();
        KuudraDoublePearl.register();
        KuudraShopHelper.register();
        KuudraStunWaypoint.register();
        KuudraLagTracker.register();
    }

    private static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.label("Supplies (Phase 1)");
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
        b.label("Stun (Phase 3)");
        b.toggle("Stun Waypoint", () -> c.kuudraStunWaypoint, v -> c.kuudraStunWaypoint = v)
                .desc("After buying the Human Cannonball: box on the belly block to break, offset to you before the launch, fixed inside Kuudra");
        b.dropdown("  Stun Marker", KuudraStunWaypoint.MARKERS, () -> c.kuudraStunMarker, v -> c.kuudraStunMarker = v);
        b.espStyle("Stun Waypoint", () -> c.kuudraStunStyle);
        b.label("General");
        b.toggle("Block Profile Viewer", () -> c.kuudraPvBlocker, v -> c.kuudraPvBlocker = v)
                .desc("Right-clicking a real player in Kuudra no longer opens the profile viewer (item abilities still work)");
        b.toggle("  Also in Dungeons", () -> c.kuudraPvBlockerDungeon, v -> c.kuudraPvBlockerDungeon = v);
        b.toggle("Lag Tracker", () -> c.kuudraLagTracker, v -> c.kuudraLagTracker = v)
                .desc("At KUUDRA DOWN / DEFEAT: seconds lost to server lag (server ticks vs real time since Elle's start line)");
        b.toggle("  Send to Party", () -> c.kuudraLagTrackerParty, v -> c.kuudraLagTrackerParty = v);
    }

    public static boolean inKuudra() {
        long now = System.currentTimeMillis();
        if (now - areaCheck > 1000) {
            areaCheck = now;
            inKuudra = SkyblockUtils.getArea().toLowerCase(Locale.ROOT).contains("kuudra");
        }
        return inKuudra;
    }

    /** 0 = not started, 1 supplies, 2 build, 3 stun/dps, 4 kill. */
    public static int phase() {
        checkLevel();
        return inKuudra() ? phase : 0;
    }

    /** Wall time of Elle's start line, 0 = none this instance. */
    public static long startMs() {
        checkLevel();
        return startMs;
    }

    private static void checkLevel() {
        Object lvl = Minecraft.getInstance().level;
        if (lvl != lastLevel) {
            lastLevel = lvl;
            phase = 0;
            startMs = 0;
            areaCheck = 0;
        }
    }

    /** Stripped server line; called by every helper that needs the phase, idempotent. */
    public static void onChat(String stripped) {
        checkLevel();
        switch (stripped) {
            case START -> { if (phase != 1) { phase = 1; startMs = System.currentTimeMillis(); } }
            case P2 -> phase = 2;
            case P3 -> phase = 3;
            case P4 -> phase = 4;
            default -> {}
        }
    }

    public static String strip(String raw) {
        return StringUtil.strip(raw).trim();
    }
}
