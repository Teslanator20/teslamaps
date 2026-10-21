package com.teslamaps.mixin;

import com.teslamaps.features.NoammTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class ArrowFixMixin extends LivingEntity {
    protected ArrowFixMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void teslamaps$noShortbowPullback(CallbackInfo ci) {
        if (!NoammTweaks.arrowFixOn()) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        if (NoammTweaks.isShortbow(useItem)) {
            useItem = ItemStack.EMPTY;
            useItemRemaining = 0;
        }
    }
}
