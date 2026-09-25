package com.teslamaps.features;

// Ported from SkyHanni's CropSpeedMeter.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CropSpeedMeter {

    private static final List<String> SAMPLE = List.of("§7Crop Speed Meter", " §7Current Wheat counter: §e1,204",
            "", "§6Calculation results", " §7Crops collected: 3,612", " §7Blocks broken: 1,204",
            " §7Crops per Block: 3", " §7Calculated Farming Fortune: §e200");

    private static Map<String, Long> start = Map.of();
    private static String crop;
    private static int blocks;
    private static List<String> snapshot = List.of();
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static HudEditElement hud;

    private CropSpeedMeter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("cropSpeedMeter", "Crop Speed Meter").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().cropSpeedMeter)
                .size(() -> CropStats.linesWidth(SAMPLE), () -> CropStats.linesHeight(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, SAMPLE, CropStats.linesWidth(SAMPLE)));
        Feature.of("CropSpeedMeter").section("Garden").when(() -> cfg().cropSpeedMeter)
                .tick(CropSpeedMeter::tick)
                .hud("cropspeedmeter", CropSpeedMeter::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crop Speed Meter");
                    b.toggle("Crop Speed Meter", () -> c.cropSpeedMeter, v -> { c.cropSpeedMeter = v; reset(); })
                            .desc("Measures your real farming fortune: open /cropmilestones, farm one crop, open it again. Also /tmap cropspeedmeter.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("cropspeedmeter").executes(c -> {
                    TeslaMapsConfig t = cfg();
                    t.cropSpeedMeter = !t.cropSpeedMeter;
                    TeslaMapsConfig.save();
                    reset();
                    msg("Crop Speed Meter " + (t.cropSpeedMeter ? "§aenabled" : "§cdisabled"));
                    return 1;
                }))));
    }

    private static void reset() {
        start = Map.of();
        crop = null;
        blocks = 0;
        snapshot = List.of();
    }

    static void onBreak(String key) {
        if (start.isEmpty()) return;
        if (!key.equals(crop)) {
            crop = key;
            blocks = 0;
            snapshot = List.of();
        }
        blocks++;
    }

    static void onMilestones() {
        Map<String, Long> now = CropStats.milestones();
        if (start.isEmpty()) {
            start = new HashMap<>(now);
            crop = null;
            snapshot = List.of();
            return;
        }
        if (crop == null || blocks <= 0) return;
        CropStats.Crop c = CropStats.byKey(crop);
        long crops = now.getOrDefault(crop, 0L) - start.getOrDefault(crop, 0L);
        double perBlock = Math.round(crops * 1000.0 / blocks) / 1000.0;
        double ff = Math.round(perBlock * 100 / c.baseDrops() * 1000.0) / 1000.0 - 100;
        List<String> s = new ArrayList<>();
        s.add("");
        s.add("§6Calculation results");
        s.add(" §7Crops collected: " + CropStats.fmt(crops));
        s.add(" §7Blocks broken: " + CropStats.fmt(blocks));
        s.add(" §7Crops per Block: " + CropStats.fmt(perBlock));
        s.add(" §7Calculated Farming Fortune: §e" + CropStats.fmt(ff));
        s.add("§cOpen /cropmilestones again to recalculate!");
        snapshot = s;
        if (perBlock > 0) {
            cfg().cropMeasuredPerBlock.put(crop, perBlock);
            cfg().cropLatestFortune.put(crop, ff);
            TeslaMapsConfig.save();
        }
    }

    private static void tick() {
        if (++ticks % 15 != 0) return;
        List<String> out = new ArrayList<>();
        out.add("§7Crop Speed Meter");
        if (start.isEmpty()) {
            out.add("§cOpen §e/cropmilestones §cto start!");
        } else if (crop == null) {
            out.add("§cStart breaking blocks!");
        } else {
            out.add(" §7Current " + CropStats.byKey(crop).name() + " counter: §e" + CropStats.fmt(blocks));
            if (snapshot.isEmpty()) out.add("§cOpen §e/cropmilestones §cagain to calculate!");
            else out.addAll(snapshot);
        }
        lines = out;
        width = CropStats.linesWidth(out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        CropStats.drawLines(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + s));
    }
}
