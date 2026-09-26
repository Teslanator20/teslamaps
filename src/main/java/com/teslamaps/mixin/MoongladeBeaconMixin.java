package com.teslamaps.mixin;

import com.teslamaps.features.foraging.MoongladeBeacon;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BeaconBlockEntity.class)
public abstract class MoongladeBeaconMixin {
    @Inject(method = "getBeamSections", at = @At("HEAD"), cancellable = true)
    private void teslamaps$moongladeBeam(CallbackInfoReturnable<List<BeaconBeamOwner.Section>> cir) {
        List<BeaconBeamOwner.Section> s = MoongladeBeacon.sections(((BeaconBlockEntity) (Object) this).getBlockPos());
        if (s != null) cir.setReturnValue(s);
    }
}
