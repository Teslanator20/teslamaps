package com.teslamaps.features;

// Ported from SkyHanni's DungeonRankTabListColor.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonTabColors {

    private static final Pattern TEAM = Pattern.compile(
            "^(\\[\\d+]) (\\[[^]]+])? ?(\\S+)\\s?[^(]* \\((?:(\\S+) ([CLXVI0]+)|DEAD)\\)$");
    private static final Component NONE = Component.empty();
    private static final Map<Component, Component> cache = new IdentityHashMap<>();
    private static Object lastLevel;
    private static boolean active;

    private DungeonTabColors() {}

    public static void register() {
        Feature.of("DungeonTabColors").section("Party").when(() -> TeslaMapsConfig.get().dungeonTabClassColor)
                .tick(DungeonTabColors::tick)
                .config("Party", b -> b.toggle("Class Level Colors in Tab (dungeon)",
                                () -> TeslaMapsConfig.get().dungeonTabClassColor, v -> TeslaMapsConfig.get().dungeonTabClassColor = v)
                        .desc("Rewrites teammate tab lines to level, rank, name and class with the class level colored by tier."));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            cache.clear();
        }
        active = DungeonManager.isInDungeon();
    }

    public static Component rewrite(PlayerInfo info, Component shown) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!active || !c.dungeonTabClassColor || !c.section("Party") || info == null) return shown;
        Component src = info.getTabListDisplayName();
        if (src == null) return shown;
        Component hit = cache.get(src);
        if (hit == null) {
            if (cache.size() > 256) cache.clear();
            hit = build(src.getString());
            cache.put(src, hit);
        }
        return hit == NONE ? shown : hit;
    }

    private static Component build(String raw) {
        Matcher m = TEAM.matcher(com.teslamaps.utils.StringUtil.stripTrim(raw));
        if (!m.matches() || m.group(4) == null) return NONE;
        String rank = m.group(2) == null ? "" : m.group(2) + " ";
        String name = m.group(3);
        int level = level(m.group(5));
        String rankColor = rankColor(raw);
        return Component.literal("§8" + m.group(1) + " " + rankColor + rank + name
                + " §f(§d" + m.group(4) + " " + color(level) + m.group(5) + "§f)");
    }

    private static String rankColor(String raw) {
        int b = raw.indexOf('[', raw.indexOf(']') + 1);
        if (b > 1 && raw.charAt(b - 2) == '§') return "§" + raw.charAt(b - 1);
        return "§7";
    }

    private static int level(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
        }
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> 0;
            };
            if (v < prev) total -= v;
            else {
                total += v;
                prev = v;
            }
        }
        return total;
    }

    private static String color(int level) {
        if (level >= 50) return "§c§l";
        if (level >= 45) return "§c";
        if (level >= 40) return "§6";
        if (level >= 35) return "§d";
        if (level >= 30) return "§9";
        if (level >= 25) return "§b";
        if (level >= 20) return "§2";
        if (level >= 15) return "§a";
        if (level >= 10) return "§e";
        if (level >= 5) return "§f";
        return "§7";
    }
}
