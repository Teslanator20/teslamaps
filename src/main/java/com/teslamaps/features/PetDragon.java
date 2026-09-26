package com.teslamaps.features;

// After Odin (Forge)'s PetDragon: a client-side ender dragon following the player
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.DragonTintHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

public final class PetDragon {

    public static final String[] COLORS = {"None", "Red", "Orange", "Green", "Blue", "Purple"};
    private static final int ID = -3_000_000;

    private static EnderDragon dragon;
    private static Object lastLevel;

    private PetDragon() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PetDragon").section("Render").when(() -> cfg().petDragon)
                .tick(PetDragon::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Pet Dragon", () -> c.petDragon, v -> c.petDragon = v)
                            .desc("A small client-side ender dragon follows you around. Only you see it.");
                    b.slider("  Size", 0.05f, 1f, () -> c.petDragonSize, v -> c.petDragonSize = v, 2);
                    b.slider("  Height", 0f, 6f, () -> c.petDragonHeight, v -> c.petDragonHeight = v, 1);
                    b.slider("  Distance", 0f, 8f, () -> c.petDragonDistance, v -> c.petDragonDistance = v, 1);
                    b.slider("  Direction", -180f, 180f, () -> c.petDragonAngle, v -> c.petDragonAngle = v, 0)
                            .desc("0 = behind you, 90 = to your right.");
                    b.slider("  Wing Speed", 0f, 4f, () -> c.petDragonSpeed, v -> c.petDragonSpeed = v, 1);
                    b.dropdown("  Color", COLORS, () -> c.petDragonColor, v -> c.petDragonColor = v);
                    b.toggle("  Only In Third Person", () -> c.petDragonThirdPersonOnly, v -> c.petDragonThirdPersonOnly = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.level != lastLevel || dragon == null) {
            lastLevel = mc.level;
            try {
                dragon = new EnderDragon(EntityType.ENDER_DRAGON, mc.level);
                dragon.setId(ID);
            } catch (Throwable t) {
                dragon = null;
                return;
            }
            place(mc, c, true);
        }
        place(mc, c, false);
        dragon.oFlapTime = dragon.flapTime;
        dragon.flapTime += 0.05f * c.petDragonSpeed;
        dragon.tickCount++;
        dragon.flightHistory.record(dragon.getY(), dragon.getYRot());
    }

    private static void place(Minecraft mc, TeslaMapsConfig c, boolean snap) {
        float yaw = mc.player.getYRot();
        double a = Math.toRadians(yaw + c.petDragonAngle);
        double tx = mc.player.getX() + Math.sin(a) * c.petDragonDistance;
        double tz = mc.player.getZ() - Math.cos(a) * c.petDragonDistance;
        double ty = mc.player.getY() + c.petDragonHeight;
        dragon.xo = dragon.getX();
        dragon.yo = dragon.getY();
        dragon.zo = dragon.getZ();
        dragon.yRotO = dragon.getYRot();
        double k = snap ? 1 : 0.25;
        dragon.setPos(dragon.getX() + (tx - dragon.getX()) * k, dragon.getY() + (ty - dragon.getY()) * k,
                dragon.getZ() + (tz - dragon.getZ()) * k);
        float target = yaw + 180f;
        float cur = dragon.getYRot();
        float diff = ((target - cur) % 360f + 540f) % 360f - 180f;
        dragon.setYRot(snap ? target : cur + diff * 0.3f);
        if (snap) {
            dragon.xo = dragon.getX();
            dragon.yo = dragon.getY();
            dragon.zo = dragon.getZ();
            dragon.yRotO = dragon.getYRot();
        }
    }

    /** From PetDragonMixin at the end of the entity extraction. */
    public static void extract(LevelRenderState level, float partialTick) {
        TeslaMapsConfig c = cfg();
        if (!c.petDragon || dragon == null || !c.section("Render")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel || mc.player == null) return;
        if (c.petDragonThirdPersonOnly && mc.options.getCameraType().isFirstPerson()) return;
        EntityRenderState s;
        try {
            s = mc.getEntityRenderDispatcher().extractEntity(dragon, partialTick);
        } catch (Throwable t) {
            return;
        }
        if (s instanceof EnderDragonRenderState ds) {
            int color = 0;
            for (int i = 1; i < COLORS.length; i++) if (COLORS[i].equals(c.petDragonColor)) color = i;
            DragonTintHolder h = (DragonTintHolder) ds;
            h.teslamaps$setPet(true);
            if (color != 0) com.teslamaps.render.DragonTintTexture.ensure();
            h.teslamaps$setOverlay(color == 0 ? -1 : OverlayTexture.pack(DragonTintHolder.TINT_U + color - 1, DragonTintHolder.TINT_V));
        }
        s.shadowPieces.clear();
        level.entityRenderStates.add(s);
    }

    /** From PetDragonMixin, before the model is drawn. */
    public static void scale(EnderDragonRenderState state, PoseStack pose) {
        if (((DragonTintHolder) state).teslamaps$isPet()) {
            float s = cfg().petDragonSize;
            pose.scale(s, s, s);
        }
    }
}
