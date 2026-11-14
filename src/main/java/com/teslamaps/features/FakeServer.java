package com.teslamaps.features;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import java.util.Locale;

// non-hypixel dungeon servers that should count as hypixel
public class FakeServer {

    public static boolean active() {
        ServerData s = Minecraft.getInstance().getCurrentServer();
        if (s == null || s.ip == null) return false;
        String ip = host(s.ip);
        for (String e : TeslaMapsConfig.get().fakeServers) if (host(e).equals(ip)) return true;
        return false;
    }

    public static DungeonFloor floor() {
        DungeonFloor f = DungeonFloor.fromString(TeslaMapsConfig.get().fakeServerFloor);
        return f == DungeonFloor.UNKNOWN ? DungeonFloor.F7 : f;
    }

    private static String host(String ip) {
        ip = ip.trim().toLowerCase(Locale.ROOT);
        return ip.endsWith(":25565") ? ip.substring(0, ip.length() - 6) : ip;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("fakeserver")
                        .executes(c -> {
                            ServerData s = Minecraft.getInstance().getCurrentServer();
                            msg(c.getSource(), "§7Fake servers: §f" + TeslaMapsConfig.get().fakeServers
                                    + " §7floor: §f" + floor() + " §7here: " + (active() ? "§ayes" : "§cno")
                                    + (s != null ? " §8(" + s.ip + ")" : ""));
                            msg(c.getSource(), "§7/tmap fakeserver add|remove [ip], floor <F1-F7|M1-M7>");
                            return 1;
                        })
                        .then(ClientCommands.literal("add")
                                .executes(c -> edit(c.getSource(), null, true))
                                .then(ClientCommands.argument("ip", StringArgumentType.greedyString())
                                        .executes(c -> edit(c.getSource(), StringArgumentType.getString(c, "ip"), true))))
                        .then(ClientCommands.literal("remove")
                                .executes(c -> edit(c.getSource(), null, false))
                                .then(ClientCommands.argument("ip", StringArgumentType.greedyString())
                                        .executes(c -> edit(c.getSource(), StringArgumentType.getString(c, "ip"), false))))
                        .then(ClientCommands.literal("floor")
                                .then(ClientCommands.argument("floor", StringArgumentType.word())
                                        .executes(c -> {
                                            DungeonFloor f = DungeonFloor.fromString(StringArgumentType.getString(c, "floor"));
                                            if (f == DungeonFloor.UNKNOWN) {
                                                msg(c.getSource(), "§cUnknown floor");
                                                return 0;
                                            }
                                            TeslaMapsConfig.get().fakeServerFloor = f.name();
                                            TeslaMapsConfig.save();
                                            msg(c.getSource(), "§7Fake server floor: §f" + f.name());
                                            return 1;
                                        }))))));
    }

    private static int edit(FabricClientCommandSource src, String ip, boolean add) {
        if (ip == null) {
            ServerData s = Minecraft.getInstance().getCurrentServer();
            if (s == null) {
                msg(src, "§cNot on a server, give an ip");
                return 0;
            }
            ip = s.ip;
        }
        String h = host(ip);
        var list = TeslaMapsConfig.get().fakeServers;
        list.removeIf(e -> host(e).equals(h));
        if (add) list.add(h);
        TeslaMapsConfig.save();
        msg(src, (add ? "§aAdded " : "§7Removed ") + "§f" + h);
        return 1;
    }

    private static void msg(FabricClientCommandSource s, String text) {
        s.sendFeedback(Component.literal(text));
    }
}
