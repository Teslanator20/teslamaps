package com.teslamaps.slayer;

// Ported from SkyblockAddons' SlayerArmorProgress + InventoryUtils.checkIfWearingSlayerArmor
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerArmorProgress {

    private static final Pattern UPGRADE = Pattern.compile("Next Upgrade: \\+([0-9,]+❈) \\(([0-9,]+)/([0-9,]+)\\)");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record Row(ItemStack stack, String text) {}

    private static List<Row> rows = List.of();
    private static List<Row> sample;
    private static HudEditElement hud;
    private static int ticks = 0;

    private SlayerArmorProgress() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("slayerArmorProgress", "Slayer Armor Progress").section("Combat")
                .enabled(() -> cfg().slayerArmorProgress)
                .size(() -> width(shown()), () -> height(shown()))
                .scaleRange(0.5f, 4.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, shown()));
        Feature.of("SlayerArmorProgress").section("Combat").when(() -> cfg().slayerArmorProgress)
                .tick(SlayerArmorProgress::tick)
                .hud("slayerarmorprogress", SlayerArmorProgress::render)
                .config("Combat", b -> b.toggle("Slayer Armor Progress", () -> cfg().slayerArmorProgress, v -> cfg().slayerArmorProgress = v)
                        .desc("Worn Revenant / Tarantula / Final Destination / Reaper pieces: kill progress to the next defense upgrade.")
                        .tags("slayer", "armor", "kills", "defense"));
    }

    private static List<Row> shown() {
        if (!rows.isEmpty()) return rows;
        if (sample == null) sample = List.of(new Row(new ItemStack(Items.LEATHER_CHESTPLATE), "§e55% §7(§a+40❈§7)"));
        return sample;
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        List<Row> out = new ArrayList<>(4);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            String id = ItemUtil.skyblockId(stack);
            if (id == null || !(id.startsWith("REVENANT") || id.startsWith("TARANTULA")
                    || id.startsWith("FINAL_DESTINATION") || id.startsWith("REAPER"))) continue;
            for (String line : ItemUtil.loreLines(stack)) {
                Matcher m = UPGRADE.matcher(line.trim());
                if (!m.matches()) continue;
                try {
                    float have = Float.parseFloat(m.group(2).replace(",", ""));
                    float need = Float.parseFloat(m.group(3).replace(",", ""));
                    int pct = need > 0 ? Math.round(have / need * 100f) : 0;
                    out.add(new Row(stack, "§e" + pct + "% §7(§a+" + m.group(1) + "§7)"));
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        rows = out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<Row> l = rows;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static int width(List<Row> l) {
        int w = 0;
        for (Row r : l) w = Math.max(w, Minecraft.getInstance().font.width(r.text()));
        return w + 24;
    }

    private static int height(List<Row> l) {
        return l.size() * 18 + 4;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Row> l) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(l), height(l));
        for (int i = 0; i < l.size(); i++) {
            Row r = l.get(i);
            g.item(r.stack(), 2, 2 + i * 18);
            g.text(mc.font, r.text(), 21, 7 + i * 18, 0xFFFFFFFF, true);
        }
        g.pose().popMatrix();
    }
}
