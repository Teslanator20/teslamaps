package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkillXp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// One tracker for whatever is being grinded. The action bar drives the xp
// ("+5.2 Foraging (12,345/50,000)"), the tab list gives the real level and cap,
// and each skill adds the numbers that matter for it: kills, bosses and cocoons
// on Combat, double hooks on Fishing, tree gifts and whispers on Foraging.
public class SkillTracker {

    private static final Pattern GAIN = Pattern.compile("\\+([\\d,.]+)\\s+([A-Za-z]+)\\s+\\(([^)]+)\\)");
    private static final Pattern TAB_PERCENT = Pattern.compile("^\\s*([A-Za-z]+)\\s+(\\d+):\\s*([\\d.]+)%");
    private static final Pattern TAB_MAX = Pattern.compile("^\\s*([A-Za-z]+)\\s+(\\d+):\\s*MAX");
    private static final Pattern KILLS = Pattern.compile("Kills: ([\\d,]+)");
    private static final Pattern GIFT_COMPACT = Pattern.compile("Tree Gift\\. You helped cut [\\d.]+% and gained \\d+ rewards");
    private static final Pattern WHISPERS_GROUND = Pattern.compile("You found ([\\d,]+) Desert Whispers");
    private static final Pattern WHISPERS_REWARD = Pattern.compile("\\+([\\d,]+) Desert Whispers");
    private static final Pattern WHISPERS_TAB = Pattern.compile("Desert Whispers:\\s*([\\d,.]+[kKmMbB]?)");

    private static final String[] SKILLS = {
        "Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting",
        "Alchemy", "Taming", "Carpentry", "Runecrafting", "Social", "Hunting"
    };

    private static final long IDLE_CAP_MS = 30_000;

    private static final class Session {
        double xpGained;
        long activeMs, lastActivity;
        double lastAbs = -1;
        String lastSegment = "";
        long kills, bossKills, cocoons;      // combat
        long catches, doubleHooks;           // fishing
        long gifts, whispers;                // foraging
        long lastGiftMs;
        void touch() {
            long now = System.currentTimeMillis();
            if (lastActivity > 0) activeMs += Math.min(now - lastActivity, IDLE_CAP_MS);
            lastActivity = now;
        }

        long liveActiveMs() {
            if (lastActivity == 0) return 0;
            return activeMs + Math.min(System.currentTimeMillis() - lastActivity, IDLE_CAP_MS);
        }

        double hours() { return liveActiveMs() / 3_600_000.0; }
    }

    private static final Map<String, Session> sessions = new HashMap<>();
    private static final Map<String, Integer> levels = new HashMap<>();
    private static final Map<String, Integer> caps = new HashMap<>();
    private static String activeSkill = null;
    private static long lastGain = 0;
    private static int tickCounter = 0;
    private static String lastItemId = "";
    private static long lastKills = -1;
    private static long lastWhisperTotal = -1;
    private static double whispersFromChat = 0;

    public static void reset() {
        cachedLines = null;
        sessions.clear();
        activeSkill = null;
        lastGain = 0;
        lastItemId = "";
        lastKills = -1;
        lastWhisperTotal = -1;
        whispersFromChat = 0;
    }

    private static Session session(String skill) {
        return sessions.computeIfAbsent(skill, k -> new Session());
    }

    /** "Farming 60" / "Farming" -> "Farming", null when it is not a skill item. */
    public static String skillOf(String hoverName) {
        if (hoverName == null || hoverName.isEmpty()) return null;
        String first = hoverName.split(" ")[0];
        return isSkill(first) ? first : null;
    }

    /** The skills Hypixel averages for "Skill Avg." — everything but Runecrafting and Social. */
    public static final String[] NON_COSMETIC = {
        "Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting",
        "Alchemy", "Taming", "Carpentry", "Hunting"
    };

    private static boolean isSkill(String name) {
        for (String s : SKILLS) if (s.equals(name)) return true;
        return false;
    }

    /** Real cap the server reports in tab ("Foraging 57: MAX"), 60 as fallback. */
    public static int cap(String skill) {
        return caps.getOrDefault(skill, 60);
    }

    public static long totalXp(String skill) {
        Long v = TeslaMapsConfig.get().skillTotalXp.get(skill);
        return v == null ? 0 : v;
    }

    public static void setTotalXp(String skill, long xp) {
        TeslaMapsConfig.get().skillTotalXp.put(skill, xp);
    }

    public static void onBossKill() {
        Session s = session("Combat");
        s.bossKills++;
        s.touch();
        show("Combat");
    }

    public static void onCocoon() {
        Session s = session("Combat");
        s.cocoons++;
        s.touch();
        show("Combat");
    }

    public static double bossesPerHour() {
        Session s = session("Combat");
        double h = s.hours();
        return h > 0 ? s.bossKills / h : 0;
    }

    public static void tick() {
        if (++tickCounter % 20 != 0) return;
        if (tickCounter % 40 == 0) readTabList();
        readKillCounter();
    }

    // weapons carry their own kill counter in the lore
    private static void readKillCounter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        long kills = readKills(held);
        if (kills < 0) return;

        String id = ItemUtil.skyblockId(held);
        if (id.equals(lastItemId) && lastKills >= 0 && kills > lastKills) {
            Session s = session("Combat");
            s.kills += kills - lastKills;
            s.touch();
        }
        lastItemId = id;
        lastKills = kills;
    }

    private static long readKills(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            Matcher m = KILLS.matcher(strip(line.getString()));
            if (m.find()) return (long) parseNum(m.group(1));
        }
        return -1;
    }

    // tab list carries the authoritative level and tells us where the cap sits
    private static void readTabList() {
        for (String raw : com.teslamaps.utils.TabListUtils.getTabListLines()) {
            String line = strip(raw);
            Matcher max = TAB_MAX.matcher(line);
            if (max.find() && isSkill(max.group(1))) {
                String skill = max.group(1);
                int lvl = Integer.parseInt(max.group(2));
                levels.put(skill, lvl);
                caps.put(skill, lvl);
                continue;
            }
            Matcher pct = TAB_PERCENT.matcher(line);
            if (pct.find() && isSkill(pct.group(1))) {
                levels.put(pct.group(1), Integer.parseInt(pct.group(2)));
                continue;
            }
            Matcher wt = WHISPERS_TAB.matcher(line);
            if (wt.find()) readWhisperTotal(parseAmount(wt.group(1)));
        }
    }

    // The tab total catches everything, chat only what Hypixel announces (and what
    // other mods leave alone), so chat counts right away and the total fills the gap.
    private static void readWhisperTotal(long total) {
        if (total <= 0) return;
        if (lastWhisperTotal < 0 || total < lastWhisperTotal) {
            lastWhisperTotal = total;
            whispersFromChat = 0;
            return;
        }
        double delta = total - lastWhisperTotal;
        lastWhisperTotal = total;
        if (delta > whispersFromChat) {
            addWhispers(delta - whispersFromChat);
            whispersFromChat = 0;
        } else {
            whispersFromChat -= delta;
        }
    }

    private static void show(String skill) {
        activeSkill = skill;
        lastGain = System.currentTimeMillis();
    }

    public static void onActionBar(String raw) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.skillTracker && !cfg.skillOverflow) return;
        Matcher m = GAIN.matcher(strip(raw));
        if (!m.find()) return;
        String skill = m.group(2);
        if (!isSkill(skill)) return;

        double gain = parseNum(m.group(1));
        String paren = m.group(3);
        String segment = m.group();
        Session s = session(skill);
        double delta = 0;

        if (paren.contains("/")) {
            double abs = parseNum(paren.substring(0, paren.indexOf('/')));
            if (s.lastAbs >= 0) {
                double d = abs - s.lastAbs;
                delta = d > 0 ? d : (d < 0 ? gain : 0); // negative = level rollover
            } else {
                delta = gain;
            }
            s.lastAbs = abs;
        } else if (!segment.equals(s.lastSegment)) {
            delta = gain;
        }
        s.lastSegment = segment;

        if (delta > 0) {
            s.xpGained += delta;
            s.touch();
            show(skill);
            // xp comes in for every catch, including the loot that never reaches chat
            if (skill.equals("Fishing") && cfg.skillTracker && countEverything()) countCatch("xp");
            long before = totalXp(skill);
            if (before > 0) {
                setTotalXp(skill, before + (long) delta);
                SkillOverflow.checkLevelUp(skill, before, before + (long) delta);
            }
        }
    }

    // "TROPHY FISH! You caught a Lavahorse GOLD! (2x)" and the frog variant "TROPHY FROG! ..."
    private static final Pattern TROPHY_FISH = Pattern.compile("TROPHY \\w+! You caught an? .+");
    // plain loot, the line every pull gets: "GOOD CATCH! You caught Raw Cod x22!"
    private static final Pattern PLAIN_CATCH = Pattern.compile("[A-Z]+ CATCH! You(?:'ve)? (?:caught|found) .+");

    private static long lastCatchPull = -1;
    private static long lastCatchAt = 0;
    private static long lastCreatureAt = 0;

    /**
     * Sea creatures only show up when they are what is being fished for, trophy fishing never
     * brings them in, so a recent creature means creatures are the thing worth counting.
     */
    private static final long CREATURE_MEMORY_MS = 10 * 60_000;

    private static boolean countEverything() {
        return switch (TeslaMapsConfig.get().fishingCatchSource) {
            case 1 -> false;
            case 2 -> true;
            default -> System.currentTimeMillis() - lastCreatureAt >= CREATURE_MEMORY_MS;
        };
    }

    /** Panel label, so it is clear which of the two the number is. */
    public static String catchLabel() {
        return countEverything() ? "Catches" : "Creatures";
    }

    /**
     * A catch reports itself twice, once as Fishing xp and once as a chat line, and plenty of
     * loot has no chat line at all. Both are counted, and the pull they belong to keeps the
     * pair from counting twice. Without a bobber to id the pull (Fishing section off) a five
     * second window does the same job.
     */
    private static void countCatch(String source) {
        long pull = FishingBobberTimer.pullId();
        long now = System.currentTimeMillis();
        FishingBobberTimer.onCatch();
        if (pull == lastCatchPull && now - lastCatchAt <= 5000) return;
        lastCatchPull = pull;
        lastCatchAt = now;

        Session f = session("Fishing");
        f.catches++;
        f.touch();
        if (TeslaMapsConfig.get().debugMode) {
            com.teslamaps.TeslaMaps.debug("[FishDbg] catch #{} via {} (pull {})", f.catches, source, pull);
        }
    }

    public static void onChatMessage(String raw) {
        if (!TeslaMapsConfig.get().skillTracker) return;
        String text = strip(raw);

        if (text.contains("Double Hook")) {
            Session f = session("Fishing");
            f.doubleHooks++;
            f.touch();
            return;
        }
        String catchLine = text.trim();
        boolean trophy = TROPHY_FISH.matcher(catchLine).matches();
        // the creature list holds the exact sentences, so it decides before the generic shape
        boolean creature = !trophy && SeaCreatures.isCatch(catchLine);
        if (creature) lastCreatureAt = System.currentTimeMillis();
        boolean plain = !trophy && !creature && PLAIN_CATCH.matcher(catchLine).matches();

        if (TeslaMapsConfig.get().debugMode && catchLine.contains("caught") && !trophy && !plain && !creature) {
            com.teslamaps.TeslaMaps.debug("[FishDbg] unmatched: '{}'", catchLine);
        }
        if (trophy || creature || (plain && countEverything())) {
            countCatch("chat");
            show("Fishing");
            return;
        }
        if (plain) return;

        String trimmed = text.trim();
        if (trimmed.equals("TREE GIFT") || GIFT_COMPACT.matcher(text).find()) {
            countGift();
            return;
        }
        Matcher wg = WHISPERS_GROUND.matcher(text);
        if (wg.find()) {
            double amount = parseNum(wg.group(1));
            whispersFromChat += amount;
            addWhispers(amount);
            return;
        }
        Matcher wr = WHISPERS_REWARD.matcher(text);
        while (wr.find()) {
            double amount = parseNum(wr.group(1));
            whispersFromChat += amount;
            addWhispers(amount);
        }
    }

    private static void addWhispers(double amount) {
        Session f = session("Foraging");
        f.whispers += (long) amount;
        f.touch();
    }

    // SkyHanni compacts the gift block into one line, so both forms can show up
    private static void countGift() {
        Session f = session("Foraging");
        long now = System.currentTimeMillis();
        if (now - f.lastGiftMs > 1000) {
            f.gifts++;
            f.lastGiftMs = now;
        }
        f.touch();
    }

    public static boolean shouldRender() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return cfg.skillTracker && activeSkill != null
                && !com.teslamaps.dungeon.DungeonManager.isInDungeon()
                && System.currentTimeMillis() - lastGain <= 10 * 60_000;
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (cfg.skillTrackerHidden && !inInventory()) return;
        Minecraft mc = Minecraft.getInstance();
        draw(ctx, mc, cfg.skillTrackerX, cfg.skillTrackerY, cfg.skillTrackerScale, lines(activeSkill));
    }

    /** The reset and hide/show buttons, only reachable from the inventory. */
    public static void onClick(double mouseX, double mouseY, int button) {
        if (!shouldRender() || !inInventory()) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        String[] ls = lines(activeSkill);
        float scale = cfg.skillTrackerScale;
        double localX = (mouseX - cfg.skillTrackerX) / scale;
        double localY = (mouseY - cfg.skillTrackerY) / scale;

        if ((int) Math.floor(localY / 10.0) != ls.length - 1) return;
        String last = ls[ls.length - 1];
        var font = Minecraft.getInstance().font;

        String toggle = cfg.skillTrackerHidden ? SHOW : HIDE;
        int tCut = last.indexOf(toggle);
        if (tCut > 0 && localX >= font.width(last.substring(0, tCut)) - 2) {
            cfg.skillTrackerHidden = !cfg.skillTrackerHidden;
            cachedLines = null;
            TeslaMapsConfig.save();
            return;
        }
        int cut = last.indexOf(RESET);
        if (cut > 0 && localX >= font.width(last.substring(0, cut)) - 2) resetSession();
    }

    /** Clears the counters but keeps the known total xp, so levels stay right. */
    public static void resetSession() {
        cachedLines = null;
        sessions.clear();
        lastKills = -1;
    }

    private static final String RESET = "§c[Reset]";
    private static final String HIDE = "§7[Hide]";
    private static final String SHOW = "§a[Show]";

    /** How long the session has been running, idle time already trimmed by touch(). */
    private static String fmtDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return h + "h " + String.format("%02dm", m);
        if (m > 0) return m + "m " + String.format("%02ds", s);
        return s + "s";
    }

    // lines() walks the whole 30 minute window per metric, so the render path
    // rebuilt thousands of entries every frame. Rates don't move that fast.
    private static String[] cachedLines = null;
    private static String cachedFor = null;
    private static long cachedAt = 0;
    private static boolean cachedInv = false;

    private static String[] lines(String skill) {
        long now = System.currentTimeMillis();
        boolean inv = inInventory();
        if (cachedLines != null && skill.equals(cachedFor)
                && inv == cachedInv && now - cachedAt < 250) {
            return cachedLines;
        }
        String[] built = buildLines(skill);
        cachedLines = built;
        cachedFor = skill;
        cachedAt = now;
        cachedInv = inv;
        return built;
    }

    private static String[] buildLines(String skill) {
        Session s = session(skill);
        double h = s.hours();
        boolean warm = s.liveActiveMs() >= 30_000 && h > 0;

        List<String> out = new ArrayList<>();
        out.add(color(skill) + "§l" + skill);
        out.add(metric("XP", "§b", s.xpGained, h, warm));

        long total = totalXp(skill);
        if (total > 0) {
            SkillXp.Level lvl = SkillXp.calculate(total, cap(skill));
            double pct = lvl.needed() > 0 ? 100.0 * lvl.current() / lvl.needed() : 0;
            String over = lvl.overflowTotal() > 0 ? " §6✦" : "";
            out.add("§7Level: §e" + lvl.level() + over + " §8(" + String.format("%.1f", pct) + "%)");
            double xph = warm ? s.xpGained / h : 0;
            if (xph > 0 && lvl.needed() > lvl.current()) {
                out.add("§7Next: §a" + fmtEta((lvl.needed() - lvl.current()) / xph));
            }
        } else if (levels.containsKey(skill)) {
            out.add("§7Level: §e" + levels.get(skill) + " §8(open /skills)");
        }

        switch (skill) {
            case "Combat" -> {
                if (s.kills > 0) out.add(metric("Kills", "§e", s.kills, h, warm));
                if (s.bossKills > 1) out.add(metric("Bosses", "§e", s.bossKills, h, warm));
                if (s.cocoons > 1) {
                    out.add("§7Cocoon: §a" + s.cocoons + "§8/§e" + s.bossKills
                            + " §8(" + Math.round(100.0 * s.cocoons / Math.max(1, s.bossKills)) + "%)");
                }
            }
            case "Fishing" -> {
                out.add(metric(catchLabel(), "§e", s.catches, h, warm));
                String chance = s.catches > 0 ? String.format("%.1f%%", 100.0 * s.doubleHooks / s.catches) : "…";
                out.add("§7Double Hook: §e" + chance + " §8(" + s.doubleHooks + ")");
            }
            case "Foraging" -> {
                out.add(metric("Gifts", "§e", s.gifts, h, warm));
                out.add(metric("Whispers", "§e", s.whispers, h, warm));
            }
            default -> { }
        }

        // uptime always, the controls only where they can be clicked
        String uptime = "§7Session: §f" + fmtDuration(s.liveActiveMs());
        if (inInventory()) {
            uptime += " " + RESET + " " + (TeslaMapsConfig.get().skillTrackerHidden ? SHOW : HIDE);
        }
        out.add(uptime);
        return out.toArray(new String[0]);
    }

    private static String metric(String label, String color, double total, double hours, boolean warm) {
        String rate = warm ? fmt(total / hours) : "…";
        return "§7" + label + "/h: " + color + rate + " §8(" + fmt(total) + ")";
    }

    private static boolean inInventory() {
        return Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
    }

    private static String color(String skill) {
        return switch (skill) {
            case "Foraging" -> "§2";
            case "Fishing" -> "§b";
            case "Combat" -> "§c";
            case "Farming" -> "§a";
            case "Mining" -> "§9";
            case "Alchemy", "Enchanting" -> "§d";
            default -> "§6";
        };
    }

    public static void drawPreview(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        draw(ctx, mc, x, y, scale, sampleLines());
    }

    private static String[] sampleLines() {
        return new String[]{
            "§2§lForaging",
            "§7XP/h: §b12.4M §8(4.13M)",
            "§7Level: §e57 §6✦ §8(63.4%)",
            "§7Next: §a1h 23m",
            "§7Gifts/h: §e42 §8(14)",
            "§7Whispers/h: §e250k §8(84.1k)",
            "§7Session: §f1h 23m"
        };
    }

    public static int width(float scale) {
        String[] ls = shouldRender() ? lines(activeSkill) : sampleLines();
        return (int) (boxWidth(Minecraft.getInstance().font, ls) * scale);
    }

    public static int height(float scale) {
        String[] ls = shouldRender() ? lines(activeSkill) : sampleLines();
        return (int) ((ls.length * 10 + 4) * scale);
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

    // Drawing a String re-parses its legacy colour codes; measured at roughly 10us
    // for the first use of a given string in a frame against under 1us for a
    // prebuilt sequence. The lines only change every 250ms, so build them once.
    private static String[] seqCacheFor = null;
    private static net.minecraft.util.FormattedCharSequence[] seqCache = null;

    private static net.minecraft.util.FormattedCharSequence[] sequences(String[] ls) {
        if (ls == seqCacheFor) return seqCache;
        net.minecraft.util.FormattedCharSequence[] out = new net.minecraft.util.FormattedCharSequence[ls.length];
        for (int i = 0; i < ls.length; i++) {
            out[i] = Component.literal(ls[i]).getVisualOrderText();
        }
        seqCacheFor = ls;
        return seqCache = out;
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String[] ls) {
        int w = boxWidth(mc.font, ls) - 8;
        net.minecraft.util.FormattedCharSequence[] seq = sequences(ls);
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        com.teslamaps.render.gui.TeslaGui.backdropRect(ctx, -4, -4, w + 8, ls.length * 10 + 4);
        for (int i = 0; i < seq.length; i++) ctx.text(mc.font, seq[i], 0, i * 10, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        String[] ls = sampleLines();
        return new int[]{boxWidth(font, ls), ls.length * 10 + 4};
    }

    private static String fmtEta(double hours) {
        long minutes = Math.round(hours * 60);
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String fmt(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

    private static long parseAmount(String raw) {
        String s = raw.replace(",", "").trim();
        if (s.isEmpty()) return 0;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k' || last == 'K') mult = 1_000;
        else if (last == 'm' || last == 'M') mult = 1_000_000;
        else if (last == 'b' || last == 'B') mult = 1_000_000_000L;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return (long) (Double.parseDouble(s) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseNum(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.strip(s);
    }
}
