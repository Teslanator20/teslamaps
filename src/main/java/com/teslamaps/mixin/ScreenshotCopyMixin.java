/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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
