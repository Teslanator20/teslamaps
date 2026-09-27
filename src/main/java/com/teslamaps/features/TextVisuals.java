package com.teslamaps.features;

// Visual words: SkyHanni's ModifyVisualWords / Odin's VisualWords / SkyOcean's TextReplacements.
// Full text shadow: SkyOcean's BakedGlyphMixin.
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Replaces words in every rendered text (StreamerMode-style font hook), plus the full text shadow toggle. */
public final class TextVisuals {

    public static class VisualWord {
        public String from = "";
        public String to = "";
        public boolean enabled = true;

        public VisualWord() {}

        VisualWord(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    private record Word(String from, String toLegacy, int[] cps, Style[] styles) {}

    public static volatile boolean on;
    public static volatile boolean fullShadow;
    public static volatile boolean noShadow;
    private static volatile Word[] words = new Word[0];
    private static volatile boolean ignoreCase;
    private static volatile String firstChars = "";
    private static int hash;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private TextVisuals() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TextVisuals")
                .tickAlways(TextVisuals::refresh)
                .config("QoL & Misc", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Text");
                    b.toggle("Full Text Shadow", () -> c.fullTextShadow, v -> c.fullTextShadow = v)
                            .desc("Draws the text shadow all around each letter (outline look) instead of only down-right.");
                    b.toggle("No Text Shadow", () -> c.noTextShadow, v -> c.noTextShadow = v)
                            .desc("Removes the 1 px shadow under all text: vanilla HUD, menus, chat and every TeslaMaps panel.")
                            .tags("shadow", "flat", "font");
                    b.toggle("Visual Words", () -> c.visualWords, v -> c.visualWords = v)
                            .desc("Replaces words or phrases in every text you see (chat, tooltips, scoreboard, name tags). Only on your screen.");
                    b.toggle("  Ignore Case", () -> c.visualWordsIgnoreCase, v -> c.visualWordsIgnoreCase = v);
                    b.info("  /tmap visualwords add <word> | <replacement>");
                    b.info("  & color codes work in the replacement · list · remove <n> · toggle <n> · clear");
                    b.button("  List Visual Words In Chat", TextVisuals::list);
                    b.toggle("Nick Hider", () -> c.nickHider, v -> c.nickHider = v)
                            .desc("Replaces your own name in every text (chat, tab, scoreboard, name tags, tooltips). Set the nick with /tmap nick <name>, & color codes work. Other names: Visual Words.")
                            .tags("streamer", "name changer", "nick");
                    b.info("  /tmap nick <name> · /tmap nick reset");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("nick")
                        .executes(x -> { msg("§7Nick: §r" + (nick().isEmpty() ? "§8(none)" : legacy(nick())) + " §7- /tmap nick <name> | reset"); return 1; })
                        .then(ClientCommands.literal("reset").executes(x -> { setNick(""); return 1; }))
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(x -> { setNick(StringArgumentType.getString(x, "name").trim()); return 1; })))
                .then(ClientCommands.literal("visualwords")
                        .executes(x -> { list(); return 1; })
                        .then(ClientCommands.literal("list").executes(x -> { list(); return 1; }))
                        .then(ClientCommands.literal("clear").executes(x -> {
                            cfg().visualWordList.clear();
                            TeslaMapsConfig.save();
                            msg("§7Cleared all visual words.");
                            return 1;
                        }))
                        .then(ClientCommands.literal("add").then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(x -> { add(StringArgumentType.getString(x, "text")); return 1; })))
                        .then(ClientCommands.literal("remove").then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                                .executes(x -> { remove(IntegerArgumentType.getInteger(x, "n")); return 1; })))
                        .then(ClientCommands.literal("toggle").then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                                .executes(x -> { toggle(IntegerArgumentType.getInteger(x, "n")); return 1; }))))));
    }

    private static String nick() {
        String n = cfg().nickHiderName;
        return n == null ? "" : n;
    }

    private static void setNick(String n) {
        cfg().nickHiderName = n;
        TeslaMapsConfig.save();
        msg(n.isEmpty() ? "§7Nick cleared." : "§7Nick set to §r" + legacy(n));
        if (!n.isEmpty() && !cfg().nickHider) msg("§7Nick Hider is off, turn it on in Interface.");
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + s));
    }

    private static void add(String raw) {
        int bar = raw.indexOf('|');
        if (bar < 0) {
            msg("§cUse: /tmap visualwords add <word> | <replacement>");
            return;
        }
        String from = raw.substring(0, bar).trim();
        String to = raw.substring(bar + 1).trim();
        if (from.isEmpty()) {
            msg("§cThe word to replace is empty.");
            return;
        }
        List<VisualWord> list = cfg().visualWordList;
        list.removeIf(w -> w.from.equals(from));
        list.add(new VisualWord(from, to));
        TeslaMapsConfig.save();
        msg("§7Added §f" + from + " §7-> §r" + legacy(to));
        if (!cfg().visualWords) msg("§7Visual Words is off, turn it on in Interface.");
    }

    private static void remove(int n) {
        List<VisualWord> list = cfg().visualWordList;
        if (n > list.size()) { msg("§cNo entry " + n + "."); return; }
        VisualWord w = list.remove(n - 1);
        TeslaMapsConfig.save();
        msg("§7Removed §f" + w.from);
    }

    private static void toggle(int n) {
        List<VisualWord> list = cfg().visualWordList;
        if (n > list.size()) { msg("§cNo entry " + n + "."); return; }
        VisualWord w = list.get(n - 1);
        w.enabled = !w.enabled;
        TeslaMapsConfig.save();
        msg("§f" + w.from + (w.enabled ? " §aenabled" : " §cdisabled"));
    }

    private static void list() {
        List<VisualWord> list = cfg().visualWordList;
        if (list.isEmpty()) {
            msg("§7No visual words. §f/tmap visualwords add <word> | <replacement>");
            return;
        }
        msg("§7Visual words (" + (cfg().visualWords ? "§aon" : "§coff") + "§7):");
        for (int i = 0; i < list.size(); i++) {
            VisualWord w = list.get(i);
            msg("§7" + (i + 1) + ". " + (w.enabled ? "§f" : "§8§m") + w.from + "§r §7-> §r" + legacy(w.to));
        }
    }

    private static String legacy(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length() && ChatFormatting.getByCode(s.charAt(i + 1)) != null) sb.append('§');
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static void refresh() {
        TeslaMapsConfig c = cfg();
        fullShadow = c.fullTextShadow;
        noShadow = c.noTextShadow;
        String self = Minecraft.getInstance().getUser().getName();
        boolean nickOn = c.nickHider && !nick().isEmpty() && self != null && !self.isEmpty();
        int h = c.visualWords ? 31 * c.visualWordList.size() + (c.visualWordsIgnoreCase ? 1 : 0) : 0;
        if (c.visualWords) for (VisualWord w : c.visualWordList) h = 31 * h + w.from.hashCode() * 7 + w.to.hashCode() + (w.enabled ? 1 : 0);
        if (nickOn) h = 31 * h + self.hashCode() * 13 + nick().hashCode() + 1;
        if (h == hash && CACHE.size() < 4096) return;
        hash = h;
        CACHE.clear();
        List<Word> out = new ArrayList<>();
        if (nickOn && !self.equals(nick())) out.add(compile(self, legacy(nick())));
        if (c.visualWords) {
            for (VisualWord w : c.visualWordList) {
                if (!w.enabled || w.from.isEmpty()) continue;
                out.add(compile(w.from, legacy(w.to)));
            }
        }
        ignoreCase = c.visualWordsIgnoreCase;
        StringBuilder fc = new StringBuilder();
        for (Word w : out) {
            char ch = w.from.charAt(0);
            fc.append(ch);
            if (ignoreCase) fc.append(Character.toLowerCase(ch)).append(Character.toUpperCase(ch));
        }
        firstChars = fc.toString();
        words = out.toArray(new Word[0]);
        on = words.length > 0;
    }

    private static Word compile(String from, String to) {
        List<Integer> cps = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        Style style = Style.EMPTY;
        for (int i = 0; i < to.length(); ) {
            int cp = to.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§' && i < to.length()) {
                ChatFormatting f = ChatFormatting.getByCode(to.charAt(i));
                if (f != null) {
                    i++;
                    if (f == ChatFormatting.RESET) style = Style.EMPTY;
                    else if (f.isColor()) style = Style.EMPTY.withColor(f);
                    else style = style.applyFormat(f);
                    continue;
                }
            }
            cps.add(cp);
            styles.add(style);
        }
        int[] a = new int[cps.size()];
        for (int i = 0; i < a.length; i++) a[i] = cps.get(i);
        return new Word(from, to, a, styles.toArray(new Style[0]));
    }

    private static int find(CharSequence text, String key, int from) {
        int n = text.length(), k = key.length();
        boolean ic = ignoreCase;
        outer:
        for (int i = from; i <= n - k; i++) {
            for (int j = 0; j < k; j++) {
                char a = text.charAt(i + j), b = key.charAt(j);
                if (a != b && !(ic && Character.toLowerCase(a) == Character.toLowerCase(b))) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** Active §-codes at {@code end}, so the original formatting resumes after a coloured replacement. */
    private static String activeCodes(String s, int end) {
        String color = "";
        StringBuilder fmt = new StringBuilder();
        for (int i = 0; i + 1 < end; i++) {
            if (s.charAt(i) != '§') continue;
            ChatFormatting f = ChatFormatting.getByCode(s.charAt(i + 1));
            if (f == null) continue;
            if (f == ChatFormatting.RESET) { color = "§r"; fmt.setLength(0); }
            else if (f.isColor()) { color = "§" + s.charAt(i + 1); fmt.setLength(0); }
            else fmt.append('§').append(s.charAt(i + 1));
            i++;
        }
        return (color.isEmpty() ? "§r" : color) + fmt;
    }

    public static String replace(String text) {
        if (text == null || text.isEmpty()) return text;
        Word[] ws = words;
        boolean any = false;
        for (Word w : ws) if (find(text, w.from, 0) >= 0) { any = true; break; }
        if (!any) return text;
        String cached = CACHE.get(text);
        if (cached != null) return cached;
        String out = text;
        for (Word w : ws) {
            int idx = find(out, w.from, 0);
            if (idx < 0) continue;
            StringBuilder sb = new StringBuilder(out.length() + 16);
            int last = 0;
            boolean coloured = w.toLegacy.indexOf('§') >= 0;
            while (idx >= 0) {
                sb.append(out, last, idx).append(w.toLegacy);
                if (coloured) sb.append(activeCodes(out, idx));
                last = idx + w.from.length();
                idx = find(out, w.from, last);
            }
            sb.append(out, last, out.length());
            out = sb.toString();
        }
        CACHE.put(text, out);
        return out;
    }

    public static FormattedCharSequence replace(FormattedCharSequence seq) {
        if (seq.accept((index, style, cp) -> firstChars.indexOf(cp) < 0)) return seq;
        Word[] ws = words;
        StringBuilder sb = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        seq.accept((index, style, cp) -> {
            int before = sb.length();
            sb.appendCodePoint(cp);
            for (int i = before; i < sb.length(); i++) styles.add(style);
            return true;
        });
        boolean any = false;
        for (Word w : ws) if (find(sb, w.from, 0) >= 0) { any = true; break; }
        if (!any) return seq;
        StringBuilder text = sb;
        List<Style> st = styles;
        for (Word w : ws) {
            int idx = find(text, w.from, 0);
            if (idx < 0) continue;
            StringBuilder nt = new StringBuilder(text.length() + 16);
            List<Style> ns = new ArrayList<>(st.size() + 16);
            int last = 0;
            while (idx >= 0) {
                nt.append(text, last, idx);
                ns.addAll(st.subList(last, idx));
                Style base = st.get(idx);
                for (int i = 0; i < w.cps.length; i++) {
                    int before = nt.length();
                    nt.appendCodePoint(w.cps[i]);
                    Style s = w.styles[i].applyTo(base);
                    for (int j = before; j < nt.length(); j++) ns.add(s);
                }
                last = idx + w.from.length();
                idx = find(text, w.from, last);
            }
            nt.append(text, last, text.length());
            ns.addAll(st.subList(last, text.length()));
            text = nt;
            st = ns;
        }
        String out = text.toString();
        Style[] arr = st.toArray(new Style[0]);
        return sink -> {
            int idx = 0;
            for (int i = 0; i < out.length(); ) {
                int cp = out.codePointAt(i);
                if (!sink.accept(idx++, arr[i], cp)) return false;
                i += Character.charCount(cp);
            }
            return true;
        };
    }
}
