package com.teslamaps.mixin;

import com.teslamaps.features.RenderHiders;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class ItemBarMixin {
    @Inject(method = "itemBar", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideDurability(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (RenderHiders.hideDurability()) ci.cancel();
        else if (com.teslamaps.features.gardenqol.GardenSafety.drawBar((GuiGraphicsExtractor) (Object) this, stack, x, y)) ci.cancel();
        else if (com.teslamaps.features.mining.DrillFuelDisplay.drawBar((GuiGraphicsExtractor) (Object) this, stack, x, y)) ci.cancel();
    }
}
