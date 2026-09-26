package com.teslamaps.features;

// Ported from SkyHanni's features/gui/LegionBobbinOverlay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.HudVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class LegionBobbinOverlay {

    private static final double DISTANCE = 30.0;
    private static final int LEGION_LIMIT = 20, BOBBIN_LIMIT = 5;
    private static final double LEGION_MULT = 0.07, BOBBIN_MULT = 0.2;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final List<String> SAMPLE = List.of("§d§lLegion: §b4 §7(1.12%)", "§3§lBobbin': §b3 §7(2.40%)");

    private static List<String> lines = List.of();
    private static int timer, width;
    private static HudEditElement hud;

    private LegionBobbinOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("legionBobbin", "Legion / Bobbin'").section("Fishing")
                .enabled(() -> cfg().legionBobbin)
                .size(() -> width(SAMPLE) + 6, () -> SAMPLE.size() * 10 + 4)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("LegionBobbinOverlay").section("Fishing").when(() -> cfg().legionBobbin)
                .tick(LegionBobbinOverlay::tick)
                .hudHiddenInInventory("legionbobbin", LegionBobbinOverlay::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Legion / Bobbin' Time");
                    b.toggle("Legion / Bobbin' Overlay", () -> c.legionBobbin, v -> c.legionBobbin = v)
                            .desc("Players within 30 blocks for Legion and bobbers for Bobbin' Time, with the buff your armor actually gets. Position in /tmap gui.")
                            .tags("legion", "bobbin", "enchant", "fishing", "armor");
                    b.toggle("  Only With Enchant", () -> c.legionBobbinOnlyEnchanted, v -> c.legionBobbinOnlyEnchanted = v)
                            .desc("Hides a line when no armor piece has that enchant");
                });
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    private static void tick() {
        if (timer++ % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        int bobbers = 0, players = 0;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(DISTANCE),
                x -> x instanceof FishingHook || x instanceof Player)) {
            if (e.distanceTo(mc.player) > DISTANCE) continue;
            if (e instanceof FishingHook) bobbers++;
            else if (e.getUUID().version() == 4 && !e.isInvisible()) players++;
        }
        bobbers = Math.min(bobbers, BOBBIN_LIMIT);
        players = Math.min(players, LEGION_LIMIT);
        int legion = 0, bobbin = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = mc.player.getItemBySlot(slot);
            CustomData d = st.isEmpty() ? null : st.get(DataComponents.CUSTOM_DATA);
            if (d == null) continue;
            var ench = d.copyTag().getCompoundOrEmpty("enchantments");
            legion += ench.getIntOr("ultimate_legion", 0);
            bobbin += ench.getIntOr("ultimate_bobbin_time", 0);
        }
        boolean only = cfg().legionBobbinOnlyEnchanted;
        List<String> next = new ArrayList<>(2);
        if (!only || legion > 0) {
            next.add("§d§lLegion: §b" + players + " §7(" + String.format(Locale.ROOT, "%.2f", legion * LEGION_MULT * players) + "%)");
        }
        if (!only || bobbin > 0) {
            next.add("§3§lBobbin': §b" + bobbers + " §7(" + String.format(Locale.ROOT, "%.2f", bobbin * BOBBIN_MULT * bobbers) + "%)");
        }
        lines = next;
        width = width(next);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        Perf.begin("LegionBobbinOverlay.hud");
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w + 6, l.size() * 10 + 4);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 3, 3 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        Perf.end();
    }
}
