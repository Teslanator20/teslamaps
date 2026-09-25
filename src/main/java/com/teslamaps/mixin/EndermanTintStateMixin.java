package com.teslamaps.mixin;

import com.teslamaps.render.ModelTintHolder;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EndermanRenderState.class)
public class EndermanTintStateMixin implements ModelTintHolder {
    @Unique
    private int teslamaps$tint;

    @Override
    public int teslamaps$tint() {
        return teslamaps$tint;
    }

    @Override
    public void teslamaps$setTint(int tint) {
        teslamaps$tint = tint;
    }
}
