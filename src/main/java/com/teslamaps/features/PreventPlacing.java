package com.teslamaps.features;

// Ported from Devonian's PreventPlacingWeapons.kt + PreventPlacingPlayerHeads.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/** Right-click on a block with a placeable weapon or ability head uses the ability instead of placing it. */
public final class PreventPlacing {

    private static final Set<String> WEAPONS = Set.of(
            "FLOWER_OF_TRUTH", "BOUQUET_OF_LIES", "MOODY_GRAPPLESHOT",
            "BAT_WAND", "STARRED_BAT_WAND", "WEIRD_TUBA", "WEIRDER_TUBA",
            "PUMPKIN_LAUNCHER", "FIRE_FREEZE_STAFF", "GEMSTONE_GAUNTLET");

    private static boolean keyWasDown;

    private PreventPlacing() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PreventPlacing").section("Inventory")
                .when(() -> cfg().preventPlacingHeads && cfg().preventPlacingBlacklistKey != -1)
                .tick(PreventPlacing::tick);
    }

    public static boolean block(ItemStack stack) {
        TeslaMapsConfig c = cfg();
        if (!c.preventPlacingWeapons && !c.preventPlacingHeads) return false;
        if (!c.section("Inventory") || stack == null || stack.isEmpty() || !SkyblockUtils.isInSkyblock()) return false;
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return false;
        if (c.preventPlacingWeapons && WEAPONS.contains(id)) return true;
        if (!c.preventPlacingHeads || stack.getItem() != Items.PLAYER_HEAD) return false;
        if (c.preventPlacingExceptGarden && SkyblockUtils.isInGarden()) return false;
        if (c.preventPlacingBlacklist.contains(id)) return false;
        for (String line : ItemUtil.loreLines(stack)) {
            String s = StringUtil.strip(line);
            if (s.contains("RIGHT CLICK") || s.contains("Right-click")) return true;
        }
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.gui.screen() == null && KeyNames.isDown(cfg().preventPlacingBlacklistKey);
        if (down && !keyWasDown) toggleHeld(mc);
        keyWasDown = down;
    }

    private static void toggleHeld(Minecraft mc) {
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        if (id.isEmpty()) return;
        Set<String> list = cfg().preventPlacingBlacklist;
        boolean removed = list.remove(id);
        if (!removed) list.add(id);
        TeslaMapsConfig.save();
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fPlace guard: §e" + id
                + (removed ? " §cblocked again" : " §aallowed to place")));
    }
}
