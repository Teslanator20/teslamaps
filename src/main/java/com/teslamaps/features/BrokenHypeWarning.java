package com.teslamaps.features;

// After DulkirMod's BrokenHypeNotif.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Set;

public final class BrokenHypeWarning {

    private static final Set<String> WITHER_BLADES = Set.of("HYPERION", "VALKYRIE", "SCYLLA", "ASTRAEA");

    private static String lastUuid;
    private static long lastKills = -1, lastXp = -1;
    private static int ticks;

    private BrokenHypeWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BrokenHypeWarning").section("Combat").when(() -> cfg().brokenHypeWarning)
                .tick(BrokenHypeWarning::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Wither Blades");
                    b.toggle("Broken Hype Warning", () -> c.brokenHypeWarning, v -> c.brokenHypeWarning = v)
                            .desc("Title when your wither blade's kill counter goes up but its Champion XP does not (the kills are not counted). "
                                    + "Needs Book of Stats and Champion on the blade. Not on your private island.")
                            .tags("hype", "hyperion", "champion", "book of stats", "broken");
                    b.toggle("  Anvil Sound", () -> c.brokenHypeSound, v -> c.brokenHypeSound = v);
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        ItemStack held = mc.player.getMainHandItem();
        if (!WITHER_BLADES.contains(ItemUtil.skyblockId(held)) || SkyblockUtils.isInArea("Private Island")) {
            lastUuid = null;
            return;
        }
        CustomData d = held.get(DataComponents.CUSTOM_DATA);
        if (d == null) return;
        CompoundTag tag = d.copyTag();
        long kills = tag.getIntOr("stats_book", -1);
        long xp = (long) tag.getDoubleOr("champion_combat_xp", -1);
        String uuid = tag.getStringOr("uuid", "");
        if (kills < 0 || xp < 0) {
            lastUuid = null;
            return;
        }
        if (uuid.equals(lastUuid) && kills > lastKills && xp <= lastXp) warn(mc);
        lastUuid = uuid;
        lastKills = kills;
        lastXp = xp;
    }

    private static void warn(Minecraft mc) {
        mc.gui.hud.setTimes(0, 30, 10);
        mc.gui.hud.setTitle(Component.literal("§c§lHype Broken"));
        mc.gui.hud.setSubtitle(Component.literal("§7Kills are not counting"));
        if (cfg().brokenHypeSound) mc.player.playSound(SoundEvents.ANVIL_LAND, 1f, 1f);
    }
}
