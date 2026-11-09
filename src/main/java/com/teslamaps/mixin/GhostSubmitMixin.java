package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.render.GhostRender;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Ported from Bax's GhostSubmitMixin
@Mixin(SubmitNodeCollection.class)
public class GhostSubmitMixin {

    @ModifyVariable(method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",
            at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private int teslamaps$ghostModelColor(int color) {
        int a = GhostRender.submitting;
        return a >= 255 ? color : GhostRender.withAlpha(color, a);
    }

    @WrapOperation(method = "submitItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/geometry/ItemQuads;translucent()Ljava/util/List;"))
    private List<BakedQuad> teslamaps$ghostTranslucent(ItemQuads quads, Operation<List<BakedQuad>> original) {
        return GhostRender.submitting < 255 ? quads.all() : original.call(quads);
    }

    @WrapOperation(method = "submitItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/geometry/ItemQuads;solid()Ljava/util/List;"))
    private List<BakedQuad> teslamaps$ghostSolid(ItemQuads quads, Operation<List<BakedQuad>> original) {
        return GhostRender.submitting < 255 ? List.of() : original.call(quads);
    }
}
