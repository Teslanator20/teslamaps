package com.teslamaps.features.visitors;

// After SkyHanni's GardenVisitorStatus + GardenVisitorHighlight
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class VisitorStatusHighlight {

    private static int ticks;

    private VisitorStatusHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("VisitorStatusHighlight").section("Garden").when(() -> cfg().visitorStatusHighlight)
                .tick(VisitorStatusHighlight::tick)
                .world(VisitorStatusHighlight::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Visitor Status Highlight", () -> c.visitorStatusHighlight, v -> c.visitorStatusHighlight = v)
                            .desc("Visitors glow in their status colour: yellow new, green items ready (inventory + sacks), grey accepted, red refused.")
                            .tags("visitor", "ready", "glow", "npc");
                    b.toggle("  Status Text Above Head", () -> c.visitorStatusText, v -> c.visitorStatusText = v);
                    b.toggle("  Rare Rewards Above Head", () -> c.visitorStatusRare, v -> c.visitorStatusRare = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !com.teslamaps.utils.SkyblockUtils.isInGarden()) return;
        if (ticks++ % 20 == 0) link(mc);
        for (Visitors.Visitor v : Visitors.all()) {
            if (v.status.rgb < 0 || v.entityId < 0) continue;
            Entity e = mc.level.getEntity(v.entityId);
            if (e != null) EntityGlow.mark(e, v.status.rgb);
        }
    }

    private static void link(Minecraft mc) {
        boolean missing = false;
        for (Visitors.Visitor v : Visitors.all()) {
            Entity tag = v.tagId < 0 ? null : mc.level.getEntity(v.tagId);
            Entity npc = v.entityId < 0 ? null : mc.level.getEntity(v.entityId);
            if (tag == null || npc == null || !npc.isAlive()) {
                v.tagId = -1;
                v.entityId = -1;
                missing = true;
            }
        }
        if (!missing) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand stand) || !stand.hasCustomName() || e.distanceToSqr(mc.player) > 96 * 96) continue;
            Visitors.Visitor v = Visitors.byName(StringUtil.stripTrim(stand.getCustomName().getString()));
            if (v == null || v.entityId >= 0) continue;
            List<Entity> near = mc.level.getEntities(stand, stand.getBoundingBox().inflate(1.0, 3.0, 1.0),
                    o -> o instanceof LivingEntity && !(o instanceof ArmorStand) && o != mc.player);
            Entity best = null;
            double bestD = 0.5 * 0.5;
            for (Entity o : near) {
                double dx = o.getX() - stand.getX(), dz = o.getZ() - stand.getZ();
                double d = dx * dx + dz * dz;
                if (d <= bestD) {
                    bestD = d;
                    best = o;
                }
            }
            if (best != null) {
                v.tagId = stand.getId();
                v.entityId = best.getId();
            }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!c.visitorStatusText && !c.visitorStatusRare) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (Visitors.Visitor v : Visitors.all()) {
            if (v.entityId < 0) continue;
            Entity e = mc.level.getEntity(v.entityId);
            if (e == null || e.distanceToSqr(mc.player) > 15 * 15) continue;
            Vec3 at = new Vec3(e.getX(), e.getY() + 2.6, e.getZ());
            if (c.visitorStatusText) ESPRenderer.drawText(pose, v.status.label, at, 1f, cam, false, false);
            if (c.visitorStatusRare) {
                double y = 0.28;
                for (String r : Visitors.rareRewards(v)) {
                    ESPRenderer.drawText(pose, "§c§l! §9" + r + " §c§l!", at.add(0, y, 0), 1f, cam, false, false);
                    y += 0.28;
                }
            }
        }
    }
}
