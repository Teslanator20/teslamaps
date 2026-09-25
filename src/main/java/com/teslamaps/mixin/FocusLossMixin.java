package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class FocusLossMixin {

    @Redirect(method = "pauseIfInactive", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z", opcode = Opcodes.GETFIELD))
    private boolean teslamaps$focusLoss(Options options) {
        return options.pauseOnLostFocus && !TeslaMapsConfig.get().focusLossPrevention;
    }
}
