package com.teslamaps.mixin;

import com.teslamaps.features.IHateDoors;
import com.teslamaps.render.DoorGlassModel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// door blocks get a model that draws glass at marked door positions (IHateDoors); render only
@Mixin(BlockStateModelSet.class)
public abstract class GlassWitherDoorModelMixin {

    @Unique private final Map<BlockState, DoorGlassModel> teslamaps$wrapped = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void teslamaps$glassDoor(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        if (!DoorGlassModel.wraps(state.getBlock())) return;
        BlockStateModel original = cir.getReturnValue();
        cir.setReturnValue(teslamaps$wrapped.computeIfAbsent(state, s -> new DoorGlassModel(original)));
    }
}
