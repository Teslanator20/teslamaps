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
    private Component teslamaps$rewriteLine(Component message) {
        Component shared = com.teslamaps.features.SharedImageChat.decorate(message);
        if (shared != null) return shared;
        Component chroma = com.teslamaps.features.ChromaPrefix.rewrite(message);
        if (chroma != null) message = chroma;
        if (!TeslaMapsConfig.get().section("Puzzles & Terminals")) return message;
        Component recolored = QuizSolver.highlightLine(message);
        return recolored != null ? recolored : message;
    }

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"), cancellable = true)
    private void onChatMessage(Component message, net.minecraft.network.chat.MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci) {
        com.teslamaps.utils.PlayerChat.State prev = com.teslamaps.utils.PlayerChat.begin(message);
        try {
            teslamaps$handle(message, ci);
            if (com.teslamaps.features.ChatSimulator.silent() && !ci.isCancelled()) ci.cancel();
        } finally {
            com.teslamaps.utils.PlayerChat.end(prev);
        }
    }

    @Unique
    private void teslamaps$handle(Component message, CallbackInfo ci) {
        String text = message.getString();

        TeslaMapsConfig cfg = TeslaMapsConfig.get();

        // Anything a player typed can imitate a server line, so triggers only run on
        // server messages. Player chat still reaches the features that want it below.
        com.teslamaps.utils.PlayerChat.Msg pm = com.teslamaps.utils.PlayerChat.current();
        boolean fromServer = com.teslamaps.utils.StringUtil.isServerMessage(text)
                && (pm == null || pm.channel() == com.teslamaps.utils.PlayerChat.Channel.NPC);
        // mimic / prince callouts come from other mods in party chat
        boolean serverOrParty = fromServer || pm != null && pm.channel() == com.teslamaps.utils.PlayerChat.Channel.PARTY;

        if (fromServer) com.teslamaps.command.PlayerNameCache.onChatMessage(message);

        if (cfg.section("Puzzles & Terminals") && QuizSolver.shouldHide(text)) {
            ci.cancel();
            return;
        }
        if (!fromServer) {
            // player lines only reach the features below that read player chat on purpose
        } else if (text.contains("SLAYER QUEST COMPLETE")) {
            if (cfg.section("Combat")) SlayerHUD.onSlayerComplete();
        } else if (text.contains("SLAYER QUEST FAILED")) {
            if (cfg.section("Combat")) SlayerHUD.onSlayerFailed();
        } else if (text.contains("A Wither Key was picked up!") || text.contains("has obtained Wither Key")) {
            if (cfg.section("ESP")) StarredMobESP.onWitherKeyPickup(text);
        } else if (text.contains("A Blood Key was picked up!") || text.contains("has obtained Blood Key")) {
            if (cfg.section("ESP")) StarredMobESP.onBloodKeyPickup(text);
        } else if (text.contains("opened a WITHER door!") || text.contains("opened the WITHER door!")) {
            if (cfg.section("ESP")) StarredMobESP.onWitherDoorOpened();
        } else if (text.contains("opened a BLOOD door!") || text.contains("opened the BLOOD door!")) {
            if (cfg.section("ESP")) StarredMobESP.onBloodDoorOpened();
        } else if (text.equals("That chest is locked!")) {
            if (cfg.section("Secrets & Waypoints")) com.teslamaps.features.SecretClickHighlight.onChestLocked();
            if (!(fromServer && com.teslamaps.features.SecretSounds.onLocked()) && cfg.section("ESP")) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
                }
            }
        }

        if (fromServer && (text.contains("found a secret") || text.contains("FOUND A SECRET"))) {
            if (cfg.section("Sounds")) {
                onSecretFound();
            }
        }

        if (fromServer && cfg.section("Garden")) com.teslamaps.features.FarmProfit.onChatComponent(message);
        if (fromServer) com.teslamaps.features.SackApi.onChatComponent(message);
        if (fromServer) com.teslamaps.features.AbiphoneAccept.onChatComponent(message);
        if (fromServer && cfg.customTrackers) com.teslamaps.features.tracker.CustomTrackers.onChatComponent(message);
        if (fromServer) com.teslamaps.features.ItemPickupLog.onChatComponent(message);
        if (fromServer && cfg.profitTracker) com.teslamaps.features.tracker.ProfitTracker.onChatComponent(message);
        if (fromServer && cfg.valuableDropWarn) com.teslamaps.features.ValuableDropWarning.onChatComponent(message);
        if (fromServer && cfg.section("Garden")) com.teslamaps.features.GardenPlots.onChatMessage(text);
        if (fromServer && cfg.section("Garden")) com.teslamaps.features.PestRoutine.onChatMessage(text);
        if (fromServer && cfg.section("Garden")) com.teslamaps.features.SixthVisitor.onChatMessage(text);

        if (fromServer && cfg.section("Auto")) {
            AutoGFS.onChatMessage(text);
            com.teslamaps.features.GetFromSacks.onChatMessage(text);
        }

        if (fromServer && cfg.section("ESP")) LividSolver.onChatMessage(text);

        if (fromServer && cfg.section("ESP")) com.teslamaps.esp.CorpseESP.onChatMessage(text);

        if (fromServer && cfg.section("Critter Safari")) {
            com.teslamaps.safari.SafariTracker.onChatMessage(text);
            com.teslamaps.safari.SafariTracker.onChatComponent(message);
        }

        if (fromServer && cfg.section("ESP")) com.teslamaps.features.HoppityEggESP.onChatMessage(text);

        if (fromServer && cfg.section("The End")) com.teslamaps.features.theend.EndProtector.onChatMessage(text);

        if (fromServer && cfg.section("Leap")) com.teslamaps.features.LeapOverlay.onChatMessage(text);

        if (fromServer && cfg.section("Combat")) com.teslamaps.slayer.SlayerAlerts.onChatMessage(text);

        if (fromServer && cfg.section("Skills")) com.teslamaps.features.SkillTracker.onChatMessage(text);
        if (fromServer && cfg.section("Skills")) com.teslamaps.features.hunting.FlareMode.onChatMessage(text);
        if (fromServer && cfg.section("Fishing")) com.teslamaps.features.UmbrellaWarning.onChatMessage(text);
        if (fromServer && cfg.section("Fishing")) com.teslamaps.features.RareSeaCreature.onChatMessage(text);
        if (fromServer && cfg.section("Fishing")) com.teslamaps.features.HotspotShare.onChatMessage(text);
        if (cfg.section("Fishing")) com.teslamaps.features.FishCatchKeySwap.onChatMessage(text);


        if (fromServer) com.teslamaps.features.KickedTimer.onChatMessage(text);

        if (fromServer) com.teslamaps.features.OverkillCounter.onChatMessage(text);

        if (serverOrParty) MimicDetector.onChatMessage(text);

        if (serverOrParty) DungeonScore.onChatMessage(text);

        if (fromServer) com.teslamaps.dungeon.terminals.TerminalUtils.onChatMessage(text);

        if (fromServer && cfg.witherDragons) com.teslamaps.dungeon.WitherDragons.onChatMessage(text);

        if (fromServer && cfg.section("Score & Splits")) {
            com.teslamaps.dungeon.Splits.onChatMessage(text);
            com.teslamaps.dungeon.WatcherAddons.onChatMessage(text);
            com.teslamaps.dungeon.ExtraStats.onChatMessage(text);
        }

        if (fromServer && cfg.section("Party")) com.teslamaps.features.PartyDuplicateAlert.onChatMessage(text);
        if (fromServer && cfg.section("Timers")) com.teslamaps.features.TimerTriggers.onChatMessage(text);
        if (fromServer && cfg.section("Timers")) com.teslamaps.features.SpiritPetReminder.onChatMessage(text);
        if (fromServer && cfg.section("Puzzles & Terminals")) com.teslamaps.features.CustomTitles.onChatMessage(text);
        if (cfg.section("Render")) com.teslamaps.features.ChatWaypoint.onChatMessage(text);
        if (fromServer && cfg.section("Render")) com.teslamaps.features.RareDropEffects.onChatMessage(text);

        if (fromServer && cfg.section("Blood Camp")) com.teslamaps.dungeon.BloodCamp.onChatMessage(text);

        if (cfg.section("Party")) {
            com.teslamaps.dungeon.AutoRequeue.onChatMessage(text);
            if (fromServer) com.teslamaps.dungeon.PbOnJoin.onChatMessage(text);
            com.teslamaps.features.ChatCommands.onChatMessage(text);
        }

        if (fromServer && cfg.section("Puzzles & Terminals")) ThreeWeirdos.onChatMessage(text);

        if (fromServer && cfg.section("Auto")) AutoWish.onChatMessage(text);

        if (fromServer && cfg.section("Puzzles & Terminals")) {
            QuizSolver.onChatMessage(text);
            com.teslamaps.dungeon.puzzle.SimonSaysSolver.onChatMessage(text);
        }

        if (fromServer && cfg.section("Shards")) com.teslamaps.features.hunting.HuntingBox.onChatMessage(text);

        if (fromServer && cfg.section("Mining")) {
            com.teslamaps.features.mining.MineshaftPity.onChatMessage(text);
            com.teslamaps.features.WorldScanner.onChatMessage(text);
        }

        if (fromServer && cfg.section("Torrhus Canyon")) {
            com.teslamaps.features.foraging.QueenAntWarning.onChatMessage(text);
        }

        if (fromServer && cfg.section("Crimson Isle")) {
            com.teslamaps.features.RescueMission.onChatMessage(text);
        }

        if (fromServer && cfg.section("Skills")) {
            com.teslamaps.features.pets.PetTracker.onChatMessage(text);
        }

        if (fromServer && cfg.section("Foraging")) {
            com.teslamaps.features.foraging.HoneyhiveReminder.onChatMessage(text);
            com.teslamaps.features.foraging.LatherReminder.onChatMessage(text);
            if (com.teslamaps.features.foraging.ForagingChat.shouldHide(message)) {
                ci.cancel();
                return;
            }
        }

        com.teslamaps.registry.FeatureRegistry.chat(text, fromServer);

        if (fromServer && cfg.section("Score & Splits") && com.teslamaps.dungeon.ExtraStats.hide(text)) {
            ci.cancel();
            return;
        }

        if (fromServer) com.teslamaps.features.SackMessages.onChat(message, text);
        if (cfg.section("Chat") && com.teslamaps.features.ChatExtras.hide(text, fromServer, pm)) {
            ci.cancel();
            return;
        }
        if (fromServer && cfg.section("Chat")) {
            com.teslamaps.features.ConfirmPrompt.onChatComponent(message);
            if (com.teslamaps.features.ChatCompacts.process(message, text)) {
                ci.cancel();
                return;
            }
        }

        if (cfg.section("Chat") && com.teslamaps.features.ChatFilter.process(message, text)) {
            ci.cancel();
            return;
        }
        if (cfg.section("Chat")) com.teslamaps.features.ChatExtras.prepare(text, fromServer, pm);
    }

    // past every HEAD handler, right before the GuiMessage is built
    @ModifyVariable(method = ADD_MESSAGE, argsOnly = true, ordinal = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;getGuiTicks()I"))
    private Component teslamaps$filterRewrite(Component message) {
        Component out = com.teslamaps.features.ChatFilter.rewrite(com.teslamaps.features.ChatCompacts.rewrite(
                com.teslamaps.dungeon.TerminalSplits.rewrite(message)));
        return com.teslamaps.features.SackMessages.rewrite(com.teslamaps.features.ChatExtras.rewrite(com.teslamaps.features.ChatRewrites.rewrite(out)));
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
        ChatComponent chat = (ChatComponent) (Object) this;
        if (TeslaMapsConfig.get().section("Chat")) com.teslamaps.features.ChatStacking.afterAdd(chat);
        com.teslamaps.features.SharedImageChat.ensurePadding(chat);
    }

    // the list is rebuilt on resize and whenever a chat mod switches tabs, which drops the
    // rows the shared pictures are drawn into
    @Inject(method = "refreshTrimmedMessages", at = @At("RETURN"))
    private void teslamaps$repadOnRefresh(CallbackInfo ci) {
        com.teslamaps.features.SharedImageChat.ensurePadding((ChatComponent) (Object) this);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void teslamaps$keepHistory(boolean history, CallbackInfo ci) {
        if (history) com.teslamaps.features.ChatHistoryPersistence.captureBeforeClear((ChatComponent) (Object) this);
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void teslamaps$restoreHistory(boolean history, CallbackInfo ci) {
        if (history) com.teslamaps.features.ChatHistoryPersistence.restoreAfterClear((ChatComponent) (Object) this);
    }

    @Unique
    private static void onSecretFound() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        com.teslamaps.features.SecretChime.onSecretFound();
        if (!config.secretSound) {
            return;
        }

        LoudSound.play(com.teslamaps.utils.SoundOptions.resolve(config.secretSoundType),
                config.secretSoundVolume, config.secretSoundPitch);
    }
}
