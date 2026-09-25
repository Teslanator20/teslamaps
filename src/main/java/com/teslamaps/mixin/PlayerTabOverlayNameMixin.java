package com.teslamaps.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayNameMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void teslamaps$dungeonClassColor(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component out = com.teslamaps.features.DungeonTabColors.rewrite(info, cir.getReturnValue());
        if (out != cir.getReturnValue()) cir.setReturnValue(out);
    }
}
