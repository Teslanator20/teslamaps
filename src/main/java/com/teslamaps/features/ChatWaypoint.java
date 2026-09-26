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
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("tmap").then(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("wp")
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("addtemp")
                                        .executes(c -> { feedback(addTemp("")); return 1; })
                                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("args",
                                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(c -> {
                                                    feedback(addTemp(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "args")));
                                                    return 1;
                                                })))
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("share")
                                        .executes(c -> { feedback(share("")); return 1; })
                                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("coords",
                                                        com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(c -> {
                                                    feedback(share(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "coords")));
                                                    return 1;
                                                })))
                                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("clear")
                                        .executes(c -> { waypoints.clear(); feedback("§aCleared chat waypoints."); return 1; })))));
    }

    // Odin WaypointCommand: addtemp [name] [x y z], share [x y z]
    private static final long TEMP_MS = 60_000;
    private static final int BOUND = 5000;
    private static final int[] TEMP_COLORS = {0xFF5555, 0x55FF55, 0x5555FF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFAA00, 0xAA00AA};

    private static void feedback(String msg) {
        if (msg == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[TeslaMaps] §r" + msg));
    }

    private static String commandsOff() {
        TeslaMapsConfig c = cfg();
        if (!c.chatWaypointCommands) return "§cTurn on Render > Chat Waypoint > /tmap wp Commands first.";
        if (!c.chatWaypoint || !c.section("Render")) return "§cChat Waypoint is off.";
        return null;
    }

    private static String addTemp(String args) {
        String off = commandsOff();
        if (off != null) return off;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        String[] parts = args.trim().isEmpty() ? new String[0] : args.trim().split("\\s+");
        BlockPos here = mc.player.blockPosition();
        String name = "";
        int[] xyz = {here.getX(), here.getY(), here.getZ()};
        int start = 0;
        if (parts.length > 0 && !isInt(parts[0].replace(",", ""))) {
            name = parts[0];
            start = 1;
        }
        for (int i = 0; i < 3 && start + i < parts.length; i++) {
            String p = parts[start + i].replace(",", "");
            if (!isInt(p)) return "§cUsage: /tmap wp addtemp [name] [x y z]";
            xyz[i] = Integer.parseInt(p);
        }
        if (Math.abs(xyz[0]) > BOUND || Math.abs(xyz[1]) > BOUND || Math.abs(xyz[2]) > BOUND) {
            return "§cCoordinates out of bounds (±" + BOUND + ").";
        }
        BlockPos pos = new BlockPos(xyz[0], xyz[1], xyz[2]);
        for (WaypointSet.Waypoint w : waypoints.all()) {
            if (w.pos.equals(pos)) return "§cThere is already a waypoint at " + xyz[0] + " " + xyz[1] + " " + xyz[2] + ".";
        }
        int color = TEMP_COLORS[java.util.concurrent.ThreadLocalRandom.current().nextInt(TEMP_COLORS.length)];
        String label = (name.isEmpty() ? "" : "§f" + name + " ") + "§b" + xyz[0] + " " + xyz[1] + " " + xyz[2];
        waypoints.add(pos, label, 0xFF000000 | color, TEMP_MS);
        return "§aAdded temporary waypoint " + label + " §a(60s).";
    }

    private static String share(String args) {
        String off = commandsOff();
        if (off != null) return off;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return null;
        String[] parts = args.trim().isEmpty() ? new String[0] : args.trim().split("[\\s,]+");
        BlockPos p = mc.player.blockPosition();
        int x = p.getX(), y = p.getY(), z = p.getZ();
        if (parts.length > 0) {
            if (parts.length != 3 || !isInt(parts[0]) || !isInt(parts[1]) || !isInt(parts[2])) return "§cUsage: /tmap wp share [x y z]";
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        }
        com.teslamaps.features.ChatQueue.chat("x: " + x + ", y: " + y + ", z: " + z);
        return null;
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
            if (!note.isEmpty() && c.chatWaypointCommands) label += " §f" + note;
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
