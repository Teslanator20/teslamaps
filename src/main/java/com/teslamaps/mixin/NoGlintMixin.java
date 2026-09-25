package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class NoGlintMixin {
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noGlint(CallbackInfoReturnable<Boolean> cir) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean colors = c.customGlintColors && !c.customGlintColor.isEmpty();
        if (!c.noEnchantGlint && !colors) return;
        // a glint picked in /tmap customize wins over the blanket hide
        if (Customize.active()) {
            String uuid = Customize.uuid((ItemStack) (Object) this);
            if (!uuid.isEmpty()) {
                if (colors && c.customGlintColor.containsKey(uuid) && !Boolean.FALSE.equals(c.customGlint.get(uuid))) {
                    cir.setReturnValue(true);
                    return;
                }
                if (c.customGlint.containsKey(uuid)) return;
            }
        }
        if (c.noEnchantGlint) cir.setReturnValue(false);
    }
}
