/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class EstimatedValueMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void teslamaps$tooltipExtras(Item.TooltipContext ctx, Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
        if (net.minecraft.client.Minecraft.getInstance().screen == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        ItemStack stack = (ItemStack) (Object) this;
        try {
            if (c.estimatedValue) {
                PriceManager.ensureFresh();
                double value = EstimatedValue.compute(stack);
                if (value > 0) cir.getReturnValue().add(Component.literal("§7Est. Value: §6" + EstimatedValue.fmt(value) + " coins"));
            }
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
