package com.teslamaps.mixin;

import com.teslamaps.render.DragonTintHolder;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EnderDragonRenderState.class)
public class EnderDragonRenderStateMixin implements DragonTintHolder {
    @Unique
    private int teslamaps$overlay = -1;
    @Unique
    private boolean teslamaps$pet;

    @Override
    public int teslamaps$overlay() {
        return teslamaps$overlay;
    }

    @Override
    public void teslamaps$setOverlay(int overlay) {
        teslamaps$overlay = overlay;
    }

    @Override
    public boolean teslamaps$isPet() {
        return teslamaps$pet;
    }

    @Override
    public void teslamaps$setPet(boolean pet) {
        teslamaps$pet = pet;
    }
}
