package com.teslamaps.features;

// SkyHanni MiscFeatures chickenRacePattern: pause Hide Explosions while racing
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.network.chat.Component;

public final class ChickenRace {

    private static volatile long lastSeenMs;

    private ChickenRace() {}

    public static void onActionBar(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noExplosions || !c.noExplosionsChickenRace) return;
        if (StringUtil.strip(message.getString()).contains("CHICKEN RACING")) lastSeenMs = System.currentTimeMillis();
    }

    public static boolean active() {
        return TeslaMapsConfig.get().noExplosionsChickenRace && System.currentTimeMillis() - lastSeenMs < 3000;
    }
}
