package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.fishing.FishingTweaks;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHook.class)
public class FishingHookFixMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean teslamaps$lavaBobber(FluidState state, TagKey<Fluid> tag, Operation<Boolean> original) {
        return original.call(state, tag) || (FishingTweaks.fixBobber() && state.is(FluidTags.LAVA));
    }

    @WrapOperation(method = "onSyncedDataUpdated", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;"))
    private Entity teslamaps$noCountdownHook(Level level, int id, Operation<Entity> original) {
        Entity e = original.call(level, id);
        if (e instanceof ArmorStand && e.getId() == ((Entity) (Object) this).getId() + 1 && FishingTweaks.fixBobber()) return null;
        return e;
    }
}
