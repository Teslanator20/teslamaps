package com.teslamaps.features;

// Ported from Skyblocker's ConsumableProtection.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Set;

public final class ConsumableProtection {

    private static final Set<String> PROTECTED = Set.of(
            "NEW_BOTTLE_OF_JYRRE", "DARK_CACAO_TRUFFLE", "DISCRITE", "MOBY_DUCK", "ROSEWATER_FLASK");

    private static Feature feature;
    private static long lastWarn;

    private ConsumableProtection() {}

    public static void register() {
        feature = Feature.of("ConsumableProtection").section("Inventory")
                .when(() -> TeslaMapsConfig.get().protectEvolvingConsumables);
        UseItemCallback.EVENT.register(ConsumableProtection::onUse);
        // placing the head counts as consuming it too
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> onUse(player, level, hand));
    }

    private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
        if (!level.isClientSide() || !TeslaMapsConfig.get().protectEvolvingConsumables || !feature.active())
            return InteractionResult.PASS;
        if (!SkyblockUtils.isInSkyblock()) return InteractionResult.PASS;
        if (!PROTECTED.contains(ItemUtil.skyblockId(player.getItemInHand(hand)))) return InteractionResult.PASS;
        long now = System.currentTimeMillis();
        if (now - lastWarn > 3000) {
            lastWarn = now;
            player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fBlocked using an evolving consumable §7(Inventory > Item Protection)"));
        }
        return InteractionResult.FAIL;
    }
}
