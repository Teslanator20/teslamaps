package com.teslamaps.features;

// After Zen's PartyDisplay.kt and SkyHanni's ScoreboardElementParty.kt, fed by PartyTracker
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyMembersHud {

    private static final Pattern PF_JOIN = Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$");
    private static final String[] AREAS = {"Dungeon Hub", "Catacombs", "Kuudra", "Crimson Isle", "Dungeon"};
    private static final List<String> SAMPLE = List.of("§9§lParty Members §7(3)", "§6♚ §fTeslanator §7Mage 48",
            "§aYou §7Healer 42", "§fSomeone §7Tank 40");

    private static final Map<String, String> joinedClass = new HashMap<>();
    private static HudEditElement el;
    private static List<String> lines = List.of();
    private static int width;
    private static int timer;

    private PartyMembersHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("partyHud", "Party Members").section("Party")
                .enabled(() -> cfg().partyHud)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("PartyMembersHud").section("Party").when(() -> cfg().partyHud)
                .tick(PartyMembersHud::tick)
                .serverChat(PartyMembersHud::onChat)
                .hud("partyhud", PartyMembersHud::render)
                .config("Party", b -> {
                    b.label("Party Members HUD");
                    b.toggle("Party Members HUD", () -> cfg().partyHud, v -> cfg().partyHud = v)
                            .desc("Everyone in your party, leader crown, you in green, dungeon class and level when known. Hidden when you are alone.");
                    b.toggle("  Only In Party Areas", () -> cfg().partyHudOnlyAreas, v -> cfg().partyHudOnlyAreas = v)
                            .desc("Dungeon Hub, dungeons, Kuudra and Crimson Isle only.");
                    b.slider("  Max Members Shown", 1f, 20f, () -> (float) cfg().partyHudMax, v -> cfg().partyHudMax = Math.round(v), 0);
                    b.info("Position and scale in /tmap gui");
                });
    }

    private static void onChat(String raw) {
        Matcher m = PF_JOIN.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        if (joinedClass.size() > 50) joinedClass.clear();
        joinedClass.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2) + " " + m.group(3));
    }

    private static void tick() {
        if (timer-- > 0) return;
        timer = 10;
        TeslaMapsConfig c = cfg();
        List<String> members = PartyTracker.members();
        if (!PartyTracker.inParty() || members.size() <= 1 || c.partyHudOnlyAreas && !inArea()) {
            lines = List.of();
            return;
        }
        var p = Minecraft.getInstance().player;
        String me = p == null ? "" : p.getGameProfile().name();
        String leader = PartyTracker.leader();
        List<String> out = new ArrayList<>();
        out.add("§9§lParty Members §7(" + members.size() + ")");
        int shown = 0;
        for (String n : members) {
            if (shown++ >= c.partyHudMax) {
                out.add("§7+" + (members.size() - c.partyHudMax) + " more");
                break;
            }
            StringBuilder sb = new StringBuilder();
            if (n.equalsIgnoreCase(leader)) sb.append("§6♚ ");
            sb.append(n.equalsIgnoreCase(me) ? "§a" : "§f").append(n);
            String cls = classOf(n);
            if (cls != null) sb.append(" §7").append(cls);
            out.add(sb.toString());
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String classOf(String name) {
        if (DungeonManager.isInDungeon()) {
            var dp = PlayerTracker.getPlayer(name).orElse(null);
            if (dp != null && dp.getDungeonClass() != null && !"EMPTY".equals(dp.getDungeonClass())) {
                return dp.getDungeonClass() + (dp.classLevel != null ? " " + dp.classLevel : "");
            }
        }
        return joinedClass.get(name.toLowerCase(Locale.ROOT));
    }

    private static boolean inArea() {
        if (DungeonManager.isInDungeon()) return true;
        String a = SkyblockUtils.getArea();
        for (String s : AREAS) if (a.contains(s)) return true;
        return false;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, el.x(), el.y(), el.scale(), l, width);
    }
}
