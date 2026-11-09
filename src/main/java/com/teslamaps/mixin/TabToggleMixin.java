package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.features.CompactTabList;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public class TabToggleMixin {
    @ModifyExpressionValue(method = "extractTabList", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean teslamaps$toggleTab(boolean original) {
        return CompactTabList.tabShown(original);
    }
}
