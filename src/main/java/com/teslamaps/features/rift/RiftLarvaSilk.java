package com.teslamaps.features.rift;

// After Zen's rift/LarvaSilkLines.kt (concept)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Larva Silk: first click marks the start block, then a line runs to the targeted block until the wire is placed. */
public final class RiftLarvaSilk {

    private static Feature feature;
    private static BlockPos start;
    private static BlockPos target;
    private static int clickTick = -1, tickCount;
    private static Object lastLevel;

    private RiftLarvaSilk() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("RiftLarvaSilk").section("Rift").when(() -> cfg().riftLarvaSilkLines)
                .tick(RiftLarvaSilk::tick)
                .world(RiftLarvaSilk::render)
                .serverChat(RiftLarvaSilk::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Larva Silk Lines", () -> c.riftLarvaSilkLines, v -> c.riftLarvaSilkLines = v)
                            .desc("First right click with Larva Silk marks the start block, then a line shows where the wire goes (4 block reach). Second click or cancelling resets.")
                            .tags("rift", "larva silk", "wire", "silk", "line");
                    b.color("  Line Color", () -> c.riftLarvaSilkColor, v -> c.riftLarvaSilkColor = v);
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide()) onClick();
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) onClick();
            return InteractionResult.PASS;
        });
    }

    private static void onClick() {
        if (feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !"LARVA_SILK".equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return;
        if (!RiftSpot.inRift()) return;
        if (clickTick == tickCount) return;
        clickTick = tickCount;
        if (start != null) {
            start = null;
            return;
        }
        start = pick(mc);
    }

    private static BlockPos pick(Minecraft mc) {
        HitResult hit = mc.player.pick(4.0, 1f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos().immutable();
        return null;
    }

    private static void tick() {
        tickCount++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            start = null;
        }
        target = start != null && "LARVA_SILK".equals(ItemUtil.skyblockId(mc.player.getMainHandItem())) ? pick(mc) : null;
    }

    private static void onChat(String raw) {
        if (StringUtil.stripTrim(raw).startsWith("You cancelled the wire")) start = null;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        BlockPos s = start;
        if (s == null) return;
        int color = TeslaMapsConfig.parseColor(cfg().riftLarvaSilkColor) | 0xFF000000;
        ESPRenderer.drawBoxOutline(pose, new AABB(s), color, 2f, cam, true);
        BlockPos t = target;
        if (t == null) return;
        ESPRenderer.drawLine(pose, Vec3.atCenterOf(s), Vec3.atCenterOf(t), color, 2f, cam);
        ESPRenderer.drawBoxOutline(pose, new AABB(t), color, 2f, cam, true);
    }
}
