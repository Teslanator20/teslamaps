package com.teslamaps.mixin;

import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver;
import com.teslamaps.features.SecretWaypoints;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void onInteractBlock(net.minecraft.client.network.ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        // Detect chest clicks (secret)
        if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)) {
            ThreeWeirdos.onChestClick(pos);
            SecretWaypoints.onChestOpened(pos);
            SecretWaypoints.onSecretInteract("chest");
        }

        // Detect lever clicks (secret)
        if (state.isOf(Blocks.LEVER)) {
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
