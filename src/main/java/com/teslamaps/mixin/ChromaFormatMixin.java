package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.ChromaColors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// §z chroma code, ported from Devonian's StringDecomposerMixin
@Mixin(StringDecomposer.class)
public class ChromaFormatMixin {

    @WrapOperation(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/ChatFormatting;getByCode(C)Lnet/minecraft/ChatFormatting;"))
    private static ChatFormatting teslamaps$chromaCode(char c, Operation<ChatFormatting> original, @Local(ordinal = 2) LocalRef<Style> style) {
        ChatFormatting f = original.call(c);
        if ((c == 'z' || c == 'Z') && TeslaMapsConfig.get().chromaFormatCode)
            style.set(style.get().applyLegacyFormat(ChatFormatting.WHITE).withColor(ChromaColors.CHROMA_CODE));
        return f;
    }
}
