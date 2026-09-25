package com.teslamaps.features;

// Concept from IQ's MuteTerminatorCooldownSoundMixin; sound match from Devonian's NoAbilityCdSound.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class TerminatorCooldownMute {

    private TerminatorCooldownMute() {}

    // read from the netty thread, so the held item is sampled on the client tick
    private static volatile boolean holding;

    public static void register() {
        ChatFilter.RULES.add(new ChatFilter.Rule("terminatorCooldownMuteChat", ChatFilter.G_COMBAT, false, true,
                c -> c.terminatorCooldownMute && c.terminatorCooldownMuteChat && holding,
                t -> t.startsWith("This ability is on cooldown for ")));
        Feature.of("TerminatorCooldownMute")
                .when(() -> TeslaMapsConfig.get().terminatorCooldownMute)
                .tickAlways(TerminatorCooldownMute::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Terminator");
                    b.toggle("Mute Terminator Cooldown", () -> c.terminatorCooldownMute, v -> { c.terminatorCooldownMute = v; if (!v) holding = false; })
                            .desc("Only while a Terminator is in your hand: drops the ability cooldown sound");
                    b.toggle("  Also Hide Cooldown Message", () -> c.terminatorCooldownMuteChat, v -> c.terminatorCooldownMuteChat = v)
                            .desc("\"This ability is on cooldown for ...\" (needs the chat filter on)");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        holding = mc.player != null && SkyblockUtils.isInSkyblock()
                && "TERMINATOR".equals(ItemUtil.skyblockId(mc.player.getMainHandItem()));
    }

    /** Any thread. */
    public static boolean muteSound(SoundEvent sound, float volume) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return holding && c.terminatorCooldownMute && c.section("Chat")
                && sound == SoundEvents.ENDERMAN_TELEPORT && volume == 8.0f;
    }
}
