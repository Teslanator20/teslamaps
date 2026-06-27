/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.QuizSolver;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.AutoWish;
import com.teslamaps.features.LividSolver;
import com.teslamaps.slayer.SlayerHUD;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatMixin {
    private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";

    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component teslamaps$highlightQuiz(Component message) {
        if (!TeslaMapsConfig.get().section("Puzzles")) return message;
        Component recolored = QuizSolver.highlightLine(message);
        return recolored != null ? recolored : message;
    }

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"), cancellable = true)
    private void onChatMessage(Component message, net.minecraft.network.chat.MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci) {
        String text = message.getString();

        TeslaMapsConfig cfg = TeslaMapsConfig.get();

        if (cfg.section("Puzzles") && QuizSolver.shouldHide(text)) {
            ci.cancel();
            return;
        }
        if (text.contains("SLAYER QUEST COMPLETE")) {
            if (cfg.section("Slayer")) SlayerHUD.onSlayerComplete();
        } else if (text.contains("SLAYER QUEST FAILED")) {
            if (cfg.section("Slayer")) SlayerHUD.onSlayerFailed();
        } else if (text.contains("A Wither Key was picked up!") || text.contains("has obtained Wither Key")) {
            if (cfg.section("ESP")) StarredMobESP.onWitherKeyPickup();
        } else if (text.contains("A Blood Key was picked up!") || text.contains("has obtained Blood Key")) {
            if (cfg.section("ESP")) StarredMobESP.onBloodKeyPickup();
        } else if (text.contains("opened a WITHER door!") || text.contains("opened the WITHER door!")) {
            if (cfg.section("ESP")) StarredMobESP.onWitherDoorOpened();
        } else if (text.contains("opened a BLOOD door!") || text.contains("opened the BLOOD door!")) {
            if (cfg.section("ESP")) StarredMobESP.onBloodDoorOpened();
        } else if (text.equals("That chest is locked!")) {
            if (cfg.section("ESP")) {
                com.teslamaps.features.SecretClickHighlight.onChestLocked();
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                }
            }
        }

        if (text.contains("found a secret") || text.contains("FOUND A SECRET")) {
            if (cfg.section("Sounds")) {
                onSecretFound();
            }
        }

        if (cfg.section("Auto")) AutoGFS.onChatMessage(text);

        if (cfg.section("ESP")) LividSolver.onChatMessage(text);

        if (cfg.section("Leap")) com.teslamaps.features.LeapOverlay.onChatMessage(text);

        MimicDetector.onChatMessage(text);

        DungeonScore.onChatMessage(text);

        if (cfg.section("Dragons")) com.teslamaps.dungeon.WitherDragons.onChatMessage(text);

        if (cfg.section("Score & Splits")) {
            com.teslamaps.dungeon.Splits.onChatMessage(text);
            com.teslamaps.dungeon.WatcherAddons.onChatMessage(text);
        }

        if (cfg.section("Party")) com.teslamaps.features.PartyDuplicateAlert.onChatMessage(text);
        if (cfg.section("Timers")) com.teslamaps.features.TimerTriggers.onChatMessage(text);
        if (cfg.section("Timers")) com.teslamaps.features.SpiritPetReminder.onChatMessage(text);
        if (cfg.section("Puzzles")) com.teslamaps.features.CustomTitles.onChatMessage(text);
        if (cfg.section("Render")) com.teslamaps.features.ChatWaypoint.onChatMessage(text);

        if (cfg.section("Blood Camp")) com.teslamaps.dungeon.BloodCamp.onChatMessage(text);

        if (cfg.section("Party")) {
            com.teslamaps.dungeon.AutoRequeue.onChatMessage(text);
            com.teslamaps.dungeon.PbOnJoin.onChatMessage(text);
            com.teslamaps.features.ChatCommands.onChatMessage(text);
        }

        if (cfg.section("Puzzles")) ThreeWeirdos.onChatMessage(text);

        if (cfg.section("Auto")) AutoWish.onChatMessage(text);

        if (cfg.section("Puzzles")) {
            QuizSolver.onChatMessage(text);
        }

        if (cfg.section("Chat") && com.teslamaps.features.ChatFilter.shouldHide(text)) {
            ci.cancel();
        }
    }

    @Redirect(method = "addMessageToQueue",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int teslamaps$allMessagesCap(java.util.List<?> list) {
        return teslamaps$cap(list);
    }

    @Redirect(method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
            slice = @Slice(from = @At(value = "INVOKE", target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V")))
    private int teslamaps$trimmedMessagesCap(java.util.List<?> list) {
        return teslamaps$cap(list);
    }

    @Unique
    private static int teslamaps$cap(java.util.List<?> list) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int size = list.size();
        return (c.section("Chat") && c.infiniteChat && size <= c.chatHistoryLimit) ? 0 : size;
    }

    @Inject(method = ADD_MESSAGE, at = @At("TAIL"))
    private void teslamaps$stackMessages(Component message, net.minecraft.network.chat.MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci) {
        if (!TeslaMapsConfig.get().section("Chat")) return;
        com.teslamaps.features.ChatStacking.afterAdd((net.minecraft.client.gui.components.ChatComponent) (Object) this);
    }

    @Unique
    private static void onSecretFound() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.secretSound) {
            return;
        }

        LoudSound.play(com.teslamaps.utils.SoundOptions.resolve(config.secretSoundType),
                config.secretSoundVolume, config.secretSoundPitch);
    }
}
