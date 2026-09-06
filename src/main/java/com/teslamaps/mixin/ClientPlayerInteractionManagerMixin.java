package com.teslamaps.mixin;

import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver;
import com.teslamaps.features.SecretWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "useItem", at = @At("HEAD"))
    private void teslamaps$onUseItem(net.minecraft.world.entity.player.Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        com.teslamaps.features.CombatTimers.onUseItem(player.getItemInHand(hand));
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos pos = hitResult.getBlockPos();

        if (com.teslamaps.dungeon.puzzle.SimonSaysSolver.blockClick(pos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            ThreeWeirdos.onChestClick(pos);
            SecretWaypoints.onChestOpened(pos);
            SecretWaypoints.onSecretInteract("chest");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (state.is(Blocks.LEVER)) {
            WaterBoardSolver.onLeverClick(pos);
            SecretWaypoints.onSecretInteract("lever");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (block instanceof ButtonBlock) {
            SecretWaypoints.onSecretInteract("button");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (block instanceof SkullBlock || block instanceof WallSkullBlock) {
            SecretWaypoints.onSecretInteract("skull");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }
    }
}
