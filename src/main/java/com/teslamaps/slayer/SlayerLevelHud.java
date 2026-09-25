package com.teslamaps.slayer;

// Ported from Skyblocker's SlayerHudWidget / SlayerManager (XP_NEEDED_PATTERN, LVL_UP_PATTERN, SlayerType tables)
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerLevelHud {

    private static final Pattern XP_NEEDED = Pattern.compile("^(Wolf|Zombie|Spider|Enderman|Blaze|Vampire) Slayer LVL (\\d) - (?:Next LVL in ([\\d,]+) XP!|LVL MAXED OUT!)$");
    private static final Pattern LVL_UP = Pattern.compile("^LVL UP! ➜ (Wolf|Zombie|Spider|Enderman|Blaze|Vampire) Slayer LVL (\\d)$");
    private static final int[] XP_PER_TIER = {5, 25, 100, 500, 1500};
    private static final int[] VAMPIRE_XP = {10, 25, 60, 120, 150};
    private static final int[] ZOMBIE_MILESTONES = {5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final int[] SPIDER_MILESTONES = {5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final int[] OTHER_MILESTONES = {10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000};
    private static final int[] VAMPIRE_MILESTONES = {20, 75, 240, 840, 2400};
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    private static final List<String> SAMPLE = List.of("§c§lRevenant Horror IV", " §7Level: §e7",
            " §7XP: §e82,500§7/§a100,000", " §7Level up in §d36 §7bosses");

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<HashMap<String, int[]>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("slayer_levels.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("slayer_levels.json"));

    // type -> {level, xpRemaining (-1 unknown / maxed)}
    private static Map<String, int[]> levels = new HashMap<>();
    private static boolean loaded;
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;

    private SlayerLevelHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("slayerLevelHud", "Slayer Level").section("Combat")
                .enabled(() -> cfg().slayerLevelHud)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("SlayerLevelHud").section("Combat").when(() -> cfg().slayerLevelHud)
                .tick(SlayerLevelHud::tick)
                .serverChat(SlayerLevelHud::onChat)
                .hudHiddenInInventory("slayerlevelhud", SlayerLevelHud::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Slayer Level HUD", () -> c.slayerLevelHud, v -> c.slayerLevelHud = v)
                            .desc("During a quest: slayer level, XP to the next level and bosses left until level up (Aura / Diaz buffs included). "
                                    + "Learns the numbers from the \"Next LVL in\" line after a kill, per profile. Position in /tmap gui")
                            .tags("slayer", "level", "xp", "bosses");
                    b.toggle("  Show Kill Times", () -> c.slayerLevelHudKillTimes, v -> c.slayerLevelHudKillTimes = v);
                });
    }

    private static String type(String word) {
        return switch (word) {
            case "Zombie" -> "REVENANT";
            case "Spider" -> "TARANTULA";
            case "Wolf" -> "SVEN";
            case "Enderman" -> "VOIDGLOOM";
            case "Blaze" -> "DEMONLORD";
            default -> "VAMPIRE";
        };
    }

    private static int[] milestones(String type) {
        return switch (type) {
            case "REVENANT" -> ZOMBIE_MILESTONES;
            case "TARANTULA" -> SPIDER_MILESTONES;
            case "VAMPIRE" -> VAMPIRE_MILESTONES;
            default -> OTHER_MILESTONES;
        };
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.contains("Slayer LVL")) return;
        Matcher m = XP_NEEDED.matcher(t);
        if (m.matches()) {
            ensure();
            int level = Integer.parseInt(m.group(2));
            int remaining = m.group(3) == null ? -1 : Integer.parseInt(m.group(3).replace(",", ""));
            levels.put(type(m.group(1)), new int[]{level, remaining});
            save();
            return;
        }
        m = LVL_UP.matcher(t);
        if (m.matches()) {
            ensure();
            levels.put(type(m.group(1)), new int[]{Integer.parseInt(m.group(2)), -1});
            save();
        }
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        String boss = SlayerQuest.boss();
        String key = SlayerQuest.key();
        if (boss == null || key == null) {
            lines = List.of();
            return;
        }
        ensure();
        String type = key.substring(0, key.indexOf('_'));
        int tier = SlayerQuest.tier();
        List<String> l = new ArrayList<>(6);
        l.add("§c§l" + boss + (tier > 0 && tier < ROMAN.length ? " " + ROMAN[tier] : ""));
        int[] data = levels.get(type);
        int[] ms = milestones(type);
        if (data == null) {
            l.add(" §8kill a boss to learn your level");
        } else {
            int level = data[0];
            l.add(" §7Level: §e" + level);
            if (level >= ms.length) {
                l.add(" §aLVL MAXED OUT!");
            } else if (data[1] < 0) {
                l.add(" §8XP updates after the next kill");
            } else {
                int next = ms[level];
                l.add(" §7XP: §e" + fmt(Math.max(0, next - data[1])) + "§7/§a" + fmt(next));
                int[] xpTable = type.equals("VAMPIRE") ? VAMPIRE_XP : XP_PER_TIER;
                if (tier > 0 && tier <= xpTable.length) {
                    double buff = 1.0;
                    if (ElectionApi.hasPerk("Work Smarter")) buff *= 1.5;
                    if (ElectionApi.hasPerk("Slayer XP Buff")) buff *= 1.25;
                    int per = (int) (xpTable[tier - 1] * buff);
                    if (per > 0) l.add(" §7Level up in §d" + fmt((long) Math.ceil((double) data[1] / per)) + " §7bosses");
                }
            }
        }
        if (cfg().slayerLevelHudKillTimes) {
            long last = SlayerHUD.lastFightMs(), avg = SlayerHUD.avgFightMs();
            if (last > 0) l.add(" §7Kill: §e" + secs(last) + " §7avg §e" + secs(avg));
        }
        lines = l;
        width = TextHud.width(l);
    }

    private static String secs(long ms) {
        return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    private static String fmt(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        levels = new HashMap<>();
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                Map<String, int[]> read = GSON.fromJson(Files.readString(p), TYPE);
                if (read != null) {
                    read.values().removeIf(v -> v == null || v.length < 2);
                    levels = read;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to load slayer levels: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(levels, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save slayer levels: {}", e.toString());
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
