package com.teslamaps.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/// Armor model override from `/tmap customize`.
@Mixin(EquipmentLayerRenderer.class)
public class CustomArmorModelMixin {

    @ModifyVariable(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("HEAD"), argsOnly = true)
    private ResourceKey<EquipmentAsset> teslamaps$customArmorModel(ResourceKey<EquipmentAsset> assetKey, @Local(argsOnly = true) ItemStack stack) {
        if (!Customize.active()) return assetKey;
        String uuid = Customize.uuid(stack);
        if (uuid.isEmpty()) return assetKey;
        Identifier identifier = TeslaMapsConfig.get().customArmorModel.get(uuid);
        return identifier == null ? assetKey : ResourceKey.create(EquipmentAssets.ROOT_ID, identifier);
    }
}
