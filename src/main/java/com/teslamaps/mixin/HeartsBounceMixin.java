package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// NEU "Stop Hearts Bouncing": no regeneration wave over the hearts
@Mixin(Hud.class)
public class HeartsBounceMixin {

    @WrapOperation(method = "extractPlayerHealth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean teslamaps$noRegenBounce(Player player, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (TeslaMapsConfig.get().stopHeartsBouncing) return false;
        return original.call(player, effect);
    }
}
