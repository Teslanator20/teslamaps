package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.TransparentArmor;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public class TransparentEquipmentMixin {
    private static final String LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

    @Unique
    private static RenderType teslamaps$trims;

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType teslamaps$translucentArmor(Identifier tex, Operation<RenderType> original) {
        return TransparentArmor.armor < 255 ? RenderTypes.armorTranslucent(tex) : original.call(tex);
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;getColorForLayer(Lnet/minecraft/client/resources/model/EquipmentClientInfo$Layer;I)I"))
    private int teslamaps$armorColor(EquipmentClientInfo.Layer layer, int dye, Operation<Integer> original) {
        int color = original.call(layer, dye);
        return TransparentArmor.armor < 255 && color != 0 ? TransparentArmor.apply(TransparentArmor.armor, color) : color;
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType teslamaps$translucentTrims(boolean decal, Operation<RenderType> original) {
        if (TransparentArmor.armor >= 255) return original.call(decal);
        if (teslamaps$trims == null) teslamaps$trims = RenderTypes.armorTranslucent(Sheets.ARMOR_TRIMS_SHEET);
        return teslamaps$trims;
    }

    @WrapOperation(method = LAYERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            ordinal = 2))
    private <S> void teslamaps$trimAlpha(OrderedSubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
                                         RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int outline,
                                         ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
        int c = TransparentArmor.armor < 255 ? TransparentArmor.apply(TransparentArmor.armor, color) : color;
        original.call(collector, model, state, pose, type, light, overlay, c, sprite, outline, crumbling);
    }
}
