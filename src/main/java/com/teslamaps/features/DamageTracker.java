package com.teslamaps.features;

// Concept from Zen's DamageTracker.kt / DamageTrackerGui.kt / DamageType.kt
import com.mojang.brigadier.Command;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.DamageTrackerScreen;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DamageTracker {

    public enum Type {
        NORMAL("§7Normal"), CRIT("§eCrit"), OVERLOAD("§dOverload"), FIRE("§6Fire"), OTHER("§bOther");
        public final String label;
        Type(String label) { this.label = label; }
    }

    public record Hit(Type type, long damage, long at) {}

    private static final int MAX = 1000;
    private static final double RANGE_SQ = 3 * 3;
    private static final long HIT_WINDOW_MS = 2000;
    private static final Pattern SPLASH = Pattern.compile("^[✧✯]?([\\d,]+)[✧✯❤♞☄✷ﬗ⚔+]*$");

    public static final ArrayDeque<Hit> HITS = new ArrayDeque<>();

    private static final IntOpenHashSet seen = new IntOpenHashSet();
    private static Entity target;
    private static long hitAt;
    private static Object lastLevel;

    private DamageTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DamageTracker").section("Combat").when(() -> cfg().damageTracker)
                .tick(DamageTracker::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Damage Tracker");
                    b.toggle("Damage Tracker", () -> c.damageTracker, v -> c.damageTracker = v)
                            .desc("Records the damage splashes around the mob you last hit (up to 1000, by type). "
                                    + "/tmap damage opens total, max and average per type.")
                            .tags("damage", "tracker", "crit", "splash", "stats", "dps");
                    b.toggle("  Chat Line Per Hit", () -> c.damageTrackerChat, v -> c.damageTrackerChat = v);
                    b.button("  Open Damage Stats", () -> Minecraft.getInstance().setScreen(
                            new DamageTrackerScreen(Minecraft.getInstance().screen)));
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("damage")
                        .executes(c -> {
                            TickScheduler.schedule(1, () -> Minecraft.getInstance().setScreen(new DamageTrackerScreen(null)));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(ClientCommands.literal("reset").executes(c -> {
                            reset();
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fDamage tracker cleared"));
                            return Command.SINGLE_SUCCESS;
                        })))));
    }

    public static void reset() {
        HITS.clear();
    }

    /** ClientPlayerInteractionManager.attack. */
    public static void onAttack(Entity e) {
        if (!cfg().damageTracker) return;
        target = e;
        hitAt = System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
            target = null;
        }
        if (seen.size() > 4000) seen.clear();
        Entity t = target;
        boolean active = t != null && System.currentTimeMillis() - hitAt < HIT_WINDOW_MS && SkyblockUtils.isInSkyblock();
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            if (!seen.add(as.getId()) || !active || as.tickCount > 10) continue;
            if (as.distanceToSqr(t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ()) > RANGE_SQ) continue;
            String raw = as.getCustomName().getString();
            String clean = StringUtil.stripTrim(raw);
            Matcher m = SPLASH.matcher(clean);
            if (!m.matches()) continue;
            long dmg;
            try { dmg = Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException ex) { continue; }
            if (dmg <= 0) continue;
            Type type = type(raw, clean);
            if (HITS.size() >= MAX) HITS.pollFirst();
            HITS.addLast(new Hit(type, dmg, System.currentTimeMillis()));
            if (cfg().damageTrackerChat && mc.player != null)
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + type.label + "§7: §f" + String.format(java.util.Locale.ROOT, "%,d", dmg)));
        }
    }

    private static Type type(String raw, String clean) {
        if (clean.indexOf('✯') >= 0) return Type.OVERLOAD;
        if (clean.indexOf('✧') >= 0) return Type.CRIT;
        int i = raw.indexOf('§');
        char code = i >= 0 && i + 1 < raw.length() ? Character.toLowerCase(raw.charAt(i + 1)) : 'f';
        return switch (code) {
            case '7', 'f' -> Type.NORMAL;
            case '6' -> Type.FIRE;
            default -> Type.OTHER;
        };
    }
}
