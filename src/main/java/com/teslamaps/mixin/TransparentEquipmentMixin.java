package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.TransparentArmor;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public class TransparentEquipmentMixin {
    private static final String LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType teslamaps$translucentArmor(Identifier tex, Operation<RenderType> original) {
        return TransparentArmor.armor < 255 ? RenderTypes.entityTranslucent(tex) : original.call(tex);
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType teslamaps$translucentGlintArmor(Identifier tex, Operation<RenderType> original) {
        return TransparentArmor.armor < 255 ? RenderTypes.entityTranslucent(tex) : original.call(tex);
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;getColorForLayer(Lnet/minecraft/client/resources/model/EquipmentClientInfo$Layer;I)I"))
    private int teslamaps$armorColor(EquipmentClientInfo.Layer layer, int dye, Operation<Integer> original) {
        int color = original.call(layer, dye);
        return TransparentArmor.armor < 255 && color != 0 ? TransparentArmor.apply(TransparentArmor.armor, color) : color;
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType teslamaps$translucentTrims(Identifier tex, boolean decal, Operation<RenderType> original) {
        return TransparentArmor.armor < 255 ? RenderTypes.entityTranslucent(tex) : original.call(tex, decal);
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",
            ordinal = 1))
    private <S> void teslamaps$trimAlpha(OrderedSubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
                                         RenderType type, int light, int overlay, int color, UvMapping uv, int outline,
                                         Operation<Void> original) {
        int c = TransparentArmor.armor < 255 ? TransparentArmor.apply(TransparentArmor.armor, color) : color;
        original.call(collector, model, state, pose, type, light, overlay, c, uv, outline);
    }
}
