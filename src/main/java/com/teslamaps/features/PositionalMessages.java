package com.teslamaps.features;

// Ported from Odin's PositionalMessages.kt + PosMsgCommand.kt
import com.teslamaps.utils.LegacyFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.PosMessage;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PositionalMessages {

    private static final int SEGMENTS = 32;
    private static final String[] COLORS = {"darkblue", "darkgreen", "darkaqua", "darkred", "darkpurple", "gold",
            "gray", "darkgray", "blue", "green", "aqua", "red", "lightpurple", "yellow", "white", "black"};

    private record Shape(List<Vec3> pts, float[] widths, AABB box, Vec3 textPos) {}

    private static final Set<PosMessage> sent = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<PosMessage, Shape> shapes = new IdentityHashMap<>();
    private static float shapeHeight = -1;
    private static ClientLevel lastLevel;

    private PositionalMessages() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PositionalMessages").section("Party").when(() -> cfg().posMessages)
                .tick(PositionalMessages::tick)
                .world(PositionalMessages::render)
                .config("Party", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Positional Messages");
                    b.toggle("Positional Messages", () -> c.posMessages, v -> c.posMessages = v)
                            .desc("Sends /pc <text> when you walk into a zone. /posmsg add | list | remove | clear");
                    b.toggle("  Only In Dungeons", () -> c.posMessagesOnlyDungeons, v -> c.posMessagesOnlyDungeons = v);
                    b.toggle("  Once Per World", () -> c.posMessagesOncePerWorld, v -> c.posMessagesOncePerWorld = v);
                    b.toggle("  Show Zones", () -> c.posMessagesShow, v -> c.posMessagesShow = v);
                    b.slider("    Cylinder Height", 0.1f, 5f, () -> c.posMessagesHeight, v -> c.posMessagesHeight = v, 1);
                    b.toggle("    Depth Check (off = through walls)", () -> c.posMessagesDepth, v -> c.posMessagesDepth = v);
                    b.toggle("    Show Message Text", () -> c.posMessagesShowText, v -> c.posMessagesShowText = v);
                    b.slider("    Text Size", 0.1f, 4f, () -> c.posMessagesTextSize, v -> c.posMessagesTextSize = v, 1);
                    b.info("/posmsg add at <x y z> <delay> <distance> <color> <text>");
                    b.info("/posmsg add in <x1 y1 z1 x2 y2 z2> <delay> <color> <text>");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> { if (cfg().posMessages) d.register(ClientCommands.literal("posmsg")
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.literal("at")
                                .then(coord("x", 0).then(coord("y", 1).then(coord("z", 2)
                                        .then(ClientCommands.argument("delay", IntegerArgumentType.integer(0))
                                                .then(ClientCommands.argument("distance", DoubleArgumentType.doubleArg(0))
                                                        .then(color()
                                                                .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                                                        .executes(PositionalMessages::addAt)))))))))
                        .then(ClientCommands.literal("in")
                                .then(coord("x", 0).then(coord("y", 1).then(coord("z", 2)
                                        .then(coord("x2", 0).then(coord("y2", 1).then(coord("z2", 2)
                                                .then(ClientCommands.argument("delay", IntegerArgumentType.integer(0))
                                                        .then(color()
                                                                .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                                                        .executes(PositionalMessages::addIn))))))))))))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                .suggests((c, sb) -> {
                                    String rem = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    for (PosMessage m : cfg().posMessageList)
                                        if (m.message != null && m.message.toLowerCase(Locale.ROOT).startsWith(rem)) sb.suggest(m.message);
                                    return sb.buildFuture();
                                })
                                .executes(c -> remove(StringArgumentType.getString(c, "message")))))
                .then(ClientCommands.literal("clear").executes(c -> {
                    cfg().posMessageList.clear();
                    changed();
                    msg("§fCleared all positional messages.");
                    return 1;
                }))
                .then(ClientCommands.literal("list").executes(c -> list()))); });
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, Double> coord(String name, int axis) {
        return ClientCommands.argument(name, DoubleArgumentType.doubleArg())
                .suggests((c, sb) -> {
                    var p = Minecraft.getInstance().player;
                    if (p != null) sb.suggest(String.valueOf((int) Math.floor(axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ())));
                    return sb.buildFuture();
                });
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> color() {
        return ClientCommands.argument("color", StringArgumentType.word())
                .suggests((c, sb) -> {
                    String rem = sb.getRemaining().toLowerCase(Locale.ROOT);
                    for (String s : COLORS) if (s.startsWith(rem)) sb.suggest(s);
                    return sb.buildFuture();
                });
    }

    private static Integer colorOf(String name) {
        for (ChatFormatting f : ChatFormatting.values()) {
            if (LegacyFormat.isColor(f) && LegacyFormat.getColor(f) != null && f.name().replace("_", "").equalsIgnoreCase(name))
                return 0xFF000000 | LegacyFormat.getColor(f);
        }
        return null;
    }

    private static int addAt(CommandContext<FabricClientCommandSource> c) {
        PosMessage m = base(c);
        if (m == null) return 0;
        m.distance = DoubleArgumentType.getDouble(c, "distance");
        cfg().posMessageList.add(m);
        changed();
        msg("§fAdded §7\"" + m.message + "\" §fat " + m.x + ", " + m.y + ", " + m.z + " (" + m.delay + "t delay, " + m.distance + " blocks)");
        return 1;
    }

    private static int addIn(CommandContext<FabricClientCommandSource> c) {
        PosMessage m = base(c);
        if (m == null) return 0;
        m.x2 = DoubleArgumentType.getDouble(c, "x2");
        m.y2 = DoubleArgumentType.getDouble(c, "y2");
        m.z2 = DoubleArgumentType.getDouble(c, "z2");
        cfg().posMessageList.add(m);
        changed();
        msg("§fAdded §7\"" + m.message + "\" §fin " + m.x + ", " + m.y + ", " + m.z + " - " + m.x2 + ", " + m.y2 + ", " + m.z2
                + " (" + m.delay + "t delay)");
        return 1;
    }

    private static PosMessage base(CommandContext<FabricClientCommandSource> c) {
        String text = StringArgumentType.getString(c, "message").trim();
        String col = StringArgumentType.getString(c, "color");
        Integer rgb = colorOf(col);
        if (rgb == null) {
            msg("§cUnknown color " + col);
            return null;
        }
        for (PosMessage o : cfg().posMessageList) {
            if (text.equalsIgnoreCase(o.message)) {
                msg("§cA message with that text already exists.");
                return null;
            }
        }
        PosMessage m = new PosMessage();
        m.x = DoubleArgumentType.getDouble(c, "x");
        m.y = DoubleArgumentType.getDouble(c, "y");
        m.z = DoubleArgumentType.getDouble(c, "z");
        m.delay = IntegerArgumentType.getInteger(c, "delay");
        m.color = rgb;
        m.message = text;
        return m;
    }

    private static int remove(String text) {
        String t = text.trim();
        int before = cfg().posMessageList.size();
        cfg().posMessageList.removeIf(m -> t.equalsIgnoreCase(m.message));
        int n = before - cfg().posMessageList.size();
        if (n == 0) {
            msg("§cNo message \"" + t + "\".");
            return 0;
        }
        changed();
        msg("§fRemoved " + n + " positional message(s): §7\"" + t + "\"");
        return 1;
    }

    private static int list() {
        List<PosMessage> l = cfg().posMessageList;
        if (l.isEmpty()) {
            msg("§fPositional message list is empty.");
            return 1;
        }
        msg("§6Positional messages:");
        for (int i = 0; i < l.size(); i++) {
            PosMessage m = l.get(i);
            String where = m.x2 != null
                    ? "in " + m.x + ", " + m.y + ", " + m.z + " - " + m.x2 + ", " + m.y2 + ", " + m.z2
                    : "at " + m.x + ", " + m.y + ", " + m.z + " r" + m.distance;
            msg(" §7" + (i + 1) + ": " + where + ", " + m.delay + "t §f\"" + m.message + "\"");
        }
        return 1;
    }

    private static void changed() {
        TeslaMapsConfig.save();
        shapes.clear();
        sent.retainAll(new ArrayList<>(cfg().posMessageList));
    }

    private static boolean activeHere() {
        return !cfg().posMessagesOnlyDungeons || DungeonManager.isInDungeon();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            sent.clear();
        }
        if (mc.player == null || !activeHere()) return;
        TeslaMapsConfig c = cfg();
        if (c.posMessagesHeight != shapeHeight || shapes.size() != c.posMessageList.size()) rebuild();
        Vec3 pos = mc.player.position();
        for (PosMessage m : c.posMessageList) {
            boolean inside;
            if (m.x2 != null && m.y2 != null && m.z2 != null) {
                inside = new AABB(m.x, m.y, m.z, m.x2, m.y2, m.z2).contains(pos);
            } else if (m.distance != null) {
                inside = pos.distanceToSqr(m.x, m.y, m.z) <= m.distance * m.distance;
            } else continue;
            if (inside) {
                if (sent.add(m) && m.message != null && !m.message.isBlank()) {
                    String text = m.message;
                    TickScheduler.schedule(m.delay, () -> Announce.party(text));
                }
            } else if (!c.posMessagesOncePerWorld) {
                sent.remove(m);
            }
        }
    }

    private static void rebuild() {
        shapes.clear();
        float h = cfg().posMessagesHeight;
        shapeHeight = h;
        for (PosMessage m : cfg().posMessageList) {
            if (m.x2 != null && m.y2 != null && m.z2 != null) {
                AABB box = new AABB(m.x, m.y, m.z, m.x2, m.y2, m.z2);
                Vec3 c = box.getCenter();
                shapes.put(m, new Shape(null, null, box, new Vec3(c.x, c.y + 1, c.z)));
            } else if (m.distance != null) {
                shapes.put(m, cylinder(m, h));
            }
        }
    }

    private static Shape cylinder(PosMessage m, float h) {
        double r = m.distance;
        List<Vec3> pts = new ArrayList<>();
        List<Float> w = new ArrayList<>();
        for (int ring = 0; ring < 2; ring++) {
            double y = m.y + ring * h;
            for (int i = 0; i <= SEGMENTS; i++) {
                double a = Math.PI * 2 * i / SEGMENTS;
                pts.add(new Vec3(m.x + Math.cos(a) * r, y, m.z + Math.sin(a) * r));
                w.add(i == 0 ? 0f : 2f);
            }
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k;
            double px = m.x + Math.cos(a) * r, pz = m.z + Math.sin(a) * r;
            pts.add(new Vec3(px, m.y, pz));
            w.add(0f);
            pts.add(new Vec3(px, m.y + h, pz));
            w.add(2f);
        }
        float[] widths = new float[w.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = w.get(i);
        return new Shape(pts, widths, null, new Vec3(m.x, m.y + 1, m.z));
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!c.posMessagesShow || !activeHere()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean through = !c.posMessagesDepth;
        for (PosMessage m : c.posMessageList) {
            Shape s = shapes.get(m);
            if (s == null) continue;
            Vec3 center = s.textPos;
            if (player.distanceToSqr(center.x, center.y - 1, center.z) > 1024) continue;
            if (s.box != null) ESPRenderer.drawBoxOutline(matrices, s.box, m.color, 2f, cam, through);
            else ESPRenderer.drawPolyline(matrices, s.pts, s.widths, m.color, cam, through);
            if (c.posMessagesShowText && m.message != null)
                ESPRenderer.drawText(matrices, m.message, center, c.posMessagesTextSize, cam, true, through, 0xFF000000 | m.color);
        }
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
