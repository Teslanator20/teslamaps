package com.teslamaps.mixin;

import com.teslamaps.features.SlotLock;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// cancel the whole drop (Q key) for a locked selected slot — this also stops the
// ServerboundPlayerActionPacket, so the server doesn't drop the item either
@Mixin(LocalPlayer.class)
public abstract class DropProtectMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$blockLockedDrop(boolean full, CallbackInfoReturnable<Boolean> cir) {
        if (SlotLock.blockSelectedDrop()
                || com.teslamaps.features.ItemProtection.blockSelectedDrop()
                || com.teslamaps.features.LimitDroppingItems.block()) {
            cir.setReturnValue(false);
        }
    }
}
