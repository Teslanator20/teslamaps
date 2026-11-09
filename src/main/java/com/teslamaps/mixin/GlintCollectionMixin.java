package com.teslamaps.mixin;

import com.teslamaps.features.customize.GlintColors;
import com.teslamaps.injected.GlintColorHolder;
import com.teslamaps.render.GhostRender;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.Submit.class)
public class GlintCollectionMixin implements GlintColorHolder {

    @Unique private int teslamaps$glint;
    @Unique private int teslamaps$ghost = 255;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void teslamaps$capture(CallbackInfo ci) {
        teslamaps$glint = GlintColors.submitting;
        teslamaps$ghost = GhostRender.submitting;
    }

    @Override
    public int teslamaps$glintColor() {
        return teslamaps$glint;
    }

    @Override
    public int teslamaps$ghostAlpha() {
        return teslamaps$ghost;
    }
}
