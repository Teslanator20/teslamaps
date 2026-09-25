package com.teslamaps.features;

// Ported from Devonian's BoxIcedMobs.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IcedMobs {

    private static final double FAR = 8.0;
    private static final double COS_HALF_FOV = Math.cos(Math.toRadians(45));
    private static final int DURATION = 100;

    private static final Map<LivingEntity, Long> frozen = new HashMap<>();
    private static final Set<Integer> seenItems = new HashSet<>();
    private static List<LivingEntity> drawList = List.of();
    private static long lastHeldSpray = -100;
    private static Object lastLevel;

    private IcedMobs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("IcedMobs").section("Mob Visuals").when(() -> cfg().icedMobs)
                .tick(IcedMobs::tick)
                .world(IcedMobs::render)
                .config("Mob Visuals", b -> {
                    b.label("Ice Spray");
                    b.toggle("Box Ice Sprayed Mobs", () -> cfg().icedMobs, v -> cfg().icedMobs = v)
                            .desc("Mobs in front of an Ice Spray user count as frozen for 5s and get a box. Client-side guess, affected by lag.");
                    b.color("  Outline Color", () -> cfg().icedMobsOutline, v -> cfg().icedMobsOutline = v);
                    b.color("  Fill Color", () -> cfg().icedMobsFill, v -> cfg().icedMobsFill = v);
                    b.slider("  Line Width", 1f, 8f, () -> cfg().icedMobsLineWidth, v -> cfg().icedMobsLineWidth = v);
                });
    }

    private static boolean isSpray(ItemStack s) {
        String id = ItemUtil.skyblockId(s);
        return "ICE_SPRAY_WAND".equals(id) || "STARRED_ICE_SPRAY_WAND".equals(id);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel || !DungeonManager.isInDungeon()) {
            lastLevel = mc.level;
            frozen.clear();
            seenItems.clear();
            drawList = List.of();
            if (!DungeonManager.isInDungeon()) return;
        }
        if (mc.player == null || mc.level == null) return;
        long now = ServerTickTimer.now();
        if (isSpray(mc.player.getMainHandItem())) lastHeldSpray = now;

        boolean newIce = false;
        List<Entity> all = EntityCache.all();
        for (Entity e : all) {
            if (!(e instanceof ItemEntity it) || it.tickCount > 5) continue;
            if (!seenItems.add(it.getId())) continue;
            if (it.getItem().is(Items.ICE)) newIce = true;
        }
        if (seenItems.size() > 2000) seenItems.clear();

        if (newIce) {
            List<Player> icers = new ArrayList<>();
            for (Player p : mc.level.players()) {
                if (p != mc.player && isSpray(p.getMainHandItem())) icers.add(p);
            }
            if (now - lastHeldSpray < 5) icers.add(mc.player);
            for (Player p : icers) freezeCone(p, all, now);
        }

        frozen.entrySet().removeIf(en -> now >= en.getValue() || en.getKey().isRemoved() || en.getKey().isDeadOrDying());
        drawList = frozen.isEmpty() ? List.of() : new ArrayList<>(frozen.keySet());
    }

    private static void freezeCone(Player p, List<Entity> all, long now) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        for (Entity e : all) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || e instanceof Player) continue;
            AABB bb = e.getBoundingBox();
            double cx = Math.clamp(eye.x, bb.minX, bb.maxX);
            double cy = Math.clamp(eye.y, bb.minY, bb.maxY);
            double cz = Math.clamp(eye.z, bb.minZ, bb.maxZ);
            double dx = cx - eye.x, dy = cy - eye.y, dz = cz - eye.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > FAR * FAR) continue;
            if (d2 > 1e-6) {
                Vec3 c = bb.getCenter().subtract(eye);
                double len = c.length();
                if (len > 1e-6 && c.dot(look) / len < COS_HALF_FOV
                        && (dx * look.x + dy * look.y + dz * look.z) / Math.sqrt(d2) < COS_HALF_FOV) continue;
            }
            frozen.put(le, now + DURATION);
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<LivingEntity> list = drawList;
        if (list.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        int outline = TeslaMapsConfig.parseColor(c.icedMobsOutline) | 0xFF000000;
        int fill = TeslaMapsConfig.parseColor(c.icedMobsFill);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (LivingEntity e : list) {
            if (e.isRemoved() || e.isDeadOrDying()) continue;
            Vec3 p = e.getPosition(pt);
            AABB box = e.getBoundingBox().move(p.subtract(e.position())).inflate(0.1);
            ESPRenderer.drawFilledBox(pose, box, fill, cam, false);
            ESPRenderer.drawBoxOutline(pose, box, outline, c.icedMobsLineWidth, cam, false);
        }
    }
}
