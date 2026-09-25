package com.teslamaps.features.mining;

// Ported from SkyHanni's features/misc/JoinCrystalHollows.kt, timer + auto renew after SkyblockExtras
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.Vec3;

public final class HollowsPass {

    private static final BlockPos GWENDOLYN = new BlockPos(88, 198, -99);
    private static final long PASS_MS = 6 * 60 * 60 * 1000L;
    private static final String RENEW_COMMAND = "purchasecrystallhollowspass";

    private static HudEditElement hud;
    private static long wrongPassAt;
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
                .when(() -> cfg().hollowsPassHelper || cfg().hollowsPassTimer || cfg().hollowsPassAutoRenew)
                .tick(HollowsPass::tick)
                .serverChat(HollowsPass::onChat)
                .world(HollowsPass::render)
                .hud("hollowspass", HollowsPass::renderHud)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Crystal Hollows Pass");
                    b.toggle("Hollows Pass Helper", () -> c.hollowsPassHelper, v -> c.hollowsPassHelper = v)
                            .desc("Without a pass: clickable /warp mines line and a waypoint to Gwendolyn. After buying: clickable /warp ch line.")
                            .tags("crystal hollows", "gwendolyn", "warp");
                    b.toggle("Hollows Pass Timer", () -> c.hollowsPassTimer, v -> c.hollowsPassTimer = v)
                            .desc("Time left on the 6 hour pass, shown in the Crystal Hollows. Move it in /tmap gui");
                    b.toggle("Auto Renew Pass", () -> c.hollowsPassAutoRenew, v -> c.hollowsPassAutoRenew = v)
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
        if (in) wrongPassAt = 0;
        if (in != wasInHollows) {
            wasInHollows = in;
            if (in && cfg().hollowsPassTimer && !timerRunning()) {
                cfg().hollowsPassExpires = System.currentTimeMillis() + PASS_MS;
            }
        }
        if (cfg().hollowsPassHelper && SkyblockUtils.isInArea("Dwarven Mines") && inTime() && !helpedHere) {
            helpedHere = true;
            say(Component.literal("§a[TeslaMaps] §eBuy a §2Crystal Hollows Pass §efrom §5Gwendolyn§e!"));
        }
        if (!SkyblockUtils.isInArea("Dwarven Mines")) helpedHere = false;
        long left = cfg().hollowsPassExpires - System.currentTimeMillis();
        if (left <= 0) {
            line = "§5Hollows Pass: §cexpired";
        } else {
            long m = left / 60000;
            line = "§5Hollows Pass: " + (m < 10 ? "§c" : m < 60 ? "§e" : "§a") + (m / 60) + "h " + (m % 60) + "m";
        }
    }

    private static boolean helpedHere;

    private static boolean inTime() {
        return System.currentTimeMillis() - wrongPassAt < 2 * 60 * 1000L;
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        TeslaMapsConfig c = cfg();
        if (t.equals("You do not have an active Crystal Hollows pass!")) {
            wrongPassAt = System.currentTimeMillis();
            helpedHere = SkyblockUtils.isInArea("Dwarven Mines");
            if (!c.hollowsPassHelper) return;
            if (!SkyblockUtils.isInArea("Dwarven Mines")) {
                say(clickable("§a[TeslaMaps] §eClick here to warp to the Dwarven Mines!", "/warp mines"));
            } else {
                say(Component.literal("§a[TeslaMaps] §eBuy a §2Crystal Hollows Pass §efrom §5Gwendolyn§e."));
            }
        } else if (t.equals("[NPC] Gwendolyn: Great! Now hop on into the Minecart and I'll get you on your way!")) {
            c.hollowsPassExpires = System.currentTimeMillis() + PASS_MS;
            if (c.hollowsPassHelper && inTime()) {
                say(clickable("§a[TeslaMaps] §eClick here to warp to the Crystal Hollows!", "/warp ch"));
            }
        } else if (t.startsWith("You purchased a new pass for")) {
            c.hollowsPassExpires = System.currentTimeMillis() + PASS_MS;
        } else if (t.contains("Click here to purchase a new") && t.contains("pass")) {
            if (c.hollowsPassAutoRenew && !LegitMode.blocksCheats() && renewAt == 0) {
                renewAt = System.currentTimeMillis() + 1500 + (long) (Math.random() * 2000);
            }
        }
    }

    private static Component clickable(String text, String command) {
        return Component.literal(text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eRun " + command))));
    }

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(c);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!cfg().hollowsPassHelper || !inTime() || !SkyblockUtils.isInArea("Dwarven Mines")) return;
        WaypointSet.draw(pose, cam, GWENDOLYN, 0xFFFFFF55, "§eBuy Crystal Hollows Pass", true, true, true);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!cfg().hollowsPassTimer || hud == null || !Mining.inHollows()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
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
