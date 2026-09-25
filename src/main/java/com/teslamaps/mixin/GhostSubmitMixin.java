package com.teslamaps.mixin;

import com.teslamaps.render.GhostRender;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Ported from Bax's GhostSubmitMixin
@Mixin(SubmitNodeCollection.class)
public class GhostSubmitMixin {

    @ModifyVariable(method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int teslamaps$ghostModelColor(int color) {
        int a = GhostRender.submitting;
        return a >= 255 ? color : GhostRender.withAlpha(color, a);
    }

    @ModifyVariable(method = "submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int[] teslamaps$ghostItemTints(int[] tints) {
        return GhostRender.submitting >= 255 ? tints : GhostRender.trackItem(tints);
    }
}
