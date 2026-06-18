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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void onChatMessage(Component message, CallbackInfo ci) {
        String text = message.getString();
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
        }

        // Pass all messages to AutoGFS for dungeon start/puzzle fail detection
        AutoGFS.onChatMessage(text);

        // Pass to LividSolver for fight start detection
        LividSolver.onChatMessage(text);

        // Pass to MimicDetector for mimic dead detection from party chat
        MimicDetector.onChatMessage(text);

        // Pass to DungeonScore for death/watcher detection
        DungeonScore.onChatMessage(text);

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

        com.teslamaps.TeslaMaps.LOGGER.info("[SecretSound] Playing secret sound: {} at volume {}",
            config.secretSoundType, config.secretSoundVolume);
        LoudSound.play(getSecretSound(), config.secretSoundVolume, 1.0f);
    }

    @Unique
    private static net.minecraft.sounds.SoundEvent getSecretSound() {
        String sound = TeslaMapsConfig.get().secretSoundType;
        return switch (sound) {
            case "NOTE_PLING" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "EXPERIENCE_ORB" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "AMETHYST_CHIME" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.PLAYER_LEVELUP; // LEVEL_UP
        };
    }
}
