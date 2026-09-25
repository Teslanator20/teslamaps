package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

// Ported from Devonian's HideNoStarTag: hides "[Lv9] Zombie 1.2k❤" style tags without a ✯
public class NonStarredNametags {

    private static final Pattern TAG = Pattern.compile("^(?:\\[Lv\\d+] )?[^\\sA-Za-z]* ?([A-Za-z .]+) [\\dkMB.,/]+❤$");

    private static final Set<String> BLOOD = Set.of(
            "Putrid", "Reaper", "Vader", "Frost", "Cannibal", "Revoker", "Tear", "Mr. Dead", "Skull",
            "Walker", "Psycho", "Ooze", "Freak", "Flamer", "Mute", "Leech", "Parasite",
            "Bonzo", "Scarf", "Spirit Bear", "Livid",
            "L.A.S.R.", "The Diamond Giant", "Jolly Pink Giant", "Bigfoot");

    private static final Set<String> EXCEPTIONS = Set.of(
            "Mimic", "Crypt Undead",
            "Prince Alexander", "Prince Bernhard", "Prince Christian", "Prince Friedrich",
            "Prince Marius", "Prince Nicholas", "Prince Pieter", "Prince Valentin",
            "Blaze", "King Midas", "Deathmite",
            "Akia", "Ilene", "Kari", "Lelani", "Steve", "Synestra", "Tyene", "Ussaea", "Yve", "Zana", "Trisha", "Nymira");

    private static final IntOpenHashSet hidden = new IntOpenHashSet();
    private static int ticks;

    public static void register() {
        Feature.of("NonStarredNametags").section("ESP")
                .when(() -> TeslaMapsConfig.get().hideNonStarredNametags)
                .tick(NonStarredNametags::tick);
    }

    private static void tick() {
        if (ticks++ % 5 != 0) return;
        hidden.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !DungeonManager.isInDungeon()) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand) || !e.hasCustomName() || e.getCustomName() == null) continue;
            String name = StringUtil.stripTrim(e.getCustomName().getString());
            if (name.indexOf('❤') < 0 || name.indexOf('✯') >= 0) continue;
            if (hide(name)) hidden.add(e.getId());
        }
    }

    private static boolean hide(String name) {
        Matcher m = TAG.matcher(name);
        if (!m.matches()) return false;
        String mob = m.group(1).trim();
        if (EXCEPTIONS.contains(mob) || BLOOD.contains(mob)) return false;
        int sp = mob.indexOf(' ');
        return sp < 0 || !BLOOD.contains(mob.substring(sp + 1));
    }

    public static boolean shouldHide(Entity e) {
        if (hidden.isEmpty() || !(e instanceof ArmorStand) || !hidden.contains(e.getId())) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.hideNonStarredNametags && c.section("ESP");
    }
}
