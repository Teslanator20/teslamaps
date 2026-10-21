// Ported from ADM's p3sim/clear/mobs/LevelLooks.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobSkin;
import com.teslamaps.adm.sim.pack.mobs.MobTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LevelLooks {
    private LevelLooks() {}

    public static final int BASE = 101_000;
    public static final String PREFIX = "lv:";

    private static final class Look {
        final String id, name, entity;
        final int level;
        final long hp;
        final String icons;
        final double dy;
        final String skin, hand;

        Look(String id, String name, String entity, int level, long hp, String icons, double dy, String skin, String hand) {
            this.id = id; this.name = name; this.entity = entity; this.level = level; this.hp = hp; this.icons = icons; this.dy = dy; this.skin = skin; this.hand = hand;
        }
    }

    private static final List<Look> LOOKS = List.of(
            new Look("undead_skeleton", "Undead Skeleton", "minecraft:skeleton", 173, 360_000, "§2§r§f", 2.1875, null, "minecraft:bow"),
            new Look("skeletor_prime", "Skeletor Prime", "minecraft:player", 277, 3_100_000, "§f", 2.125, "1bd3a4f8e22db15e486acac5d456ff35f34ed9ab5c64eec057afd2866d559401", "minecraft:bone")
    );

    private static final List<MobArchetype> archetypes = build();

    private static List<MobArchetype> build() {
        List<MobArchetype> out = new ArrayList<>();
        for (int i = 0; i < LOOKS.size(); i++) {
            Look l = LOOKS.get(i);
            out.add(new MobArchetype(
                    BASE + i, PREFIX + l.id, l.name, "clear",
                    l.entity, l.entity.equals("minecraft:player"),
                    l.skin != null ? new MobSkin(l.skin, BloodLooks.skinValue(l.skin, false), null, false) : null,
                    null, null, Map.of("F7", l.hp), false,
                    new MobTag(1, l.dy, "", "§c", false),
                    "static", null, Map.of(), Map.of()
            ));
        }
        return out;
    }

    public static List<MobArchetype> all() { return archetypes; }

    public static MobArchetype archetype(int index) {
        int i = index - BASE;
        return i >= 0 && i < archetypes.size() ? archetypes.get(i) : null;
    }

    public static boolean isLevel(MobArchetype arch) { return arch.id.startsWith(PREFIX); }

    private static Look look(MobArchetype arch) {
        int i = arch.index - BASE;
        return i >= 0 && i < LOOKS.size() ? LOOKS.get(i) : null;
    }

    public static String hand(MobArchetype arch) {
        Look l = look(arch);
        return l != null ? l.hand : null;
    }

    public static String tag(MobArchetype arch, double hp, double maxHp) {
        Look l = look(arch);
        if (l == null) return arch.name;
        String colour = hp * 2 < maxHp ? "e" : "a";
        return "§8[§r§7Lv" + l.level + "§r§8] §r" + l.icons + " §r§c" + l.name + "§r §" + colour + MobTags.formatHp(hp) + "§r§f/§r§a" + MobTags.formatHp(maxHp) + "§r§c❤";
    }
}
