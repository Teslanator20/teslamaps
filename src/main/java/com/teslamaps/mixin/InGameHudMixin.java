package com.teslamaps.mixin;

import com.teslamaps.features.RarityBackground;
import com.teslamaps.scanner.SecretTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
    @org.spongepowered.asm.mixin.Shadow private int toolHighlightTimer;
    @org.spongepowered.asm.mixin.Shadow private ItemStack lastToolHighlight;

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"))
    private void teslamaps$alwaysShowItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!com.teslamaps.config.TeslaMapsConfig.get().alwaysShowItemName) return;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        lastToolHighlight = held;
        toolHighlightTimer = 40;
    }

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component onSetOverlayMessage(Component message) {
        SecretTracker.onActionBarMessage(message);
        com.teslamaps.features.ArmorStackHud.onActionBar(message);
        com.teslamaps.features.AccurateAbsorption.onActionBar(message);
        com.teslamaps.features.AbilityCooldowns.onActionBar(message);
        if (com.teslamaps.config.TeslaMapsConfig.get().endStatsHud) com.teslamaps.features.theend.EndStatsHud.onActionBar(message.getString());
        if (com.teslamaps.config.TeslaMapsConfig.get().vanillaManaBar) com.teslamaps.features.hud.VanillaManaBar.onActionBar(message.getString());
        if (com.teslamaps.config.TeslaMapsConfig.get().section("Skills")) {
            com.teslamaps.features.SkillTracker.onActionBar(message.getString());
        }
        return com.teslamaps.features.ActionBarFilter.filter(message);
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "lambda$displayScoreboardSidebar$1",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"), require = 0)
    private net.minecraft.network.chat.MutableComponent teslamaps$scoreboardText(net.minecraft.network.chat.MutableComponent original) {
        return com.teslamaps.features.ScoreboardText.apply(original);
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "extractPlayerHealth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAbsorptionAmount()F"))
    private float teslamaps$accurateAbsorption(float original) {
        return com.teslamaps.features.AccurateAbsorption.absorption(original);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void teslamaps$hotbarRarity(GuiGraphicsExtractor context, int x, int y, DeltaTracker delta,
                                        Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!com.teslamaps.config.TeslaMapsConfig.get().rarityBgHideHotbar) RarityBackground.draw(context, x, y, stack);
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void teslamaps$arrowPoison(GuiGraphicsExtractor context, int x, int y, DeltaTracker delta,
                                       Player player, ItemStack stack, int seed, CallbackInfo ci) {
        com.teslamaps.features.ArrowPoison.drawHotbar(context, x, y, stack);
        com.teslamaps.features.ItemStars.draw(context, stack, x, y);
    }

}
