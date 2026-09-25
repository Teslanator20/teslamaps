package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

// Ported from WpcMod's PigeonSwapper.kt, plus fixed + random delay before the swap
public final class PigeonSwapper {

    private static final String ROYAL_PIGEON = "ROYAL_PIGEON";
    private static Feature feature;
    private static long swapAt;

    private PigeonSwapper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PigeonSwapper").section("Mining").legit().when(() -> cfg().pigeonSwapper)
                .tick(PigeonSwapper::tick)
                .config("Mining", b -> {
                    b.toggle("Pigeon Swapper", () -> cfg().pigeonSwapper, v -> cfg().pigeonSwapper = v).cheat()
                            .desc("After using the Royal Pigeon in the Dwarven Mines, Crystal Hollows or a Mineshaft, "
                                    + "switches back to the drill in your hotbar.")
                            .tags("royal pigeon", "drill", "commission");
                    b.slider("  Fixed Delay (ms)", 50f, 500f,
                            () -> (float) cfg().pigeonSwapperDelay, v -> cfg().pigeonSwapperDelay = Math.round(v));
                    b.slider("  Random Extra Delay (ms)", 0f, 400f,
                            () -> (float) cfg().pigeonSwapperRandom, v -> cfg().pigeonSwapperRandom = Math.round(v));
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !cfg().pigeonSwapper || feature == null) return InteractionResult.PASS;
            if (!ROYAL_PIGEON.equals(ItemUtil.skyblockId(player.getMainHandItem()))) return InteractionResult.PASS;
            if (System.currentTimeMillis() < swapAt + 2000 || !allowedArea() || !feature.active()) return InteractionResult.PASS;
            TeslaMapsConfig c = cfg();
            int extra = c.pigeonSwapperRandom > 0 ? ThreadLocalRandom.current().nextInt(c.pigeonSwapperRandom + 1) : 0;
            swapAt = System.currentTimeMillis() + Math.max(50, c.pigeonSwapperDelay) + extra;
            return InteractionResult.PASS;
        });
    }

    private static boolean allowedArea() {
        String area = SkyblockUtils.getArea().toLowerCase();
        return area.contains("dwarven") || area.contains("crystal hollows") || area.contains("mineshaft");
    }

    private static void tick() {
        if (swapAt == 0 || System.currentTimeMillis() < swapAt) return;
        swapAt = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || LegitMode.blocksCheats()) return;
        var inv = mc.player.getInventory();
        if (!ROYAL_PIGEON.equals(ItemUtil.skyblockId(inv.getItem(inv.getSelectedSlot())))) return;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).getHoverName().getString().contains("Drill")) {
                inv.setSelectedSlot(i);
                return;
            }
        }
    }
}
