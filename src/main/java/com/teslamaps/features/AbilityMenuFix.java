package com.teslamaps.features;

// Ported from GanKura's AbilityMenuHandler
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;

/** Holds the hotbar slot packet while an ability menu (Royal Pigeon, ...) opens, so Hypixel does not close it. */
public final class AbilityMenuFix {

    private static final long PENDING_MS = 1500;
    private static long pendingUntil;

    private static final Feature FEATURE = Feature.of("AbilityMenuFix").section("Inventory").legit()
            .when(() -> TeslaMapsConfig.get().abilityMenuFix);

    private AbilityMenuFix() {}

    public static void register() {
        FEATURE.config("Inventory", b -> b.toggle("Ability Menu Fix", () -> TeslaMapsConfig.get().abilityMenuFix,
                        v -> TeslaMapsConfig.get().abilityMenuFix = v)
                .desc("After using an item that opens a menu (Royal Pigeon, ...) the hotbar switch is only sent once the menu is closed, so switching items fast no longer closes it.")
                .cheat());
    }

    private static boolean enabled() {
        return FEATURE.active() && !LegitMode.blocksCheats() && SkyblockUtils.isInSkyblock();
    }

    public static void onUseItem(ItemStack stack) {
        if (!enabled() || !opensMenu(stack)) return;
        pendingUntil = System.currentTimeMillis() + PENDING_MS;
    }

    public static boolean holdSlotChange() {
        if (pendingUntil == 0) return false;
        if (enabled()) {
            if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>) return true;
            if (System.currentTimeMillis() < pendingUntil) return true;
        }
        pendingUntil = 0;
        return false;
    }

    private static boolean opensMenu(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            if (line.getString().toLowerCase(Locale.ROOT).contains("menu")) return true;
        }
        return false;
    }
}
