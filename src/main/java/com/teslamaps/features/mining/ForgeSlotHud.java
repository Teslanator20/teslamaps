package com.teslamaps.features.mining;

// Tab widget read after Skyblocker's ForgeWidget, menu snapshot + countdown after NEU's MiningOverlay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Forge slots with item and time left, from the Forges tab widget or the last look into the Forge menu. */
public final class ForgeSlotHud {

    public static final int SLOTS = 7;
    private static final int LOCKED = 1, EMPTY = 2, ITEM = 3;

    private static final Pattern TAB = Pattern.compile("^([1-7])\\) (.+?)(?:: (.+))?$");
    private static final Pattern TIME = Pattern.compile("^(?:(\\d+)d)? ?(?:(\\d+)h)? ?(?:(\\d+)m)? ?(?:(\\d+)s)?$");
    private static final Pattern MENU_SLOT = Pattern.compile("^Slot #([1-7])$");
    private static final Pattern REMAINING = Pattern.compile("Time Remaining: (.+)");

    private static final int[] state = new int[SLOTS];
    private static final String[] name = new String[SLOTS];
    private static final long[] end = new long[SLOTS];
    private static final boolean[] alerted = new boolean[SLOTS];
    private static boolean loaded;

    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static AbstractContainerMenu scannedMenu;
    private static int scannedState = -1;
    private static HudEditElement hud;

    private static final List<String> SAMPLE = List.of("§3§lForges", "§e1) Diamonite§7: §aReady!",
            "§e2) Refined Mithril§7: §f1h 12m", "§73) Empty", "§c4) Locked §8(HotM 4)");

    private ForgeSlotHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("forgeSlotHud", "Forge Slots").section("Mining")
                .enabled(() -> cfg().forgeSlotHud)
                .size(() -> Math.max(80, lines.isEmpty() ? width(SAMPLE) : width),
                        () -> (lines.isEmpty() ? SAMPLE.size() : lines.size()) * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? width(SAMPLE) : width));
        Feature.of("ForgeSlotData").section("Mining").when(() -> cfg().forgeSlotHud || cfg().dwarvenOverlay || cfg().forgeReminder)
                .tick(ForgeSlotHud::tick);
        Feature.of("ForgeSlotHud").section("Mining").when(() -> cfg().forgeSlotHud)
                .hudHiddenInInventory("forgeslothud", ForgeSlotHud::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Forge");
                    b.toggle("Forge Slot HUD", () -> c.forgeSlotHud, v -> c.forgeSlotHud = v)
                            .desc("Every Forge slot with its item and time left. Read from the Forges tab widget, and remembered from the last time you opened the Forge, so it counts down anywhere. Move it in /tmap gui.")
                            .tags("forge", "timer", "slot", "dwarven", "refined");
                    b.toggle("  Only On Mining Islands", () -> c.forgeSlotHudMiningOnly, v -> c.forgeSlotHudMiningOnly = v);
                    b.toggle("  Show Locked Slots", () -> c.forgeSlotHudShowLocked, v -> c.forgeSlotHudShowLocked = v);
                    b.toggle("  Ready Alert", () -> c.forgeSlotReadyAlert, v -> c.forgeSlotReadyAlert = v)
                            .desc("Title and sound once a slot finishes");
                });
    }

    /** Finished items by name with their count, for the Forge Reminder. */
    public static java.util.Map<String, Integer> readyItems() {
        if (!loaded) load();
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < SLOTS; i++) if (state[i] == ITEM && end[i] <= now && name[i] != null) out.merge(name[i], 1, Integer::sum);
        return out;
    }

    /** Lines for the Dwarven Overlay, without the header. */
    public static List<String> slotLines() {
        return lines.size() > 1 ? lines.subList(1, lines.size()) : List.of();
    }

    private static void load() {
        loaded = true;
        for (String s : cfg().forgeSlotData) {
            String[] p = s.split("\\|", 4);
            if (p.length < 4) continue;
            try {
                int i = Integer.parseInt(p[0]);
                if (i < 0 || i >= SLOTS) continue;
                state[i] = Integer.parseInt(p[1]);
                end[i] = Long.parseLong(p[2]);
                name[i] = p[3];
                alerted[i] = end[i] <= System.currentTimeMillis();
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void save() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (state[i] != 0) out.add(i + "|" + state[i] + "|" + end[i] + "|" + (name[i] == null ? "" : name[i]));
        }
        if (out.equals(cfg().forgeSlotData)) return;
        cfg().forgeSlotData = out;
        TeslaMapsConfig.save();
    }

    private static void tick() {
        if (!loaded) load();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs && cs.getTitle().getString().startsWith("The Forge")) scanMenu(cs);
        if (++ticks % 20 != 0) return;
        if (Mining.onMiningIsland()) readTab();
        checkReady(mc);
        build(mc);
    }

    private static void readTab() {
        boolean in = false, changed = false;
        long now = System.currentTimeMillis();
        for (String raw : TabListUtils.getOrderedLines()) {
            String line = StringUtil.stripTrim(raw);
            if (!in) {
                if (line.equals("Forges:")) in = true;
                continue;
            }
            Matcher m = TAB.matcher(line);
            if (!m.matches()) break;
            int i = Integer.parseInt(m.group(1)) - 1;
            String item = m.group(2).trim();
            String time = m.group(3);
            if (time == null) {
                changed |= set(i, item.equalsIgnoreCase("LOCKED") ? LOCKED : EMPTY, "", 0);
            } else if (time.startsWith("Ready")) {
                changed |= set(i, ITEM, item, end[i] > 0 && end[i] <= now && name[i] != null && name[i].equals(item) ? end[i] : 0);
            } else {
                long ms = parse(time);
                if (ms < 0) continue;
                long e = now + ms;
                // tab only shows minutes, keep a menu-precise end time when it still fits
                if (state[i] == ITEM && item.equals(name[i]) && Math.abs(end[i] - e) < 60_000) continue;
                changed |= set(i, ITEM, item, e);
            }
        }
        if (changed) save();
    }

    private static void scanMenu(AbstractContainerScreen<?> cs) {
        AbstractContainerMenu menu = cs.getMenu();
        if (menu == scannedMenu && menu.getStateId() == scannedState) return;
        scannedMenu = menu;
        scannedState = menu.getStateId();
        if (menu.slots.size() < 17) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack st = menu.getSlot(10 + i).getItem();
            if (st.isEmpty()) continue;
            String n = StringUtil.stripTrim(st.getHoverName().getString());
            Matcher sm = MENU_SLOT.matcher(n);
            if (sm.matches()) {
                changed |= set(i, isRed(st.getHoverName()) ? LOCKED : EMPTY, "", 0);
                continue;
            }
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = REMAINING.matcher(l);
                if (!m.find()) continue;
                String t = m.group(1).trim();
                if (t.startsWith("Completed")) changed |= set(i, ITEM, n, 0);
                else {
                    long ms = parse(t);
                    if (ms >= 0) changed |= set(i, ITEM, n, now + ms);
                }
                break;
            }
        }
        if (changed) save();
    }

    private static boolean isRed(Component c) {
        boolean[] red = new boolean[1];
        c.visit((FormattedText.StyledContentConsumer<Object>) (style, text) -> {
            if (text.isBlank()) return Optional.empty();
            TextColor col = style.getColor();
            if (col != null && col.getValue() == 0xFF5555) red[0] = true;
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        return red[0] || c.getString().startsWith("§c");
    }

    private static boolean set(int i, int st, String n, long e) {
        if (state[i] == st && e == end[i] && (n == null ? "" : n).equals(name[i] == null ? "" : name[i])) return false;
        state[i] = st;
        name[i] = n;
        end[i] = e;
        alerted[i] = st != ITEM || e <= System.currentTimeMillis();
        return true;
    }

    private static long parse(String t) {
        Matcher m = TIME.matcher(t.trim());
        if (!m.matches() || t.isBlank()) return -1;
        long ms = 0;
        if (m.group(1) != null) ms += Long.parseLong(m.group(1)) * 86_400_000L;
        if (m.group(2) != null) ms += Long.parseLong(m.group(2)) * 3_600_000L;
        if (m.group(3) != null) ms += Long.parseLong(m.group(3)) * 60_000L;
        if (m.group(4) != null) ms += Long.parseLong(m.group(4)) * 1000L;
        return ms;
    }

    private static void checkReady(Minecraft mc) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < SLOTS; i++) {
            if (state[i] != ITEM || alerted[i] || end[i] > now) continue;
            alerted[i] = true;
            if (!cfg().forgeSlotHud || !cfg().forgeSlotReadyAlert || mc.player == null) continue;
            mc.gui.setTitle(Component.literal("§a§lForge Ready!"));
            mc.gui.setSubtitle(Component.literal("§e" + name[i]));
            mc.gui.setTimes(5, 40, 10);
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        }
    }

    private static void build(Minecraft mc) {
        List<String> out = new ArrayList<>();
        boolean any = false;
        long now = System.currentTimeMillis();
        out.add("§3§lForges");
        for (int i = 0; i < SLOTS; i++) {
            int n = i + 1;
            switch (state[i]) {
                case ITEM -> {
                    long left = end[i] - now;
                    out.add("§e" + n + ") " + name[i] + "§7: " + (left <= 0 ? "§aReady!" : "§f" + format(left)));
                    any = true;
                }
                case EMPTY -> { out.add("§7" + n + ") Empty"); any = true; }
                case LOCKED -> {
                    if (cfg().forgeSlotHudShowLocked && n >= 3) out.add("§c" + n + ") Locked §8(HotM " + n + ")");
                }
                default -> {}
            }
        }
        lines = any ? out : List.of();
        width = width(lines);
    }

    public static String format(long ms) {
        long s = ms / 1000;
        long d = s / 86400, h = s / 3600 % 24, m = s / 60 % 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static int width(List<String> l) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : l) w = Math.max(w, mc.font.width(s));
        return w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        if (cfg().forgeSlotHudMiningOnly && !Mining.onMiningIsland()) return;
        if (!SkyblockUtils.inSkyblockCached()) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), l, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        int pad = HudStyle.padding();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, w + pad * 2, l.size() * 10 - 1 + pad * 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
