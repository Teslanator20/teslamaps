package com.teslamaps.features.mining;

// Ported from SkyHanni's features/misc/JoinCrystalHollows.kt, timer + auto renew after SkyblockExtras
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HollowsPass {

    private static final long PASS_MS = 6 * 60 * 60 * 1000L;
    private static final String RENEW_COMMAND = "purchasecrystallhollowspass";

    private static HudEditElement hud;
    private static long renewAt;
    private static boolean wasInHollows;
    private static String line = "";
    private static int ticks;

    private HollowsPass() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("hollowsPass", "Crystal Hollows Pass").section("Mining")
                .ctx(HudEditElement.HudCtx.MINING)
                .enabled(() -> cfg().hollowsPassTimer)
                .size(() -> Minecraft.getInstance().font.width("Hollows Pass: 5h 59m") + 8, () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§5Hollows Pass: §a5h 59m"));
        Feature.of("HollowsPass").section("Mining")
                .when(() -> cfg().hollowsPassTimer || cfg().hollowsPassAutoRenew)
                .tick(HollowsPass::tick)
                .serverChat(HollowsPass::onChat)
                .hud("hollowspass", HollowsPass::renderHud)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Crystal Hollows Pass");
                    b.toggle("Hollows Pass Timer", () -> c.hollowsPassTimer, v -> c.hollowsPassTimer = v)
                            .desc("Time left on the 6 hour pass, shown in the Crystal Hollows. Move it in /tmap gui");
                    b.toggle("Auto Renew Hollows Pass", () -> c.hollowsPassAutoRenew, v -> c.hollowsPassAutoRenew = v)
                            .desc("Rebuys the pass (10,000 coins) when Hypixel offers a new one.")
                            .cheat();
                });
    }

    private static boolean timerRunning() {
        return cfg().hollowsPassExpires > System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (renewAt != 0 && System.currentTimeMillis() >= renewAt) {
            renewAt = 0;
            if (cfg().hollowsPassAutoRenew && !LegitMode.blocksCheats() && mc.getConnection() != null && Mining.inHollows()) {
                mc.getConnection().sendCommand(RENEW_COMMAND);
            }
        }
        if (++ticks < 20) return;
        ticks = 0;
        boolean in = Mining.inHollows();
        if (in != wasInHollows) {
            wasInHollows = in;
            if (in && cfg().hollowsPassTimer && !timerRunning()) {
                cfg().hollowsPassExpires = System.currentTimeMillis() + PASS_MS;
            }
        }
        long left = cfg().hollowsPassExpires - System.currentTimeMillis();
        if (left <= 0) {
            line = "§5Hollows Pass: §cexpired";
        } else {
            long m = left / 60000;
            line = "§5Hollows Pass: " + (m < 10 ? "§c" : m < 60 ? "§e" : "§a") + (m / 60) + "h " + (m % 60) + "m";
        }
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        TeslaMapsConfig c = cfg();
        if (t.equals("[NPC] Gwendolyn: Great! Now hop on into the Minecart and I'll get you on your way!")) {
            c.hollowsPassExpires = System.currentTimeMillis() + PASS_MS;
        } else if (t.startsWith("You purchased a new pass for")) {
            c.hollowsPassExpires = System.currentTimeMillis() + PASS_MS;
        } else if (t.contains("Click here to purchase a new") && t.contains("pass")) {
            if (c.hollowsPassAutoRenew && !LegitMode.blocksCheats() && renewAt == 0) {
                renewAt = System.currentTimeMillis() + 1500 + (long) (Math.random() * 2000);
            }
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!cfg().hollowsPassTimer || hud == null || !Mining.inHollows()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), line);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        int w = mc.font.width(text) + 8;
        HudStyle.panel(g, 0, 0, w, 16);
        g.text(mc.font, text, 4, 4, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
