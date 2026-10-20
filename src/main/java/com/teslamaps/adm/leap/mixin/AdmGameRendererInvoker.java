package com.teslamaps.adm.leap.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The private halves of GameRenderer.extract a POV pass needs (not extractGui, which would reset the frame's GUI state). */
@Mixin(GameRenderer.class)
public interface AdmGameRendererInvoker {
    @Invoker("extractWindow")
    void adm$extractWindow();

    @Invoker("extractOptions")
    void adm$extractOptions();

    @Invoker("extractCamera")
    void adm$extractCamera(DeltaTracker deltaTracker, float worldPartialTicks, float cameraEntityPartialTicks);
}
