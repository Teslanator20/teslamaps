package com.teslamaps.features.tracker;

// After Skysoft's features/profit (ProfitTracker, ProfitTrackerPresets, SlayerQuestCostCapture); presets json bundled from Skysoft
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.slayer.SlayerQuest;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfitTracker {

    private record Preset(String name, String key, boolean anyIsland, Set<String> islands, Map<String, Set<String>> islandAreas,
                          Set<String> items, String action, String actionName) {}

    public static final String[] CHOICES = {"Auto", "Zombie Slayer", "Spider Slayer", "Wolf Slayer", "Enderman Slayer",
            "Blaze Slayer", "Vampire Slayer", "Fishing", "Foraging", "Mining", "Diana"};
    private static final String BOSSES = "PROFIT_BOSSES", BURROWS = "PROFIT_BURROWS", QUEST_COST = "PROFIT_QUEST_COST";
    private static final String CATCHES = "PROFIT_CATCHES";
    private static final Pattern SACK_CHANGE = Pattern.compile("([+-][\\d,]+) (.+?) \\((.+?)\\)");
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");
    private static final long GRACE_MS = 1500, DROP_SUPPRESS_MS = 10_000, SACK_SUPPRESS_MS = 60_000;

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    private static final Map<String, Tracker> TRACKERS = new HashMap<>();
    private static HudEditElement hud;
    private static Preset active;
    private static Tracker shown;

    private static Map<String, Long> counts = new HashMap<>();
    private static final Map<String, long[]> suppress = new HashMap<>();
    private static Object lastLevel;
    private static long graceUntil, lastTick, questStartAt, burrowAt, fishingAt;
    private static long purse = -1, pendingCost, pendingAt;
    private static boolean screenOpen;
    private static int ticks;
    private static String lastHover;
    private static long lastHoverAt;

    private ProfitTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        loadPresets();
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("tmap").then(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("undolastslayer").executes(c -> {
                            Minecraft.getInstance().execute(ProfitTracker::undoLastSlayer);
                            return 1;
                        }))));
        for (Preset p : PRESETS.values()) {
            Tracker t = Tracker.create("profit_" + p.key, "§e§l" + p.name + " Profit")
                    .priceSource(() -> cfg().profitTrackerPriceSource)
                    .idleSeconds(() -> cfg().profitTrackerIdle)
                    .name(Tracker.COINS, p.key.contains("slayer") ? "§6Mob Kill Coins" : "§6Coins")
                    .name(QUEST_COST, "§cQuest Cost").coinKey(QUEST_COST).fixedValue(QUEST_COST, -1)
                    .name(BOSSES, "§bBosses Killed").fixedValue(BOSSES, 0)
                    .name(BURROWS, "§bBurrows Dug").fixedValue(BURROWS, 0)
                    .name(CATCHES, "§bTimes Fished").fixedValue(CATCHES, 0);
            if ("fishing".equals(p.key)) {
                t.extra(v -> {
                    long n = t.count(CATCHES, v);
                    return n > 0 ? List.of("§eper Catch: §6" + ItemPrice.format(t.profit(v) / n)) : List.of();
                });
            }
            if (BOSSES.equals(p.action)) {
                t.extra(v -> {
                    long n = t.count(BOSSES, v);
                    List<String> out = new ArrayList<>(3);
                    if (n > 0) out.add("§eper Boss: §6" + ItemPrice.format(t.profit(v) / n));
                    if (cfg().profitTrackerSlayerRng) rngLines(p.key, out);
                    return out;
                });
            }
            TRACKERS.put(p.name, t);
        }
        hud = HudEditElement.of("profitTracker", "Profit Tracker")
                .enabled(() -> cfg().profitTracker)
                .size(() -> shown == null ? 110 : Math.max(80, shown.size(Minecraft.getInstance().font)[0]),
                        () -> shown == null ? 50 : Math.max(30, shown.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> {
                    Tracker t = shown != null ? shown : TRACKERS.get("Mining");
                    if (t != null) t.draw(g, mc, x, y, s);
                });
        Feature.of("ProfitTracker").when(() -> cfg().profitTracker)
                .tick(ProfitTracker::tick)
                .serverChat(ProfitTracker::onChat)
                .hudHiddenInInventory("profittracker", ProfitTracker::render)
                .config("Trackers", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Profit Tracker");
                    b.toggle("Profit Tracker", () -> c.profitTracker, v -> c.profitTracker = v)
                            .desc("Drops, coins and profit/h for Slayer, Fishing, Foraging, Mining and Diana, one tracker each (/tmap gui).");
                    b.dropdown("  Activity", CHOICES, () -> c.profitTrackerPreset, v -> c.profitTrackerPreset = v)
                            .desc("Auto = slayer quest, fishing rod out, Diana burrows or the island you are on");
                    b.toggle("  Only Known Drops", () -> c.profitTrackerPresetOnly, v -> c.profitTrackerPresetOnly = v)
                            .desc("Count only the drop list of the activity instead of every item you pick up");
                    b.toggle("  Sacks", () -> c.profitTrackerSacks, v -> c.profitTrackerSacks = v);
                    b.toggle("  Coins", () -> c.profitTrackerCoins, v -> c.profitTrackerCoins = v);
                    b.toggle("  Subtract Slayer Quest Cost", () -> c.profitTrackerSlayerCost, v -> c.profitTrackerSlayerCost = v);
                    b.toggle("  Slayer: Bosses Since RNG + Recent Kills", () -> c.profitTrackerSlayerRng, v -> c.profitTrackerSlayerRng = v)
                            .desc("Bosses and time since the last RNG drop per slayer, plus the last kill times. /tmap undolastslayer takes back a miscounted boss.")
                            .tags("rng", "slayer", "since", "undo");
                    b.toggle("  Fishing: Times Fished + Profit/Catch", () -> c.profitTrackerFishingCatches, v -> c.profitTrackerFishingCatches = v)
                            .desc("Counts your catches (reel right after the catch sound) and shows profit per catch on the Fishing tracker")
                            .tags("fishing", "catch", "times fished");
                    b.priceSource("  Price Source", () -> c.profitTrackerPriceSource, v -> c.profitTrackerPriceSource = v);
                    b.slider("  Pause After (s)", 10f, 600f, () -> (float) c.profitTrackerIdle, v -> c.profitTrackerIdle = Math.round(v), 0);
                    for (Preset p : PRESETS.values()) {
                        Tracker t = TRACKERS.get(p.name);
                        b.buttonRow(new String[]{"Reset " + p.name, "Session/Total"},
                                new Runnable[]{t::resetSession, t::toggleView});
                    }
                });
    }

    private static final int RECENT = 5;
    private static final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();
    private static long recentSeenAt;
    private static String lastSlayerKey;
    private static long lastRngAt;

    private static void rngLines(String key, List<String> out) {
        TeslaMapsConfig c = cfg();
        Integer since = c.slayerBossesSinceRng.get(key);
        Long at = c.slayerLastRngMs.get(key);
        out.add("§eSince RNG: §f" + (since == null ? 0 : since) + " bosses"
                + (at == null || at <= 0 ? "" : " §7(" + ago(System.currentTimeMillis() - at) + ")"));
        if (recent.isEmpty() || !key.equals(lastSlayerKey)) return;
        StringBuilder sb = new StringBuilder("§eRecent:");
        for (long ms : recent) sb.append(" §f").append(String.format(java.util.Locale.ROOT, "%d:%02d", ms / 60000, ms / 1000 % 60));
        out.add(sb.toString());
    }

    private static String ago(long ms) {
        long m = ms / 60000;
        if (m < 60) return m + "m";
        long h = m / 60;
        return h < 48 ? h + "h " + (m % 60) + "m" : (h / 24) + "d";
    }

    private static boolean rngDrop(String t) {
        return t.startsWith("CRAZY RARE DROP!") || t.startsWith("INSANE DROP!") || t.startsWith("PRAY TO RNGESUS DROP!")
                || t.startsWith("RNGESUS INCARNATE DROP!") || t.startsWith("RNG METER! ");
    }

    private static void slayerKill(String key) {
        lastSlayerKey = key;
        if (!cfg().profitTrackerSlayerRng) return;
        // a drop right before the slain line belongs to this boss
        if (System.currentTimeMillis() - lastRngAt < 5000) return;
        cfg().slayerBossesSinceRng.merge(key, 1, Integer::sum);
        TeslaMapsConfig.save();
    }

    private static void undoLastSlayer() {
        Minecraft mc = Minecraft.getInstance();
        Tracker t = null;
        String key = lastSlayerKey;
        if (key == null && active != null && BOSSES.equals(active.action)) key = active.key;
        if (key != null) for (Preset p : PRESETS.values()) if (p.key.equals(key)) t = TRACKERS.get(p.name);
        if (t == null || t.count(BOSSES, Tracker.View.SESSION) <= 0) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cNo slayer boss to undo this session."));
            return;
        }
        t.edit(BOSSES, -1);
        Integer since = cfg().slayerBossesSinceRng.get(key);
        if (since != null && since > 0) cfg().slayerBossesSinceRng.put(key, since - 1);
        if (!recent.isEmpty()) recent.pollLast();
        TeslaMapsConfig.save();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §aRemoved the last slayer boss."));
    }

    private static void loadPresets() {
        try (var in = ProfitTracker.class.getResourceAsStream("/assets/teslamaps/data/profit_tracker_presets.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject slayer = root.getAsJsonObject("slayer");
            String[][] slayers = {{"ZOMBIE", "Zombie Slayer"}, {"SPIDER", "Spider Slayer"}, {"WOLF", "Wolf Slayer"},
                    {"ENDERMAN", "Enderman Slayer"}, {"BLAZE", "Blaze Slayer"}, {"VAMPIRE", "Vampire Slayer"}};
            for (String[] s : slayers) put(slayer.getAsJsonObject(s[0]), s[1], s[0].toLowerCase() + "_slayer", BOSSES, null);
            put(root.getAsJsonObject("fishing"), "Fishing", "fishing", null, null);
            put(root.getAsJsonObject("foraging"), "Foraging", "foraging", null, null);
            put(root.getAsJsonObject("mining"), "Mining", "mining", null, null);
            put(root.getAsJsonObject("mythologicalRitual"), "Diana", "diana", BURROWS, null);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read profit_tracker_presets.json: {}", e.toString());
        }
    }

    private static void put(JsonObject o, String name, String key, String action, String actionName) {
        if (o == null) return;
        Map<String, Set<String>> islandAreas = new HashMap<>();
        if (o.has("islandAreas")) {
            for (var e : o.getAsJsonObject("islandAreas").entrySet()) islandAreas.put(e.getKey(), strings(e.getValue()));
        }
        PRESETS.put(name, new Preset(name, key, o.has("anyIsland") && o.get("anyIsland").getAsBoolean(),
                strings(o.get("islands")), islandAreas, strings(o.get("items")), action, actionName));
    }

    private static Set<String> strings(JsonElement e) {
        Set<String> out = new HashSet<>();
        if (e instanceof JsonArray a) for (JsonElement x : a) out.add(x.getAsString());
        return out;
    }

    private static Preset detect(String island, String area) {
        String choice = cfg().profitTrackerPreset;
        if (!"Auto".equals(choice)) return PRESETS.get(choice);
        String boss = SlayerQuest.boss();
        if (boss != null) {
            String p = switch (boss) {
                case "Revenant Horror", "Atoned Horror" -> "Zombie Slayer";
                case "Tarantula Broodfather", "Conjoined Brood" -> "Spider Slayer";
                case "Sven Packmaster" -> "Wolf Slayer";
                case "Voidgloom Seraph" -> "Enderman Slayer";
                case "Inferno Demonlord" -> "Blaze Slayer";
                default -> "Vampire Slayer";
            };
            return PRESETS.get(p);
        }
        long now = System.currentTimeMillis();
        Preset diana = PRESETS.get("Diana");
        if (diana != null && burrowAt != 0 && now - burrowAt < 300_000 && diana.islands.contains(island)) return diana;
        if (fishingAt != 0 && now - fishingAt < 120_000 && !SkyblockUtils.isInGarden()) return PRESETS.get("Fishing");
        for (String n : new String[]{"Mining", "Foraging"}) {
            Preset p = PRESETS.get(n);
            if (p == null || !p.islands.contains(island)) continue;
            Set<String> areas = p.islandAreas.get(island);
            if (areas == null || areas.contains(area)) return p;
        }
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            graceUntil = now + 3000;
            purse = -1;
        }
        if (now - lastTick > 1000) graceUntil = now + GRACE_MS;
        lastTick = now;
        boolean open = mc.gui.screen() != null;
        if (open != screenOpen) {
            screenOpen = open;
            if (!open) graceUntil = now + GRACE_MS;
        }
        if (mc.player.fishing != null) fishingAt = now;
        trackCatch(mc, now);
        for (Tracker t : TRACKERS.values()) t.tick();
        long fightAt = com.teslamaps.slayer.SlayerHUD.lastFightAt();
        if (fightAt != recentSeenAt) {
            recentSeenAt = fightAt;
            recent.addLast(com.teslamaps.slayer.SlayerHUD.lastFightMs());
            while (recent.size() > RECENT) recent.pollFirst();
        }

        ticks++;
        if (ticks % 20 == 0) {
            Preset prev = active;
            if (!SkyblockUtils.isInSkyblock()) active = null;
            else active = detect(SkyblockUtils.getArea(), subArea());
            if (active != prev) graceUntil = now + GRACE_MS;
            if (active != null) shown = TRACKERS.get(active.name);
        }
        boolean grace = open || now < graceUntil;
        long coins = 0;
        if (ticks % 10 == 0) {
            long p = readPurse();
            if (p >= 0) {
                long d = purse >= 0 ? p - purse : 0;
                if (d < 0 && questStartAt != 0 && now - questStartAt < 1500) {
                    pendingCost = -d;
                    pendingAt = now;
                    questStartAt = 0;
                } else if (d > 0 && !grace) {
                    coins = d;
                }
                purse = p;
            }
        }
        if (pendingCost > 0 && now - pendingAt > 10_000) pendingCost = 0;
        if (pendingCost > 0 && active != null && BOSSES.equals(active.action)) {
            if (cfg().profitTrackerSlayerCost) TRACKERS.get(active.name).add(QUEST_COST, pendingCost);
            pendingCost = 0;
        }
        if (active == null) {
            counts.clear();
            return;
        }
        Tracker t = TRACKERS.get(active.name);
        if (coins > 0 && cfg().profitTrackerCoins) t.addCoins(coins);

        if (!open && ticks % 4 == 0) {
            Map<String, Long> next = new HashMap<>();
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                if (i == 8) continue;
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                String id = ItemUtil.skyblockId(st);
                if (id.isEmpty()) continue;
                next.merge(id, (long) st.getCount(), Long::sum);
            }
            if (!grace) {
                for (var e : next.entrySet()) {
                    long d = e.getValue() - counts.getOrDefault(e.getKey(), 0L);
                    if (d > 0) gain(t, e.getKey(), d, now);
                    else if (d < 0) suppress(e.getKey(), -d, now + DROP_SUPPRESS_MS);
                }
                for (var e : counts.entrySet()) {
                    if (!next.containsKey(e.getKey())) suppress(e.getKey(), e.getValue(), now + DROP_SUPPRESS_MS);
                }
            }
            counts = next;
        }

        if (suppress.size() > 64) suppress.values().removeIf(s -> s[1] < now);
    }

    private static Object ownHook;
    private static long reelAt, catchSoundAt;

    /** SoundPlayMixin HEAD, main thread. */
    public static void onSound(net.minecraft.client.resources.sounds.SoundInstance s) {
        TeslaMapsConfig c = cfg();
        if (!c.profitTracker || !c.profitTrackerFishingCatches || ownHook == null && System.currentTimeMillis() - reelAt > 600) return;
        var id = s.getIdentifier();
        if (id != null && "entity.experience_orb.pickup".equals(id.getPath())) catchSoundAt = System.currentTimeMillis();
    }

    private static void trackCatch(Minecraft mc, long now) {
        if (!cfg().profitTrackerFishingCatches) return;
        Object hook = mc.player.fishing;
        if (ownHook != null && hook != ownHook) reelAt = now;
        ownHook = hook;
        if (reelAt == 0 || catchSoundAt == 0 || Math.abs(reelAt - catchSoundAt) > 600) return;
        reelAt = 0;
        catchSoundAt = 0;
        Tracker t = TRACKERS.get("Fishing");
        if (t != null) t.add(CATCHES, 1);
    }

    private static void gain(Tracker t, String id, long amount, long now) {
        long[] s = suppress.get(id);
        if (s != null) {
            if (s[1] < now) {
                suppress.remove(id);
            } else {
                long used = Math.min(s[0], amount);
                s[0] -= used;
                amount -= used;
                if (s[0] <= 0) suppress.remove(id);
            }
        }
        if (amount <= 0 || !wanted(id)) return;
        t.add(id, amount);
    }

    private static boolean wanted(String id) {
        if (!cfg().profitTrackerPresetOnly || active == null) return true;
        return active.items.contains(id) || (id.startsWith("ENCHANTED_") && active.items.contains(id.substring(10)));
    }

    private static void suppress(String id, long amount, long until) {
        long[] s = suppress.get(id);
        if (s == null || s[1] < System.currentTimeMillis()) suppress.put(id, new long[]{amount, until});
        else { s[0] += amount; s[1] = Math.max(s[1], until); }
    }

    private static String subArea() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String t = ScoreboardUtils.cleanLine(line);
            if (t.startsWith("⏣") || t.startsWith("ф")) return t.substring(1).trim();
        }
        return "";
    }

    private static long readPurse() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String t = ScoreboardUtils.cleanLine(line);
            if (!t.startsWith("Purse") && !t.startsWith("Piggy")) continue;
            Matcher m = PURSE.matcher(t);
            if (!m.find()) continue;
            try { return (long) Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return -1; }
        }
        return -1;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals("SLAYER QUEST STARTED!")) {
            questStartAt = System.currentTimeMillis();
        } else if (t.equals("NICE! SLAYER BOSS SLAIN!")) {
            if (active != null && BOSSES.equals(active.action)) {
                TRACKERS.get(active.name).add(BOSSES, 1);
                slayerKill(active.key);
            }
        } else if (rngDrop(t)) {
            if (cfg().profitTrackerSlayerRng && active != null && BOSSES.equals(active.action)) {
                lastRngAt = System.currentTimeMillis();
                cfg().slayerBossesSinceRng.put(active.key, 0);
                cfg().slayerLastRngMs.put(active.key, lastRngAt);
                TeslaMapsConfig.save();
            }
        } else if (t.startsWith("You dug out a Griffin Burrow") || t.startsWith("You finished the Griffin burrow chain")) {
            burrowAt = System.currentTimeMillis();
            Tracker d = TRACKERS.get("Diana");
            if (d != null && (active == null || !BOSSES.equals(active.action))) d.add(BURROWS, 1);
        }
    }

    /** From ChatMixin, server lines only: the [Sacks] hover lists what went into the sacks. */
    public static void onChatComponent(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.profitTracker || !c.profitTrackerSacks || active == null) return;
        if (!message.getString().contains("[Sacks]")) return;
        StringBuilder sb = new StringBuilder();
        collect(message, sb);
        String text = sb.toString();
        long now = System.currentTimeMillis();
        if (text.isBlank() || (text.equals(lastHover) && now - lastHoverAt < 500)) return;
        lastHover = text;
        lastHoverAt = now;
        Tracker t = TRACKERS.get(active.name);
        for (String line : text.split("\n")) {
            Matcher m = SACK_CHANGE.matcher(StringUtil.stripTrim(line));
            while (m.find()) {
                long d;
                try { d = Long.parseLong(m.group(1).replace(",", "").replace("+", "")); } catch (NumberFormatException e) { continue; }
                String name = m.group(2).trim();
                if (name.contains("other items")) continue;
                String id = PriceManager.idForName(name);
                if (id == null) continue;
                // taken out of a sack = lands in the inventory, not a drop
                if (d < 0) suppress(id, -d, now + SACK_SUPPRESS_MS);
                else if (!screenOpen) gain(t, id, d, now);
            }
        }
    }

    private static void collect(Component c, StringBuilder out) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            String t = h.trim();
            if (t.startsWith("Added") || t.startsWith("Removed")) out.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collect(s, out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (active == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TRACKERS.get(active.name).draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
