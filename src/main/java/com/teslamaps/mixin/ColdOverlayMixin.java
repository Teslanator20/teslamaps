package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.safari.SafariData;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Hypixel drives the Icy Biome cold with the vanilla freeze counter, so zeroing it here removes
// the frost vignette without touching the cold value itself.
@Mixin(Hud.class)
public class ColdOverlayMixin {

    @ModifyExpressionValue(method = "extractCameraOverlays",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"))
    private int teslamaps$hideSafariCold(int original) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.section("Critter Safari") || !cfg.safariHideCold) return original;
        // the freeze counter keeps ticking down after leaving the Icy Biome, so gate on the whole
        // Safari - nothing else in here freezes you anyway
        return SafariData.inSafari() ? 0 : original;
    }
}
