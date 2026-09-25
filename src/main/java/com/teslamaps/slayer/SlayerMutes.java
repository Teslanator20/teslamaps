package com.teslamaps.slayer;

// Ported from Skyblocker's MuteBlazeSounds, MuteWolfSounds and MuteSpiderSounds
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;

import java.util.Set;

/** Slayer sound presets with island binding, driven from SoundPlayMixin. */
public final class SlayerMutes {

    private static final Set<String> BLAZE = Set.of(
            "minecraft:entity.blaze.burn", "minecraft:entity.blaze.ambient", "minecraft:entity.blaze.hurt",
            "minecraft:entity.ghast.shoot", "minecraft:block.fire.ambient", "minecraft:block.lava.pop",
            "minecraft:entity.lightning_bolt.thunder", "minecraft:entity.lightning_bolt.impact");

    private static final Set<String> SPIDER = Set.of(
            "minecraft:entity.spider.ambient", "minecraft:entity.spider.death", "minecraft:entity.spider.hurt",
            "minecraft:entity.spider.step", "minecraft:entity.bat.hurt",
            "minecraft:entity.silverfish.hurt", "minecraft:entity.silverfish.death", "minecraft:entity.silverfish.ambient",
            "minecraft:entity.skeleton.ambient", "minecraft:entity.skeleton.death", "minecraft:entity.skeleton.hurt",
            "minecraft:entity.skeleton.step");

    private SlayerMutes() {}

    public static boolean enabled(TeslaMapsConfig c) {
        return c.muteBlazeSounds || c.muteWolfSounds || c.muteSpiderSounds;
    }

    public static boolean shouldMute(TeslaMapsConfig c, String id) {
        if (!Minecraft.getInstance().isSameThread() || !SkyblockUtils.isInSkyblock()) return false;
        String area = SkyblockUtils.getArea();
        boolean crimson = area.equalsIgnoreCase("Crimson Isle");
        if (c.muteBlazeSounds && crimson && BLAZE.contains(id)) return true;
        if (c.muteWolfSounds && id.startsWith("minecraft:entity.wolf.")
                && (area.equalsIgnoreCase("The Park") || area.equalsIgnoreCase("Hub"))) return true;
        if (c.muteSpiderSounds && SPIDER.contains(id) && (crimson || area.equalsIgnoreCase("Spider's Den"))) {
            // keeps the Demonlord's skeleton demon sounds
            return !(SlayerQuest.bossActive() && "Inferno Demonlord".equals(SlayerQuest.boss()));
        }
        return false;
    }
}
