package com.teslamaps.mixin;

import com.teslamaps.features.CompactTabList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class CompactTabListMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$compact(GuiGraphicsExtractor g, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (objective == null && CompactTabList.render(g, width)) ci.cancel();
    }
}
