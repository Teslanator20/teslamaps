package com.teslamaps.features.hunting;

// Ported from WpcMod's AutoReelLasso.kt, plus fixed + random delay
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.mixin.MinecraftUseInvoker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoReelLasso {

    private static long reelAt;
    private static long cooldownUntil;

    private AutoReelLasso() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AutoReelLasso").section("Foraging").legit().when(() -> cfg().autoReelLasso)
                .tick(AutoReelLasso::tick)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Auto Reel Lasso", () -> c.autoReelLasso, v -> c.autoReelLasso = v).cheat()
                            .desc("Uses the lasso when REEL shows over your leashed animal, after the delay below")
                            .tags("lasso", "reel", "hunting", "auto");
                    b.slider("  Delay (ms)", 150f, 500f, () -> (float) c.autoReelLassoDelay, v -> c.autoReelLassoDelay = Math.round(v), 0);
                    b.slider("  Random Extra (ms)", 0f, 400f, () -> (float) c.autoReelLassoRandom, v -> c.autoReelLassoRandom = Math.round(v), 0);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (LegitMode.blocksCheats() || mc.screen != null || !LassoDisplay.holdingLasso(mc.player.getMainHandItem())) {
            reelAt = 0;
            return;
        }
        if (reelAt != 0) {
            if (now < reelAt) return;
            reelAt = 0;
            if (leashed(mc) == null) return;
            ((MinecraftUseInvoker) mc).teslamaps$startUseItem();
            cooldownUntil = now + 1000;
            return;
        }
        if (now < cooldownUntil) return;
        Entity animal = leashed(mc);
        if (animal == null || !canReel(mc, animal)) return;
        TeslaMapsConfig c = cfg();
        int extra = c.autoReelLassoRandom > 0 ? ThreadLocalRandom.current().nextInt(c.autoReelLassoRandom + 1) : 0;
        reelAt = now + Math.max(150, c.autoReelLassoDelay) + extra;
    }

    private static Entity leashed(Minecraft mc) {
        for (Entity e : EntityCache.all()) {
            if (e instanceof Leashable l && e.isAlive() && l.getLeashHolder() == mc.player) return e;
        }
        return null;
    }

    private static boolean canReel(Minecraft mc, Entity animal) {
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, animal.getBoundingBox().expandTowards(0, 3, 0))) {
            Component name = as.getCustomName();
            if (name != null && StringUtil.stripTrim(name.getString()).equals("REEL")) return true;
        }
        return false;
    }
}
