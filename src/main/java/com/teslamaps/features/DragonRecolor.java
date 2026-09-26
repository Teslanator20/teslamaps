package com.teslamaps.features;

// Recolor + hurt flash from Devonian's RecolorDragons.kt (overlay texture trick),
// perch warning and boss bar hiding after SkyblockExtras' settings
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.DragonTintHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonRecolor {

    private static final int[] TINTS = DragonTintHolder.TINTS;
    private static final int TINT_U = DragonTintHolder.TINT_U;
    private static final int TINT_V = DragonTintHolder.TINT_V;

    private static final String[] BAR_NAMES = {"Power", "Flame", "Apex", "Ice", "Soul"};

    private static final Map<Integer, Integer> types = new ConcurrentHashMap<>();
    private static final Map<Integer, double[]> perch = new HashMap<>();
    private static final List<LerpingBossEvent> bars = new ArrayList<>();
    private static volatile boolean p5;
    private static Object lastLevel;

    private DragonRecolor() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DragonRecolor").when(() -> cfg().dragonRecolor || cfg().dragonHideHurt || cfg().dragonPerchWarning || cfg().dragonHideBossbars)
                .tick(DragonRecolor::tick);
    }

    private static boolean inP5() {
        Minecraft mc = Minecraft.getInstance();
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return mc.player != null && DungeonManager.isInDungeon() && f != null && f.getLevel() == 7 && mc.player.getY() <= 45;
    }

    /** Spawn packet, any thread: type = nearest dragon spawn. */
    public static void onAddEntity(ClientboundAddEntityPacket packet) {
        if (packet.getType() != EntityType.ENDER_DRAGON || !cfg().dragonRecolor) return;
        int best = 0;
        double bestD = Double.MAX_VALUE;
        WitherDragons.Dragon[] all = WitherDragons.Dragon.values();
        for (int i = 0; i < all.length; i++) {
            var p = all[i].spawnPos;
            double dx = p.getX() - packet.getX(), dy = p.getY() - packet.getY(), dz = p.getZ() - packet.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = i; }
        }
        types.put(packet.getId(), best);
    }

    /** Render-state extraction: overlay to use instead of vanilla, -1 = keep. */
    public static int overlayFor(EnderDragon dragon) {
        if (!p5) return -1;
        TeslaMapsConfig c = cfg();
        if (c.dragonRecolor) {
            Integer t = types.get(dragon.getId());
            if (t != null && t < TINTS.length) {
                com.teslamaps.render.DragonTintTexture.ensure();
                return OverlayTexture.pack(TINT_U + t, TINT_V);
            }
        }
        if (c.dragonHideHurt) return OverlayTexture.NO_OVERLAY;
        return -1;
    }

    /** Boss bar list for this frame: drops the bars of dragons other than the priority one. */
    public static Collection<LerpingBossEvent> filterBars(Collection<LerpingBossEvent> in) {
        WitherDragons.Dragon prio = WitherDragons.priorityDragon;
        if (!p5 || prio == null || !cfg().dragonHideBossbars) return in;
        String keep = BAR_NAMES[prio.ordinal()];
        bars.clear();
        for (LerpingBossEvent ev : in) {
            String name = ev.getName() == null ? "" : ev.getName().getString();
            if (name.contains("Dragon") && !name.contains(keep)) continue;
            bars.add(ev);
        }
        return bars;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            types.clear();
            perch.clear();
        }
        p5 = inP5();
        if (!p5 || !cfg().dragonPerchWarning) {
            perch.clear();
            return;
        }
        for (EnderDragon d : com.teslamaps.utils.EntityCache.byType(EnderDragon.class)) {
            if (d.getHealth() <= 0 || d.isRemoved()) continue;
            // x, z, still ticks, warned, has moved (a fresh spawn hovering is no perch)
            double[] s = perch.computeIfAbsent(d.getId(), k -> new double[]{d.getX(), d.getZ(), 0, 0, 0});
            double dx = d.getX() - s[0], dz = d.getZ() - s[1];
            if (dx * dx + dz * dz > 0.25) {
                s[0] = d.getX();
                s[1] = d.getZ();
                s[2] = 0;
                s[3] = 0;
                s[4] = 1;
                continue;
            }
            if (s[4] == 0 || ++s[2] < 20 || s[3] != 0) continue;
            s[3] = 1;
            Integer t = types.get(d.getId());
            WitherDragons.Dragon type = t != null ? WitherDragons.Dragon.values()[t] : nearest(d);
            mc.gui.setTimes(0, 30, 5);
            mc.gui.setTitle(Component.literal("§" + type.colorCode + type.dispName + " §fperched!"));
            if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        }
        perch.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static WitherDragons.Dragon nearest(EnderDragon d) {
        WitherDragons.Dragon best = WitherDragons.Dragon.RED;
        double bestD = Double.MAX_VALUE;
        for (WitherDragons.Dragon dr : WitherDragons.Dragon.values()) {
            double dx = dr.spawnPos.getX() - d.getX(), dz = dr.spawnPos.getZ() - d.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestD) { bestD = dist; best = dr; }
        }
        return best;
    }
}
