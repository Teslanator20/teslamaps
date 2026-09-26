package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/general/trackers/KuudraTrackers.kt + trackerData.kt
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Infernal keys bought minus used, and finished runs whose chest was not opened yet. */
public final class KuudraRunTrackers {

    private static final String[] NPCS = {"Vesuvius", "Barbarian Emissary", "Mage Emissary"};

    private static HudEditElement hud;
    private static boolean nearNpc;
    private static String keyLine = "", runLine = "";
    private static int ticks;

    private KuudraRunTrackers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuudraRunTrackers", "Kuudra Keys / Pending Runs").section("Kuudra")
                .enabled(() -> cfg().kuudraKeyTracker || cfg().kuudraRunTracker)
                .size(() -> Minecraft.getInstance().font.width("Pending Runs: 00") + 8, () -> 28)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§6Infernal Keys: §f4", "§bPending Runs: §f2"));
        Feature.of("KuudraRunTrackers").section("Kuudra")
                .when(() -> cfg().kuudraKeyTracker || cfg().kuudraRunTracker)
                .tick(KuudraRunTrackers::tick)
                .serverChat(KuudraRunTrackers::onChat)
                .hud("kuudraruntrackers", KuudraRunTrackers::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Keys & Pending Runs");
                    b.toggle("Infernal Key Tracker", () -> c.kuudraKeyTracker, v -> c.kuudraKeyTracker = v)
                            .desc("Counts bought Infernal Kuudra Keys and subtracts one per opened paid Infernal chest. Shown near Vesuvius and the emissaries. "
                                    + "Fix with /tmap kuudratrack keys <n>. Move it in /tmap gui.");
                    b.toggle("Pending Run Tracker", () -> c.kuudraRunTracker, v -> c.kuudraRunTracker = v)
                            .desc("Finished runs minus opened chests = chests still waiting at Vesuvius. Fix with /tmap kuudratrack runs <n>.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("kuudratrack")
                        .then(ClientCommands.literal("keys").then(ClientCommands.argument("n", IntegerArgumentType.integer(0))
                                .executes(c -> set(true, IntegerArgumentType.getInteger(c, "n")))))
                        .then(ClientCommands.literal("runs").then(ClientCommands.argument("n", IntegerArgumentType.integer(0))
                                .executes(c -> set(false, IntegerArgumentType.getInteger(c, "n"))))))));
    }

    private static int set(boolean keys, int n) {
        if (keys) cfg().kuudraInfernalKeys = n;
        else cfg().kuudraPendingRuns = n;
        TeslaMapsConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7"
                + (keys ? "Infernal Keys" : "Pending Runs") + " set to §f" + n));
        return 1;
    }

    /** Called by the profit tracker, or by our own chat hook when that one is off. */
    static void onChestOpened(boolean infernalPaid) {
        TeslaMapsConfig c = cfg();
        if (!c.section("Kuudra")) return;
        boolean changed = false;
        if (c.kuudraKeyTracker && infernalPaid && c.kuudraInfernalKeys > 0) { c.kuudraInfernalKeys--; changed = true; }
        if (c.kuudraRunTracker && c.kuudraPendingRuns > 0) { c.kuudraPendingRuns--; changed = true; }
        if (changed) TeslaMapsConfig.save();
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        TeslaMapsConfig c = cfg();
        if (c.kuudraKeyTracker && s.contains("You bought Infernal Kuudra Key!")) {
            c.kuudraInfernalKeys++;
            TeslaMapsConfig.save();
        } else if (c.kuudraRunTracker && KuudraContext.inKuudra() && (s.equals("KUUDRA DOWN!") || s.equals("DEFEAT"))) {
            c.kuudraPendingRuns++;
            TeslaMapsConfig.save();
        } else if (!c.kuudraProfitTracker && s.contains("CHEST REWARDS")) {
            onChestOpened(s.contains("PAID") && KuudraContext.inKuudra() && inInfernal());
        }
    }

    private static boolean inInfernal() {
        return Kuudra.tier() == 5;
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        nearNpc = false;
        if (c.kuudraKeyTracker && mc.level != null && mc.player != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.distanceToSqr(mc.player) > 100 || !e.hasCustomName()) continue;
                String name = StringUtil.stripTrim(e.getName().getString());
                for (String n : NPCS) if (name.equals(n)) { nearNpc = true; break; }
                if (nearNpc) break;
            }
        }
        keyLine = c.kuudraKeyTracker && nearNpc ? "§6Infernal Keys: §f" + c.kuudraInfernalKeys : "";
        runLine = c.kuudraRunTracker ? "§bPending Runs: §f" + c.kuudraPendingRuns : "";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || keyLine.isEmpty() && runLine.isEmpty()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), keyLine, runLine);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String a, String b) {
        int w = Math.max(mc.font.width(a), mc.font.width(b));
        int lines = (a.isEmpty() ? 0 : 1) + (b.isEmpty() ? 0 : 1);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, 0, 0, w + 8, lines * 10 + 6);
        int ty = 4;
        if (!a.isEmpty()) { g.text(mc.font, a, 4, ty, 0xFFFFFFFF, true); ty += 10; }
        if (!b.isEmpty()) g.text(mc.font, b, 4, ty, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
