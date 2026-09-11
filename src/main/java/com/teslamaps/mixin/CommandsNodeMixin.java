package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.teslamaps.command.HypixelNodes;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hypixel's command tree carries no suggestions, so the nodes we have a list for are
 * swapped for our own while the tree is being read. Ported from Skyblocker's
 * ClientboundCommandsPacketMixin.
 */
@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundCommandsPacket$NodeResolver")
public class CommandsNodeMixin {

    @ModifyExpressionValue(method = "resolve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket$NodeResolver;"
                    + "resolve(I)Lcom/mojang/brigadier/tree/CommandNode;", ordinal = 1))
    private CommandNode<SharedSuggestionProvider> teslamaps$ourSuggestions(
            CommandNode<SharedSuggestionProvider> original) {
        if (!(original instanceof LiteralCommandNode<SharedSuggestionProvider> literal)) return original;
        return HypixelNodes.replace(literal.getLiteral(), original);
    }
}
