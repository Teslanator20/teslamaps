package com.teslamaps.adm.adr.mixin;

import com.teslamaps.adm.adr.Adr;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// block changes in the client world (Odin's BlockUpdateEvent)
@Mixin(LevelChunk.class)
public abstract class AdrChunkMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void teslamaps$adrBlockChange(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (Adr.session() == null) return;
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ClientLevel)) return;
        if (self.getBlockState(pos) != state) Adr.onBlockUpdate(pos, state);
    }
}
