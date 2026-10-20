package com.teslamaps.adm.leap;

import com.teslamaps.adm.Adm;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** World hooks shared by the leap area: block/entity events, world change, deaths, Witherborn withers. */
public final class AdmWorld {

    private AdmWorld() {}

    private static ClientLevel lastLevel;

    // Ported from ADM's DeadPlayers.kt
    private static final Pattern DIED = Pattern.compile("^ ☠ (\\w{1,16}) (?:.*became a ghost\\.|was killed by .*)$");
    private static final Pattern REVIVED = Pattern.compile("^ ❣ (\\w{1,16}) was revived\\b.*");
    private static final Pattern SELF_REVIVED = Pattern.compile("^(?:Your .* revived you\\b.*| ❣ You were revived\\b.*)");
    private static final Set<String> dead = new HashSet<>();

    // Ported from ADM's misc/Witherborn.kt
    private static final double NEAR = 4.5;
    private static final Set<Integer> minions = new HashSet<>();
    private static final Set<Integer> seen = new HashSet<>();

    /** Client tick: world change resets everything that keys off one world. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == lastLevel) return;
        lastLevel = level;
        dead.clear();
        minions.clear();
        seen.clear();
        AdmLeap.onWorldChange();
    }

    public static void onChat(String raw) {
        String line = StringUtil.strip(raw);
        if (!line.startsWith(" ☠ ") && !line.startsWith(" ❣ ") && !line.startsWith("Your ")) return;
        var p = Minecraft.getInstance().player;
        String me = p == null ? null : p.getGameProfile().name().toLowerCase(Locale.ROOT);
        Matcher m = DIED.matcher(line);
        if (m.find()) {
            String n = m.group(1).equals("You") ? me : m.group(1).toLowerCase(Locale.ROOT);
            if (n != null) dead.add(n);
            return;
        }
        m = REVIVED.matcher(line);
        if (m.find()) {
            String n = m.group(1).equals("You") ? me : m.group(1).toLowerCase(Locale.ROOT);
            if (n != null) dead.remove(n);
            return;
        }
        if (SELF_REVIVED.matcher(line).matches() && me != null) dead.remove(me);
    }

    public static boolean isDead(String name) {
        if (name == null) return false;
        if (dead.contains(name.toLowerCase(Locale.ROOT))) return true;
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equalsIgnoreCase(name) && !dp.isAlive()) return true;
        }
        return false;
    }

    public static boolean isMinion(Entity e) {
        return minions.contains(e.getId()) || (e instanceof WitherBoss w && w.getScale() < 0.9f);
    }

    public static boolean isBossWither(Entity e) {
        return e instanceof WitherBoss && !isMinion(e);
    }

    /** Mixin: ClientLevel.addEntity. */
    public static void onEntityAdd(Entity e) {
        if (!Adm.on()) return;
        if (e instanceof WitherBoss && seen.add(e.getId())) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                for (Player p : level.players()) {
                    if (p.distanceToSqr(e.getX(), e.getY(), e.getZ()) <= NEAR * NEAR) { minions.add(e.getId()); break; }
                }
            }
        }
        com.teslamaps.adm.leap.splits.DungeonSplits.onEntityAdd(e);
    }

    /** Mixin: ClientLevel.removeEntity, before it goes. */
    public static void onEntityRemove(Entity e) {
        if (!Adm.on()) return;
        com.teslamaps.adm.leap.splits.DungeonSplits.onEntityRemove(e);
    }

    /** Mixin: Level.setBlock on the client. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState now) {
        if (!Adm.on()) return;
        com.teslamaps.adm.leap.splits.DungeonSplits.onBlockUpdate(pos, old, now);
    }
}
