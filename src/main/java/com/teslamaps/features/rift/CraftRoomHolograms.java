package com.teslamaps.features.rift;

// Ported from SkyHanni's CraftRoomHolographicMob.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityAlphaHolder;
import com.teslamaps.utils.HypixelModApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class CraftRoomHolograms {

    private static final AABB ROOM = new AABB(-117, 51, -128, -108, 58, -106);
    private static final double WALL_Z = -116.5;

    private record Holo(LivingEntity mob, String label) {}

    private static List<Holo> holos = List.of();
    private static boolean inRoom;
    private static int ticks;

    private CraftRoomHolograms() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CraftRoomHolograms").section("Rift").when(() -> cfg().craftRoomHolograms)
                .tick(CraftRoomHolograms::tick)
                .world(CraftRoomHolograms::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Crafting Room Holograms", () -> c.craftRoomHolograms, v -> c.craftRoomHolograms = v)
                            .desc("In the Mirrorverse crafting room: mirrors the mobs from behind the wall onto your side as holograms.");
                    b.toggle("  Show Name", () -> c.craftRoomHoloName, v -> c.craftRoomHoloName = v);
                    b.toggle("  Show Health", () -> c.craftRoomHoloHealth, v -> c.craftRoomHoloHealth = v);
                    b.toggle("  Hide Other Players", () -> c.craftRoomHidePlayers, v -> c.craftRoomHidePlayers = v)
                            .desc("Hides other players while you are in the room.");
                });
    }

    static boolean inRift() {
        String mode = HypixelModApi.mode();
        if (mode != null) return mode.equalsIgnoreCase("rift");
        return RiftSpot.inRift();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 10 == 0) inRoom = inRift() && ROOM.contains(mc.player.position());
        if (!inRoom) {
            holos = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        List<Holo> out = new ArrayList<>();
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, ROOM,
                e -> e instanceof Zombie || e instanceof Slime || e instanceof CaveSpider)) {
            if (!e.isAlive() || e.getZ() > WALL_Z) continue;
            String label = null;
            if (c.craftRoomHoloName || c.craftRoomHoloHealth) {
                StringBuilder sb = new StringBuilder();
                if (c.craftRoomHoloName) sb.append("§a").append(e.getDisplayName().getString()).append(' ');
                if (c.craftRoomHoloHealth) sb.append("§c").append(Math.round(e.getHealth() * 10) / 10f).append("♥");
                label = sb.toString().trim();
            }
            out.add(new Holo(e, label));
        }
        holos = out;
    }

    private static double mirror(double z) {
        return z + (WALL_Z - z) * 2;
    }

    /** From PetDragonMixin, end of the entity extraction. */
    public static void extract(LevelRenderState level, float pt) {
        if (holos.isEmpty() || !cfg().craftRoomHolograms || !cfg().section("Rift")) return;
        Minecraft mc = Minecraft.getInstance();
        for (Holo h : holos) {
            EntityRenderState s;
            try {
                s = mc.getEntityRenderDispatcher().extractEntity(h.mob, pt);
            } catch (Throwable t) {
                continue;
            }
            s.z = mirror(s.z);
            s.nameTag = null;
            s.scoreText = null;
            s.outlineColor = EntityRenderState.NO_OUTLINE;
            s.shadowPieces.clear();
            if (s instanceof LivingEntityRenderState ls) {
                ls.bodyRot = 180f - ls.bodyRot;
                ls.yRot = -ls.yRot;
                ((EntityAlphaHolder) ls).teslamaps$setAlpha(150);
            }
            level.entityRenderStates.add(s);
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (holos.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Holo h : holos) {
            if (h.label == null || h.label.isEmpty()) continue;
            Vec3 p = h.mob.getPosition(pt);
            ESPRenderer.drawText(pose, h.label, new Vec3(p.x, p.y + h.mob.getBbHeight() + 0.5, mirror(p.z)), 1f, cam, true, false);
        }
    }

    /** From EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        return inRoom && e instanceof RemotePlayer && cfg().craftRoomHidePlayers && cfg().craftRoomHolograms && cfg().section("Rift");
    }
}
