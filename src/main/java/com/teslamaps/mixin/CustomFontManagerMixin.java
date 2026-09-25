package com.teslamaps.mixin;

import com.teslamaps.features.CustomFont;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(FontManager.class)
public class CustomFontManagerMixin {
    @Shadow @Final private Map<Identifier, FontSet> fontSets;

    @Inject(method = "apply", at = @At("TAIL"))
    private void teslamaps$reapplyFont(CallbackInfo ci) {
        CustomFont.onFontsLoaded(fontSets);
    }
}
