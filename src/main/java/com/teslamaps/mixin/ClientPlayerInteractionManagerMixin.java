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
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onInteractBlock(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        // Detect chest clicks (secret)
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            ThreeWeirdos.onChestClick(pos);
            SecretWaypoints.onChestOpened(pos);
            SecretWaypoints.onSecretInteract("chest");
        }

        // Detect lever clicks (secret)
        if (state.is(Blocks.LEVER)) {
            WaterBoardSolver.onLeverClick(pos);
            SecretWaypoints.onSecretInteract("lever");
        }

        // Detect button clicks (secret)
        if (block instanceof ButtonBlock) {
            SecretWaypoints.onSecretInteract("button");
        }

        // Detect skull/head clicks (secret - wither essence)
        if (block instanceof SkullBlock || block instanceof WallSkullBlock) {
            SecretWaypoints.onSecretInteract("skull");
        }
    }
}
