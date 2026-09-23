package com.teslamaps.screen.config;

import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mob names around the player for the searchable Custom ESP list, refreshed once a second. */
final class NearbyMobs {
    private static final double RANGE = 48;
    private static final Set<String> seen = new LinkedHashSet<>();
    private static final Map<String, EntityType<?>> types = new HashMap<>();
    private static final Map<EntityType<?>, ItemStack> eggs = new HashMap<>();
    private static long lastScan = 0;

    private NearbyMobs() {}

    /** Configured entries first, then names seen nearby this session. */
    static List<String> options(List<String> configured) {
        scan();
        Set<String> out = new LinkedHashSet<>(configured);
        Set<String> lower = new java.util.HashSet<>();
        for (String s : configured) lower.add(s.toLowerCase());
        for (String s : seen) if (lower.add(s.toLowerCase())) out.add(s);
        return new ArrayList<>(out);
    }

    /** Remembers an entry so it stays in the list after being switched off. */
    static void remember(String name) {
        if (name != null && !name.isBlank()) seen.add(name);
    }

    static ItemStack icon(String name) {
        EntityType<?> t = types.get(name.toLowerCase());
        if (t == null) return ItemStack.EMPTY;
        return eggs.computeIfAbsent(t, k -> SpawnEggItem.byId(k).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    /** Name of the mob under the crosshair, or null. */
    static String looked() {
        Entity e = Minecraft.getInstance().crosshairPickEntity;
        if (e == null || e instanceof Player) return null;
        String n = nameOf(e);
        if (n != null) {
            types.putIfAbsent(n.toLowerCase(), e.getType());
            seen.add(n);
        }
        return n;
    }

    private static void scan() {
        long now = System.currentTimeMillis();
        if (now - lastScan < 1000) return;
        lastScan = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Player || e.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            if (!(e instanceof LivingEntity)) continue;
            String n = nameOf(e);
            if (n == null) continue;
            seen.add(n);
            if (!(e instanceof ArmorStand)) types.putIfAbsent(n.toLowerCase(), e.getType());
        }
    }

    /** "[Lv12] Zombie 50/100❤" -> "Zombie"; unnamed mobs give their type name. */
    private static String nameOf(Entity e) {
        if (e.getCustomName() != null) {
            String s = StringUtil.stripTrim(e.getCustomName().getString());
            if (s.startsWith("[")) {
                int close = s.indexOf("] ");
                if (close > 0) s = s.substring(close + 2);
            }
            StringBuilder sb = new StringBuilder();
            for (String w : s.split(" ")) {
                if (w.isEmpty()) continue;
                char c = w.charAt(0);
                if (Character.isDigit(c) || w.contains("❤")) break;
                if (!Character.isLetter(c)) {
                    if (sb.length() > 0) break;
                    continue;
                }
                if (sb.length() > 0) sb.append(' ');
                sb.append(w);
            }
            String n = sb.toString().trim();
            return n.length() >= 2 ? n : null;
        }
        if (e instanceof ArmorStand) return null;
        return e.getType().getDescription().getString();
    }
}
