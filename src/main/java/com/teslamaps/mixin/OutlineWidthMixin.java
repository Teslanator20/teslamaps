package com.teslamaps.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class OutlineWidthMixin {

    @ModifyArg(method = "*", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"))
    private Identifier teslamaps$outlineWidth(Identifier id) {
        return com.teslamaps.render.GlowOutlineWidth.swap(id);
    }
}
