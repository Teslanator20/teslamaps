// Ported from ADM's p3sim/run/RunInput.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimServer;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * One Fabric callback per click event, in a phase before the default one (before SimItems and Sim.guardBlocks). Asks the modules in
 * AdsModules order; the first non-null answer wins. Only while a Run is in a dungeon stage, server side, sim world, main hand.
 */
public final class RunInput {
    private RunInput() {}

    /** Before Event.DEFAULT_PHASE. */
    public static final Identifier PHASE = Identifier.fromNamespaceAndPath("teslamaps", "ads_run");

    private static boolean installed = false;

    /** First non-null answer wins; a module that throws is logged and treated as no opinion. */
    public static <R> R ask(List<AdsModule> modules, String what, Function<AdsModule, R> call) {
        for (AdsModule m : modules) {
            R r;
            try {
                r = call.apply(m);
            } catch (Throwable t) {
                Ec.LOG.error("[ads] " + m.id() + "." + what + " failed", t);
                r = null;
            }
            if (r != null) return r;
        }
        return null;
    }

    /** The run to route to, or null (client side, another world, no run, not a dungeon stage). */
    private static Run runFor(Level level, Player player) {
        if (level.isClientSide() || !(player instanceof ServerPlayer) || !SimServer.isSimLevel(level)) return null;
        Run run = Runs.current;
        if (run == null) return null;
        return run.isOver() || !run.stage.inDungeon() ? null : run;
    }

    /** Registers the four callbacks (once). */
    public static void install() {
        if (installed) return;
        installed = true;
        UseBlockCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(PHASE, (player, level, hand, hit) -> {
            Run run = runFor(level, player);
            if (run == null || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ServerPlayer sp = (ServerPlayer) player;
            return route(sp, "onUseBlock", m -> m.onUseBlock(run, sp, hand, hit));
        });
        UseEntityCallback.EVENT.register(PHASE, (player, level, hand, entity, hitResult) -> {
            Run run = runFor(level, player);
            if (run == null || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ServerPlayer sp = (ServerPlayer) player;
            return route(sp, "onUseEntity", m -> m.onUseEntity(run, sp, hand, entity));
        });
        AttackEntityCallback.EVENT.register(PHASE, (player, level, hand, entity, hitResult) -> {
            Run run = runFor(level, player);
            if (run == null || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ServerPlayer sp = (ServerPlayer) player;
            return route(sp, "onAttackEntity", m -> m.onAttackEntity(run, sp, hand, entity));
        });
        UseItemCallback.EVENT.register(PHASE, (player, level, hand) -> {
            Run run = runFor(level, player);
            if (run == null || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ServerPlayer sp = (ServerPlayer) player;
            return route(sp, "onUseItem", m -> m.onUseItem(run, sp, hand));
        });
    }

    private static InteractionResult route(ServerPlayer player, String what, Function<AdsModule, InteractionResult> call) {
        InteractionResult r = Sim.asPlayer(player, false, () -> ask(AdsModules.all(), what, call));
        return r != null ? r : InteractionResult.PASS;
    }
}
