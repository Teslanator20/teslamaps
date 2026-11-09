package com.teslamaps.features.hud;

// HUD-043, after SkyHanni's MaxwellApi.kt + ScoreboardElementPower/Tuning.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaxwellHud {

    private static final List<String> SAMPLE = List.of("§7Power: §aSighted §7(§61,263§7)", "§7Tunings: §c❁34§7, §e⚔20§7, §9☣7");
    private static final Pattern CHAT_POWER = Pattern.compile("^You selected the (.+?) (?:power )?for your Accessory Bag!$");
    private static final Pattern CHAT_POWER_SET = Pattern.compile("^Your selected power was set to (.+)!$");
    private static final Pattern THAUMATURGY = Pattern.compile("^(?:\\(\\d/\\d\\) )?Accessory Bag Thaumaturgy$");
    private static final Pattern TUNING = Pattern.compile("^\\+([^ ]+)(.) (.+)$");
    private static final Pattern STATS_TUNING = Pattern.compile("^You have: .+ \\+ ([^ ]+) (.)$");
    private static final Pattern THAUM_MP = Pattern.compile("^Total: ([\\d.,]+) Accessory Power$");
    private static final Pattern BAG_POWER = Pattern.compile("^Selected Power: (.+)$");
    private static final Pattern BAG_MP = Pattern.compile("^Accessory Power: ([\\d,]+)");
    private static final Pattern STAT_NAME = Pattern.compile("^. (.+)$");
    private static final Map<String, String> ICON_COLORS = Map.of("❤", "§c", "❈", "§a", "✦", "§f", "❁", "§c",
            "☠", "§9", "☣", "§9", "⚔", "§e", "✎", "§b");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static Object scannedScreen;
    private static int scans;

    private MaxwellHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("maxwellHud", "Maxwell Power")
                .enabled(() -> cfg().maxwellHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("MaxwellHud").when(() -> cfg().maxwellHud)
                .tick(MaxwellHud::tick)
                .serverChat(MaxwellHud::onChat)
                .hudHiddenInInventory("maxwellhud", MaxwellHud::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Maxwell Power");
                    b.toggle("Maxwell Power HUD", () -> c.maxwellHud, v -> { c.maxwellHud = v; timer = 0; })
                            .desc("Your Accessory Power and Maxwell tunings. Open \"Your Bags\" and talk to Maxwell once to fill it. "
                                    + "Position in /tmap gui.")
                            .tags("maxwell", "power", "tuning", "magical power", "accessory");
                    b.toggle("  Show Magical Power", () -> c.maxwellHudMp, v -> { c.maxwellHudMp = v; timer = 0; });
                    b.toggle("  Show Tunings", () -> c.maxwellHudTunings, v -> { c.maxwellHudTunings = v; timer = 0; });
                    b.toggle("    Compact (Top 3 In One Line)", () -> c.maxwellHudCompact, v -> { c.maxwellHudCompact = v; timer = 0; });
                });
    }

    private static TeslaMapsConfig.MaxwellData data() {
        String k = SbProfile.key();
        return cfg().maxwellData.computeIfAbsent(k == null ? "default" : k, x -> new TeslaMapsConfig.MaxwellData());
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        Matcher m = CHAT_POWER.matcher(s);
        if (!m.find()) m = CHAT_POWER_SET.matcher(s);
        else { setPower(m.group(1)); return; }
        if (m.find()) setPower(m.group(1));
    }

    private static void setPower(String p) {
        data().power = p.trim();
        TeslaMapsConfig.save();
        timer = 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs) {
            if (cs != scannedScreen) { scannedScreen = cs; scans = 0; }
            // items fill in async, read a few times
            if (scans < 6 && timer % 10 == 0) {
                scans++;
                if (scan(cs)) timer = 0;
            }
        } else {
            scannedScreen = null;
        }
        if (timer++ % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock() || SkyblockUtils.isInArea("The Rift")) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        TeslaMapsConfig.MaxwellData d = data();
        List<String> out = new ArrayList<>();
        if (d.power == null) out.add("§cOpen \"Your Bags\"!");
        else out.add("§7Power: §a" + d.power + (c.maxwellHudMp && d.magicalPower >= 0
                ? " §7(§6" + String.format(java.util.Locale.ROOT, "%,d", d.magicalPower) + "§7)" : ""));
        if (c.maxwellHudTunings) {
            if (d.tunings == null) out.add("§cTalk to \"Maxwell\"!");
            else if (d.tunings.isEmpty()) out.add("§cNo Maxwell Tunings");
            else if (c.maxwellHudCompact) {
                StringBuilder sb = new StringBuilder(d.tunings.size() == 1 ? "§7Tuning: " : "§7Tunings: ");
                for (int i = 0; i < Math.min(3, d.tunings.size()); i++) {
                    String[] t = d.tunings.get(i).split("\\|", 3);
                    if (t.length < 3) continue;
                    if (i > 0) sb.append("§7, ");
                    sb.append(color(t[1])).append(t[1]).append(t[0]);
                }
                out.add(sb.toString());
            } else {
                out.add(d.tunings.size() == 1 ? "§7Tuning:" : "§7Tunings:");
                for (String e : d.tunings) {
                    String[] t = e.split("\\|", 3);
                    if (t.length == 3) out.add(" §7- " + color(t[1]) + t[1] + t[0] + " §f" + t[2]);
                }
            }
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String color(String icon) {
        return ICON_COLORS.getOrDefault(icon, "§f");
    }

    private static boolean scan(AbstractContainerScreen<?> cs) {
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        TeslaMapsConfig.MaxwellData d = data();
        boolean changed = false;
        if (THAUMATURGY.matcher(title).matches()) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
                List<String> lore = ItemUtil.loreLines(slot.getItem());
                if (!lore.isEmpty() && lore.get(lore.size() - 1).trim().equals("Power is selected!")) {
                    String name = StringUtil.stripTrim(slot.getItem().getHoverName().getString());
                    if (!name.equals(d.power)) { d.power = name; changed = true; }
                }
            }
            ItemStack tuning = item(cs, 51);
            if (tuning != null) {
                List<String> found = new ArrayList<>();
                boolean active = false;
                for (String l : ItemUtil.loreLines(tuning)) {
                    String s = l.trim();
                    if (s.equals("Your tuning:")) { active = true; continue; }
                    if (!active) continue;
                    if (s.isEmpty()) break;
                    Matcher m = TUNING.matcher(s);
                    if (m.find()) found.add(m.group(1) + "|" + m.group(2) + "|" + m.group(3));
                }
                // the rounded values here only fill an empty list, Stats Tuning has the exact ones
                if (active && (d.tunings == null || d.tunings.isEmpty()) && !found.equals(d.tunings)) { d.tunings = found; changed = true; }
            }
            ItemStack mp = item(cs, 48);
            if (mp != null) {
                for (String l : ItemUtil.loreLines(mp)) {
                    Matcher m = THAUM_MP.matcher(l.trim());
                    if (m.find()) { changed |= setMp(d, m.group(1)); break; }
                }
            }
        } else if (title.equals("Stats Tuning")) {
            List<String> found = new ArrayList<>();
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
                for (String l : ItemUtil.loreLines(slot.getItem())) {
                    Matcher m = STATS_TUNING.matcher(l.trim());
                    if (!m.find()) continue;
                    Matcher n = STAT_NAME.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString()));
                    found.add(m.group(1) + "|" + m.group(2) + "|" + (n.find() ? n.group(1) : "?"));
                }
            }
            if (!found.isEmpty() && !found.equals(d.tunings)) { d.tunings = found; changed = true; }
        } else if (title.equals("Your Bags")) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
                if (!StringUtil.stripTrim(slot.getItem().getHoverName().getString()).equals("Accessory Bag")) continue;
                boolean foundMp = false;
                for (String l : ItemUtil.loreLines(slot.getItem())) {
                    String s = l.trim();
                    if (s.contains("Visit Maxwell in the Hub to learn") && !"No Power".equals(d.power)) { d.power = "No Power"; changed = true; }
                    Matcher p = BAG_POWER.matcher(s);
                    if (p.find() && !p.group(1).equals(d.power)) { d.power = p.group(1); changed = true; }
                    Matcher m = BAG_MP.matcher(s);
                    if (m.find()) {
                        foundMp = true;
                        // boosted inside dungeons
                        if (!DungeonManager.isInDungeon()) changed |= setMp(d, m.group(1));
                    }
                }
                if (!foundMp && d.magicalPower != 0 && !ItemUtil.loreLines(slot.getItem()).isEmpty()) {
                    d.magicalPower = 0;
                    d.tunings = new ArrayList<>();
                    changed = true;
                }
            }
        }
        if (changed) TeslaMapsConfig.save();
        return changed;
    }

    private static boolean setMp(TeslaMapsConfig.MaxwellData d, String raw) {
        try {
            int v = (int) Double.parseDouble(raw.replace(",", ""));
            if (v == d.magicalPower) return false;
            d.magicalPower = v;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static ItemStack item(AbstractContainerScreen<?> cs, int index) {
        List<Slot> slots = cs.getMenu().slots;
        if (index >= slots.size()) return null;
        Slot s = slots.get(index);
        return s.container instanceof Inventory || s.getItem().isEmpty() ? null : s.getItem();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
