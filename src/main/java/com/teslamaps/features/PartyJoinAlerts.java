package com.teslamaps.features;

// Concept from IQ Addons' partyJoinSound / limboAlert
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Pattern;

public final class PartyJoinAlerts {

    private static final Pattern JOINED = Pattern.compile("^(?:\\[[^]]*] ?)?\\w{1,16} joined the party\\.$");
    private static final Pattern PF_JOINED = Pattern.compile("^Party Finder > \\w{1,16} joined the (?:dungeon )?group!.*");
    private static long lastLimbo = 0L;

    private PartyJoinAlerts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PartyJoinSound").section("Party").when(() -> cfg().partyJoinSound)
                .serverChat(PartyJoinAlerts::onJoin)
                .config("Party", b -> {
                    b.label("Party Join Sound");
                    b.toggle("Party Join Sound", () -> cfg().partyJoinSound, v -> cfg().partyJoinSound = v)
                            .desc("Plays a sound whenever someone joins your party, also through the Party Finder.");
                    b.soundDropdown("  Sound", SoundOptions.keys(), () -> cfg().partyJoinSoundType, v -> cfg().partyJoinSoundType = v);
                });
        Feature.of("LimboAlert").when(() -> cfg().kickedTimer && cfg().kickedLimboAlert)
                .serverChat(PartyJoinAlerts::onLimbo);
    }

    private static void onJoin(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (!s.contains("joined the")) return;
        if (!JOINED.matcher(s).matches() && !PF_JOINED.matcher(s).matches()) return;
        LoudSound.play(SoundOptions.resolve(cfg().partyJoinSoundType), 1.0f, 1.0f);
    }

    private static void onLimbo(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (!s.equals("You were spawned in Limbo.") && !s.contains("routed to limbo")
                && !s.equals("You are AFK. Move around to return from AFK.")) return;
        long now = System.currentTimeMillis();
        if (now - lastLimbo < 5000L) return;
        lastLimbo = now;
        Minecraft mc = Minecraft.getInstance();
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        mc.gui.setTitle(Component.literal("§c§lLIMBO"));
        mc.gui.setSubtitle(Component.literal("§7You were sent to Limbo"));
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c§l[Limbo] §7You were sent to Limbo. §f/lobby §7to get out."));
    }
}
