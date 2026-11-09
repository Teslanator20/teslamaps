package com.teslamaps.features.rift;

// After SkyHanni's LivingMetalSuitProgress.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LivingMetalSuitProgress {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SAMPLE = {"§7Living Metal Suit Progress: §a62.5%",
            "§7- Living Metal Helmet: " + bar(0.5) + " §b50%"};

    private static String[] lines = new String[0];
    private static ItemStack[] icons = new ItemStack[0];
    private static int w;
    private static int ticks;

    private LivingMetalSuitProgress() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("riftLivingMetalHud", "Living Metal Suit").section("Rift")
                .enabled(() -> cfg().riftLivingMetalHud)
                .size(() -> lines.length > 0 ? w : Minecraft.getInstance().font.width(SAMPLE[1]),
                        () -> lines.length > 0 ? RiftSpot.height(lines, icons) : 19)
                .preview((g, mc, x, y, s) -> {
                    if (lines.length > 0) RiftSpot.drawLines(g, mc, x, y, s, lines, icons, w);
                    else RiftSpot.drawLines(g, mc, x, y, s, SAMPLE, null, mc.font.width(SAMPLE[1]));
                });
        Feature.of("LivingMetalSuitProgress").section("Rift").when(() -> cfg().riftLivingMetalHud)
                .tick(LivingMetalSuitProgress::tick)
                .hud("riftlivingmetal", LivingMetalSuitProgress::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Living Metal Suit Progress", () -> c.riftLivingMetalHud, v -> c.riftLivingMetalHud = v)
                            .desc("HUD with the evolution progress of every worn Living Metal piece. Move it in /tmap gui.")
                            .tags("rift", "living metal", "armor", "evolution");
                    b.toggle("  Compact When Maxed", () -> c.riftLivingMetalCompact, v -> c.riftLivingMetalCompact = v);
                });
    }

    private static void tick() {
        if (ticks++ % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!RiftSpot.inRift()) {
            lines = new String[0];
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        List<Double> progress = new ArrayList<>();
        boolean any = false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (st.isEmpty()) continue;
            Double p = progress(st);
            if (p != null || ItemUtil.skyblockId(st).contains("LIVING_METAL")) any = true;
            stacks.add(st);
            progress.add(p);
        }
        if (!any) {
            lines = new String[0];
            return;
        }
        int maxed = 0;
        double sum = 0;
        for (Double p : progress) {
            if (p != null && p >= 1) maxed++;
            sum += p == null ? 1 : p;
        }
        boolean isMaxed = maxed == 4;
        List<String> out = new ArrayList<>();
        List<ItemStack> ic = new ArrayList<>();
        out.add("§7Living Metal Suit Progress: " + (isMaxed ? "§a§lMAXED!" : "§a" + pct(sum / progress.size())));
        ic.add(null);
        if (!(isMaxed && cfg().riftLivingMetalCompact)) {
            for (int i = 0; i < stacks.size(); i++) {
                Double p = progress.get(i);
                out.add("§7" + stacks.get(i).getHoverName().getString() + "§7: "
                        + (p == null ? "§cStart upgrading it!" : bar(p) + " §b" + pct(p)));
                ic.add(stacks.get(i));
            }
        }
        String[] l = out.toArray(new String[0]);
        ItemStack[] icn = ic.toArray(new ItemStack[0]);
        w = RiftSpot.width(mc, l, icn);
        icons = icn;
        lines = l;
    }

    private static Double progress(ItemStack st) {
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        return d.copyTag().getInt("lm_evo").map(v -> Math.min(v, 100) / 100.0).orElse(null);
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%.1f%%", v * 100).replace(".0%", "%");
    }

    private static String bar(double p) {
        int filled = (int) (p * 20);
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) sb.append('|');
        sb.append("§7");
        for (int i = filled; i < 20; i++) sb.append('|');
        return sb.append("§f").toString();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String[] l = lines;
        if (l.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TeslaMapsConfig c = cfg();
        RiftSpot.drawLines(g, mc, c.riftLivingMetalHudX, c.riftLivingMetalHudY, c.riftLivingMetalHudScale, l, icons, w);
    }
}
