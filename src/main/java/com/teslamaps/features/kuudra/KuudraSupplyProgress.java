package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/phaseone/CrateOverlay.kt and legitcatmod's CrateHud / Alerts ("Already Picking")
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Supply pick-up progress as a movable bar instead of Hypixel's centre title, plus the "Already Picking" alert. */
public final class KuudraSupplyProgress {

    private static final Pattern PROGRESS = Pattern.compile("\\[\\|+\\]\\s*(\\d+)%");
    private static final int W = 100, H = 24;

    private static Feature feature;
    private static HudEditElement hud;
    private static double current = -1, last = -1;
    private static long lastPickTick, ticksPerStep = 10, lastUpdateMs;
    private static long alertUntil;
    private static int pctVal = -1;
    private static String pctStr = "";

    private KuudraSupplyProgress() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuudraSupplyProgress", "Supply Progress").section("Kuudra")
                .enabled(() -> cfg().kuudraSupplyProgress)
                .size(() -> W, () -> H)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, 63));
        feature = Feature.of("KuudraSupplyProgress").section("Kuudra")
                .when(() -> cfg().kuudraSupplyProgress || cfg().kuudraAlreadyPicking)
                .tick(KuudraSupplyProgress::tick)
                .serverChat(KuudraSupplyProgress::onChat)
                .hud("kuudrasupplyprogress", KuudraSupplyProgress::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("  Supply Progress HUD", () -> c.kuudraSupplyProgress, v -> c.kuudraSupplyProgress = v)
                            .desc("Hides Hypixel's '[|||] n%' pick-up title and draws a smooth, movable bar instead (/tmap gui). "
                                    + "Gone at 100%, on drop or after 2s without an update.")
                            .tags("supply", "progress", "title");
                    b.toggle("  Already Picking Alert", () -> c.kuudraAlreadyPicking, v -> c.kuudraAlreadyPicking = v)
                            .desc("Big 'Already Picking' when someone else is already picking up that supply");
                    b.slider("    Alert Seconds", 0.5f, 5f, () -> c.kuudraAlreadyPickingSecs, v -> c.kuudraAlreadyPickingSecs = v, 1);
                });
    }

    private static boolean active() {
        return feature != null && cfg().kuudraSupplyProgress && feature.active() && KuudraContext.phase() == KuudraContext.SUPPLIES;
    }

    /** Title / subtitle / action bar hook, main thread. True = hide the vanilla text. */
    public static boolean onText(String text) {
        if (!active() || text.indexOf('%') < 0) return false;
        Matcher m = PROGRESS.matcher(StringUtil.strip(text));
        if (!m.find()) return false;
        double p = Double.parseDouble(m.group(1));
        long now = ServerTickTimer.now();
        if (current >= 0 && p > current) {
            last = current;
            ticksPerStep = Math.max(1, now - lastPickTick);
        } else {
            last = p;
            ticksPerStep = 10;
        }
        current = p;
        lastPickTick = now;
        lastUpdateMs = System.currentTimeMillis();
        if (p >= 100) current = -1;
        return true;
    }

    /** Action bar variant: empty component when the progress was taken over. */
    public static Component onActionBar(Component msg) {
        return onText(msg.getString()) ? Component.empty() : msg;
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (s.contains("You retrieved some of Elle's supplies") || s.contains("You moved and the Chest slipped out")) current = -1;
        if (cfg().kuudraAlreadyPicking && KuudraContext.inKuudra() && s.contains("Someone else is currently trying to pick up these supplies!")) {
            alertUntil = System.currentTimeMillis() + (long) (cfg().kuudraAlreadyPickingSecs * 1000);
        }
    }

    private static void tick() {
        if (current >= 0 && (System.currentTimeMillis() - lastUpdateMs > 2000 || KuudraContext.phase() != KuudraContext.SUPPLIES)) current = -1;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (cfg().kuudraAlreadyPicking && System.currentTimeMillis() < alertUntil) {
            String t = "§c§lAlready Picking";
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(mc.getWindow().getGuiScaledWidth() / 2f, mc.getWindow().getGuiScaledHeight() / 2f - 40);
            pose.scale(3, 3);
            g.text(mc.font, t, -mc.font.width(t) / 2, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
        if (!cfg().kuudraSupplyProgress || current < 0) return;
        double shown = current;
        if (last >= 0 && current > last) {
            long since = ServerTickTimer.now() - lastPickTick;
            shown = Math.min(current, last + (current - last) * Math.min(since, ticksPerStep) / (double) ticksPerStep);
        }
        draw(g, mc, hud.x(), hud.y(), hud.scale(), shown);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, double p) {
        p = Math.max(0, Math.min(100, p));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, 0, 0, W, H);
        g.fill(4, 4, W - 4, 12, 0xFF333333);
        g.fill(4, 4, 4 + (int) ((W - 8) * p / 100), 12, 0xFF55FFFF);
        if ((int) p != pctVal) { pctVal = (int) p; pctStr = pctVal + "%"; }
        String pct = pctStr;
        g.text(mc.font, pct, (W - mc.font.width(pct)) / 2, 14, 0xFF55FFFF, true);
        pose.popMatrix();
    }
}
