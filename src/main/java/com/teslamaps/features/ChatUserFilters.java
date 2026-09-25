package com.teslamaps.features;

// Match types from Zen's ChatCleaner / SkyHanni's MultiFilter, commands like NoammAddons' /chathider
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.ChatUserFilter;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Chat lines the player hides himself: /tmap chatfilter, or the hotkey over a line in open chat. */
public final class ChatUserFilters {

    public static final String[] MODES = {"contains", "equals", "startswith", "endswith", "regex"};

    private static final Pattern NEVER = Pattern.compile("(?!)");
    private static final Map<String, Pattern> REGEX = new HashMap<>();

    private ChatUserFilters() {}

    private static List<ChatUserFilter> list() {
        return TeslaMapsConfig.get().chatUserFilters;
    }

    /** {@code plain} = colour-stripped line. */
    public static ChatUserFilter match(String plain) {
        List<ChatUserFilter> filters = list();
        if (filters == null || filters.isEmpty()) return null;
        String t = plain.trim();
        for (int i = 0, n = filters.size(); i < n; i++) {
            ChatUserFilter f = filters.get(i);
            if (f == null || f.text == null || f.text.isEmpty() || ChatNotify.isNotify(f)) continue;
            boolean hit = switch (f.mode == null ? "contains" : f.mode) {
                case "equals" -> t.equals(f.text);
                case "startswith" -> t.startsWith(f.text);
                case "endswith" -> t.endsWith(f.text);
                case "regex" -> pattern(f.text).matcher(t).find();
                default -> t.contains(f.text);
            };
            if (hit) return f;
        }
        return null;
    }

    static Pattern pattern(String regex) {
        return REGEX.computeIfAbsent(regex, ChatUserFilters::compile);
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return NEVER;
        }
    }

    public static boolean add(String mode, String text) {
        if (text == null || text.isBlank()) return false;
        return add(mode, text, null);
    }

    public static boolean add(String mode, String text, String action) {
        if (text == null || text.isBlank()) return false;
        for (ChatUserFilter f : list()) {
            if (mode.equals(f.mode) && text.equals(f.text) && java.util.Objects.equals(action, f.action)) return false;
        }
        ChatUserFilter nf = new ChatUserFilter(mode, text);
        nf.action = action;
        list().add(nf);
        TeslaMapsConfig.save();
        return true;
    }

    /** Hotkey over a chat line: that exact line becomes an "equals" filter. */
    public static void addLine(Component line) {
        String text = ChatTimestamps.cut(StringUtil.stripTrim(line.getString()));
        var player = Minecraft.getInstance().player;
        if (text.isEmpty() || player == null) return;
        boolean added = add("equals", text);
        player.sendSystemMessage(Component.literal(added
                ? "§a[TeslaMaps] §7Now filtering: §f" + text
                : "§e[TeslaMaps] §7Already filtered: §f" + text));
    }

    private static int notify(FabricClientCommandSource src, String mode, String text) {
        if (mode.equals("regex")) {
            try {
                Pattern.compile(text);
            } catch (PatternSyntaxException e) {
                say(src, "§c[TeslaMaps] Bad regex: " + e.getDescription());
                return 0;
            }
        }
        say(src, add(mode, text, ChatNotify.ACTION) ? "§a[TeslaMaps] §7Highlighting + ping: §f" + text
                : "§e[TeslaMaps] §7Already there");
        return 1;
    }

    private static void say(FabricClientCommandSource src, String msg) {
        src.sendFeedback(Component.literal(msg));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("chatfilter")
                .executes(ctx -> {
                    List<ChatUserFilter> l = list();
                    say(ctx.getSource(), "§b[TeslaMaps] §fChat filters: " + (l.isEmpty() ? "§7(none)" : ""));
                    for (int i = 0; i < l.size(); i++) {
                        ChatUserFilter f = l.get(i);
                        say(ctx.getSource(), "§7" + (i + 1) + ". §e" + f.mode + (ChatNotify.isNotify(f) ? " §6[notify]" : "") + " §f" + f.text);
                    }
                    say(ctx.getSource(), "§7/tmap chatfilter <contains|equals|startswith|endswith|regex> <text> · notify [regex] <text> · remove <n> · clear · export · import");
                    return 1;
                });
        for (String mode : MODES) {
            root.then(ClientCommands.literal(mode)
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String text = StringArgumentType.getString(ctx, "text").trim();
                                if (mode.equals("regex")) {
                                    try {
                                        Pattern.compile(text);
                                    } catch (PatternSyntaxException e) {
                                        say(ctx.getSource(), "§c[TeslaMaps] Bad regex: " + e.getDescription());
                                        return 0;
                                    }
                                }
                                say(ctx.getSource(), add(mode, text) ? "§a[TeslaMaps] §7Filtering (" + mode + "): §f" + text
                                        : "§e[TeslaMaps] §7Already there");
                                return 1;
                            })));
        }
        root.then(ClientCommands.literal("notify")
                .then(ClientCommands.literal("regex")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> notify(ctx.getSource(), "regex", StringArgumentType.getString(ctx, "text").trim()))))
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> notify(ctx.getSource(), "contains", StringArgumentType.getString(ctx, "text").trim()))));
        root.then(ClientCommands.literal("remove")
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int n = IntegerArgumentType.getInteger(ctx, "n");
                            if (n > list().size()) {
                                say(ctx.getSource(), "§c[TeslaMaps] No filter " + n);
                                return 0;
                            }
                            ChatUserFilter f = list().remove(n - 1);
                            TeslaMapsConfig.save();
                            say(ctx.getSource(), "§a[TeslaMaps] §7Removed: §f" + f.text);
                            return 1;
                        })));
        root.then(ClientCommands.literal("clear").executes(ctx -> {
            list().clear();
            TeslaMapsConfig.save();
            say(ctx.getSource(), "§a[TeslaMaps] §7Cleared chat filters");
            return 1;
        }));
        root.then(ClientCommands.literal("export").executes(ctx -> {
            String json = new Gson().toJson(list());
            Minecraft.getInstance().keyboardHandler.setClipboard(
                    Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
            say(ctx.getSource(), "§a[TeslaMaps] §7Copied " + list().size() + " filters to the clipboard");
            return 1;
        }));
        root.then(ClientCommands.literal("import").executes(ctx -> {
            try {
                String raw = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
                String json = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
                List<ChatUserFilter> in = new Gson().fromJson(json, new TypeToken<List<ChatUserFilter>>() {}.getType());
                int added = 0;
                if (in != null) for (ChatUserFilter f : in) if (f != null && add(f.mode == null ? "contains" : f.mode, f.text, f.action)) added++;
                say(ctx.getSource(), "§a[TeslaMaps] §7Imported " + added + " filters");
                return 1;
            } catch (RuntimeException e) {
                say(ctx.getSource(), "§c[TeslaMaps] Clipboard holds no filter export");
                return 0;
            }
        }));
        return root;
    }
}
