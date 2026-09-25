package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.customize.GlintColors;
import com.teslamaps.injected.GlintColorHolder;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class GlintCollectionMixin implements GlintColorHolder {

    @Shadow @Final private List<SubmitNodeStorage.ItemSubmit> itemSubmits;
    @Unique private final Map<SubmitNodeStorage.ItemSubmit, Integer> teslamaps$glints = new IdentityHashMap<>();

    @Inject(method = "submitItem", at = @At("RETURN"))
    private void teslamaps$glintSubmit(PoseStack pose, ItemDisplayContext ctx, int light, int overlay, int outline,
                                       int[] tints, List<BakedQuad> quads, ItemStackRenderState.FoilType foil, CallbackInfo ci) {
        int c = GlintColors.submitting;
        if (c == 0 || foil == ItemStackRenderState.FoilType.NONE || itemSubmits.isEmpty()) return;
        teslamaps$glints.put(itemSubmits.get(itemSubmits.size() - 1), c);
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void teslamaps$glintClear(CallbackInfo ci) {
        if (!teslamaps$glints.isEmpty()) teslamaps$glints.clear();
    }

    @Override
    public int teslamaps$glintColor(SubmitNodeStorage.ItemSubmit submit) {
        if (teslamaps$glints.isEmpty()) return 0;
        Integer c = teslamaps$glints.get(submit);
        return c == null ? 0 : c;
    }
}
