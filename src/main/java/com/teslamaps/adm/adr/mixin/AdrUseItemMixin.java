package com.teslamaps.adm.adr.mixin;

import com.teslamaps.adm.adr.Adr;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a Simon Says click about to be used; GameModeRecordMixin tells whether it went through
@Mixin(value = Minecraft.class, priority = 900)
public class AdrUseItemMixin {
    @Shadow public HitResult hitResult;

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private void teslamaps$adrUseAttempt(CallbackInfo ci) {
        if (this.hitResult instanceof BlockHitResult b) Adr.onUseAttempt(b.getBlockPos());
    }
}
