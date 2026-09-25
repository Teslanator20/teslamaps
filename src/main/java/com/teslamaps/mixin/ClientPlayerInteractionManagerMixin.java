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
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onUseItem(net.minecraft.world.entity.player.Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (com.teslamaps.features.SecretTrackerLocator.onUseItem(player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        com.teslamaps.features.CombatTimers.onUseItem(player.getItemInHand(hand));
        com.teslamaps.features.HotspotRadar.onUseItem(player.getItemInHand(hand));
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void teslamaps$onInteractEntity(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity entity, net.minecraft.world.phys.EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
            if (com.teslamaps.dungeon.puzzle.ArrowAlignSolver.blockClick(frame)) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }
            com.teslamaps.dungeon.puzzle.ArrowAlignSolver.onFrameClick(frame.blockPosition());
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (com.teslamaps.features.SecretTrackerLocator.onUseItem(player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        com.teslamaps.features.HotspotRadar.onUseItem(player.getItemInHand(hand));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos pos = hitResult.getBlockPos();

        if (com.teslamaps.dungeon.puzzle.SimonSaysSolver.blockClick(pos)
                || com.teslamaps.dungeon.puzzle.TicTacToe.blockClick(pos)
                || com.teslamaps.dungeon.puzzle.QuizSolver.blockClick(pos)
                || ThreeWeirdos.blockClick(pos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        com.teslamaps.features.KingRelics.onUseBlock(state, hand);
        com.teslamaps.dungeon.puzzle.BoulderSolver.onBlockInteract(pos);

        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) {
            com.teslamaps.features.ItemIndex.onChestClick(pos);
            com.teslamaps.features.SearchWaypoint.onChestClick(pos);
            com.teslamaps.features.MinionFeatures.onChestClick(pos);
        }

        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            ThreeWeirdos.onChestClick(pos);
            SecretWaypoints.onChestOpened(pos);
            SecretWaypoints.onSecretInteract("chest");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (state.is(Blocks.BEE_NEST) || state.is(Blocks.BEEHIVE)) {
            com.teslamaps.safari.SafariESP.onNestClick(pos);
        }

        if (state.is(Blocks.LEVER)) {
            WaterBoardSolver.onLeverClick(pos);
            SecretWaypoints.onSecretInteract("lever", pos);
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (block instanceof ButtonBlock) {
            SecretWaypoints.onSecretInteract("button", pos);
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (block instanceof SkullBlock || block instanceof WallSkullBlock) {
            SecretWaypoints.onSecretInteract("skull", pos);
            SecretWaypoints.onSkullClick(pos);
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }
    }

    // after the block had its chance (chests, levers still work), before the held item is placed
    @Inject(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"),
            cancellable = true)
    private void teslamaps$preventPlacing(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (com.teslamaps.features.PreventPlacing.block(player.getItemInHand(hand))) cir.setReturnValue(InteractionResult.PASS);
    }
}
