package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    @Unique
    private long teslamaps$lastClick = -1L;

    // onPress of the disconnect button
    @Inject(method = "lambda$createPauseMenu$10", at = @At("HEAD"), cancellable = true)
    private void teslamaps$confirmDisconnect(Button button, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.confirmDisconnect) return;
        long now = System.currentTimeMillis();
        if (teslamaps$lastClick == -1L || now - teslamaps$lastClick < c.confirmDisconnectMs) {
            teslamaps$lastClick = now;
            button.setMessage(Component.literal("§cClick again to disconnect"));
            ci.cancel();
            return;
        }
        teslamaps$lastClick = -1L;
    }
}
