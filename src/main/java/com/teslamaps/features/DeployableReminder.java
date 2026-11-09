package com.teslamaps.features;

// Ported from SkyHanni's features/combat/DeployableReminder.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.slayer.SlayerQuest;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class DeployableReminder {

    private static boolean wasBoss, wasMineshaft;
    private static long slayerAt, mineshaftAt;

    private DeployableReminder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.deployableOverlay && c.deployableReminder;
    }

    public static void register() {
        Feature.of("DeployableReminder").section("Skills").when(DeployableReminder::on)
                .tick(DeployableReminder::tick)
                .serverChat(DeployableReminder::onChat);
    }

    private static long delayMs() {
        return Math.max(0, Math.round(cfg().deployableReminderDelay * 1000));
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        boolean boss = SlayerQuest.bossActive();
        if (boss && !wasBoss) slayerAt = now + delayMs();
        wasBoss = boss;
        if (slayerAt > 0 && now >= slayerAt) {
            slayerAt = 0;
            if (c.deployableReminderSlayer && boss && !Deployables.anyActive(Deployables.Type.FLUX)) warn("Place Down Power Orb!");
        }
        boolean mineshaft = SkyblockUtils.isInArea("Mineshaft");
        if (mineshaft && !wasMineshaft) mineshaftAt = now + delayMs();
        wasMineshaft = mineshaft;
        if (mineshaftAt > 0 && now >= mineshaftAt) {
            mineshaftAt = 0;
            if (c.deployableReminderMineshaft && mineshaft && !Deployables.anyActive(Deployables.Type.LANTERN)) warn("Place Down Lantern!");
        }
    }

    private static void onChat(String raw) {
        if (!cfg().deployableReminderTrophy || !SkyblockUtils.isInArea("Crimson Isle")) return;
        if (!StringUtil.stripTrim(raw).startsWith("TROPHY FISH!")) return;
        if (!Deployables.anyActive(Deployables.Type.UMBERELLA)) warn("Place Down Umberella!");
    }

    private static void warn(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(5, 50, 10);
        mc.gui.hud.setTitle(Component.literal("§4§l" + msg));
        mc.gui.hud.setSubtitle(Component.empty());
        LoudSound.play(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
    }
}
