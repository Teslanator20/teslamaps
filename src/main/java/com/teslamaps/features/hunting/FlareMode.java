package com.teslamaps.features.hunting;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SkillTracker;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkillXp;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Flare grinding: enough damage makes every kill count three times, and only then the
 * hunting xp is worth the trip. The Book of Stats counter on the weapon is the only live
 * signal for it - it climbs in threes while the 3x holds and in ones when it stops.
 * The panel carries Hunting and Combat at once plus the flare bestiary count, which the
 * tab list cannot give once the family is maxed (the Bestiary menu seeds it instead).
 */
public class FlareMode {

    private static final Pattern KILLS = Pattern.compile("Kills: ([\\d,]+)");
    // the one mob in the area that never counts triple - it announces itself on death
    private static final Pattern RARE_MOB_DROP = Pattern.compile("RARE DROP!.*Nether Star");
    // Hypixel counts the streak itself, it just only says so at its milestones
    private static final Pattern COMBO = Pattern.compile("^\\+([\\d,]+) Kill Combo");
    private static final Pattern COMBO_END =
            Pattern.compile("^Your Kill Combo has expired! You reached a ([\\d,]+) Kill Combo!");
    private static final Pattern MENU_TITLE = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Bestiary ➜ .+");
    private static final Pattern MENU_NAME = Pattern.compile("^(?<name>.+?)(?: (?:[IVXLC]+|\\d+))?$");

    private static final long IDLE_CAP_MS = 60_000;
    /** Nothing dies for this long and the 3x verdict is stale, not wrong. */
    private static final long STALE_MS = 20_000;
    /** The drop line and the counter step land in either order, so both sides get a window. */
    private static final long RARE_WINDOW_MS = 3_000;

    // the 3x verdict needs a few counter steps, a single one is noise
    private static final int WINDOW = 12;
    private static final int MIN_SAMPLES = 5;
    private static final double TRIPLE_RATIO = 0.75;

    private static long activeMs, lastActivity;
    private static double huntingXp, combatXp;
    private static double lastGain, lastCombatGain;
    private static long gains;

    private static long kills;            // counter steps, which is what the bestiary counts too
    private static long lastKillAt;
    private static long flareTotal = -1;  // absolute bestiary kills, read from the Bestiary menu
    private static long killsSinceSeed;

    /** 0 = a step that belongs to the rare mob and says nothing about the 3x. */
    private static final long[] deltas = new long[WINDOW];
    private static int deltaCount, deltaAt;
    private static long lastRecordAt;
    private static boolean tripled = true;
    private static long lastWarn;
    private static long rareDropAt, lastRareLine;
    private static long rareKills;
    private static long streak, bestStreak;
    private static boolean paused = false;

    private static String lastItemId = "";
    private static long lastCounter = -1;
    private static ItemLore lastLore = null;

    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.flareMode && c.section("Skills");
    }

    /** Pause keeps every number, it only stops the clock and the counters. */
    public static void togglePause() {
        paused = !paused;
        if (paused) {
            activeMs = liveActiveMs();
            lastActivity = 0;
        }
        cachedLines = null;
    }

    public static boolean paused() {
        return paused;
    }

    public static void reset() {
        cachedLines = null;
        paused = false;
        streak = bestStreak = 0;
        activeMs = 0;
        lastActivity = 0;
        huntingXp = combatXp = 0;
        lastGain = lastCombatGain = 0;
        gains = 0;
        kills = 0;
        lastKillAt = 0;
        killsSinceSeed = 0;
        deltaCount = deltaAt = 0;
        lastRecordAt = 0;
        tripled = true;
        lastWarn = 0;
        rareDropAt = lastRareLine = 0;
        rareKills = 0;
        lastCounter = -1;
        lastLore = null;
    }

    private static void touch() {
        long now = System.currentTimeMillis();
        if (lastActivity > 0) activeMs += Math.min(now - lastActivity, IDLE_CAP_MS);
        lastActivity = now;
    }

    private static long liveActiveMs() {
        if (lastActivity == 0) return activeMs;
        return activeMs + Math.min(System.currentTimeMillis() - lastActivity, IDLE_CAP_MS);
    }

    private static double hours() {
        return liveActiveMs() / 3_600_000.0;
    }

    /** Fed by SkillTracker, which already turns the action bar into a real per-gain delta. */
    public static void onSkillXp(String skill, double delta) {
        if (!enabled() || paused) return;
        if (skill.equals("Hunting")) {
            huntingXp += delta;
            lastGain = delta;
            gains++;
            touch();
        } else if (skill.equals("Combat")) {
            combatXp += delta;
            lastCombatGain = delta;
            touch();
        }
    }

    private static int tickCounter = 0;
    private static boolean onSkyblock = false;

    public static void onChatMessage(String raw) {
        if (!enabled()) return;
        String text = StringUtil.strip(raw).trim();

        Matcher combo = COMBO.matcher(text);
        if (combo.find()) {
            setStreak(parseNum(combo.group(1)));
            return;
        }
        Matcher end = COMBO_END.matcher(text);
        if (end.find()) {
            setStreak(parseNum(end.group(1)));
            streak = 0;
            cachedLines = null;
            return;
        }

        if (!RARE_MOB_DROP.matcher(text).find()) return;
        long now = System.currentTimeMillis();
        // one kill can drop twice, and two lines in the same moment are still one mob
        if (now - lastRareLine < 400) return;
        lastRareLine = now;
        rareDropAt = now;
        undoLastSingle();
    }

    /** The step usually arrives first, so the already recorded one is corrected in place. */
    private static void undoLastSingle() {
        if (deltaCount == 0 || System.currentTimeMillis() - lastRecordAt > RARE_WINDOW_MS) return;
        int last = (deltaAt - 1 + WINDOW) % WINDOW;
        if (deltas[last] == 0 || deltas[last] % 3 == 0) return;
        deltas[last] = dropRareKill(deltas[last]);
        rareDropAt = 0; // spent, the next single step is a real one again
        cachedLines = null;
        evaluate();
    }

    public static void tick() {
        if (!enabled()) return;
        readCounter();
        // isInSkyblock scans the scoreboard, the menu walks every slot - neither belongs on a frame
        if (++tickCounter % 10 == 0) {
            onSkyblock = SkyblockUtils.isInSkyblock();
            readMenu();
        }
        warnIfSingle();
    }

    // The weapon lore only changes when the server pushes a new one, so a fresh ItemLore
    // object is the whole check on every other tick.
    private static void readCounter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        ItemLore lore = held.isEmpty() ? null : held.get(DataComponents.LORE);
        if (lore == null) return;
        if (lore == lastLore) return;
        lastLore = lore;

        long counter = -1;
        for (Component line : lore.lines()) {
            Matcher m = KILLS.matcher(StringUtil.strip(line.getString()));
            if (m.find()) {
                counter = parseNum(m.group(1));
                break;
            }
        }
        if (counter < 0) return;

        String id = ItemUtil.skyblockId(held);
        if (!id.equals(lastItemId) || lastCounter < 0 || counter < lastCounter) {
            lastItemId = id;
            lastCounter = counter;
            return;
        }
        long delta = counter - lastCounter;
        lastCounter = counter;
        if (delta <= 0 || paused) return;

        kills += delta;
        killsSinceSeed += delta;
        lastKillAt = System.currentTimeMillis();
        touch();
        record(delta);
    }

    private static void record(long delta) {
        // the rare mob is worth a single kill even while the 3x holds, so its step is
        // dropped instead of counted as proof that the damage stopped being enough
        if (delta % 3 != 0 && rareDropAt > 0 && System.currentTimeMillis() - rareDropAt <= RARE_WINDOW_MS) {
            delta = dropRareKill(delta);
            rareDropAt = 0;
        }
        deltas[deltaAt] = delta;
        deltaAt = (deltaAt + 1) % WINDOW;
        if (deltaCount < WINDOW) deltaCount++;
        lastRecordAt = System.currentTimeMillis();
        cachedLines = null;
        evaluate();
    }

    private static void setStreak(long value) {
        if (value < 0 || paused) return;
        streak = value;
        if (value > bestStreak) bestStreak = value;
        cachedLines = null;
    }

    private static void evaluate() {
        int mult = 0, total = 0;
        for (int i = 0; i < deltaCount; i++) {
            if (deltas[i] == 0) continue;
            total++;
            if (deltas[i] % 3 == 0) mult++;
        }
        if (total >= MIN_SAMPLES) tripled = (double) mult / total >= TRIPLE_RATIO;
    }

    /** Takes the rare mob's single kill out of the step, whatever else was batched with it. */
    private static long dropRareKill(long delta) {
        kills--;
        killsSinceSeed--;
        rareKills++;
        return delta - 1;
    }

    /** True while the counter has been climbing in threes, i.e. the damage is enough. */
    public static boolean tripled() {
        return tripled;
    }

    private static boolean verdictStale() {
        return deltaCount < MIN_SAMPLES
                || lastKillAt == 0
                || System.currentTimeMillis() - lastKillAt > STALE_MS;
    }

    private static void warnIfSingle() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.flareWarnChat || paused || tripled || verdictStale()) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < Math.max(5, c.flareWarnSeconds) * 1000L) return;
        lastWarn = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] §c§lFlare 1x! §fKills count single - not enough damage"));
        if (c.flareWarnSound) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        }
    }

    // The tab list drops the numbers once the family is maxed, so the total only ever
    // arrives from the Bestiary menu - open it once and the panel has an absolute count.
    private static void readMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return;
        if (!MENU_TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return;

        String want = mob();
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher n = MENU_NAME.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!n.matches() || !n.group("name").trim().equalsIgnoreCase(want)) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            for (Component line : lore.lines()) {
                Matcher m = KILLS.matcher(StringUtil.strip(line.getString()));
                if (!m.find()) continue;
                long total = parseNum(m.group(1));
                if (total <= 0) return;
                flareTotal = total;
                killsSinceSeed = 0;
                cachedLines = null;
                return;
            }
        }
    }

    private static String mob() {
        String m = TeslaMapsConfig.get().flareMob;
        return m == null || m.isBlank() ? "Flare" : m.trim();
    }

    public static boolean shouldRender() {
        return enabled() && onSkyblock;
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        draw(ctx, Minecraft.getInstance(), c.flareModeX, c.flareModeY, c.flareModeScale, lines());
    }

    /** The reset button, only reachable from the inventory. */
    public static void onClick(double mouseX, double mouseY, int button) {
        if (!shouldRender() || !inInventory()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String[] ls = lines();
        double localX = (mouseX - c.flareModeX) / c.flareModeScale;
        double localY = (mouseY - c.flareModeY) / c.flareModeScale;
        if ((int) Math.floor(localY / 10.0) != ls.length - 1) return;
        String last = ls[ls.length - 1];
        if (com.teslamaps.utils.PanelButtons.hit(last, paused ? RESUME : PAUSE, localX)) {
            togglePause();
            return;
        }
        if (com.teslamaps.utils.PanelButtons.hit(last, RESET, localX)) reset();
    }

    private static final String RESET = "§c[Reset]";
    private static final String PAUSE = "§e[Pause]";
    private static final String RESUME = "§a[Resume]";

    private static String[] cachedLines = null;
    private static long cachedAt = 0;
    private static boolean cachedInv = false;

    private static String[] lines() {
        long now = System.currentTimeMillis();
        boolean inv = inInventory();
        if (cachedLines != null && inv == cachedInv && now - cachedAt < 250) return cachedLines;
        cachedAt = now;
        cachedInv = inv;
        return cachedLines = buildLines();
    }

    private static String[] buildLines() {
        double h = hours();
        boolean warm = liveActiveMs() >= 30_000 && h > 0;
        List<String> out = new ArrayList<>();

        String status = verdictStale() ? "§7…" : (tripled ? "§a§l3x" : "§c§l1x");
        out.add("§6§lFlare Mode §r" + status);

        out.add(skillHeader("Hunting", "§6"));
        out.add(metric("XP", "§b", huntingXp, h, warm));
        if (gains > 0) {
            out.add("§7Last: §b+" + fmtExact(lastGain) + " §8(ø " + fmtExact(huntingXp / gains) + ")");
        }

        out.add(skillHeader("Combat", "§c"));
        out.add(metric("XP", "§b", combatXp, h, warm));
        if (lastCombatGain > 0) out.add("§7Last: §b+" + fmtExact(lastCombatGain));

        out.add("§e" + mob() + " §8(bestiary)");
        out.add(metric("Kills", "§e", kills, h, warm));
        out.add("§7Streak: §a" + streak + " §8(best " + bestStreak + ")");
        if (flareTotal >= 0) out.add("§7Total: §e" + fmt(flareTotal + killsSinceSeed));
        else out.add("§8open the Bestiary menu for the total");

        String uptime = "§7Session: §f" + fmtDuration(liveActiveMs()) + (paused ? " §6❚❚" : "");
        if (inInventory()) uptime += " " + (paused ? RESUME : PAUSE) + " " + RESET;
        out.add(uptime);
        return out.toArray(new String[0]);
    }

    private static String skillHeader(String skill, String color) {
        long total = SkillTracker.totalXp(skill);
        if (total <= 0) {
            Integer lvl = SkillTracker.tabLevel(skill);
            return color + "§l" + skill + (lvl != null ? " §r§e" + lvl + " §8(open /skills)" : "");
        }
        SkillXp.Level lvl = SkillXp.calculate(total, SkillTracker.cap(skill));
        double pct = lvl.needed() > 0 ? 100.0 * lvl.current() / lvl.needed() : 0;
        String over = lvl.overflowTotal() > 0 ? " §6✦" : "";
        return color + "§l" + skill + " §r§e" + lvl.level() + over
                + " §8(" + String.format("%.1f", pct) + "%)";
    }

    private static String metric(String label, String color, double total, double hours, boolean warm) {
        String rate = warm ? fmt(total / hours) : "…";
        return "§7" + label + "/h: " + color + rate + " §8(" + fmt(total) + ")";
    }

    private static boolean inInventory() {
        return Minecraft.getInstance().gui.screen()
                instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        draw(ctx, mc, x, y, scale, sampleLines());
    }

    private static String[] sampleLines() {
        return new String[]{
            "§6§lFlare Mode §r§a§l3x",
            "§6§lHunting §r§e41 §8(62.3%)",
            "§7XP/h: §b14.2M §8(4.13M)",
            "§7Last: §b+7,425 §8(ø 7,431)",
            "§c§lCombat §r§e52 §8(18.1%)",
            "§7XP/h: §b812.0k §8(240.1k)",
            "§eFlare §8(bestiary)",
            "§7Kills/h: §e1.9k §8(563)",
            "§7Streak: §a34 §8(best 211)",
            "§7Total: §e1.24M",
            "§7Session: §f1h 23m"
        };
    }

    public static int width(float scale) {
        return (int) (boxWidth(Minecraft.getInstance().font, shouldRender() ? lines() : sampleLines()) * scale);
    }

    public static int height(float scale) {
        String[] ls = shouldRender() ? lines() : sampleLines();
        return (int) ((ls.length * 10 + 4) * scale);
    }

    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        String[] ls = sampleLines();
        return new int[]{boxWidth(font, ls), ls.length * 10 + 4};
    }

    private static String[] widthCacheFor = null;
    private static int widthCache = 0;

    private static int boxWidth(net.minecraft.client.gui.Font font, String[] ls) {
        if (ls == widthCacheFor) return widthCache;
        int w = 0;
        for (String l : ls) w = Math.max(w, font.width(l));
        widthCacheFor = ls;
        return widthCache = w + 8;
    }

    private static String[] seqCacheFor = null;
    private static FormattedCharSequence[] seqCache = null;

    private static FormattedCharSequence[] sequences(String[] ls) {
        if (ls == seqCacheFor) return seqCache;
        FormattedCharSequence[] out = new FormattedCharSequence[ls.length];
        for (int i = 0; i < ls.length; i++) out[i] = Component.literal(ls[i]).getVisualOrderText();
        seqCacheFor = ls;
        return seqCache = out;
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String[] ls) {
        int w = boxWidth(mc.font, ls) - 8;
        FormattedCharSequence[] seq = sequences(ls);
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        com.teslamaps.render.gui.TeslaGui.backdropRect(ctx, -4, -4, w + 8, ls.length * 10 + 4);
        for (int i = 0; i < seq.length; i++) ctx.text(mc.font, seq[i], 0, i * 10, 0xFFFFFFFF);
        pose.popMatrix();
    }

    /** Chat summary behind /tmap flare. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        out.add("§b[TeslaMaps] §fFlare Mode: " + (c.flareMode ? "§aon" : "§coff")
                + (paused ? " §6(paused)" : "") + " §8· mob §e" + mob());
        out.add("§7Steps: " + (verdictStale() ? "§7unknown (no recent kills)"
                : (tripled ? "§a3x" : "§c1x")) + " §8(" + deltaCount + " samples)");
        out.add("§7Kills: §e" + fmt(kills) + " §8· total "
                + (flareTotal >= 0 ? fmt(flareTotal + killsSinceSeed) : "? (open the Bestiary menu)"));
        out.add("§7Hunting: §b" + fmt(huntingXp) + " §8· Combat §b" + fmt(combatXp));
        out.add("§7Streak: §a" + streak + " §8· best §a" + bestStreak);
        out.add("§7Rare mob (Nether Star): §e" + rareKills + " §8ignored for the 3x check");
        out.add("§7Session: §f" + fmtDuration(liveActiveMs()));
        out.add("§7/tmap flare on|off · pause · reset · mob <name>");
        return out;
    }

    private static String fmtDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return h + "h " + String.format("%02dm", m);
        if (m > 0) return m + "m " + String.format("%02ds", s);
        return s + "s";
    }

    /** The per-gain xp is the whole point, so it stays exact instead of rounding to 7.4k. */
    private static String fmtExact(double v) {
        return String.format("%,d", Math.round(v));
    }

    private static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    private static long parseNum(String s) {
        try {
            return Long.parseLong(s.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
