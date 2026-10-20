package com.teslamaps.adm.leap.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface AdmCameraAccessor {
    @Accessor("eyeHeight")
    float adm$getEyeHeight();

    @Accessor("eyeHeight")
    void adm$setEyeHeight(float v);

    @Accessor("eyeHeightOld")
    float adm$getEyeHeightOld();

    @Accessor("eyeHeightOld")
    void adm$setEyeHeightOld(float v);
}
