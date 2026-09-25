package com.teslamaps.features;

// Concept from Bax's IslandChangeTrigger
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class IslandAlerts {

    public static final String[] MODES = {"Title + Sound", "Title", "Chat + Sound", "Chat"};
    private static final String[] ISLANDS = {"Any", "Private_Island", "Hub", "Garden", "The_Park", "Gold_Mine",
            "Deep_Caverns", "Dwarven_Mines", "Crystal_Hollows", "Mineshaft", "The_Farming_Islands", "Spiders_Den",
            "The_End", "Crimson_Isle", "Kuudra", "Dungeon_Hub", "Catacombs", "Jerrys_Workshop", "The_Rift",
            "Backwater_Bayou", "Galatea"};

    private static String last = "";
    private static int ticks;

    private IslandAlerts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("IslandAlerts").section("Chat")
                .when(() -> cfg().islandAlerts)
                .tick(IslandAlerts::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Island Change Alerts", () -> c.islandAlerts, v -> c.islandAlerts = v)
                            .desc("Your own reminders when you enter an island. {from} and {to} are replaced with the island names.");
                    b.dropdown("  Alert Style", MODES, () -> c.islandAlertMode, v -> c.islandAlertMode = v);
                    b.soundDropdown("  Sound", SoundOptions.keys(), () -> c.islandAlertSound, v -> c.islandAlertSound = v);
                    b.info("  /tmap islandalert add <island|any> <text>");
                    b.button("  List Rules", IslandAlerts::list);
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        String area = SkyblockUtils.getArea();
        if (area.isEmpty() || area.equals(last)) return;
        String from = last.isEmpty() ? "none" : last;
        last = area;
        for (String rule : cfg().islandAlertRules) {
            int sep = rule.indexOf('|');
            if (sep < 0) continue;
            String island = rule.substring(0, sep);
            if (!island.equalsIgnoreCase("any") && !norm(island).equals(norm(area))) continue;
            fire(rule.substring(sep + 1).replace("{from}", from).replace("{to}", area));
        }
    }

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toLowerCase().toCharArray()) if (Character.isLetterOrDigit(ch)) sb.append(ch);
        return sb.toString();
    }

    private static void fire(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String mode = cfg().islandAlertMode;
        String t = text.replace('&', '§');
        if (mode.startsWith("Title")) {
            mc.gui.setTitle(Component.literal(t));
            mc.gui.setSubtitle(Component.empty());
            mc.gui.setTimes(5, 50, 10);
        } else {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + t));
        }
        if (mode.endsWith("Sound")) LoudSound.play(SoundOptions.resolve(cfg().islandAlertSound), 1f, 1f);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }

    private static void list() {
        List<String> rules = cfg().islandAlertRules;
        if (rules.isEmpty()) {
            msg("§7No island alerts. §f/tmap islandalert add <island|any> <text>");
            return;
        }
        msg("§fIsland alerts:");
        for (int i = 0; i < rules.size(); i++) {
            String r = rules.get(i);
            int sep = r.indexOf('|');
            if (sep < 0) continue;
            msg("§e" + (i + 1) + ". §6" + r.substring(0, sep) + "§7: §f" + r.substring(sep + 1));
        }
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("islandalert")
                .executes(c -> { list(); return 1; })
                .then(ClientCommands.literal("list").executes(c -> { list(); return 1; }))
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("island", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String s : ISLANDS) if (s.toLowerCase().startsWith(b.getRemainingLowerCase())) b.suggest(s);
                                    return b.buildFuture();
                                })
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            String island = StringArgumentType.getString(c, "island").replace('_', ' ');
                                            String text = StringArgumentType.getString(c, "text").replace("|", "/");
                                            cfg().islandAlertRules.add(island + "|" + text);
                                            TeslaMapsConfig.save();
                                            msg("§fAdded alert for §6" + island + "§f: " + text);
                                            return 1;
                                        }))))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    int n = IntegerArgumentType.getInteger(c, "n");
                                    List<String> rules = cfg().islandAlertRules;
                                    if (n > rules.size()) {
                                        msg("§cNo alert #" + n);
                                        return 0;
                                    }
                                    rules.remove(n - 1);
                                    TeslaMapsConfig.save();
                                    msg("§fRemoved alert #" + n);
                                    return 1;
                                })))
                .then(ClientCommands.literal("clear").executes(c -> {
                    cfg().islandAlertRules.clear();
                    TeslaMapsConfig.save();
                    msg("§fCleared all island alerts");
                    return 1;
                }));
    }
}
