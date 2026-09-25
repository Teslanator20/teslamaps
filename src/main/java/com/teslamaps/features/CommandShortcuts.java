package com.teslamaps.features;

// Alias redirects + extra defaults from Skyblocker's Shortcuts.java, lowercase fix from bax's EditBoxCommandCaseMixin
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CommandShortcuts {

    private static final String[][] MORE_DEFAULTS = {
            {"pa", "p accept"}, {"m", "msg"}, {"v", "visit"}, {"vp", "visit portalhub"},
            {"ca", "chat all"}, {"cp", "chat party"}, {"cg", "chat guild"}, {"co", "chat officer"},
            {"ga", "warp garden"}, {"dn", "warp dungeon_hub"},
            {"pko", "party kickoffline"}, {"pw", "party warp"}, {"pp", "party promote"}
    };

    private static final Command<Object> NOOP = ctx -> 0;

    private static Object lastDispatcher;
    private static int lastKey;
    private static final Set<String> added = new HashSet<>();

    private CommandShortcuts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CommandShortcuts").section("Chat").when(() -> cfg().shortcutTabComplete)
                .tick(CommandShortcuts::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Command Shortcuts");
                    b.button("Edit Command Shortcuts", () -> Minecraft.getInstance().setScreen(new com.teslamaps.screen.ShortcutScreen()));
                    b.toggle("  Shortcut Tab Completion", () -> c.shortcutTabComplete, v -> c.shortcutTabComplete = v)
                            .desc("Aliases get the tab completion of the command they stand for (/pk <player>)");
                    b.toggle("  Lowercase Command Names", () -> c.commandLowercase, v -> c.commandLowercase = v)
                            .desc("/Warp hub is sent as /warp hub");
                    b.button("  Add More Default Shortcuts", CommandShortcuts::addDefaults);
                    b.toggle("  Party Shortcuts (/pko /pw /pp /rpt)", () -> c.partyShortcuts, v -> c.partyShortcuts = v)
                            .desc("/pko kicks offline members, /pw warps, /pp promotes, /rpt gives the party back to the previous leader");
                    b.toggle("    Kick Reason (/pk name reason)", () -> c.partyKickReason, v -> c.partyKickReason = v)
                            .desc("Writes 'Kicking name: reason' into party chat, then kicks");
                    b.toggle("    Complete Party Members", () -> c.partyTabComplete, v -> c.partyTabComplete = v)
                            .desc("/p kick, promote, demote and transfer suggest your party members first");
                    b.toggle("    Clickable Transfer Back", () -> c.reversePtClickable, v -> c.reversePtClickable = v)
                            .desc("After a transfer to you, a clickable line gives the party back. /rpt message <text> sets a party message");
                    b.toggle("  Garden Aliases (/home /barn /tp)", () -> c.gardenWarpAliases, v -> c.gardenWarpAliases = v)
                            .desc("On the Garden only: /home warps to the garden, /barn and /tp <plot> run /plottp")
                            .tags("garden", "plottp", "warp");
                    b.keybind("    Warp Garden Key", () -> c.gardenWarpKey, v -> c.gardenWarpKey = v);
                    b.keybind("    Set Spawn Key", () -> c.gardenSetspawnKey, v -> c.gardenSetspawnKey = v);
                    b.keybind("    Barn Key", () -> c.gardenBarnKey, v -> c.gardenBarnKey = v);
                    b.info("/tmap shortcut list shows every shortcut");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("shortcut")
                        .then(ClientCommands.literal("list").executes(c -> {
                            list();
                            return 1;
                        }))
                        .then(ClientCommands.literal("defaults").executes(c -> {
                            addDefaults();
                            return 1;
                        })))));
    }

    private static void list() {
        msg("§6Command Shortcuts:");
        for (TeslaMapsConfig.Shortcut sc : cfg().shortcuts) {
            if (sc.alias == null || sc.alias.isBlank() || sc.command == null || sc.command.isBlank()) continue;
            msg(" §7/" + sc.alias.trim() + " §f→ §7/" + sc.command.trim());
        }
    }

    private static void addDefaults() {
        TeslaMapsConfig c = cfg();
        int n = 0;
        for (String[] d : MORE_DEFAULTS) {
            boolean exists = c.shortcuts.stream().anyMatch(s -> s.alias != null && s.alias.trim().equalsIgnoreCase(d[0]));
            if (exists) continue;
            c.shortcuts.add(new TeslaMapsConfig.Shortcut(d[0], d[1]));
            n++;
        }
        TeslaMapsConfig.save();
        msg("§fAdded §e" + n + "§f shortcuts.");
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        var dispatcher = mc.getConnection().getCommands();
        int key = key();
        if (dispatcher == lastDispatcher && key == lastKey) return;
        if (dispatcher != lastDispatcher) added.clear();
        lastDispatcher = dispatcher;
        lastKey = key;
        apply(dispatcher.getRoot());
    }

    private static int key() {
        int h = 1;
        for (TeslaMapsConfig.Shortcut sc : cfg().shortcuts)
            h = 31 * (31 * h + java.util.Objects.hashCode(sc.alias)) + java.util.Objects.hashCode(sc.command);
        return h;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void apply(RootCommandNode root) {
        for (TeslaMapsConfig.Shortcut sc : cfg().shortcuts) {
            if (sc.alias == null || sc.command == null) continue;
            String alias = sc.alias.trim().toLowerCase(Locale.ROOT);
            String target = sc.command.trim();
            if (target.startsWith("/")) target = target.substring(1);
            if (alias.isEmpty() || target.isEmpty() || alias.contains(" ")) continue;
            if (root.getChild(alias) != null || !added.add(alias)) continue;

            CommandNode node = root;
            for (String word : target.split(" +")) {
                node = node.getChild(word.toLowerCase(Locale.ROOT));
                if (node == null) break;
            }
            LiteralArgumentBuilder b = LiteralArgumentBuilder.literal(alias).executes(NOOP);
            if (node != null && node != root) {
                b.redirect(node);
            } else {
                b.then(RequiredArgumentBuilder.argument("args", StringArgumentType.greedyString()).executes(NOOP));
            }
            root.addChild(b.build());
        }
    }
}
