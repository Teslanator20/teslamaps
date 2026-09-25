package com.teslamaps.command;

// Skyblocker ConfigCommands / SkyHanni SkyHanniConfigSearchResetCommand + HelpCommand
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.MapConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class ConfigCommands {

    private static final String PREFIX = "§b[TeslaMaps] §r";
    private static final Gson GSON = new Gson();
    private static final int PER_PAGE = 15;
    private static Map<String, Field> fields;
    private static Component lastHelp;
    private static String lastHelpText;

    private ConfigCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SuggestionProvider<FabricClientCommandSource> anyField = (c, b) -> suggestFields(b, false);
        SuggestionProvider<FabricClientCommandSource> boolField = (c, b) -> suggestFields(b, true);

        dispatcher.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("config")
                        .then(ClientCommands.literal("get")
                                .then(ClientCommands.argument("field", StringArgumentType.word()).suggests(anyField)
                                        .executes(c -> get(c.getSource(), StringArgumentType.getString(c, "field")))))
                        .then(ClientCommands.literal("set")
                                .then(ClientCommands.argument("field", StringArgumentType.word()).suggests(anyField)
                                        .then(ClientCommands.argument("value", StringArgumentType.greedyString())
                                                .suggests((c, b) -> suggestValue(c, b))
                                                .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "field"),
                                                        StringArgumentType.getString(c, "value"))))))
                        .then(ClientCommands.literal("toggle")
                                .then(ClientCommands.argument("field", StringArgumentType.word()).suggests(boolField)
                                        .executes(c -> toggle(c.getSource(), StringArgumentType.getString(c, "field")))))
                        .then(ClientCommands.literal("reset")
                                .then(ClientCommands.argument("field", StringArgumentType.word()).suggests(anyField)
                                        .executes(c -> reset(c.getSource(), StringArgumentType.getString(c, "field")))))
                        .then(ClientCommands.literal("search")
                                .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                        .executes(c -> search(c.getSource(), StringArgumentType.getString(c, "query"))))))
                .then(ClientCommands.literal("help")
                        .executes(c -> help(dispatcher, c.getSource(), ""))
                        .then(ClientCommands.argument("query", StringArgumentType.greedyString())
                                .executes(c -> help(dispatcher, c.getSource(), StringArgumentType.getString(c, "query")))))
                .then(ClientCommands.literal("newfeatures")
                        .executes(c -> {
                            Minecraft.getInstance().schedule(com.teslamaps.features.NewOptions::open);
                            return 1;
                        }))
                .then(ClientCommands.argument("search", StringArgumentType.greedyString())
                        .executes(c -> {
                            String q = StringArgumentType.getString(c, "search");
                            Minecraft.getInstance().schedule(() -> MapConfigScreen.openWithSearch(q));
                            return 1;
                        })));
    }

    // ---- config fields ----

    private static Map<String, Field> fields() {
        if (fields != null) return fields;
        Map<String, Field> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Field f : TeslaMapsConfig.class.getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m) || Modifier.isTransient(m)) continue;
            out.put(f.getName(), f);
        }
        fields = out;
        return out;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFields(
            com.mojang.brigadier.suggestion.SuggestionsBuilder b, boolean boolOnly) {
        String typed = b.getRemaining().toLowerCase(Locale.ROOT);
        int n = 0;
        for (Field f : fields().values()) {
            if (boolOnly && f.getType() != boolean.class) continue;
            String name = f.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(typed) && !name.equalsIgnoreCase(typed)) {
                b.suggest(name);
                if (++n >= 200) break;
            }
        }
        return b.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestValue(
            CommandContext<FabricClientCommandSource> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        Field f = fields().get(StringArgumentType.getString(c, "field"));
        if (f != null) {
            String typed = b.getRemaining();
            List<String> opts = new ArrayList<>();
            if (f.getType() == boolean.class) { opts.add("true"); opts.add("false"); }
            else opts.add(valueOf(f));
            for (String o : opts) if (o.startsWith(typed) && !o.equals(typed)) b.suggest(o);
        }
        return b.buildFuture();
    }

    private static Field field(FabricClientCommandSource src, String name) {
        Field f = fields().get(name);
        if (f == null) src.sendFeedback(Component.literal(PREFIX + "§cNo option called " + name + " §7(try /tmap config search)"));
        return f;
    }

    private static String valueOf(Field f) {
        try {
            Object v = f.get(TeslaMapsConfig.get());
            return f.getType() == String.class && v != null ? (String) v : GSON.toJson(v);
        } catch (IllegalAccessException e) {
            return "?";
        }
    }

    private static String shorten(String s) {
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }

    private static int get(FabricClientCommandSource src, String name) {
        Field f = field(src, name);
        if (f == null) return 0;
        src.sendFeedback(Component.literal(PREFIX + "§f" + f.getName() + " §7= §e" + shorten(valueOf(f))));
        return 1;
    }

    private static int set(FabricClientCommandSource src, String name, String raw) {
        Field f = field(src, name);
        if (f == null) return 0;
        Object v;
        try {
            v = f.getType() == String.class && !raw.startsWith("\"") ? raw : GSON.fromJson(raw, f.getGenericType());
        } catch (JsonParseException | IllegalArgumentException e) {
            src.sendFeedback(Component.literal(PREFIX + "§cNot a valid " + f.getType().getSimpleName() + ": " + raw));
            return 0;
        }
        if (v == null && f.getType().isPrimitive()) {
            src.sendFeedback(Component.literal(PREFIX + "§cValue missing"));
            return 0;
        }
        return write(src, f, v);
    }

    private static int toggle(FabricClientCommandSource src, String name) {
        Field f = field(src, name);
        if (f == null) return 0;
        if (f.getType() != boolean.class) {
            src.sendFeedback(Component.literal(PREFIX + "§c" + f.getName() + " is not an on/off option"));
            return 0;
        }
        try {
            return write(src, f, !f.getBoolean(TeslaMapsConfig.get()));
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    private static int reset(FabricClientCommandSource src, String name) {
        Field f = field(src, name);
        if (f == null) return 0;
        try {
            Object def = f.get(new TeslaMapsConfig());
            // copy, a fresh default collection must not be shared
            if (def != null && !f.getType().isPrimitive() && f.getType() != String.class)
                def = GSON.fromJson(GSON.toJson(def), f.getGenericType());
            return write(src, f, def);
        } catch (IllegalAccessException | RuntimeException e) {
            src.sendFeedback(Component.literal(PREFIX + "§cCould not reset " + f.getName()));
            return 0;
        }
    }

    private static int write(FabricClientCommandSource src, Field f, Object v) {
        try {
            f.set(TeslaMapsConfig.get(), v);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            src.sendFeedback(Component.literal(PREFIX + "§cCould not set " + f.getName()));
            return 0;
        }
        TeslaMapsConfig.save();
        src.sendFeedback(Component.literal(PREFIX + "§f" + f.getName() + " §7-> §a" + shorten(valueOf(f))));
        return 1;
    }

    private static int search(FabricClientCommandSource src, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        StringBuilder clip = new StringBuilder();
        List<Field> hits = new ArrayList<>();
        for (Field f : fields().values()) {
            if (f.getName().toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(f);
                clip.append(f.getName()).append(" = ").append(valueOf(f)).append('\n');
            }
        }
        if (hits.isEmpty()) {
            src.sendFeedback(Component.literal(PREFIX + "§7No option matches \"" + query + "\""));
            return 0;
        }
        MutableComponent out = Component.literal(PREFIX + "§f" + hits.size() + " option" + (hits.size() == 1 ? "" : "s")
                + " §7(copied to clipboard)");
        int shown = Math.min(30, hits.size());
        for (int i = 0; i < shown; i++) {
            Field f = hits.get(i);
            out.append(Component.literal("\n§7 " + f.getName() + " §8= §e" + shorten(valueOf(f)))
                    .withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/tmap config set " + f.getName() + " "))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to set")))));
        }
        if (hits.size() > shown) out.append(Component.literal("\n§8 ... " + (hits.size() - shown) + " more in the clipboard"));
        Minecraft.getInstance().keyboardHandler.setClipboard(clip.toString());
        src.sendFeedback(out);
        return 1;
    }

    // ---- help ----

    private static int help(CommandDispatcher<FabricClientCommandSource> dispatcher, FabricClientCommandSource src, String args) {
        String query = args.trim();
        int page = 1;
        int p = query.indexOf("-p");
        if (p >= 0) {
            String rest = query.substring(p + 2).trim();
            query = query.substring(0, p).trim();
            try { page = Integer.parseInt(rest.split(" ")[0]); } catch (NumberFormatException ignored) {}
        }
        CommandNode<FabricClientCommandSource> tmap = dispatcher.getRoot().getChild("tmap");
        if (tmap == null) return 0;
        String q = query.toLowerCase(Locale.ROOT);
        List<String[]> rows = new ArrayList<>();
        Map<CommandNode<FabricClientCommandSource>, String> smart = dispatcher.getSmartUsage(tmap, src);
        for (Map.Entry<CommandNode<FabricClientCommandSource>, String> e : smart.entrySet()) {
            CommandNode<FabricClientCommandSource> node = e.getKey();
            if (!(node instanceof com.mojang.brigadier.tree.LiteralCommandNode)) continue;
            String[] all = dispatcher.getAllUsage(node, src, true);
            StringBuilder full = new StringBuilder();
            int lines = 0;
            for (String u : all) {
                if (lines++ >= 12) { full.append("\n..."); break; }
                if (full.length() > 0) full.append('\n');
                full.append("/tmap ").append(node.getName()).append(' ').append(u);
            }
            String usage = "/tmap " + e.getValue();
            if (!q.isEmpty() && !usage.toLowerCase(Locale.ROOT).contains(q) && !full.toString().toLowerCase(Locale.ROOT).contains(q)) continue;
            rows.add(new String[]{node.getName(), usage, full.length() == 0 ? usage : full.toString()});
        }
        rows.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        int pages = Math.max(1, (rows.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(pages, page));

        MutableComponent out = Component.literal("§b[TeslaMaps] §f/tmap commands" + (q.isEmpty() ? "" : " §7matching \"" + query + "\"")
                + " §8(" + page + "/" + pages + ", " + rows.size() + ")");
        for (int i = (page - 1) * PER_PAGE; i < Math.min(rows.size(), page * PER_PAGE); i++) {
            String[] r = rows.get(i);
            out.append(Component.literal("\n §e" + shorten(r[1]))
                    .withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/tmap " + r[0] + " "))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(r[2]).withStyle(ChatFormatting.GRAY)))));
        }
        if (rows.isEmpty()) out.append(Component.literal("\n §7Nothing found"));
        String base = "/tmap help " + (query.isEmpty() ? "" : query + " ") + "-p ";
        MutableComponent nav = Component.literal("\n ");
        if (page > 1) {
            int prev = page - 1;
            nav.append(Component.literal("§a[< Prev] ").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(base + prev))));
        }
        if (page < pages) {
            int next = page + 1;
            nav.append(Component.literal("§a[Next >]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(base + next))));
        }
        if (pages > 1) out.append(nav);

        removeLastHelp();
        lastHelp = out;
        lastHelpText = out.getString();
        src.sendFeedback(out);
        return 1;
    }

    /** The list replaces its previous copy instead of stacking up. */
    private static void removeLastHelp() {
        if (lastHelp == null) return;
        var acc = (com.teslamaps.mixin.ChatComponentAccessor) Minecraft.getInstance().gui.getChat();
        List<GuiMessage> all = acc.teslamaps$getAllMessages();
        for (int i = 0; i < Math.min(all.size(), 100); i++) {
            Component c = all.get(i).content();
            if (c == lastHelp || c.getString().equals(lastHelpText)) {
                all.remove(i);
                acc.teslamaps$refreshTrimmedMessages();
                break;
            }
        }
        lastHelp = null;
        lastHelpText = null;
    }
}
