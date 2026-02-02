package com.teslamaps.mixin;

import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.LividSolver;
import com.teslamaps.slayer.SlayerHUD;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onChatMessage(Text message, CallbackInfo ci) {
        String text = message.getString();
        if (text.contains("SLAYER QUEST COMPLETE")) {
            SlayerHUD.onSlayerComplete();
        } else if (text.contains("SLAYER QUEST FAILED")) {
            SlayerHUD.onSlayerFailed();
        } else if (text.contains("A Wither Key was picked up!") || text.contains("has obtained Wither Key")) {
            StarredMobESP.onWitherKeyPickup();
        } else if (text.contains("A Blood Key was picked up!") || text.contains("has obtained Blood Key")) {
            StarredMobESP.onBloodKeyPickup();
        }

        // Pass all messages to AutoGFS for dungeon start/puzzle fail detection
        AutoGFS.onChatMessage(text);

        // Pass to LividSolver for fight start detection
        LividSolver.onChatMessage(text);
    }
}
