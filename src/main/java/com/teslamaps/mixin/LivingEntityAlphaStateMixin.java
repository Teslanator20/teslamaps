package com.teslamaps.mixin;

import com.teslamaps.render.EntityAlphaHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityAlphaStateMixin implements EntityAlphaHolder {
    @Unique
    private int teslamaps$alpha = 255;

    @Override
    public int teslamaps$alpha() {
        return teslamaps$alpha;
    }

    @Override
    public void teslamaps$setAlpha(int alpha) {
        teslamaps$alpha = alpha;
    }
}
