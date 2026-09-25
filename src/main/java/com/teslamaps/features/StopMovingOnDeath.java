package com.teslamaps.features;

// Skytils' preventMovingOnDeath / SkyblockAddons' PREVENT_MOVEMENT_ON_DEATH
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class StopMovingOnDeath {

    private static boolean wasDead = false;

    private StopMovingOnDeath() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature f = Feature.of("StopMovingOnDeath").section("Combat").when(() -> cfg().stopMovingOnDeath)
                .serverChat(StopMovingOnDeath::onChat)
                .tick(StopMovingOnDeath::tick);
        f.config("Combat", b -> b.toggle("Stop Moving On Death", () -> cfg().stopMovingOnDeath, v -> cfg().stopMovingOnDeath = v)
                .desc("Releases every held key when you die, so you do not walk on after respawning."));
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (s.startsWith("☠ You ") || s.startsWith("You died")) release();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean dead = mc.player != null && mc.player.isDeadOrDying();
        if (dead && !wasDead) release();
        wasDead = dead;
    }

    private static void release() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(KeyMapping::releaseAll);
    }
}
