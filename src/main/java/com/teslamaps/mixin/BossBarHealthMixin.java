package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.BossBarHealth;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossBarHealthMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideInTerminal(net.minecraft.client.gui.GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (com.teslamaps.features.TerminalSolver.hideClutter()) ci.cancel();
    }

    @WrapOperation(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    private Component teslamaps$appendHealth(LerpingBossEvent instance, Operation<Component> original) {
        return BossBarHealth.onRender(instance, original.call(instance));
    }

    @WrapOperation(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BossEvent;getProgress()F"))
    private float teslamaps$sadanProgress(net.minecraft.world.BossEvent instance, Operation<Float> original) {
        return com.teslamaps.features.SadanFeatures.interestProgress(instance, original.call(instance));
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private java.util.Collection<?> teslamaps$hideBars(java.util.Map<?, ?> map, Operation<java.util.Collection<?>> original) {
        java.util.Collection<?> all = original.call(map);
        if (com.teslamaps.features.BossBarHealth.hideUseless()) {
            all = com.teslamaps.features.BossBarHealth.filterUseless((java.util.Collection<LerpingBossEvent>) all);
        }
        if (!com.teslamaps.config.TeslaMapsConfig.get().dragonHideBossbars) return all;
        return com.teslamaps.features.DragonRecolor.filterBars((java.util.Collection<LerpingBossEvent>) all);
    }
}
