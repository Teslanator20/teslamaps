package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class HidePlayers {

    private static long hideAfterLeapUntilMs = 0;

    public static void onLeap() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.hidePlayersAfterLeap) return;
        hideAfterLeapUntilMs = System.currentTimeMillis() + (long) (config.hidePlayersAfterLeapSeconds * 1000);
    }

    public static boolean shouldHide(Entity entity) {
        if (!matches(entity)) return false;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return !config.hidePlayersTransparent || config.hidePlayersOpacity <= 0f;
    }

    /** 255 = drawn normally. From LivingEntityAlphaMixin, every rendered living entity. */
    public static int alpha(Entity entity) {
        if (!(entity instanceof Player)) return 255;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.hidePlayersTransparent && matches(entity)) return pct(config.hidePlayersOpacity);
        return 255;
    }

    private static int pct(float p) {
        return Math.round(Math.max(0f, Math.min(100f, p)) * 2.55f);
    }


    private static boolean matches(Entity entity) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        boolean arachne = ArachneQol.hidePlayers() || ParkourGuides.hidePlayers();
        if (!config.hidePlayers && !config.hidePlayersAfterLeap && !arachne) return false;
        if (!(entity instanceof Player)) return false;
        if (entity.getUUID().version() != 4) return false; // skip NPCs / fake players

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == mc.player) return false;
        if (arachne) return true;
        if (config.hidePlayersAfterLeap && System.currentTimeMillis() < hideAfterLeapUntilMs) return true;
        if (!config.hidePlayers) return false;
        if (config.hidePlayersOnlyDungeon && !DungeonManager.isInDungeon()) return false;
        if (config.hidePlayersOnlyDevs && !atDevs(mc.player)) return false;
        if (config.hidePlayersAll) return true;

        double d = config.hidePlayersDistance;
        return entity.distanceToSqr(mc.player) <= d * d; // hide players within range
    }

    // Odin: SS and 4th device stands, radius 1.8, P3 only
    private static boolean atDevs(Player self) {
        boolean near = self.distanceToSqr(108.63, 120.0, 94.0) <= 1.8 * 1.8
                || self.distanceToSqr(63.5, 127.0, 35.5) <= 1.8 * 1.8;
        return near && com.teslamaps.dungeon.F7Devices.phase() == 3;
    }
}
