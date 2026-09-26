package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @ModifyExpressionValue(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
    private boolean teslamaps$autoSprint(boolean original) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.section("Auto") || !cfg.autoSprint || !com.teslamaps.features.AutoSprint.allowed()) return original;
        return true;
    }
}
