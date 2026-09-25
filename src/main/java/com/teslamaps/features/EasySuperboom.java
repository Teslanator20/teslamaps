package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.MinecraftUseInvoker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

// Ported from WpcMod's EasySuperboom.kt, plus fixed + random delay before the use
public final class EasySuperboom {

    private static final String SUPERBOOM = "SUPERBOOM_TNT";
    private static long useAt = 0;
    private static int useSlot = -1;

    private EasySuperboom() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EasySuperboom").section("Auto").legit().when(() -> cfg().easySuperboom)
                .tick(EasySuperboom::tick)
                .config("Auto", b -> {
                    b.toggle("Easy Superboom (pick block)", () -> cfg().easySuperboom, v -> cfg().easySuperboom = v).cheat()
                            .desc("Middle click on a block in dungeons selects Superboom TNT from the hotbar and uses it");
                    b.slider("  Fixed Delay (ms)", 50f, 500f, () -> (float) cfg().easySuperboomDelay, v -> cfg().easySuperboomDelay = Math.round(v));
                    b.slider("  Random Extra Delay (ms)", 0f, 300f, () -> (float) cfg().easySuperboomRandom, v -> cfg().easySuperboomRandom = Math.round(v));
                });
    }

    /** Minecraft.pickBlockOrEntity HEAD. True = swallow the pick. */
    public static boolean onPickBlock(HitResult hit) {
        TeslaMapsConfig c = cfg();
        if (!c.easySuperboom || !c.section("Auto") || LegitMode.blocksCheats()) return false;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !DungeonManager.isInDungeon()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || useAt != 0) return false;
        var inv = mc.player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (SUPERBOOM.equals(ItemUtil.skyblockId(inv.getItem(i)))) { slot = i; break; }
        }
        if (slot < 0) return false;
        inv.setSelectedSlot(slot);
        useSlot = slot;
        int extra = c.easySuperboomRandom > 0 ? ThreadLocalRandom.current().nextInt(c.easySuperboomRandom + 1) : 0;
        useAt = System.currentTimeMillis() + Math.max(50, c.easySuperboomDelay) + extra;
        return true;
    }

    private static void tick() {
        if (useAt == 0 || System.currentTimeMillis() < useAt) return;
        useAt = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || LegitMode.blocksCheats() || !DungeonManager.isInDungeon()) return;
        if (mc.player.getInventory().getSelectedSlot() != useSlot) return;
        if (!SUPERBOOM.equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        ((MinecraftUseInvoker) mc).teslamaps$startUseItem();
    }
}
