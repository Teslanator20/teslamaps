package com.teslamaps.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.AutoCopyScreenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(NativeImage.class)
public abstract class ScreenshotCopyMixin {
    @Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("HEAD"))
    private void teslamaps$copyScreenshot(File file, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().autoCopyScreenshot) return;
        if (!file.getPath().replace('\\', '/').contains("/screenshots/")) return;
        AutoCopyScreenshot.copy((NativeImage) (Object) this);
    }
}
