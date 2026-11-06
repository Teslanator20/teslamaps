package com.teslamaps.mixin;

import com.teslamaps.features.AbilityMenuFix;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Ported from GanKura's HeldSlotSyncMixin.
// require = 0 everywhere: with GanKura installed its HeldSlotSyncMixin redirects the same ensureHasSentCarriedItem()
// calls, ours then finds no target and the hard failure disconnected the player on login ("Packet handling error",
// Mixin transformation of MultiPlayerGameMode failed). Now ours just doesn't apply and GanKura's does the job.
@Mixin(MultiPlayerGameMode.class)
public class AbilityMenuSlotMixin {

    @Shadow
    private void ensureHasSentCarriedItem() {
        throw new AssertionError();
    }

    @Redirect(method = "tick", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V"))
    private void teslamaps$holdSlotChange(MultiPlayerGameMode instance) {
        if (AbilityMenuFix.holdSlotChange()) return;
        ensureHasSentCarriedItem();
    }

    @Inject(method = "useItem", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V", shift = At.Shift.AFTER))
    private void teslamaps$abilityUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        AbilityMenuFix.onUseItem(player.getItemInHand(hand));
    }

    @Inject(method = "useItemOn", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;ensureHasSentCarriedItem()V", shift = At.Shift.AFTER))
    private void teslamaps$abilityUseOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        AbilityMenuFix.onUseItem(player.getItemInHand(hand));
    }
}
