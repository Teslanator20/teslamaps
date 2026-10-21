// Ported from ADM's mixin/AdsBotsBodyMixin.java
package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.clear.bots.BotRegistry;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ADS teammate bots reach the client as real player entities; in the sim the crosshair, clicks and arrows go through them. */
@Mixin(Player.class)
public class AdsBotsBodyMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void ads$botsAreNotPickable(CallbackInfoReturnable<Boolean> cir) {
        if (BotRegistry.isBot(((Player) (Object) this).getUUID())) cir.setReturnValue(false);
    }
}
