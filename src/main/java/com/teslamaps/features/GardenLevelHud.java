package com.teslamaps.features;

// Ported from SkyHanni's GardenLevelDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Garden level N (x/y)" from the desk, counted up live from visitor rewards; overflow past 15. */
public final class GardenLevelHud {

    private static final long[] EXP = {0, 70, 70, 140, 240, 600, 1500, 2000, 2500, 3000, 10000, 10000, 10000, 10000, 10000};
    private static final int MAX = EXP.length;
    private static final long OVERFLOW = 10000;
    private static final String BAR_INDENT = "                    ";

    private static final Pattern NAME = Pattern.compile("^Garden (?:Desk|Level (.+))$");
    private static final Pattern PROGRESS = Pattern.compile("^Progress to Level ([^:]+):.*");
    private static final Pattern NEXT_XP = Pattern.compile(".* ([\\d,.]+[kKmM]?)/[\\d,.]+[kKmM]?$");
    private static final Pattern OVERFLOW_LINE = Pattern.compile("^\\s+([\\d,]+)$");
    private static final Pattern REWARD = Pattern.compile("^\\s*\\+([\\d,]+) Garden Experience$");

    private static HudEditElement hud;
    private static Feature feature;
    private static String line = "";
    private static int width = 100;
    private static int lastVersion = -1;
    private static boolean overflowSetting;
    private static net.minecraft.client.gui.screens.Screen scanned;
    private static int scannedState = -1;
    private static boolean roman = true;

    private GardenLevelHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("gardenLevelHud", "Garden Level").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().gardenLevelHud)
                .size(() -> width + 4, () -> 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§aGarden level 17 §7(§e3,120§7/§e10,000§7)",
                        mc.font.width("§aGarden level 17 §7(§e3,120§7/§e10,000§7)")));
        feature = Feature.of("GardenLevelHud").section("Garden").when(() -> cfg().gardenLevelHud)
                .tick(GardenLevelHud::tick)
                .serverChat(GardenLevelHud::onChat)
                .hudHiddenInInventory("gardenlevel", GardenLevelHud::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Garden Level");
                    b.toggle("Garden Level Display", () -> c.gardenLevelHud, v -> c.gardenLevelHud = v)
                            .desc("Garden level and xp to the next level, read from the Desk or SkyBlock Menu "
                                    + "and counted up from visitor rewards.")
                            .tags("garden xp", "desk");
                    b.toggle("  Overflow Levels Past 15", () -> c.gardenLevelOverflow, v -> c.gardenLevelOverflow = v)
                            .desc("Levels past 15 in the HUD and in the Garden Level tooltip, with bar and overflow xp");
                    b.toggle("  Overflow Level Up Message", () -> c.gardenLevelChat, v -> c.gardenLevelChat = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active() && cfg().gardenLevelOverflow && !lines.isEmpty()) tooltip(stack, lines);
        });
    }

    public static long expForLevel(int level) {
        long total = 0;
        for (int i = 0; i < Math.min(level, MAX); i++) total += EXP[i];
        if (level > MAX) total += (level - MAX) * OVERFLOW;
        return total;
    }

    public static int levelOf(long xp, boolean overflow) {
        long total = 0;
        int level = 0;
        for (long e : EXP) {
            total += e;
            if (total > xp) return level;
            level++;
        }
        if (!overflow) return level;
        return level + (int) ((xp - total) / OVERFLOW);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs) readMenu(cs);
        int v = GardenMilestoneData.version();
        boolean ov = cfg().gardenLevelOverflow;
        if (v == lastVersion && ov == overflowSetting && !line.isEmpty()) return;
        lastVersion = v;
        overflowSetting = ov;
        long xp = GardenMilestoneData.get().gardenXp;
        if (xp < 0) {
            line = "§aGarden Level ? §cOpen the desk!";
        } else {
            int level = levelOf(xp, ov);
            long have = xp - expForLevel(level);
            if (!ov && level >= MAX) {
                line = "§aGarden level " + level + " §7(§e" + GardenMilestoneData.sep(have) + "§7)";
            } else {
                long need = expForLevel(level + 1) - expForLevel(level);
                line = "§aGarden level " + level + " §7(§e" + GardenMilestoneData.sep(have) + "§7/§e" + GardenMilestoneData.sep(need) + "§7)";
            }
        }
        width = mc.font.width(line);
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        int state = cs.getMenu().getStateId();
        if (cs == scanned && state == scannedState) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        int index = title.equals("Desk") ? 4 : title.equals("SkyBlock Menu") ? 10 : -1;
        if (index < 0 || cs.getMenu().slots.size() <= index) return;
        ItemStack item = cs.getMenu().slots.get(index).getItem();
        if (item.isEmpty()) return;
        scanned = cs;
        scannedState = state;
        Matcher n = NAME.matcher(StringUtil.stripTrim(item.getHoverName().getString()));
        if (!n.matches()) return;
        if (n.group(1) != null) roman = !Character.isDigit(n.group(1).trim().charAt(0));
        int current = 0;
        long next = 0;
        for (String raw : ItemUtil.loreLines(item)) {
            String l = StringUtil.strip(raw);
            Matcher p = PROGRESS.matcher(l.trim());
            if (p.matches()) current = GardenMilestoneData.parseTier(p.group(1)) - 1;
            if (l.trim().equals("Max level reached!")) current = MAX;
            Matcher o = OVERFLOW_LINE.matcher(l);
            if (current >= MAX && o.matches()) {
                setXp(Long.parseLong(o.group(1).replace(",", "")));
                return;
            }
            Matcher x = NEXT_XP.matcher(l);
            if (l.startsWith(BAR_INDENT.substring(0, 4)) && x.matches()) next = parseShort(x.group(1));
        }
        if (current <= 0) return;
        setXp(expForLevel(current) + next);
    }

    private static void setXp(long xp) {
        var d = GardenMilestoneData.get();
        if (d.gardenXp == xp) return;
        d.gardenXp = xp;
        GardenMilestoneData.markDirty();
    }

    private static long parseShort(String s) {
        s = s.replace(",", "");
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'K') mult = 1_000;
        else if (last == 'm' || last == 'M') mult = 1_000_000;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try { return (long) (Double.parseDouble(s) * mult); } catch (NumberFormatException e) { return 0; }
    }

    private static void onChat(String raw) {
        if (!raw.contains("Garden Experience") || !SkyblockUtils.isInGarden()) return;
        Matcher m = REWARD.matcher(StringUtil.strip(raw));
        if (!m.matches()) return;
        long xp = GardenMilestoneData.get().gardenXp;
        if (xp < 0) return;
        int oldLevel = levelOf(xp, true);
        long now = xp + Long.parseLong(m.group(1).replace(",", ""));
        setXp(now);
        int newLevel = levelOf(now, true);
        if (!cfg().gardenLevelChat || !cfg().gardenLevelOverflow || newLevel != oldLevel + 1 || newLevel <= MAX) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(Component.literal(" "));
            mc.player.sendSystemMessage(Component.literal("§b§lGARDEN LEVEL UP §8" + oldLevel + " ➜ §b" + newLevel));
            mc.player.sendSystemMessage(Component.literal(" §8+§aOverflow level past " + MAX));
            mc.player.sendSystemMessage(Component.literal(" "));
        });
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        int index = title.equals("Desk") ? 4 : title.equals("SkyBlock Menu") ? 10 : -1;
        if (index < 0) return;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.getItem() != stack || cs.getMenu().slots.indexOf(hovered) != index) return;
        long xp = GardenMilestoneData.get().gardenXp;
        if (xp < 0) return;
        int level = levelOf(xp, true);
        if (level < MAX) return;
        long base = expForLevel(level);
        long overflow = xp - base;
        long overflowTotal = xp - expForLevel(MAX);
        long need = expForLevel(level + 1) - base;
        if (index == 4 && level > MAX) lines.set(0, Component.literal("§aGarden Level " + num(level)));
        boolean next = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = StringUtil.stripTrim(lines.get(i).getString());
            if (l.equals("Max level reached!")) {
                lines.set(i, Component.literal("§7Progress to Level " + num(level + 1)));
                next = true;
                continue;
            }
            if (next && lines.get(i).getString().contains(BAR_INDENT)) {
                lines.set(i, Component.literal(bar(overflow, need) + " §e" + GardenMilestoneData.sep(overflow)
                        + "§6/§e" + GardenMilestoneData.sep(need)));
                lines.add(i + 1, Component.empty());
                lines.add(i + 2, Component.literal("§b§lOVERFLOW XP:"));
                lines.add(i + 3, Component.literal("§7▸ " + GardenMilestoneData.sep(overflowTotal)));
                return;
            }
        }
    }

    private static String num(int level) {
        return roman ? GardenMilestoneData.toRoman(level) : String.valueOf(level);
    }

    static String bar(long current, long needed) {
        int filled = needed > 0 ? (int) Math.round(20.0 * current / needed) : 0;
        filled = Math.max(0, Math.min(20, filled));
        return "§2§l§m" + " ".repeat(filled) + "§f§l§m" + " ".repeat(20 - filled) + "§r";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || line.isEmpty() || !SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), line, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, 12);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
