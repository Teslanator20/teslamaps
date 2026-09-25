package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.CompactorPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class CompactorTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void teslamaps$compactorGrid(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (cir.getReturnValue().isPresent()) return;
        if (!TeslaMapsConfig.get().compactorPreview || !CompactorPreview.previewVisible()) return;
        CompactorPreview.Contents contents = CompactorPreview.contents((ItemStack) (Object) this);
        if (contents != null) cir.setReturnValue(Optional.of(contents));
    }
}
