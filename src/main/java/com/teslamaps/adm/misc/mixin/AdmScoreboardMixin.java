package com.teslamaps.adm.misc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.adm.misc.ScoreboardLines;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

// Clean Scoreboard: the renderer only gets the lines left, so the box shrinks around them
@Mixin(Gui.class)
public class AdmScoreboardMixin {
    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/scores/Scoreboard;listPlayerScores(Lnet/minecraft/world/scores/Objective;)Ljava/util/Collection;"))
    private Collection<PlayerScoreEntry> teslamaps$admHideSidebarLines(Scoreboard scoreboard, Objective objective, Operation<Collection<PlayerScoreEntry>> original) {
        Collection<PlayerScoreEntry> all = original.call(scoreboard, objective);
        try {
            return ScoreboardLines.visibleEntries(scoreboard, objective, all);
        } catch (Throwable t) {
            return all;
        }
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component teslamaps$admHideSidebarTitle(Objective objective, Operation<Component> original) {
        return ScoreboardLines.hidesTitle() ? Component.empty() : original.call(objective);
    }
}
