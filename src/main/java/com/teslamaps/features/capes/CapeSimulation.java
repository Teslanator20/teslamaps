package com.teslamaps.features.capes;

// Ported from WaveyCapes' CapeHolder/CapeMovement (movement extraction + per-tick simulation)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.capes.StickSimulation3d.Vec3f;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class CapeSimulation {

    private static final int PART_COUNT = 16;
    private static final float GRAVITY = 25;
    private static final float HEIGHT_MULTIPLIER = 6;
    private static final float STRAVE_MULTIPLIER = 2;

    private static final Int2ObjectMap<StickSimulation3d> SIMS = new Int2ObjectOpenHashMap<>();
    private static int cleanupCounter;

    public static StickSimulation3d get(int entityId) {
        return SIMS.get(entityId);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!SIMS.isEmpty()) SIMS.clear();
            return;
        }
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (cfg.capeWaviness <= 0f) return;

        for (Player player : mc.level.players()) {
            if (!(player instanceof AbstractClientPlayer acp)) continue;
            boolean local = acp == mc.player;
            if (local ? (!cfg.capeEnabled || cfg.capeSelected.isEmpty())
                      : CapeSync.textureFor(acp.getId()) == null) continue;

            StickSimulation3d sim = SIMS.get(acp.getId());
            if (sim == null) {
                sim = new StickSimulation3d();
                SIMS.put(acp.getId(), sim);
            }
            if (sim.init(PART_COUNT)) {
                // fresh sim: shake once and settle so it doesn't spawn mid-explosion
                sim.applyMovement(new Vec3f(1f, 1f, 0));
                for (int i = 0; i < 5; i++) simulate(acp, sim);
            }
            simulate(acp, sim);
        }

        if (++cleanupCounter >= 100) {
            cleanupCounter = 0;
            SIMS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    private static void simulate(AbstractClientPlayer player, StickSimulation3d sim) {
        if (sim.empty()) return;
        double d = player.avatarState().getInterpolatedCloakX(1f) - player.getX();
        double m = player.avatarState().getInterpolatedCloakZ(1f) - player.getZ();
        float n = player.yBodyRotO + player.yBodyRot - player.yBodyRotO;
        double o = Mth.sin(n * 0.017453292F);
        double p = -Mth.cos(n * 0.017453292F);
        float heightMul = HEIGHT_MULTIPLIER;
        if (player.isUnderWater()) {
            heightMul *= 2;
            sim.gravity = GRAVITY / 10f;
        } else {
            sim.gravity = GRAVITY;
        }
        // small swing when jumping/falling so it doesn't clip into itself
        double fallHack = Mth.clamp((player.yo - player.getY()) * 10, 0, 1);

        Vec3f gravity = new Vec3f(0, -1, 0);
        Vec3f strave = new Vec3f((float) (player.getX() - player.xo), (float) (player.getZ() - player.zo), 0);
        strave.rotateDegrees(-player.getYRot());
        double changeX = (d * o + m * p) + fallHack + (player.isCrouching() && !sim.sneaking ? 3 : 0);
        double changeY = ((player.getY() - player.yo) * heightMul) + (player.isCrouching() && !sim.sneaking ? 1 : 0);
        double changeZ = -strave.x * STRAVE_MULTIPLIER;
        sim.sneaking = player.isCrouching();
        Vec3f change = new Vec3f((float) changeX, (float) changeY, (float) changeZ);
        if (player.isVisuallySwimming()) {
            // -90 = swimming up, 0 = straight, 90 = down; body is the sim's reference
            float rotation = player.getXRot() + 90;
            gravity.rotateDegrees(rotation);
            change.rotateDegrees(rotation);
        }
        sim.gravityDirection = gravity;
        sim.applyMovement(change);
        sim.simulate();
    }
}
