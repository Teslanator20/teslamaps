package com.teslamaps.mixin;

import com.teslamaps.features.cosmetic.CustomHubMap;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public class HubMapRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void teslamaps$customHubMap(MapId id, MapItemSavedData data, MapRenderState state, CallbackInfo ci) {
        CustomHubMap.apply(id, state);
    }
}
