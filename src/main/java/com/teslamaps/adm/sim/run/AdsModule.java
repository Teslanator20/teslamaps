// Ported from ADM's p3sim/run/RunSystem.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.run.api.PuzzleType;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/** A work item's module: a RunSystem plus hooks for settings, commands and puzzle types. */
public interface AdsModule extends RunSystem {
    /** The work item that owns this module. */
    String owner();

    /** Once at game start, client thread: SimNet kinds, Fabric callbacks. */
    default void onInit() {}

    /** Creates this module's settings (SimSettings). Once at game start. */
    default void registerSettings() {}

    default List<AdsCommand> devCommands() { return List.of(); }

    default List<AdsCommand> runCommands() { return List.of(); }

    default List<PuzzleType> puzzleTypes() { return List.of(); }

    // Server thread, as the clicking player, main hand, only in COUNTDOWN/CLEAR/BLOOD. null = not mine.

    default InteractionResult onUseBlock(Run run, ServerPlayer player, InteractionHand hand, BlockHitResult hit) { return null; }

    default InteractionResult onUseEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) { return null; }

    default InteractionResult onAttackEntity(Run run, ServerPlayer player, InteractionHand hand, Entity entity) { return null; }

    default InteractionResult onUseItem(Run run, ServerPlayer player, InteractionHand hand) { return null; }
}
