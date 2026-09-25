package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.BossBarHealth;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossHealthOverlay.class)
public class BossBarHealthMixin {
    @WrapOperation(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    private Component teslamaps$appendHealth(LerpingBossEvent instance, Operation<Component> original) {
        return BossBarHealth.onRender(instance, original.call(instance));
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private java.util.Collection<?> teslamaps$hideBars(java.util.Map<?, ?> map, Operation<java.util.Collection<?>> original) {
        java.util.Collection<?> all = original.call(map);
        if (!com.teslamaps.config.TeslaMapsConfig.get().dragonHideBossbars) return all;
        return com.teslamaps.features.DragonRecolor.filterBars((java.util.Collection<LerpingBossEvent>) all);
    }
}
