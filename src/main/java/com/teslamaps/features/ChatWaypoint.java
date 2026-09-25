package com.teslamaps.features;

// Formats from Skyblocker's ChatPositionShare.java, channel/sender/arrival from Devonian's ChatWaypoint.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatWaypoint {

    private static final Pattern COORDS =
            Pattern.compile("x:\\s*(-?\\d+)[,.]?\\s*y:\\s*(-?\\d+)[,.]?\\s*z:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AREA = Pattern.compile("^(?:\\.\\d*)?\\s*\\|\\s*(?<area>[^|]{1,40})");
    private static final Pattern INQUISITOR = Pattern.compile(
            "A MINOS INQUISITOR has spawned near \\[(?<area>[^]]*)] at Coords (-?\\d+)(?:\\.\\d*)? (-?\\d+)(?:\\.\\d*)? (-?\\d+)(?:\\.\\d*)?");
    private static final long DURATION_MS = 60_000;
    private static final double ARRIVAL = 5;

    private static final WaypointSet waypoints = new WaypointSet().box(false).distance(false);
    private static Object lastLevel;

    public static void register() {
        Feature.of("ChatWaypoint").section("Render").when(() -> cfg().chatWaypoint).tick(ChatWaypoint::tick);
    }

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void onChatMessage(String message) {
        TeslaMapsConfig c = cfg();
        if (!c.chatWaypoint) return;
        String t = StringUtil.strip(message);
        if (t.startsWith("[TeslaMaps]") || t.startsWith("[NPC] Treasure Hunter:")) return;

        PlayerChat.Msg msg = null;
        boolean parsed = false;
        BlockPos pos = null;
        String area = "";
        String note = "";
        Matcher m = COORDS.matcher(t);
        if (m.find()) {
            pos = pos(m.group(1), m.group(2), m.group(3));
            String rest = t.substring(m.end());
            Matcher a = AREA.matcher(rest);
            if (a.find()) {
                area = a.group("area").trim();
                rest = rest.substring(a.end());
                if (rest.startsWith("|")) rest = rest.substring(1);
                else rest = "";
            }
            note = rest.trim();
            if (note.length() > 40) note = note.substring(0, 40);
        } else if (c.chatWaypointMoreFormats) {
            Matcher inq = INQUISITOR.matcher(t);
            if (inq.find()) {
                pos = pos(inq.group(2), inq.group(3), inq.group(4));
                area = inq.group("area");
            } else {
                msg = PlayerChat.current();
                parsed = true;
                if (msg != null) pos = WaypointSet.fromChatLoose(msg.message());
            }
        }
        if (pos == null) return;
        if (!parsed) msg = PlayerChat.current();
        if (msg != null && c.chatWaypointIgnoreAllChat && msg.channel() == PlayerChat.Channel.ALL) return;
        if (msg != null && c.chatWaypointIgnoreOwn && msg.own()) return;

        String label = "§b" + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (c.chatWaypointDetails) {
            if (msg != null) label = "§e" + msg.author() + " " + label;
            if (!area.isEmpty()) label += " §7at §b" + area;
            if (!note.isEmpty()) label += " §f" + note;
        }
        waypoints.add(pos, label, TeslaMapsConfig.parseColor(c.chatWaypointColor), DURATION_MS);
    }

    private static BlockPos pos(String x, String y, String z) {
        try {
            return new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void tick() {
        Object level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            waypoints.clear();
        }
        TeslaMapsConfig c = cfg();
        waypoints.distance(c.chatWaypointDetails).removeWithin(c.chatWaypointRemoveOnArrival ? ARRIVAL : -1);
        waypoints.tick();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!cfg().chatWaypoint || waypoints.all().isEmpty()) return;
        waypoints.render(matrices, cameraPos);
    }

    public static void reset() { waypoints.clear(); }
}
