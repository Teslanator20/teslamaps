package com.teslamaps.mixin;

import com.teslamaps.features.SlotLock;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// cancel the whole drop (Q key) for a locked selected slot — this also stops the
// ServerboundPlayerActionPacket, so the server doesn't drop the item either
@Mixin(MultiPlayerGameMode.class)
public abstract class DropProtectMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$blockLockedDrop(LocalPlayer player, boolean full, CallbackInfo ci) {
        if (SlotLock.blockDrop()
                || com.teslamaps.features.ItemProtection.blockSelectedDrop()
                || com.teslamaps.features.LimitDroppingItems.block()) {
            ci.cancel();
        }
    }
}
