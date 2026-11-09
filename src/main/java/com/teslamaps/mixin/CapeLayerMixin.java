package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.capes.CapeManager;
import com.teslamaps.features.capes.WavyCapeRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the picked cape instead of the profile one, and also when there is none. */
@Mixin(CapeLayer.class)
public class CapeLayerMixin {

    @Shadow @Final private HumanoidModel<AvatarRenderState> model;

    @Shadow private boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType layer) {
        throw new AssertionError();
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void teslamaps$customCape(PoseStack pose, SubmitNodeCollector collector, int light,
                                      AvatarRenderState state, float f, float g, CallbackInfo ci) {
        Identifier texture = CapeManager.textureFor(state);
        if (texture == null) return;
        ci.cancel();

        if (state.isInvisible || !state.showCape) return;
        if (hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) return;

        boolean chestplate = hasLayer(state.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID);
        if (TeslaMapsConfig.get().capeWaviness > 0f
                && WavyCapeRenderer.submit(pose, collector, light, state, model, texture, chestplate)) {
            return;
        }
        pose.pushPose();
        if (chestplate) {
            pose.translate(0f, -0.053125f, 0.06875f);
        }
        collector.submitModel(model, state, pose, RenderTypes.entitySolid(texture),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
