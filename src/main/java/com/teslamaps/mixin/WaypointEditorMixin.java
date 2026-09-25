package com.teslamaps.mixin;

import com.teslamaps.dungeon.DungeonWaypoints;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class WaypointEditorMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$waypointEdit(CallbackInfo ci) {
        if (DungeonWaypoints.onUseItem()) ci.cancel();
    }
}
