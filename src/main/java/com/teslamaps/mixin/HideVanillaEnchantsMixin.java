package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// Ported from Skyblocker's ItemStackMixin hideVanillaEnchants
@Mixin(ItemStack.class)
public class HideVanillaEnchantsMixin {
    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideVanillaEnchants(DataComponentType<?> type, Item.TooltipContext context, TooltipDisplay display,
                                               Consumer<Component> builder, TooltipFlag flag, CallbackInfo ci) {
        if (type != DataComponents.ENCHANTMENTS || !TeslaMapsConfig.get().hideVanillaEnchants) return;
        if (SkyblockUtils.isInSkyblock()) ci.cancel();
    }
}
