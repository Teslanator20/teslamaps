package com.teslamaps.features;

// HUD-040, after SkyHanni's ExperimentsDryStreakDisplay.kt, ExperimentsProfitTracker.kt, GuardianReminder.kt
// and ExperimentationTableApi.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExperimentStats {

    private static final Pattern TABLE_GUIS = Pattern.compile(
            "(?:Superpairs|Chronomatron|Ultrasequencer) ?(?:\\(.+\\)|➜ Stakes|Rewards)|Experiment(?:ation Tabl| [Oo]v)er?");
    private static final Pattern TYPE = Pattern.compile("^(Superpairs|Chronomatron|Ultrasequencer) \\((.+)\\)$");
    private static final Pattern OVER = Pattern.compile("^(?:Experiment [Oo]ver|Superpairs Rewards)$");
    private static final Pattern REWARD = Pattern.compile("^\\+\\s*(?:\\[Lvl \\d+] )?(.*?)(?:\\s\\((?:Stakes|Pairs)\\))?$");
    private static final Pattern XP = Pattern.compile("^(\\d+(?:,\\d+)*(?:\\.\\d+)?)([kMB]?) Enchanting Exp$");
    private static final Pattern BOTTLE_NAME = Pattern.compile("^(Colossal |Titanic |Grand )?Experience Bottle$");
    private static final Pattern BOTTLE_ID = Pattern.compile("^(?:COLOSSAL_|TITANIC_|GRAND_)?EXP_BOTTLE$");
    private static final Pattern CLAIM = Pattern.compile("^You claimed the \\S+ rewards!$");
    private static final Pattern RENEW = Pattern.compile("You bought a bonus charge for the Experimentation Table! \\((\\d)/3\\)");
    private static final Pattern BOOK = Pattern.compile("^(.+) ([IVX]+)$");
    private static final Pattern GUARDIAN = Pattern.compile("§([956d])Guardian");
    private static final int[] RENEW_BITS = {0, 150, 300, 500};

    private static final List<String> DRY_SAMPLE = List.of("§cDry-Streak since last §5ULTRA-RARE", "§e ├ 12 Attempts", "§e └ 1.2M XP");

    private static Tracker tracker;
    private static HudEditElement dryHud, profitHud;
    private static Feature feature;
    private static List<String> dryLines = List.of();
    private static int dryWidth;

    private static Screen lastScreen;
    private static boolean inTable;
    private static String type;
    private static boolean rareFired, ignoreNextFinish;
    private static int overHash;
    private static long pendingXp;
    private static String pendingType;
    private static Map<String, Integer> pendingItems = new HashMap<>();
    private static BlockPos tablePos;
    private static long tableOpenedAt, guardianWarnAt;
    private static boolean guardianChecked;
    private static int lastBottles = -1;
    private static int ticks;

    private ExperimentStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.expDryStreak || c.expProfitTracker || c.expGuardianReminder;
    }

    public static void register() {
        tracker = Tracker.create("experiments", "§e§lExperiments Profit")
                .priceSource(() -> cfg().expProfitPriceSource)
                .name("EXPERIMENTS_DONE", "§eExperiments Done")
                .name("ENCHANTING_XP", "§3Enchanting Exp")
                .name("BITS_SPENT", "§bBits Spent")
                .name(Tracker.COINS, "§cXP Bottles Used");
        dryHud = HudEditElement.of("expDryStreak", "Experiments Dry Streak").section("Inventory")
                .enabled(() -> cfg().expDryStreak)
                .size(() -> dryLines.isEmpty() ? TextHud.width(DRY_SAMPLE) : dryWidth,
                        () -> TextHud.height(dryLines.isEmpty() ? DRY_SAMPLE.size() : dryLines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, dryLines.isEmpty() ? DRY_SAMPLE : dryLines,
                        dryLines.isEmpty() ? TextHud.width(DRY_SAMPLE) : dryWidth));
        profitHud = HudEditElement.of("expProfit", "Experiments Profit").section("Inventory")
                .enabled(() -> cfg().expProfitTracker)
                .size(() -> Math.max(60, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(10, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        feature = Feature.of("ExperimentStats").section("Inventory").when(ExperimentStats::on)
                .tick(ExperimentStats::tick)
                .serverChat(ExperimentStats::onChat)
                .hudHiddenInInventory("expstats", ExperimentStats::renderHud)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Experimentation Stats");
                    b.toggle("Experiments Dry Streak", () -> c.expDryStreak, v -> { c.expDryStreak = v; rebuildDry(); })
                            .desc("Superpairs attempts and Enchanting Exp since your last ULTRA-RARE, shown at the table. Position in /tmap gui.");
                    b.toggle("  Attempts", () -> c.expDryStreakAttempts, v -> { c.expDryStreakAttempts = v; rebuildDry(); });
                    b.toggle("  Enchanting Exp", () -> c.expDryStreakXp, v -> { c.expDryStreakXp = v; rebuildDry(); });
                    b.button("  Reset Dry Streak", ExperimentStats::resetDry);
                    b.toggle("Experiments Profit Tracker", () -> c.expProfitTracker, v -> c.expProfitTracker = v)
                            .desc("Loot, Enchanting Exp, bits for bonus charges and XP bottles used, with profit per hour "
                                    + "and time at the table. Shown at the table. Position in /tmap gui.");
                    b.toggle("  Count Used XP Bottles", () -> c.expProfitTrackBottles, v -> c.expProfitTrackBottles = v);
                    b.priceSource("  Price Source", () -> c.expProfitPriceSource, v -> c.expProfitPriceSource = v);
                    b.trackerButtons(tracker);
                    b.toggle("Guardian Pet Reminder", () -> c.expGuardianReminder, v -> c.expGuardianReminder = v)
                            .desc("Chat, sound and a warning over the table menu when you open it without a Guardian pet.");
                });
    }

    private static long[] dry() {
        String k = SbProfile.key();
        return cfg().expDryStreakData.computeIfAbsent(k == null ? "default" : k, x -> new long[2]);
    }

    private static void resetDry() {
        long[] d = dry();
        d[0] = 0;
        d[1] = 0;
        TeslaMapsConfig.save();
        rebuildDry();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Dry-streak counter reset."));
    }

    private static void rebuildDry() {
        TeslaMapsConfig c = cfg();
        if (!c.expDryStreakAttempts && !c.expDryStreakXp) {
            dryLines = List.of();
            return;
        }
        long[] d = dry();
        String attempts = d[0] + (d[0] == 1 ? " Attempt" : " Attempts");
        String xp = shortNum(d[1]) + " XP";
        List<String> out = new ArrayList<>();
        out.add("§cDry-Streak since last §5ULTRA-RARE");
        if (c.expDryStreakAttempts && c.expDryStreakXp) {
            out.add("§e ├ " + attempts);
            out.add("§e └ " + xp);
        } else {
            out.add("§e └ " + (c.expDryStreakAttempts ? attempts : xp));
        }
        dryLines = out;
        dryWidth = TextHud.width(out);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        ticks++;
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            onOpen(mc.screen);
        }
        if (ticks % 20 == 0 && dryLines.isEmpty() && c.expDryStreak) rebuildDry();

        if (mc.screen instanceof AbstractContainerScreen<?> cs && inTable) {
            if (c.expProfitTracker && ticks % 20 == 0) tracker.markActivity();
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            if (c.expGuardianReminder && !guardianChecked && title.equals("Experimentation Table")
                    && System.currentTimeMillis() - tableOpenedAt > 200) {
                guardianChecked = true;
                checkGuardian(mc);
            }
            if (ticks % 5 == 0) {
                if ("Superpairs".equals(type) && !rareFired) scanRare(cs);
                if (OVER.matcher(title).matches()) readOver(cs);
            }
        }
        if (c.expProfitTracker) {
            tracker.tick();
            if (c.expProfitTrackBottles) trackBottles(mc);
        }
    }

    private static void onOpen(Screen s) {
        if (!(s instanceof AbstractContainerScreen<?>)) {
            inTable = false;
            return;
        }
        String title = StringUtil.stripTrim(s.getTitle().getString());
        inTable = TABLE_GUIS.matcher(title).find();
        if (!inTable) return;
        if (title.equals("Experimentation Table")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) tablePos = mc.player.blockPosition();
            tableOpenedAt = System.currentTimeMillis();
            guardianChecked = false;
            type = null;
            return;
        }
        Matcher m = TYPE.matcher(title);
        if (m.matches()) overHash = 0;
        if (m.matches() && !m.group(1).equals(type)) {
            type = m.group(1);
            rareFired = false;
        }
    }

    private static void checkGuardian(Minecraft mc) {
        String pet = PetTracker.displayName();
        if (pet != null && StringUtil.strip(pet).contains("Guardian")) return;
        guardianWarnAt = System.currentTimeMillis();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §eUse a §9§lGuardian Pet §efor more Exp in the Experimentation Table."));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }

    private static void scanRare(AbstractContainerScreen<?> cs) {
        if (!cfg().expDryStreak) return;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(slot.getItem());
            if (lore.isEmpty() || !lore.get(0).trim().equals("XX ULTRA-RARE BOOK! XX")) continue;
            rareFired = true;
            long[] d = dry();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String finally_ = d[0] >= 10 ? "§o(finally)§r§e " : "";
                mc.player.sendSystemMessage(Component.literal("§a§lDRY-STREAK ENDED! §eYou have " + finally_ + "found an §5ULTRA-RARE §eafter §3"
                        + shortNum(d[1]) + " Enchanting Exp §eand §2" + d[0] + (d[0] == 1 ? " attempt" : " attempts") + "§e!"));
            }
            d[0] = 0;
            d[1] = 0;
            ignoreNextFinish = true;
            TeslaMapsConfig.save();
            rebuildDry();
            return;
        }
    }

    private static void readOver(AbstractContainerScreen<?> cs) {
        if (type == null) return;
        List<Slot> slots = cs.getMenu().slots;
        int idx = "Superpairs".equals(type) ? 13 : 11;
        if (idx >= slots.size()) return;
        ItemStack st = slots.get(idx).getItem();
        var lore = ItemUtil.lore(st);
        if (lore == null || lore.lines().isEmpty()) return;
        List<String> plain = ItemUtil.loreLines(st);
        int h = plain.hashCode();
        if (h == overHash) return;
        overHash = h;
        long xp = 0;
        Map<String, Integer> items = new HashMap<>();
        boolean active = false;
        for (int i = 0; i < plain.size(); i++) {
            String s = plain.get(i).trim();
            if (s.equals("Rewards:")) { active = true; continue; }
            if (!active) continue;
            if (s.equals("Click to claim rewards!")) break;
            Matcher m = REWARD.matcher(s);
            if (!m.matches()) continue;
            String r = m.group(1).trim();
            if (r.isEmpty() || r.endsWith("Superpairs clicks")) continue;
            Matcher x = XP.matcher(r);
            if (x.matches()) { xp += parseXp(x.group(1), x.group(2)); continue; }
            String id = rewardId(r, i < lore.lines().size() ? ChatText.formatted(lore.lines().get(i)) : "");
            if (id != null) items.merge(id, 1, Integer::sum);
        }
        pendingXp = xp;
        pendingItems = items;
        pendingType = type;
    }

    private static String rewardId(String name, String formatted) {
        if (name.equals("Guardian")) {
            Matcher g = GUARDIAN.matcher(formatted);
            String rarity = !g.find() ? "3" : switch (g.group(1)) {
                case "9" -> "2";
                case "6" -> "4";
                case "d" -> "5";
                default -> "3";
            };
            return "GUARDIAN;" + rarity;
        }
        Matcher b = BOTTLE_NAME.matcher(name);
        if (b.matches()) return b.group(1) == null ? "EXP_BOTTLE" : b.group(1).trim().toUpperCase(Locale.ROOT) + "_EXP_BOTTLE";
        String id = SkyblockItems.idForName(name);
        if (id != null) return id;
        Matcher k = BOOK.matcher(name);
        if (k.matches()) {
            int lvl = roman(k.group(2));
            if (lvl > 0) return "ENCHANTMENT_" + k.group(1).toUpperCase(Locale.ROOT).replace("'", "")
                    .replaceAll("[^A-Z0-9 ]", "").trim().replace(' ', '_') + "_" + lvl;
        }
        return null;
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
            default -> 0;
        };
    }

    private static long parseXp(String n, String suffix) {
        double v;
        try {
            v = Double.parseDouble(n.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
        return (long) (v * switch (suffix) {
            case "k" -> 1_000;
            case "M" -> 1_000_000;
            case "B" -> 1_000_000_000;
            default -> 1;
        });
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        TeslaMapsConfig c = cfg();
        if (CLAIM.matcher(s).matches()) {
            if (pendingType == null) return;
            boolean superpairs = pendingType.equals("Superpairs");
            if (c.expDryStreak) {
                if (ignoreNextFinish) {
                    ignoreNextFinish = false;
                } else {
                    long[] d = dry();
                    d[1] += pendingXp;
                    if (superpairs) d[0]++;
                    TeslaMapsConfig.save();
                    rebuildDry();
                }
            }
            if (c.expProfitTracker) {
                if (superpairs) tracker.add("EXPERIMENTS_DONE", 1);
                if (pendingXp > 0) tracker.add("ENCHANTING_XP", pendingXp);
                for (var e : pendingItems.entrySet()) tracker.add(e.getKey(), e.getValue());
            }
            pendingType = null;
            pendingItems = new HashMap<>();
            pendingXp = 0;
            overHash = 0;
            return;
        }
        if (c.expProfitTracker && s.contains("bonus charge")) {
            Matcher m = RENEW.matcher(s);
            if (m.find()) {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 3) tracker.add("BITS_SPENT", RENEW_BITS[n]);
            }
        }
    }

    private static void trackBottles(Minecraft mc) {
        if (mc.player == null || tablePos == null || ticks % 4 != 0) return;
        if (!SkyblockUtils.isInArea("Private Island")) { lastBottles = -1; return; }
        Map<String, Integer> now = new HashMap<>();
        int total = 0;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = ItemUtil.skyblockId(st);
            if (!BOTTLE_ID.matcher(id).matches()) continue;
            now.merge(id, st.getCount(), Integer::sum);
            total += st.getCount();
        }
        boolean near = mc.player.blockPosition().distSqr(tablePos) <= 15 * 15;
        if (lastBottles >= 0 && total < lastBottles && near && mc.screen == null) {
            for (var e : lastCounts.entrySet()) {
                int used = e.getValue() - now.getOrDefault(e.getKey(), 0);
                if (used > 0) tracker.addCoins(-(long) (bottlePrice(e.getKey()) * used));
            }
        }
        lastBottles = total;
        lastCounts = now;
    }

    private static Map<String, Integer> lastCounts = new HashMap<>();

    private static double bottlePrice(String id) {
        Double p = PriceManager.getPriceOrNull(id, cfg().expProfitPriceSource);
        return Math.max(p == null ? 0 : p, SkyblockItems.npcPrice(id));
    }

    private static boolean nearTable() {
        Minecraft mc = Minecraft.getInstance();
        return tablePos != null && mc.player != null && mc.player.blockPosition().distSqr(tablePos) <= 25
                && SkyblockUtils.isInArea("Private Island");
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || !cfg().expProfitTracker || !nearTable()) return;
        tracker.draw(g, mc, profitHud.x(), profitHud.y(), profitHud.scale());
    }

    /** Over the table menus, from ContainerOverlays. */
    public static void render(GuiGraphicsExtractor g) {
        if (feature == null || !inTable || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (c.expDryStreak && !dryLines.isEmpty()) TextHud.draw(g, mc, dryHud.x(), dryHud.y(), dryHud.scale(), dryLines, dryWidth);
        if (c.expProfitTracker) tracker.draw(g, mc, profitHud.x(), profitHud.y(), profitHud.scale());
        if (c.expGuardianReminder && guardianWarnAt != 0 && System.currentTimeMillis() - guardianWarnAt < 2000
                && mc.screen instanceof AbstractContainerScreen<?> cs) {
            String t = "§cWrong Pet equipped!";
            int top = ((HandledScreenAccessor) cs).getY();
            g.pose().pushMatrix();
            g.pose().translate(cs.width / 2f, Math.max(4, top - 26));
            g.pose().scale(2f, 2f);
            g.text(mc.font, t, -mc.font.width(t) / 2, 0, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
    }

    private static String shortNum(long n) {
        if (n >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", n / 1e9);
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 1_000) return String.format(Locale.ROOT, "%.1fk", n / 1e3);
        return String.valueOf(n);
    }
}
