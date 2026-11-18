package com.teslamaps.mixin;

import com.teslamaps.safari.SafariEntryGuard;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class SafariManagerMixin {

    @ModifyVariable(method = "handleContainerInput", at = @At("HEAD"), argsOnly = true)
    private ContainerInput teslamaps$entryInput(ContainerInput input, int containerId, int slotId, int button) {
        return SafariEntryGuard.entryInput(input, containerId, slotId);
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void teslamaps$guardEntry(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (SafariEntryGuard.shouldBlockEntry(containerId, slotId)) ci.cancel();
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void teslamaps$guardManager(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SafariEntryGuard.shouldBlock(entity)) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void teslamaps$guardAttack(Player player, Entity entity, CallbackInfo ci) {
        if (SafariEntryGuard.shouldBlock(entity)) ci.cancel();
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$guardTicket(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SafariEntryGuard.shouldBlockTicket(player, hand)) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void teslamaps$guardTicketOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (SafariEntryGuard.shouldBlockTicket(player, hand)) cir.setReturnValue(InteractionResult.FAIL);
    }
}
