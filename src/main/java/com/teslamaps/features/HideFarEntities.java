package com.teslamaps.features;

// Ported from SkyHanni's features/misc/HideFarEntities.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.mobvisuals.MobVisuals;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

import java.util.List;
import java.util.Locale;

/** Renders only the N nearest entities past a minimum distance. Important mobs are never hidden. */
public final class HideFarEntities {

    private static IntOpenHashSet hidden = new IntOpenHashSet();
    private static IntOpenHashSet neverHide = new IntOpenHashSet();
    private static int timer;
    private static int[] idx = new int[256];
    private static double[] dist = new double[256];
    private static Entity[] ents = new Entity[256];

    private HideFarEntities() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HideFarEntities").when(() -> cfg().hideFarEntities)
                .tick(HideFarEntities::tick)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Far Entities");
                    b.toggle("Hide Far Entities", () -> c.hideFarEntities, v -> {
                        c.hideFarEntities = v;
                        if (!v) hidden = new IntOpenHashSet();
                    }).desc("SkyBlock: only the nearest entities are drawn. Bosses, starred mobs, party members and highlighted mobs always show.")
                            .tags("fps", "performance", "entities");
                    b.slider("  Min Distance", 3f, 30f, () -> (float) c.hideFarEntitiesMinDistance,
                            v -> c.hideFarEntitiesMinDistance = Math.round(v), 0)
                            .desc("Everything this close always shows.");
                    b.slider("  Max Amount", 1f, 150f, () -> (float) c.hideFarEntitiesMaxAmount,
                            v -> c.hideFarEntitiesMaxAmount = Math.round(v), 0)
                            .desc("At most this many entities past the min distance, nearest first.");
                });
    }

    public static boolean shouldHide(Entity e) {
        return !hidden.isEmpty() && cfg().hideFarEntities && hidden.contains(e.getId());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock()) {
            if (!hidden.isEmpty()) hidden = new IntOpenHashSet();
            return;
        }
        List<Entity> all = EntityCache.all();
        if (timer++ % 20 == 0) neverHide = neverHide(mc, all);

        TeslaMapsConfig c = cfg();
        int max = Math.max(1, c.hideFarEntitiesMaxAmount);
        double min = Math.max(3, c.hideFarEntitiesMinDistance);
        double minSq = min * min;
        LocalPlayer self = mc.player;
        Entity vehicle = self.getVehicle();
        int n = 0;
        for (Entity e : all) {
            if (e == self || e == vehicle) continue;
            double d = e.distanceToSqr(self);
            if (d <= minSq || neverHide.contains(e.getId()) || important(e)) continue;
            if (n == ents.length) grow();
            ents[n] = e;
            dist[n] = d;
            idx[n] = n;
            n++;
        }
        IntOpenHashSet next = new IntOpenHashSet();
        if (n > max) {
            final double[] ds = dist;
            IntArrays.quickSort(idx, 0, n, (a, b) -> Double.compare(ds[a], ds[b]));
            for (int i = max; i < n; i++) next.add(ents[idx[i]].getId());
        }
        java.util.Arrays.fill(ents, 0, n, null);
        hidden = next;
    }

    private static boolean important(Entity e) {
        return EntityGlow.COLORS.containsKey(e) || MobVisuals.glowing.contains(e);
    }

    private static void grow() {
        int len = ents.length * 2;
        ents = java.util.Arrays.copyOf(ents, len);
        dist = java.util.Arrays.copyOf(dist, len);
        idx = java.util.Arrays.copyOf(idx, len);
    }

    private static IntOpenHashSet neverHide(Minecraft mc, List<Entity> all) {
        IntOpenHashSet out = new IntOpenHashSet();
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        boolean dungeon = DungeonManager.isInDungeon();
        boolean kuudra = area.contains("kuudra");
        boolean winter = area.contains("jerry");
        boolean mines = area.contains("dwarven");
        if (dungeon) for (StarredMobESP.EntityHighlight h : StarredMobESP.getHighlightedEntities()) out.add(h.entity().getId());
        for (Entity e : all) {
            boolean keep = e instanceof WitherBoss || e instanceof EnderDragon
                    || winter && e instanceof MagmaCube
                    || mines && (e instanceof Ghast || e instanceof IronGolem);
            if (!keep && e instanceof RemotePlayer p) {
                boolean real = p.getUUID().version() == 4;
                keep = real && (dungeon || kuudra) || PartyTracker.isMember(p.getName().getString());
            }
            if (!keep && dungeon && e.hasCustomName() && e.getCustomName() != null) {
                String name = e.getCustomName().getString();
                keep = name.contains("Mort");
            }
            if (keep) out.add(e.getId());
        }
        return out;
    }
}
