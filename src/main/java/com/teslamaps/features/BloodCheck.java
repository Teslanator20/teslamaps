package com.teslamaps.features;

// IllegalMap DmapDungeon whiteCheckBlood
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;

public class BloodCheck {

    private static boolean done = false;
    private static Object lastLevel = null;

    public static void register() {
        Feature.of("BloodCheck").section("Map").when(() -> TeslaMapsConfig.get().mapWhiteCheckBlood)
                .tick(BloodCheck::tick)
                .serverChat(BloodCheck::onChat);
    }

    public static boolean done() {
        return done && TeslaMapsConfig.get().mapWhiteCheckBlood;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            done = false;
        }
    }

    private static void onChat(String msg) {
        if (!done && StringUtil.stripTrim(msg).equals("[BOSS] The Watcher: That will be enough for now.")) done = true;
    }
}
