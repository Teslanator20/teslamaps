package com.teslamaps.features;

// Effective area: concept from Zen's EffectiveAreaOverlay. Fire Veil: ported from SkyHanni's FireVeilWandParticles.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class AbilityRadius {

    private static final Set<String> AREA_ITEMS = Set.of("HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE", "BAT_WAND", "STARRED_BAT_WAND");
    private static final String[] FIRE_VEIL_MODES = {"Circle", "Nothing"};
    private static final long FIRE_VEIL_MS = 5500;
    private static final double AREA_RANGE = 45;

    private static boolean holdingArea;
    private static long fireVeilClick;

    private AbilityRadius() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AbilityRadius").section("Render").when(() -> cfg().effectiveAreaOverlay || cfg().fireVeilCircle)
                .tick(AbilityRadius::tick)
                .world(AbilityRadius::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Ability Radius");
                    b.toggle("Wither Blade & Sceptre Area", () -> c.effectiveAreaOverlay, v -> c.effectiveAreaOverlay = v)
                            .desc("Holding a Wither Blade or Spirit Sceptre: filled circle at the block you look at.");
                    b.slider("  Radius", 1f, 15f, () -> c.effectiveAreaRadius, v -> c.effectiveAreaRadius = v, 1);
                    b.color("  Area Color", () -> c.effectiveAreaColor, v -> c.effectiveAreaColor = v);
                    b.toggle("Fire Veil Wand Circle", () -> c.fireVeilCircle, v -> c.fireVeilCircle = v)
                            .desc("Hides the Fire Veil flame particles for 5.5s after use and shows its 3.5 block range instead.");
                    b.dropdown("  Display", FIRE_VEIL_MODES, () -> c.fireVeilMode, v -> c.fireVeilMode = v);
                    b.color("  Circle Color", () -> c.fireVeilColor, v -> c.fireVeilColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        holdingArea = cfg().effectiveAreaOverlay && SkyblockUtils.isInSkyblock()
                && AREA_ITEMS.contains(ItemUtil.skyblockId(mc.player.getMainHandItem()));
    }

    /** From PacketSendMixin, main thread. */
    public static void onUseItem(InteractionHand hand) {
        if (!cfg().fireVeilCircle) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if ("FIRE_VEIL_WAND".equals(ItemUtil.skyblockId(player.getItemInHand(hand)))) fireVeilClick = System.currentTimeMillis();
    }

    private static boolean fireVeilActive() {
        return fireVeilClick != 0 && System.currentTimeMillis() - fireVeilClick <= FIRE_VEIL_MS;
    }

    /** From ClientPlayNetworkHandlerMixin, main thread. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (!c.fireVeilCircle || !fireVeilActive() || !c.section("Render")) return false;
        return p.particle().getType() == ParticleTypes.FLAME && p.xMaxSpeed() == 0.55f;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (c.fireVeilCircle && "Circle".equals(c.fireVeilMode) && fireVeilActive()) {
            Vec3 p = mc.player.getPosition(pt);
            ESPRenderer.drawCircle(pose, p.x, p.y + 0.05, p.z, 3.5, TeslaMapsConfig.parseColor(c.fireVeilColor), 2f, cam);
        }
        if (holdingArea) {
            HitResult hit = mc.player.pick(AREA_RANGE, pt, false);
            if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                Vec3 l = bh.getLocation();
                ESPRenderer.drawDisc(pose, l.x, bh.getBlockPos().getY() + 1.02, l.z, c.effectiveAreaRadius,
                        TeslaMapsConfig.parseColor(c.effectiveAreaColor), cam);
            }
        }
    }
}
