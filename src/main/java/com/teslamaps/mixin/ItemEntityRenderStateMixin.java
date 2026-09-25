package com.teslamaps.mixin;

import com.teslamaps.render.ItemScaleHolder;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemScaleHolder {
    @Unique
    private float teslamaps$itemScale = 1f;

    @Override
    public float teslamaps$itemScale() {
        return teslamaps$itemScale;
    }

    @Override
    public void teslamaps$setItemScale(float scale) {
        teslamaps$itemScale = scale;
    }
}
