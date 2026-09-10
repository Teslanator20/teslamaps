package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.safari.SafariData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientLevel.class)
public class SparkleForceMixin {
    @ModifyVariable(method = "doAddParticle", at = @At("HEAD"), index = 2, argsOnly = true)
    private boolean teslamaps$forceSparkle(boolean force, ParticleOptions options) {
        if (force || options.getType() != ParticleTypes.WAX_ON.getType()) return force;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.safariSparkleForce && c.section("Critter Safari") && SafariData.inSafari();
    }
}
