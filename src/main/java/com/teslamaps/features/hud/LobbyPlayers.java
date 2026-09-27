package com.teslamaps.features.hud;

// HUD-042, after SkyHanni's ScoreboardElementPlayerAmount.kt + HypixelData.getPlayersOnCurrentServer
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LobbyPlayers {

    private static final List<String> SAMPLE = List.of("§7Players: §a69§7/§a80");
    private static final Pattern PLAYERS = Pattern.compile("^Players \\((\\d+)\\)");
    private static final Pattern GUESTS = Pattern.compile("^Guests \\((\\d+)\\)");
    private static final Pattern PARTY = Pattern.compile("^Party \\((\\d+)\\)");
    private static final Pattern VISITING = Pattern.compile("✌ \\((\\d+)/(\\d+)\\)");
    // "09/25/26 m77CK": lowercase m = mini server, capital M = mega
    private static final Pattern SERVER = Pattern.compile("^\\d\\d/\\d\\d/\\d\\d\\s+(\\S+)");
    private static final int MAX_MINI = 24, MAX_MEGA = 80;

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;

    private LobbyPlayers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("lobbyPlayers", "Lobby Players")
                .enabled(() -> cfg().lobbyPlayers)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("LobbyPlayers").when(() -> cfg().lobbyPlayers)
                .tick(LobbyPlayers::tick)
                .hudHiddenInInventory("lobbyplayers", LobbyPlayers::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Lobby Players");
                    b.toggle("Lobby Player Count", () -> c.lobbyPlayers, v -> { c.lobbyPlayers = v; timer = 0; })
                            .desc("\"Players: 12/24\" for the lobby you are in, read from the tab list. Position in /tmap gui.")
                            .tags("lobby", "players", "hopping");
                    b.toggle("  Show Lobby Capacity", () -> c.lobbyPlayersMax, v -> c.lobbyPlayersMax = v)
                            .desc("24 on mini servers, 80 on mega servers, the visitor limit on private islands and gardens.");
                });
    }

    private static void tick() {
        if (timer++ % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        int players = 0;
        boolean seenPlayers = false, seenGuests = false, seenParty = false;
        boolean dungeon = DungeonManager.isInDungeon();
        for (String raw : TabListUtils.getTabListLines()) {
            String s = StringUtil.stripTrim(raw);
            if (!s.endsWith(")")) continue;
            Matcher m;
            if (!seenPlayers && (m = PLAYERS.matcher(s)).find()) { players += Integer.parseInt(m.group(1)); seenPlayers = true; }
            else if (!seenGuests && (m = GUESTS.matcher(s)).find()) { players += Integer.parseInt(m.group(1)); seenGuests = true; }
            else if (dungeon && !seenParty && (m = PARTY.matcher(s)).find()) { players += Integer.parseInt(m.group(1)); seenParty = true; }
        }
        if (!seenPlayers && !seenGuests && !seenParty) {
            lines = List.of();
            return;
        }
        String line = "§7Players: §a" + players;
        if (cfg().lobbyPlayersMax) line += "§7/§a" + maxPlayers();
        lines = List.of(line);
        width = TextHud.width(lines);
    }

    private static int maxPlayers() {
        int max = MAX_MINI;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String s = StringUtil.stripTrim(raw);
            Matcher v = VISITING.matcher(s);
            if (v.find()) return Integer.parseInt(v.group(2));
            Matcher m = SERVER.matcher(s);
            if (m.find() && m.group(1).startsWith("M")) max = MAX_MEGA;
        }
        return max;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
