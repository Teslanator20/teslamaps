package com.teslamaps.features.theend;

// Concept from SkyblockExtras (Soul Flow / Final Destination / Overflow Mana / Legion displays),
// legion math from SkyHanni's LegionBobbinOverlay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EndStatsHud {

    private static final List<String> SAMPLE = List.of(
            "§3Soulflow: §b12,345⸎", "§5Final Destination: §d8⚶", "§3Overflow Mana: §b400ʬ", "§dLegion: §b5 §7(1.75%)");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double LEGION_RANGE = 30.0;
    private static final int LEGION_MAX = 20;
    private static final double LEGION_MULT = 0.07;

    private static volatile String overflow = null;
    private static volatile String mana = null;
    private static volatile String fdStacks = null;
    private static volatile long actionBarMs = 0;

    private static String soulflow = null;
    private static String fdKills = null;
    private static int legionLevels = 0;
    private static int nearbyPlayers = 0;
    private static List<String> lines = List.of();
    private static int width;
    private static int timer;
    private static HudEditElement hud;

    private EndStatsHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("endStats", "End Stats").section("The End")
                .enabled(() -> cfg().endStatsHud)
                .size(() -> panelWidth(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, panelWidth(SAMPLE)));
        Feature.of("EndStatsHud").section("The End").when(() -> cfg().endStatsHud)
                .tick(EndStatsHud::tick)
                .hudHiddenInInventory("endstats", EndStatsHud::render)
                .config("The End", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("End Stats");
                    b.toggle("End Stats HUD", () -> c.endStatsHud, v -> c.endStatsHud = v)
                            .desc("Small panel for Enderman slayer / Legion setups. Lines without data stay hidden. Position in /tmap gui.")
                            .tags("soulflow", "final destination", "overflow mana", "legion");
                    b.toggle("  Soulflow", () -> c.endStatsSoulflow, v -> c.endStatsSoulflow = v)
                            .desc("From the Soulflow tab widget, else the Internalized line of a Soulflow item in your inventory");
                    b.toggle("  Final Destination", () -> c.endStatsFinalDestination, v -> c.endStatsFinalDestination = v)
                            .desc("Armor stacks from the action bar, else the Kills line of the worn pieces");
                    b.toggle("  Overflow Mana", () -> c.endStatsOverflow, v -> c.endStatsOverflow = v);
                    b.toggle("    Combine With Mana", () -> c.endStatsOverflowWithMana, v -> c.endStatsOverflowWithMana = v)
                            .desc("One line: Mana 1,016/1,355 +400ʬ");
                    b.toggle("  Legion", () -> c.endStatsLegion, v -> c.endStatsLegion = v)
                            .desc("Players within 30 blocks (max 20) and the buff of your Legion armor");
                });
    }

    /** Gui.setOverlayMessage, main thread. */
    public static void onActionBar(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.endStatsHud || raw == null || raw.isEmpty()) return;
        String s = StringUtil.strip(raw);
        String of = null, mn = null, fd = null;
        for (String tok : s.split("\\s+")) {
            if (tok.isEmpty()) continue;
            if (tok.indexOf('') >= 0 || tok.indexOf('ʬ') >= 0) of = digits(tok);
            else if (tok.indexOf('') >= 0 || tok.indexOf('✎') >= 0) mn = tok.replace("", "").replace("✎", "");
            else if (tok.indexOf('⚶') >= 0) fd = digits(tok);
        }
        overflow = of;
        mana = mn;
        fdStacks = fd;
        actionBarMs = System.currentTimeMillis();
    }

    private static String digits(String tok) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tok.length(); i++) {
            char ch = tok.charAt(i);
            if (Character.isDigit(ch) || ch == ',') b.append(ch);
        }
        return b.isEmpty() ? null : b.toString();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.player == null || !SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        if (timer++ % 20 == 0) scanSlow(mc, c);

        if (System.currentTimeMillis() - actionBarMs > 3000) {
            overflow = null;
            mana = null;
            fdStacks = null;
        }

        List<String> out = new ArrayList<>(4);
        if (c.endStatsSoulflow && soulflow != null) out.add("§3Soulflow: §b" + soulflow + "⸎");
        if (c.endStatsFinalDestination) {
            String st = fdStacks;
            if (st != null) out.add("§5Final Destination: §d" + st + "⚶");
            else if (fdKills != null) out.add("§5Final Destination: §d" + fdKills + " §7kills");
        }
        if (c.endStatsOverflow) {
            String of = overflow, mn = mana;
            if (c.endStatsOverflowWithMana && mn != null) {
                out.add("§bMana: §b" + mn + (of != null ? " §3+" + of + "ʬ" : ""));
            } else if (of != null) {
                out.add("§3Overflow Mana: §b" + of + "ʬ");
            }
        }
        if (c.endStatsLegion) {
            String line = "§dLegion: §b" + nearbyPlayers;
            if (legionLevels > 0) line += String.format(Locale.ROOT, " §7(%.2f%%)", legionLevels * LEGION_MULT * nearbyPlayers);
            out.add(line);
        }
        lines = out;
        width = panelWidth(out);
    }

    private static void scanSlow(Minecraft mc, TeslaMapsConfig c) {
        Player self = mc.player;
        if (c.endStatsSoulflow) {
            String found = null;
            for (String raw : TabListUtils.getTabListLines()) {
                String l = StringUtil.stripTrim(raw);
                if (l.startsWith("Soulflow: ")) {
                    found = l.substring(10).replace("⸎", "").trim();
                    break;
                }
            }
            if (found == null) {
                for (int i = 0; i < self.getInventory().getContainerSize() && found == null; i++) {
                    ItemStack st = self.getInventory().getItem(i);
                    if (st.isEmpty()) continue;
                    for (String l : ItemUtil.loreLines(st)) {
                        if (l.startsWith("Internalized: ")) {
                            found = digits(l.substring(14));
                            break;
                        }
                    }
                }
            }
            soulflow = found;
        }

        int levels = 0;
        String kills = null;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = self.getItemBySlot(slot);
            if (st.isEmpty()) continue;
            if (c.endStatsLegion) {
                CustomData d = st.get(DataComponents.CUSTOM_DATA);
                if (d != null) levels += d.copyTag().getCompoundOrEmpty("enchantments").getIntOr("ultimate_legion", 0);
            }
            if (c.endStatsFinalDestination && kills == null && ItemUtil.skyblockId(st).startsWith("FINAL_DESTINATION")) {
                for (String l : ItemUtil.loreLines(st)) {
                    String t = l.trim();
                    if (t.startsWith("Kills: ")) {
                        kills = digits(t.substring(7));
                        break;
                    }
                }
            }
        }
        legionLevels = levels;
        fdKills = kills;

        if (c.endStatsLegion && mc.level != null) {
            int n = 0;
            for (Player p : mc.level.players()) {
                if (p == self || p.getUUID().version() != 4) continue;
                if (p.distanceToSqr(self) > LEGION_RANGE * LEGION_RANGE) continue;
                if (++n >= LEGION_MAX) break;
            }
            nearbyPlayers = n;
        }
    }

    private static int panelWidth(List<String> ls) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
