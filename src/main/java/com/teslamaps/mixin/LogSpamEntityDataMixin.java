package com.teslamaps.mixin;

import com.teslamaps.features.LogSpamFilter;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// wrong-typed entity data from Hypixel; the exception per packet causes small lag spikes
@Mixin(SynchedEntityData.class)
public class LogSpamEntityDataMixin {

    @Inject(method = "assignValue", at = @At(value = "NEW", target = "Ljava/lang/IllegalStateException;"), cancellable = true)
    private void teslamaps$invalidData(CallbackInfo ci) {
        if (LogSpamFilter.quiet()) ci.cancel();
    }
}
