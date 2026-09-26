package com.teslamaps.mixin;

import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.puzzle.SimonSaysSolver;
import com.teslamaps.dungeon.puzzle.SpiritBearTimer;
import com.teslamaps.dungeon.puzzle.TerracottaTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class BlockUpdateMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void onSetBlockState(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.BlockEsp.listening() && ((Level) (Object) this).isClientSide()) {
            com.teslamaps.features.BlockEsp.onBlockUpdate(pos, newState);
        }
        if (!DungeonManager.isInDungeon()) {
            if (com.teslamaps.features.mining.TreasureChestHelper.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.features.mining.TreasureChestHelper.onBlockUpdate(pos, newState);
            }
            if (com.teslamaps.features.foraging.ForagingHighlighter.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.features.foraging.ForagingHighlighter.onBlockUpdate((Level) (Object) this, pos, newState);
            }
            if (com.teslamaps.features.pets.RockMilestone.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.features.pets.RockMilestone.onBlockUpdate(pos, ((Level) (Object) this).getBlockState(pos), newState);
            }
            if (com.teslamaps.features.BpsDisplay.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.features.BpsDisplay.onBlockUpdate(pos, ((Level) (Object) this).getBlockState(pos), newState);
            }
            if (com.teslamaps.features.mining.MineshaftPity.tracking()) {
                Level level = (Level) (Object) this;
                if (level.isClientSide()) com.teslamaps.features.mining.MineshaftPity.onBlockUpdate(pos, level.getBlockState(pos), newState);
            }
            if (com.teslamaps.features.mining.FlowstateHelper.tracking()) {
                Level level = (Level) (Object) this;
                if (level.isClientSide()) com.teslamaps.features.mining.FlowstateHelper.onBlockUpdate(pos, level.getBlockState(pos), newState);
            }
            if (com.teslamaps.slayer.VoidgloomBeacon.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.slayer.VoidgloomBeacon.onBlockUpdate(pos, ((Level) (Object) this).getBlockState(pos), newState);
            }
            if (com.teslamaps.features.events.Carnival.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.features.events.Carnival.onBlockUpdate(pos, ((Level) (Object) this).getBlockState(pos), newState);
            }
            if (com.teslamaps.features.mining.MiningNotifications.listening() && ((Level) (Object) this).isClientSide()) {
                com.teslamaps.features.mining.MiningNotifications.onBlockUpdate(pos, ((Level) (Object) this).getBlockState(pos), newState);
            }
            if ((com.teslamaps.features.rift.LivingMetalHelper.listening() || com.teslamaps.features.rift.LivingCaveDefenseBlocks.listening()
                    || com.teslamaps.features.rift.LivingCaveSnakes.listening()) && ((Level) (Object) this).isClientSide()) {
                BlockState old = ((Level) (Object) this).getBlockState(pos);
                com.teslamaps.features.rift.LivingMetalHelper.onBlockUpdate(pos, old, newState);
                com.teslamaps.features.rift.LivingCaveDefenseBlocks.onBlockUpdate(pos, old, newState);
                com.teslamaps.features.rift.LivingCaveSnakes.onBlockUpdate(pos, old, newState);
            }
            return;
        }

        Level world = (Level)(Object)this;
        if (!world.isClientSide()) return;

        BlockState oldState = world.getBlockState(pos);

        SimonSaysSolver.onBlockUpdate(pos, oldState, newState);
        TerracottaTimer.onBlockUpdate(pos, oldState, newState);
        SpiritBearTimer.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.features.OverkillCounter.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.features.CustomTitles.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.dungeon.WitherDragons.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.features.ColorPortal.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.dungeon.F7Devices.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.features.DimensionalSlash.onBlockUpdate(pos, oldState, newState);
    }
}
