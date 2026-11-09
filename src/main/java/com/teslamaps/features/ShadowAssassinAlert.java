package com.teslamaps.features;

// Ported from SkyHanni's DungeonShadowAssassinNotification.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class ShadowAssassinAlert {

    private ShadowAssassinAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ShadowAssassinAlert").section("Score & Splits")
                .config("Score & Splits", b -> b.toggle("Shadow Assassin Jump Alert", () -> cfg().shadowAssassinAlert,
                                v -> cfg().shadowAssassinAlert = v)
                        .desc("Title + beep when a Shadow Assassin teleports (world border packet), not in the F3 boss."));
    }

    /** Main thread. */
    public static void onBorder(int warningTime) {
        TeslaMapsConfig c = cfg();
        if (warningTime != 10000 || !c.shadowAssassinAlert || !c.section("Score & Splits") || !DungeonManager.isInDungeon()) return;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f != null && f.getLevel() == 3 && DungeonManager.isInBossArea()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.hud.setTimes(0, 30, 5);
        mc.gui.hud.setTitle(Component.literal("§cShadow Assassin Jumping!"));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
    }
}
