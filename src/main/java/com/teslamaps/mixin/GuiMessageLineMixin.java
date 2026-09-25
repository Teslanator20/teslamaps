package com.teslamaps.mixin;

// Ported from Skysoft's GuiMessageLineMixin
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiMessage.Line.class)
public class GuiMessageLineMixin {
    @ModifyReturnValue(method = "tag", at = @At("RETURN"))
    private GuiMessageTag teslamaps$hideIndicator(GuiMessageTag original) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.hideChatIndicator && c.section("Chat") ? null : original;
    }
}
