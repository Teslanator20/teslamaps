package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.dungeon.MimicDetector;
import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.QuizSolver;
import com.teslamaps.dungeon.puzzle.SimonSaysSolver;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatMixin {
    private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";

    // Recolor the correct quiz answer line (green + bold) in place, before it's added to chat.
    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component teslamaps$highlightQuiz(Component message) {
        Component recolored = QuizSolver.highlightLine(message);
        return recolored != null ? recolored : message;
    }

    // 26.1.2: addMessage(Component) split into addServer/Client/PlayerSystemMessage; they all
    // funnel through this private addMessage overload, which catches every chat message.
    @Inject(method = ADD_MESSAGE, at = @At("HEAD"), cancellable = true)
    private void onChatMessage(Component message, net.minecraft.network.chat.MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci) {
        String text = message.getString();

        // Hide wrong quiz answer lines entirely (no extra message — just clean chat).
        if (QuizSolver.shouldHide(text)) {
            ci.cancel();
            return;
        }
        if (text.contains("SLAYER QUEST COMPLETE")) {
            SlayerHUD.onSlayerComplete();
        } else if (text.contains("SLAYER QUEST FAILED")) {
            SlayerHUD.onSlayerFailed();
        } else if (text.contains("A Wither Key was picked up!") || text.contains("has obtained Wither Key")) {
            StarredMobESP.onWitherKeyPickup();
        } else if (text.contains("A Blood Key was picked up!") || text.contains("has obtained Blood Key")) {
            StarredMobESP.onBloodKeyPickup();
        } else if (text.contains("opened a WITHER door!") || text.contains("opened the WITHER door!")) {
            StarredMobESP.onWitherDoorOpened();
        } else if (text.contains("opened a BLOOD door!") || text.contains("opened the BLOOD door!")) {
            StarredMobESP.onBloodDoorOpened();
        } else if (text.equals("That chest is locked!")) {
            // Play anvil sound for locked chest
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            }
        }

        // Check for secret found - multiple patterns
        if (text.contains("found a secret") || text.contains("FOUND A SECRET")) {
            onSecretFound();
            com.teslamaps.features.SecretChime.onSecretFound();
        }

        // Pass all messages to AutoGFS for dungeon start/puzzle fail detection
        AutoGFS.onChatMessage(text);

        // Pass to LividSolver for fight start detection
        LividSolver.onChatMessage(text);

        // Pass to MimicDetector for mimic dead detection from party chat
        MimicDetector.onChatMessage(text);

        // Pass to DungeonScore for death/watcher detection
        DungeonScore.onChatMessage(text);

        // Pass to Splits for dungeon run split tracking
        com.teslamaps.dungeon.Splits.onChatMessage(text);

        // Pass to Blood Camp for watcher move prediction
        com.teslamaps.dungeon.BloodCamp.onChatMessage(text);

        // Auto requeue (dungeon end / party "r")
        com.teslamaps.dungeon.AutoRequeue.onChatMessage(text);

        // Show PBs when a player joins the party
        com.teslamaps.dungeon.PbOnJoin.onChatMessage(text);

        // Party chat commands (!8ball, !warp, !pt, ...)
        com.teslamaps.features.ChatCommands.onChatMessage(text);

        // Pass to ThreeWeirdos for NPC message detection
        ThreeWeirdos.onChatMessage(text);

        // Pass to AutoWish for boss trigger detection
        AutoWish.onChatMessage(text);

        // Pass to QuizSolver for trivia answer detection
        QuizSolver.onChatMessage(text);

        // Pass to SimonSaysSolver for phase detection
        SimonSaysSolver.onChatMessage(text);
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
