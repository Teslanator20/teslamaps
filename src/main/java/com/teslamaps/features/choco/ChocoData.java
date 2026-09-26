package com.teslamaps.features.choco;

// Factory menu parsing after SkyHanni's CFDataLoader + Skyblocker's ChocolateFactorySolver
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChocoData {

    public static final String CATEGORY = "Events";

    /** Persistent per-profile state. */
    public static final class State {
        public double chocolate = -1, cps = -1, multiplier = -1, rawCps = -1, ttMultiplier = -1;
        public long allTime = -1, thisPrestige = -1, prestigeReq = -1, lastUpdate;
        public int prestige = -1, barnCur = -1, barnMax = -1, ttCharges = -1, ttMax = -1;
        public long ttActiveUntil, ttNextCharge;
        public boolean maxPrestige;
        public long bestCost;
        public String bestName;
        public int leaderboard = -1;
        public double percentile = -1;
        public int hitmanEggs = -1, hitmanSlots = -1, hitmanMax = -1;
        public long hitmanSlotReady, hitmanAllReady;
        public long goal = -1;
        public String goalName;
        public Map<String, Integer> strays = new LinkedHashMap<>();
        public long strayChocolate;
    }

    public record Upgrade(int slot, String name, int level, long cost, double extra, boolean rabbit) {
        public double perCps() { return extra > 0 ? cost / extra : Double.MAX_VALUE; }
    }

    private static final Pattern CHOCOLATE_NAME = Pattern.compile("^([\\d,]+) Chocolate$");
    private static final Pattern CPS = Pattern.compile("^([\\d,.]+) (?:Chocolate )?per second$");
    private static final Pattern ALL_TIME = Pattern.compile("^All-time Chocolate: ([\\d,]+)");
    private static final Pattern THIS_PRESTIGE = Pattern.compile("^Chocolate this Prestige: ([\\d,]+)");
    private static final Pattern PRESTIGE_REQ = Pattern.compile("Requires (\\S+) Chocolate this Prestige");
    private static final Pattern MULTIPLIER = Pattern.compile("^Total Multiplier: ([\\d.]+)x");
    private static final Pattern BARN = Pattern.compile("^Your Barn: (\\d+)/(\\d+) Rabbits");
    private static final Pattern TT_CHARGES = Pattern.compile("^Charges: (\\d+)/(\\d+)");
    private static final Pattern TT_STATUS = Pattern.compile("^Status: (ACTIVE|INACTIVE)\\s*(.*)$");
    private static final Pattern TT_NEXT = Pattern.compile("^Next Charge: (\\S+)");
    private static final Pattern TT_MULT = Pattern.compile("by \\+([\\d.]+)x for");
    private static final Pattern LB_POS = Pattern.compile("You are #([\\d,]+)");
    private static final Pattern LB_PCT = Pattern.compile("You are in the top ([\\d.]+)% of players");
    private static final Pattern HITMAN_EGGS = Pattern.compile("^Available eggs: (\\d+)");
    private static final Pattern HITMAN_SLOTS = Pattern.compile("^Purchased slots: (\\d+)/(\\d+)");
    private static final Pattern HITMAN_SLOT_CD = Pattern.compile("^Slot cooldown: (.+)$");
    private static final Pattern HITMAN_ALL_CD = Pattern.compile("^All slots in: (.+)$");
    private static final Pattern PRESTIGE_NAME = Pattern.compile("^Chocolate Factory ([IVXLC]+)$");
    private static final Pattern RABBIT_NAME = Pattern.compile("^Rabbit \\S+ - (?:\\[(\\d+)] .*|Unemployed)$");
    private static final Pattern UPGRADE_NAME = Pattern.compile("^(Coach Jackrabbit|Rabbit Barn|Time Tower|Rabbit Shrine|Hand-Baked Chocolate)(?: ([IVXLC]+))?$");
    private static final Pattern CPS_INC = Pattern.compile("\\+([\\d,]+) Chocolate per second");
    private static final Pattern MULT_INC = Pattern.compile("\\+([\\d.]+)x Chocolate per second");
    private static final Pattern COST = Pattern.compile("Cost ([\\d,]+) Chocolate");
    private static final Pattern TIME_PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProfiledFile FILE = new ProfiledFile("choco_factory.json", null);

    private static State state = new State();
    private static boolean loaded, dirty;
    private static int ticks;
    private static boolean inFactory;
    private static int containerId = -1;

    public static List<Upgrade> upgrades = List.of();
    public static int bestSlot = -1, bestAffordableSlot = -1;
    public static int prestigeSlot = -1, barnSlot = -1, ttSlot = -1, hitmanSlot = -1, shrineSlot = -1, handSlot = -1;
    public static int prestigeLevel = -1, barnLevel = -1, ttLevel = -1, shrineLevel = -1, handLevel = -1;
    public static boolean canPrestige;
    public static List<Integer> rabbitSlots = List.of();

    private ChocoData() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static boolean anyEnabled() {
        TeslaMapsConfig c = cfg();
        return c.cfHelper || c.cfStats || c.cfUpgradeWarning || c.cfStrayWarning || c.cfStrayTimer || c.cfStrayTracker
                || c.cfTimeTowerWarning || c.cfTimeTowerExpired || c.cfBarnWarning || c.cfDupeTime || c.cfShopPrice
                || c.cfHitman || c.cfKeybinds || c.cfCompactTooltip || c.cfReminder;
    }

    public static void register() {
        FeatureRegistry.category(CATEGORY, "Skyblock", true);
        Feature.of("ChocoData").section(CATEGORY).when(ChocoData::anyEnabled).tickAlways(ChocoData::tick);
    }

    public static State state() {
        if (!loaded || FILE.stale()) load();
        return state;
    }

    public static boolean inFactory() { return inFactory; }

    public static void changed() { dirty = true; }

    /** Chocolate now, counting production since the last menu read. */
    public static double chocolateNow() {
        State s = state();
        return s.chocolate;
    }

    /** Seconds until {@code target} chocolate at the current rate; 0 when already there, -1 unknown. */
    public static double secondsUntil(double target) {
        State s = state();
        if (s.chocolate < 0) return -1;
        double missing = target - s.chocolate;
        if (missing <= 0) return 0;
        if (s.cps <= 0) return -1;
        return missing / s.cps;
    }

    public static String timeUntil(double target) {
        double sec = secondsUntil(target);
        if (sec < 0) return "§7?";
        if (sec == 0) return "§aNow";
        return "§b" + format((long) (sec * 1000));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        State s = state();
        boolean now = mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Chocolate Factory");
        if (!now && inFactory) {
            inFactory = false;
            containerId = -1;
            upgrades = List.of();
            bestSlot = bestAffordableSlot = -1;
            rabbitSlots = List.of();
        }
        inFactory = now;
        if (now && ticks % 5 == 0) parse((AbstractContainerScreen<?>) mc.screen);
        if (ticks % 20 == 0) {
            if (!now && SkyblockUtils.isInSkyblock() && s.chocolate >= 0 && s.cps > 0) {
                s.chocolate += s.cps;
                if (s.allTime >= 0) s.allTime += (long) s.cps;
                if (s.thisPrestige >= 0) s.thisPrestige += (long) s.cps;
            }
            if (dirty && ticks % 200 == 0) save();
        }
    }

    private static void parse(AbstractContainerScreen<?> cs) {
        State s = state();
        List<Slot> slots = cs.getMenu().slots;
        List<Upgrade> ups = new ArrayList<>();
        List<Integer> rabbits = new ArrayList<>();
        List<ItemStack> coachLater = new ArrayList<>();
        int coachSlot = -1;
        long now = System.currentTimeMillis();
        boolean prestigeClick = false, maxPrestige = false;
        double chocolate = -1;
        prestigeSlot = barnSlot = ttSlot = hitmanSlot = shrineSlot = handSlot = -1;
        for (Slot slot : slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            int i = slot.index;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            List<String> lore = ItemUtil.loreLines(st);
            Matcher m = CHOCOLATE_NAME.matcher(name);
            if (m.matches()) chocolate = num(m.group(1));
            for (String raw : lore) {
                String l = raw.trim();
                if (l.isEmpty()) continue;
                if ((m = CPS.matcher(l)).matches()) s.cps = dbl(m.group(1));
                else if ((m = ALL_TIME.matcher(l)).find()) s.allTime = (long) num(m.group(1));
                else if ((m = THIS_PRESTIGE.matcher(l)).find()) s.thisPrestige = (long) num(m.group(1));
                else if ((m = MULTIPLIER.matcher(l)).find()) s.multiplier = dbl(m.group(1));
                else if ((m = BARN.matcher(l)).find()) { s.barnCur = Integer.parseInt(m.group(1)); s.barnMax = Integer.parseInt(m.group(2)); }
                else if ((m = LB_POS.matcher(l)).find()) s.leaderboard = (int) num(m.group(1));
                else if ((m = LB_PCT.matcher(l)).find()) s.percentile = dbl(m.group(1));
                else if (l.contains("Click to prestige!")) prestigeClick = true;
                else if (l.contains("You have reached max prestige!")) maxPrestige = true;
                if ((m = PRESTIGE_REQ.matcher(l)).find()) s.prestigeReq = (long) NumberUtil.parseSuffixed(m.group(1));
            }
            if ((m = PRESTIGE_NAME.matcher(name)).matches()) {
                prestigeSlot = i;
                prestigeLevel = roman(m.group(1));
                s.prestige = prestigeLevel;
                continue;
            }
            if (name.equals("Rabbit Hitman")) {
                hitmanSlot = i;
                for (String l : lore) {
                    String t = l.trim();
                    if ((m = HITMAN_EGGS.matcher(t)).find()) s.hitmanEggs = Integer.parseInt(m.group(1));
                    else if ((m = HITMAN_SLOTS.matcher(t)).find()) { s.hitmanSlots = Integer.parseInt(m.group(1)); s.hitmanMax = Integer.parseInt(m.group(2)); }
                    else if ((m = HITMAN_SLOT_CD.matcher(t)).find()) s.hitmanSlotReady = now + parseTime(m.group(1));
                    else if ((m = HITMAN_ALL_CD.matcher(t)).find()) s.hitmanAllReady = now + parseTime(m.group(1));
                }
                continue;
            }
            String joined = String.join(" ", lore);
            if (RABBIT_NAME.matcher(name).matches()) {
                Matcher rm = RABBIT_NAME.matcher(name);
                rm.matches();
                int level = rm.group(1) == null ? 0 : Integer.parseInt(rm.group(1));
                rabbits.add(i);
                Matcher cm = CPS_INC.matcher(joined);
                long cur = -1, next = -1;
                if (cm.find()) {
                    cur = (long) num(cm.group(1));
                    if (cm.find()) next = (long) num(cm.group(1));
                    else { next = cur; cur = 0; }
                }
                Matcher co = COST.matcher(joined);
                if (cur >= 0 && co.find()) {
                    double mult = s.multiplier > 0 ? s.multiplier : 1;
                    ups.add(new Upgrade(i, name.substring(0, name.indexOf(" - ")), level, (long) num(co.group(1)), (next - cur) * mult, true));
                }
                continue;
            }
            if ((m = UPGRADE_NAME.matcher(name)).matches()) {
                int lvl = m.group(2) == null ? 0 : roman(m.group(2));
                switch (m.group(1)) {
                    case "Coach Jackrabbit" -> { coachSlot = i; coachLater.add(st); }
                    case "Rabbit Barn" -> { barnSlot = i; barnLevel = lvl; }
                    case "Time Tower" -> {
                        ttSlot = i;
                        ttLevel = lvl;
                        for (String l : lore) {
                            String t = l.trim();
                            if ((m = TT_CHARGES.matcher(t)).find()) { s.ttCharges = Integer.parseInt(m.group(1)); s.ttMax = Integer.parseInt(m.group(2)); }
                            else if ((m = TT_STATUS.matcher(t)).find()) {
                                if (m.group(1).equals("ACTIVE")) s.ttActiveUntil = now + parseTime(m.group(2));
                                else s.ttActiveUntil = 0;
                            } else if ((m = TT_NEXT.matcher(t)).find()) s.ttNextCharge = now + parseTime(m.group(1));
                            if ((m = TT_MULT.matcher(t)).find()) s.ttMultiplier = dbl(m.group(1));
                        }
                        if (lore.stream().anyMatch(l -> l.contains("What does it do? Nobody knows"))) { s.ttCharges = 0; s.ttMax = 0; }
                    }
                    case "Rabbit Shrine" -> { shrineSlot = i; shrineLevel = lvl; }
                    case "Hand-Baked Chocolate" -> { handSlot = i; handLevel = lvl; }
                    default -> { }
                }
            }
        }
        if (chocolate >= 0) s.chocolate = chocolate;
        if (s.cps > 0 && s.multiplier > 0) s.rawCps = s.cps / s.multiplier;
        canPrestige = prestigeClick;
        s.maxPrestige = maxPrestige;
        if (coachSlot >= 0 && !coachLater.isEmpty() && s.cps > 0 && s.multiplier > 0) {
            ItemStack st = coachLater.get(0);
            String joined = String.join(" ", ItemUtil.loreLines(st));
            Matcher mm = MULT_INC.matcher(joined);
            if (mm.find()) {
                double cur = dbl(mm.group(1)), next;
                if (mm.find()) next = dbl(mm.group(1));
                else { next = cur; cur = 0; }
                Matcher co = COST.matcher(joined);
                if (co.find()) {
                    Matcher lv = UPGRADE_NAME.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
                    int level = lv.matches() && lv.group(2) != null ? roman(lv.group(2)) : 0;
                    ups.add(new Upgrade(coachSlot, "Coach Jackrabbit", level, (long) num(co.group(1)), s.cps / s.multiplier * (next - cur), false));
                }
            }
        }
        ups.sort(Comparator.comparingDouble(Upgrade::perCps));
        rabbitSlots = rabbits;
        upgrades = ups;
        bestSlot = bestAffordableSlot = -1;
        if (!ups.isEmpty()) {
            Upgrade best = ups.get(0);
            bestSlot = best.slot;
            if (s.chocolate >= 0) {
                for (Upgrade u : ups) {
                    if (u.cost <= s.chocolate) { bestAffordableSlot = u.slot; break; }
                }
            }
            if (s.bestCost != best.cost || !best.name.equals(s.bestName)) ChocoFactory.upgradeChanged();
            s.bestCost = best.cost;
            s.bestName = best.name;
        }
        s.lastUpdate = now;
        if (containerId != cs.getMenu().containerId) {
            containerId = cs.getMenu().containerId;
            ChocoExtras.onFactoryOpened();
        }
        dirty = true;
    }

    public static Upgrade upgradeAt(int slot) {
        for (Upgrade u : upgrades) if (u.slot == slot) return u;
        return null;
    }

    // helpers

    static double num(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static double dbl(String s) {
        return num(s);
    }

    static long parseTime(String s) {
        Matcher m = TIME_PART.matcher(s);
        long ms = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }

    static int roman(String r) {
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

    public static String format(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000, d = s / 86400, h = s / 3600 % 24, m = s / 60 % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s % 60 + "s";
        return s + "s";
    }

    public static String commas(double v) {
        return String.format(Locale.ROOT, "%,d", (long) v);
    }

    static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + msg));
    }

    /** Chat line that runs /cf on click. */
    static void chatOpen(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + msg)
                .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/chocolatefactory"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /cf")))));
    }

    static void beep() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 1f));
    }

    private static void load() {
        loaded = true;
        dirty = false;
        state = new State();
        try {
            Path p = FILE.path();
            if (p != null && Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p)) {
                    State s = GSON.fromJson(r, State.class);
                    if (s != null) state = s;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[ChocoFactory] load failed: {}", e.toString());
        }
        if (state.strays == null) state.strays = new LinkedHashMap<>();
    }

    static void save() {
        if (!loaded) return;
        dirty = false;
        try {
            Path p = FILE.path();
            if (p == null) return;
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p)) {
                GSON.toJson(state, w);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[ChocoFactory] save failed: {}", e.toString());
        }
    }
}
