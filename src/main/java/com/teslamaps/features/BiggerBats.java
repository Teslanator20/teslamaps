package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;

/** Skytils' biggerBatModels: dungeon bats drawn larger. Sub-option of Dungeon Bat ESP. */
public final class BiggerBats {

    private BiggerBats() {}

    /** From MobScaleMixin, LivingEntityRenderer.scale for bat states. */
    public static void scale(PoseStack pose) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.biggerBatModels || !c.dungeonBatESP || !DungeonManager.isInDungeon()) return;
        float s = c.biggerBatScale;
        if (s != 1f) pose.scale(s, s, s);
    }
}
