package com.teslamaps.features.rift;

// Ported from SkyHanni's RiftHorsezookaHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.Horse;

public final class HorsezookaHider {

    private static boolean holding;
    private static boolean rift;
    private static int ticks;

    private HorsezookaHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HorsezookaHider").section("Rift").when(() -> cfg().horsezookaHider)
                .tick(HorsezookaHider::tick)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Horsezooka Hider", () -> c.horsezookaHider, v -> c.horsezookaHider = v)
                            .desc("Hides all horses in the Rift while you hold the Horsezooka.");
                });
    }

    private static void tick() {
        if (++ticks % 20 == 0) rift = CraftRoomHolograms.inRift();
        holding = rift && "HORSEZOOKA".equals(ItemUtil.skyblockId(Minecraft.getInstance().player.getMainHandItem()));
    }

    /** From EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        return holding && e instanceof Horse && cfg().horsezookaHider && cfg().section("Rift");
    }
}
