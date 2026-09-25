package com.teslamaps.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(FontSet.class)
public interface FontSetProvidersAccessor {
    @Accessor("allProviders")
    List<GlyphProvider.Conditional> teslamaps$getAllProviders();
}
