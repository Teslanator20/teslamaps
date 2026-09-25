package com.teslamaps.features;

// Ported from SkyHanni's BingoCardReader.kt, BingoCardDisplay.kt and nextstephelper/BingoNextStepHelper.kt
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BingoCard {

    private static final int MAX_PERSONAL = 20;
    private static final Pattern PERCENT = Pattern.compile("^\\s*Top (.+)%$");
    private static final Pattern HINT = Pattern.compile("The next hint will unlock in (.+)$");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final Pattern COLLECTION = Pattern.compile("Reach ([0-9,]+) (.*) Collection\\.");
    private static final Pattern CRYSTAL = Pattern.compile("Obtain a (\\w+) Crystal in the Crystal Hollows\\.");
    private static final Pattern SKILL = Pattern.compile("Obtain level (\\d+) in the (.*) Skill\\.");
    private static final Pattern TAB_SKILL = Pattern.compile("^(Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Hunting) (\\d+|[IVXLC]+):");
    private static final Pattern SKILL_UP = Pattern.compile("SKILL LEVEL UP (\\w+) .*?➜\\s*(\\d+|[IVXLC]+)");
    private static final Pattern CRYSTAL_OBTAINED = Pattern.compile("^(Topaz|Sapphire|Jade|Amethyst|Amber) Crystal$");
    private static final String SAMPLE_W = "§6Personal Goals: (12/20 done)________";

    public static final class Goal {
        public boolean community;
        public String name = "";
        public String description = "";
        public boolean done;
        public boolean unknownTip;
        public long hintSecs = -1;
        public double percent = -1;
    }

    private static final class Data {
        List<Goal> goals = new ArrayList<>();
        long lastOpen;
    }

    private static final class Step {
        final String name;
        final List<Step> req = new ArrayList<>();
        boolean done;
        long have, need;
        String island, skill, crystal;
        Map<String, Integer> variants;

        Step(String name) { this.name = name; }
        Step requires(Step s) { req.add(s); return this; }
    }

    private static Data data = new Data();
    private static String dataKey;
    private static boolean bingo;
    private static int ticks;
    private static boolean crystalNext;
    private static final Map<String, Integer> skillLevels = new HashMap<>();
    private static final java.util.Set<String> visited = new java.util.HashSet<>();

    private static final Map<String, Step> islands = new LinkedHashMap<>();
    private static final Map<String, Step> itemIsland = new HashMap<>();
    private static final List<Step> finalSteps = new ArrayList<>();
    private static List<Step> current = List.of();
    private static boolean stepsDirty = true;

    private static List<String> lines = List.of();
    private static int width = 0;
    private static HudEditElement el;

    private BingoCard() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("bingoCardDisplay", "Bingo Card").section("Inventory")
                .enabled(() -> cfg().bingoCardDisplay)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE_W), () -> 6 * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of("§6Community Goals:", "  §7Kill 100 Zombies §e4.2%", " ",
                        "§6Personal Goals: (12/20 done)", "  §7Reach 1,000 Ink Sac Collection.", "§eThe next hint will unlock in §b2h 5m"),
                        mc.font.width(SAMPLE_W)));
        Feature.of("BingoCard").section("Inventory").when(() -> cfg().bingoCardDisplay)
                .tick(BingoCard::tick)
                .serverChat(BingoCard::onChat)
                .hud("bingocarddisplay", BingoCard::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String key = SbProfile.key();
        if (key != null && !key.equals(dataKey)) {
            dataKey = key;
            load();
            resetSteps();
        }
        ticks++;
        if (mc.screen instanceof AbstractContainerScreen<?> cs && ticks % 10 == 0
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Bingo Card")) {
            read(cs);
        }
        if (ticks % 20 != 0) return;
        bingo = detectBingo();
        if (!bingo) { lines = List.of(); return; }
        if (cfg().bingoStepHelper) steps(mc);
        build(mc);
    }

    private static boolean detectBingo() {
        for (String l : TabListUtils.getTabListLines()) {
            String s = StringUtil.strip(l);
            if (s.contains("Profile:") && s.contains("Ⓑ")) return true;
        }
        for (String l : ScoreboardUtils.getScoreboardLines()) if (l.contains("Ⓑ")) return true;
        return false;
    }

    private static void read(AbstractContainerScreen<?> cs) {
        List<Goal> out = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            boolean personal = false, community = false, done = false;
            for (String l : lore) {
                if (l.endsWith("Personal Goal")) personal = true;
                else if (l.endsWith("Community Goal")) community = true;
                if (l.contains("GOAL REACHED")) done = true;
            }
            if (!personal && !community) continue;
            Goal g = new Goal();
            g.community = community;
            g.name = StringUtil.stripTrim(st.getHoverName().getString());
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < lore.size(); i++) {
                String s = lore.get(i).trim();
                if (s.isEmpty()) break;
                if (sb.length() > 0) sb.append(' ');
                sb.append(s);
            }
            g.description = sb.toString();
            g.done = done;
            if (community) {
                g.unknownTip = g.description.equals("This goal will be revealed when it hits Tier IV.");
                for (String l : lore) {
                    Matcher m = PERCENT.matcher(l);
                    if (m.matches()) {
                        try { g.percent = Double.parseDouble(m.group(1)) / 100; } catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                Matcher m = HINT.matcher(g.description);
                if (m.find()) {
                    g.unknownTip = true;
                    g.hintSecs = duration(m.group(1));
                }
            }
            out.add(g);
        }
        if (out.isEmpty()) return;
        if (cfg().bingoCommunityChange) {
            for (Goal g : out) {
                if (!g.community || g.percent < 0) continue;
                for (Goal old : data.goals) {
                    if (old.community && old.name.equals(g.name) && old.percent >= 0 && old.percent != g.percent) {
                        String color = g.percent > old.percent ? "§c" : "§a";
                        chat(color + g.name + ": " + pct(old.percent) + " §b-> " + pct(g.percent));
                    }
                }
            }
        }
        boolean changed = !sameGoals(data.goals, out);
        data.goals = out;
        data.lastOpen = System.currentTimeMillis();
        if (changed) stepsDirty = true;
        save();
    }

    private static boolean sameGoals(List<Goal> a, List<Goal> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).description.equals(b.get(i).description) || a.get(i).done != b.get(i).done) return false;
        }
        return true;
    }

    private static long duration(String s) {
        long total = 0;
        Matcher m = DURATION.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86400;
                case "h" -> n * 3600;
                case "m" -> n * 60;
                default -> n;
            };
        }
        return total > 0 ? total : -1;
    }

    private static String pct(double p) {
        String c = p < 0.01 ? "§a" : p < 0.05 ? "§e" : p < 0.25 ? "§6" : "§c";
        return c + String.format(Locale.US, "%.2f%%", p * 100);
    }

    private static String time(long secs) {
        long d = secs / 86400, h = secs % 86400 / 3600, m = secs % 3600 / 60, s = secs % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static void build(Minecraft mc) {
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>();
        List<Goal> goals = data.goals;
        if (goals.isEmpty()) {
            out.add("§6Bingo Goals:");
            out.add("§cOpen the §e/bingo §ccard.");
        } else {
            if (!c.bingoHideCommunity) {
                out.add("§6Community Goals:");
                int hidden = 0;
                for (Goal g : goals) {
                    if (!g.community) continue;
                    if (g.unknownTip) { hidden++; continue; }
                    out.add("  §7" + g.description + (g.percent >= 0 ? " " + pct(g.percent) : ""));
                }
                if (hidden > 0) out.add("§7+ " + hidden + " more §cunknown §7community goal" + (hidden == 1 ? "." : "s."));
                out.add(" ");
            }
            int todo = 0, hidden = 0;
            long nextTip = -1;
            List<String> personal = new ArrayList<>();
            for (Goal g : goals) {
                if (g.community || g.done) continue;
                todo++;
                if (g.unknownTip) {
                    hidden++;
                    if (g.hintSecs > 0 && (nextTip < 0 || g.hintSecs < nextTip)) nextTip = g.hintSecs;
                    continue;
                }
                personal.add("  §7" + g.description);
            }
            out.add("§6Personal Goals: (" + (MAX_PERSONAL - todo) + "/" + MAX_PERSONAL + " done)");
            out.addAll(personal);
            if (hidden > 0) out.add("§7+ " + hidden + " more §cunknown §7goal" + (hidden == 1 ? "." : "s."));
            if (c.bingoNextHint && nextTip > 0) {
                long left = (data.lastOpen + nextTip * 1000 - System.currentTimeMillis()) / 1000;
                if (left <= 0) {
                    out.add("§eThe next hint got unlocked already!");
                    out.add("§eOpen the bingo card to update!");
                } else out.add("§eThe next hint will unlock in §b" + time(left));
            }
        }
        if (c.bingoStepHelper) {
            out.add(" ");
            out.add("§6Bingo Step Helper:");
            if (current.isEmpty()) out.add(goals.isEmpty() ? "§cOpen the §e/bingo §ccard." : "  §7Nothing to do right now.");
            for (Step s : current) out.add("  §7" + s.name + progress(s));
        }
        int w = 0;
        for (String l : out) w = Math.max(w, mc.font.width(l));
        lines = out;
        width = w;
    }

    private static String progress(Step s) {
        if (s.need <= 0 || s.have <= 0) return "";
        return String.format(Locale.US, " §e%.0f%% §8(%,d/%,d)", Math.min(100.0, s.have * 100.0 / s.need), s.have, s.need);
    }

    // --- step helper ---

    private static void resetSteps() {
        islands.clear();
        itemIsland.clear();
        finalSteps.clear();
        current = List.of();
        stepsDirty = true;
        itemIsland.put("Acacia Wood", island("The Park"));
        for (String s : new String[]{"Redstone", "Lapis Lazuli", "Coal", "Slimeball", "Emerald", "Mithril"}) {
            itemIsland.put(s, island("Deep Caverns"));
        }
        island("Gold Mine").requires(island("Hub")).requires(skill("Mining", 1));
        island("Deep Caverns").requires(island("Gold Mine")).requires(skill("Mining", 5));
        String rhys = "30x Enchanted Minerals (Redstone, Lapis Lazuli, Coal) (for Rhys)";
        for (String m : new String[]{"Redstone", "Lapis Lazuli", "Coal"}) {
            island("Dwarven Mines").requires(items(rhys + " - " + m, 1600, Map.of(m, 1, "Enchanted " + m, 160))
                    .requires(island("Deep Caverns")));
        }
        island("Dwarven Mines").requires(skill("Mining", 12).requires(island("The Farming Islands")));
        island("Crystal Hollows").requires(island("Dwarven Mines"));
        island("Garden").requires(island("Hub"));
        island("The End").requires(skill("Combat", 12).requires(island("Deep Caverns")));
        finalSteps.add(island("Dwarven Mines"));
        finalSteps.add(new Step("Get Ender Armor").requires(island("The End")));
    }

    private static Step island(String name) {
        return islands.computeIfAbsent(name, n -> {
            Step s = new Step("Visit " + n);
            s.island = n;
            return s;
        });
    }

    private static Step skill(String skill, int level) {
        Step s = new Step(skill + " level " + level);
        s.skill = skill;
        s.need = level;
        return s;
    }

    private static Step items(String name, long need, Map<String, Integer> variants) {
        Step s = new Step(name);
        s.need = need;
        s.variants = variants;
        return s;
    }

    private static Step fromGoal(String desc) {
        Matcher m = COLLECTION.matcher(desc);
        if (m.find()) {
            String item = m.group(2);
            Step s = new Step("Reach " + m.group(1) + " " + item + " Collection");
            Step isl = itemIsland.get(item);
            if (isl != null) s.requires(isl);
            return s;
        }
        if (desc.equals("Craft an Emerald Ring.")) {
            return new Step("Craft Emerald Ring").requires(items("32x Enchanted Emerald", 160 * 32,
                    Map.of("Emerald", 1, "Enchanted Emerald", 160)).requires(island("Dwarven Mines")));
        }
        if (desc.equals("Obtain a Mathematical Hoe Blueprint.")) {
            return new Step("Craft Mathematical Hoe Blueprint").requires(items("32x Jacob's Ticket", 32,
                    Map.of("Jacob's Ticket", 1)).requires(new Step("Farming Contest").requires(skill("Farming", 10)))
                    .requires(island("Garden")));
        }
        m = CRYSTAL.matcher(desc);
        if (m.find()) {
            Step s = new Step("Obtain a " + m.group(1) + " Crystal");
            s.crystal = m.group(1);
            return s.requires(island("Crystal Hollows"));
        }
        m = SKILL.matcher(desc);
        if (m.find()) return skill(m.group(2), Integer.parseInt(m.group(1)));
        return null;
    }

    private static void steps(Minecraft mc) {
        if (stepsDirty) {
            resetSteps();
            stepsDirty = false;
            for (Goal g : data.goals) {
                if (g.community || g.done) continue;
                Step s = fromGoal(g.description);
                if (s != null) finalSteps.add(s);
            }
        }
        readSkills();
        String area = SkyblockUtils.getArea();
        if (!area.isEmpty()) visited.add(area.toLowerCase(Locale.ROOT));
        for (Step s : islands.values()) if (visited.contains(s.island.toLowerCase(Locale.ROOT))) s.done = true;
        List<Step> out = new ArrayList<>();
        for (Step s : finalSteps) collect(s, false, out, mc);
        current = out;
    }

    private static void collect(Step step, boolean parentDone, List<Step> out, Minecraft mc) {
        update(step, mc);
        int todo = 0;
        for (Step r : step.req) {
            collect(r, step.done || parentDone, out, mc);
            if (!r.done) todo++;
        }
        if (!step.done && !parentDone && todo == 0 && !out.contains(step)) out.add(step);
    }

    private static void update(Step s, Minecraft mc) {
        if (s.done) return;
        if (s.skill != null) {
            Integer lvl = skillLevels.get(s.skill.toLowerCase(Locale.ROOT));
            if (lvl != null) {
                s.have = lvl;
                if (lvl >= s.need) s.done = true;
            }
        } else if (s.variants != null && mc.player != null) {
            long total = 0;
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                Integer mult = s.variants.get(StringUtil.stripTrim(st.getHoverName().getString()));
                if (mult != null) total += (long) st.getCount() * mult;
            }
            s.have = total;
            if (total >= s.need) s.done = true;
        }
    }

    private static void readSkills() {
        for (String l : TabListUtils.getTabListLines()) {
            Matcher m = TAB_SKILL.matcher(StringUtil.stripTrim(l));
            if (m.find()) setSkill(m.group(1), m.group(2));
        }
    }

    private static void setSkill(String skill, String level) {
        int lvl;
        try {
            lvl = Character.isDigit(level.charAt(0)) ? Integer.parseInt(level) : roman(level);
        } catch (Exception e) { return; }
        skillLevels.merge(skill.toLowerCase(Locale.ROOT), lvl, Math::max);
    }

    private static int roman(String r) {
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> 0;
            };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (s.startsWith("BINGO GOAL COMPLETE! ")) {
            String name = s.substring("BINGO GOAL COMPLETE! ".length()).trim();
            for (Goal g : data.goals) {
                if (!g.community && g.name.equals(name) && !g.done) {
                    g.done = true;
                    stepsDirty = true;
                    save();
                }
            }
            return;
        }
        Matcher m = SKILL_UP.matcher(s);
        if (m.find()) { setSkill(m.group(1), m.group(2)); return; }
        if (s.startsWith("✦ CRYSTAL FOUND")) { crystalNext = true; return; }
        if (crystalNext) {
            crystalNext = false;
            Matcher c = CRYSTAL_OBTAINED.matcher(s);
            if (c.matches()) {
                for (Step st : current) if (c.group(1).equals(st.crystal)) st.done = true;
            }
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> text, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -3, -3, w + 6, text.size() * 10 + 4);
        for (int i = 0; i < text.size(); i++) g.text(mc.font, text.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Bingo] §r" + msg));
    }

    // --- storage ---

    private static Path file() {
        Path dir = SbProfile.dir();
        return dir == null ? null : dir.resolve("bingo.json");
    }

    private static void load() {
        data = new Data();
        Path f = file();
        if (f == null || !Files.isRegularFile(f)) return;
        try {
            Data d = new Gson().fromJson(Files.readString(f), new TypeToken<Data>() {}.getType());
            if (d != null) {
                if (d.goals == null) d.goals = new ArrayList<>();
                data = d;
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        Path f = file();
        if (f == null) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, new Gson().toJson(data));
        } catch (Exception ignored) {
        }
    }
}
