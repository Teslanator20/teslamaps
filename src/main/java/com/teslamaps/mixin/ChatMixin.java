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
        String text = message.getString();

        TeslaMapsConfig cfg = TeslaMapsConfig.get();

        // Anything a player typed can imitate a server line, so triggers only run on
        // server messages. Player chat still reaches the features that want it below.
        boolean fromServer = com.teslamaps.utils.StringUtil.isServerMessage(text);

        if (fromServer) com.teslamaps.command.PlayerNameCache.onChatMessage(message);

        if (cfg.section("Puzzles & Terminals") && QuizSolver.shouldHide(text)) {
            ci.cancel();
            return;
        }
        if (text.contains("SLAYER QUEST COMPLETE")) {
            if (cfg.section("Combat")) SlayerHUD.onSlayerComplete();
        } else if (text.contains("SLAYER QUEST FAILED")) {
            if (cfg.section("Combat")) SlayerHUD.onSlayerFailed();
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

        if (cfg.section("Garden")) com.teslamaps.features.FarmProfit.onChatComponent(message);
        if (cfg.section("Garden")) com.teslamaps.features.GardenPlots.onChatMessage(text);
        if (cfg.section("Garden")) com.teslamaps.features.PestRoutine.onChatMessage(text);
        if (fromServer && cfg.section("Garden")) com.teslamaps.features.SixthVisitor.onChatMessage(text);

        if (fromServer && cfg.section("Auto")) {
            AutoGFS.onChatMessage(text);
            com.teslamaps.features.GetFromSacks.onChatMessage(text);
        }

        if (cfg.section("ESP")) LividSolver.onChatMessage(text);

        if (cfg.section("ESP")) com.teslamaps.esp.CorpseESP.onChatMessage(text);

        if (cfg.section("Critter Safari")) {
            com.teslamaps.safari.SafariTracker.onChatMessage(text);
            com.teslamaps.safari.SafariTracker.onChatComponent(message);
        }

        if (cfg.section("ESP")) com.teslamaps.features.HoppityEggESP.onChatMessage(text);

        if (fromServer && cfg.section("The End")) com.teslamaps.features.theend.EndProtector.onChatMessage(text);

        if (cfg.section("Leap")) com.teslamaps.features.LeapOverlay.onChatMessage(text);

        if (cfg.section("Combat")) com.teslamaps.slayer.SlayerAlerts.onChatMessage(text);

        if (fromServer && cfg.section("Skills")) com.teslamaps.features.SkillTracker.onChatMessage(text);
        if (fromServer && cfg.section("Skills")) com.teslamaps.features.hunting.FlareMode.onChatMessage(text);
        if (fromServer && cfg.section("Fishing")) com.teslamaps.features.UmbrellaWarning.onChatMessage(text);
        if (fromServer && cfg.section("Fishing")) com.teslamaps.features.RareSeaCreature.onChatMessage(text);
        if (fromServer && cfg.section("Fishing")) com.teslamaps.features.HotspotShare.onChatMessage(text);
        if (cfg.section("Fishing")) com.teslamaps.features.FishCatchKeySwap.onChatMessage(text);


        com.teslamaps.features.KickedTimer.onChatMessage(text);

        com.teslamaps.features.OverkillCounter.onChatMessage(text);

        MimicDetector.onChatMessage(text);

        DungeonScore.onChatMessage(text);

        com.teslamaps.dungeon.terminals.TerminalUtils.onChatMessage(text);

        if (cfg.witherDragons) com.teslamaps.dungeon.WitherDragons.onChatMessage(text);

        if (cfg.section("Score & Splits")) {
            com.teslamaps.dungeon.Splits.onChatMessage(text);
            com.teslamaps.dungeon.WatcherAddons.onChatMessage(text);
            com.teslamaps.dungeon.ExtraStats.onChatMessage(text);
        }

        if (cfg.section("Party")) com.teslamaps.features.PartyDuplicateAlert.onChatMessage(text);
        if (cfg.section("Timers")) com.teslamaps.features.TimerTriggers.onChatMessage(text);
        if (cfg.section("Timers")) com.teslamaps.features.SpiritPetReminder.onChatMessage(text);
        if (cfg.section("Puzzles & Terminals")) com.teslamaps.features.CustomTitles.onChatMessage(text);
        if (cfg.section("Render")) com.teslamaps.features.ChatWaypoint.onChatMessage(text);
        if (cfg.section("Render")) com.teslamaps.features.RareDropEffects.onChatMessage(text);

        if (cfg.section("Blood Camp")) com.teslamaps.dungeon.BloodCamp.onChatMessage(text);

        if (cfg.section("Party")) {
            com.teslamaps.dungeon.AutoRequeue.onChatMessage(text);
            com.teslamaps.dungeon.PbOnJoin.onChatMessage(text);
            com.teslamaps.features.ChatCommands.onChatMessage(text);
        }

        if (cfg.section("Puzzles & Terminals")) ThreeWeirdos.onChatMessage(text);

        if (cfg.section("Auto")) AutoWish.onChatMessage(text);

        if (cfg.section("Puzzles & Terminals")) {
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
