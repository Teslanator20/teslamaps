package com.teslamaps.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.features.ScreenshotManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(NativeImage.class)
public abstract class ScreenshotCopyMixin {

    // TAIL, so the file exists by the time the manager looks at it
    @Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("TAIL"))
    private void teslamaps$screenshotSaved(File file, CallbackInfo ci) {
        if (!file.getPath().replace('\\', '/').contains("/screenshots/")) return;
        ScreenshotManager.onSaved((NativeImage) (Object) this, file);
    }
}
