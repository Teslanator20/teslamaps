package com.teslamaps.features;

// Ported from Skysoft's DianaLobbyCompromisedWatcher
import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.choco.ChocoData;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DianaLobbyAlert {

    public static final String MESSAGE = "Lobby compromised!";
    private static final Pattern PLAYERS = Pattern.compile("^Players \\((\\d+)\\)$");
    private static final long STABLE_MS = 2000, GRACE_MS = 15_000, BASELINE_MS = 3000;

    private static final Map<String, Long> friendlySeen = new HashMap<>();
    private static boolean baseline, wasCompromised;
    private static int lastThreshold = -1, acknowledged;
    private static long candidateSince, joinedAt;
    private static Object lastLevel;
    private static int ticks;

    private DianaLobbyAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DianaLobbyAlert").section(ChocoData.CATEGORY).when(() -> cfg().dianaLobbyAlert || cfg().dianaBlackHoleTitle)
                .tick(DianaLobbyAlert::tick)
                .serverChat(DianaLobbyAlert::onChat)
                .config(ChocoData.CATEGORY, b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Diana");
                    b.toggle("Diana Lobby Compromised Alert", () -> c.dianaLobbyAlert, v -> c.dianaLobbyAlert = v)
                            .desc("While Diana's Mythological Ritual runs: alerts when the Hub lobby holds this many players outside your party (tab list count).");
                    b.slider("  Stranger Limit", 1, 30, () -> (float) c.dianaLobbyStrangers, v -> c.dianaLobbyStrangers = Math.round(v));
                    b.toggle("  Title", () -> c.dianaLobbyTitle, v -> c.dianaLobbyTitle = v);
                    b.toggle("  Party Chat", () -> c.dianaLobbyParty, v -> c.dianaLobbyParty = v)
                            .desc("Sends '" + MESSAGE + "' to party chat.");
                    b.toggle("Black Hole Reminder", () -> c.dianaBlackHoleTitle, v -> c.dianaBlackHoleTitle = v)
                            .desc("2 s 'Black Hole' title when you dig out a Cretan Bull, Harpy or Minotaur, as a reminder for the shard drop.");
                });
    }

    private static final Pattern DUG = Pattern.compile("^(?:Oh|Uh oh|Yikes|Oi|Good Grief|Danger|Woah)! You dug out (?:a )?([\\w\\s]+)!$");

    private static void onChat(String raw) {
        if (!cfg().dianaBlackHoleTitle) return;
        Matcher m = DUG.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String mob = m.group(1).trim();
        if (!mob.equals("Cretan Bull") && !mob.equals("Harpy") && !mob.equals("Minotaur")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 40, 5);
        mc.gui.setTitle(Component.literal("§5§lBlack Hole"));
    }

    private static void reset() {
        baseline = false;
        wasCompromised = false;
        lastThreshold = -1;
        acknowledged = 0;
        candidateSince = 0;
    }

    private static void tick() {
        if (!cfg().dianaLobbyAlert) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            joinedAt = System.currentTimeMillis();
            friendlySeen.clear();
            reset();
        }
        if (++ticks % 10 != 0) return;
        if (!SkyblockUtils.isInArea("Hub") || !ElectionApi.hasPerk("Mythological Ritual")) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - joinedAt < BASELINE_MS) return;
        int reported = -1;
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = PLAYERS.matcher(StringUtil.stripTrim(line));
            if (m.matches()) { reported = Integer.parseInt(m.group(1)); break; }
        }
        if (reported < 0) { reset(); return; }
        int strangers = Math.max(0, reported - friendly(mc, reported, now));
        int threshold = cfg().dianaLobbyStrangers;
        if (update(strangers, threshold, now)) alert(strangers);
    }

    private static int friendly(Minecraft mc, int reported, long now) {
        String me = mc.player == null ? null : mc.player.getGameProfile().name();
        if (me != null) friendlySeen.put(me.toLowerCase(Locale.ROOT), now);
        if (mc.getConnection() != null) {
            List<String> members = PartyTracker.members();
            for (String m : members) {
                if (mc.getConnection().getPlayerInfo(m) != null) friendlySeen.put(m.toLowerCase(Locale.ROOT), now);
            }
        }
        friendlySeen.entrySet().removeIf(e -> now - e.getValue() > GRACE_MS
                || (me != null && !e.getKey().equals(me.toLowerCase(Locale.ROOT)) && !PartyTracker.isMember(e.getKey())));
        return Math.min(reported, friendlySeen.size());
    }

    private static boolean update(int strangers, int threshold, long now) {
        boolean compromised = strangers >= threshold;
        if (!baseline || lastThreshold != threshold) {
            baseline = true;
            wasCompromised = compromised;
            lastThreshold = threshold;
            acknowledged = strangers;
            candidateSince = 0;
            return false;
        }
        if (!compromised) {
            wasCompromised = false;
            acknowledged = strangers;
            candidateSince = 0;
            return false;
        }
        if (wasCompromised && strangers <= acknowledged) {
            acknowledged = strangers;
            candidateSince = 0;
            return false;
        }
        if (candidateSince == 0) candidateSince = now;
        if (now - candidateSince < STABLE_MS) return false;
        wasCompromised = true;
        acknowledged = strangers;
        candidateSince = 0;
        return true;
    }

    private static void alert(int strangers) {
        Minecraft mc = Minecraft.getInstance();
        if (cfg().dianaLobbyTitle) {
            mc.gui.setTimes(0, 50, 10);
            mc.gui.setTitle(Component.literal("§c§l" + MESSAGE));
            mc.gui.setSubtitle(Component.literal("§7" + strangers + " strangers in this lobby"));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1f));
        }
        if (cfg().dianaLobbyParty && PartyTracker.inParty()) Announce.party(MESSAGE);
    }
}
