package com.teslamaps.features;

// Emote list from Odin's ChatCommands / Devonian's ChatEmotes, command scope from WpcMod's ChatEmotes
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChatEmotes {

    private static final Map<String, String> EMOTES = Map.ofEntries(
            Map.entry("<3", "❤"),
            Map.entry("o/", "( ﾟ◡ﾟ)/"),
            Map.entry("h/", "ヽ(^◇^*)/"),
            Map.entry(":star:", "✮"),
            Map.entry(":yes:", "✔"),
            Map.entry(":no:", "✖"),
            Map.entry(":java:", "☕"),
            Map.entry(":arrow:", "➜"),
            Map.entry(":shrug:", "¯\\_(ツ)_/¯"),
            Map.entry(":tableflip:", "(╯°□°）╯︵ ┻━┻"),
            Map.entry(":totem:", "☉_☉"),
            Map.entry(":typing:", "✎..."),
            Map.entry(":maths:", "√(π+x)=L"),
            Map.entry(":snail:", "@'-'"),
            Map.entry(":thinking:", "(0.o?)"),
            Map.entry(":gimme:", "༼つ◕_◕༽つ"),
            Map.entry(":wizard:", "('-')⊃━☆ﾟ.*･｡ﾟ"),
            Map.entry(":pvp:", "⚔"),
            Map.entry(":peace:", "✌"),
            Map.entry(":oof:", "OOF"),
            Map.entry(":puffer:", "<('O')>"),
            Map.entry(":cute:", "(✿◠‿◠)"),
            Map.entry(":dj:", "ヽ(⌐■_■)ノ♬"),
            Map.entry(":sloth:", "(・⊝・)"),
            Map.entry(":dog:", "(ᵔᴥᵔ)"),
            Map.entry(":snow:", "☃"),
            Map.entry(":cat:", "= ＾● ⋏ ●＾ ="),
            Map.entry(":yey:", "ヽ (◕◡◕) ﾉ"),
            Map.entry(":dab:", "<o/"),
            Map.entry(":skull:", "☠"));

    private static final Set<String> COMMANDS = Set.of("msg", "w", "tell", "message", "r", "pc", "gc", "ac", "cc", "oc");

    private ChatEmotes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        return cfg().chatEmotes && cfg().section("Chat");
    }

    public static void register() {
        ClientSendMessageEvents.MODIFY_CHAT.register(m -> on() ? replace(m, false) : m);
        ClientSendMessageEvents.MODIFY_COMMAND.register(m -> on() ? replace(m, true) : m);
        Feature.of("ChatEmotes").section("Chat")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Chat Emotes", () -> c.chatEmotes, v -> c.chatEmotes = v)
                            .desc("Replaces :shrug:, :tableflip:, <3, o/, :skull: ... with the MVP++ emote characters when you send. Only whole words; commands only for /msg, /r, /pc, /gc, /ac, /cc.")
                            .tags("emote", "emoji", "shrug", "heart");
                });
    }

    private static String replace(String msg, boolean command) {
        if (msg == null || msg.isEmpty()) return msg;
        String[] words = msg.split(" ", -1);
        int start = 0;
        if (command) {
            if (!COMMANDS.contains(words[0].toLowerCase(Locale.ROOT))) return msg;
            String c = words[0].toLowerCase(Locale.ROOT);
            start = c.equals("msg") || c.equals("w") || c.equals("tell") || c.equals("message") ? 2 : 1;
        }
        boolean changed = false;
        for (int i = start; i < words.length; i++) {
            String e = EMOTES.get(words[i]);
            if (e != null) {
                words[i] = e;
                changed = true;
            }
        }
        if (!changed) return msg;
        String out = String.join(" ", words);
        return out.length() > (command ? 255 : 256) ? msg : out;
    }
}
