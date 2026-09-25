package com.teslamaps.features;

// Ported from SkyHanni's TranslatorCommand.kt / GoogleTranslator.kt
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Player messages get a click that translates them through Google Translate. */
public final class ChatTranslator {

    public static final String[] LANGUAGES = {"en", "de", "fr", "es", "it", "pt", "nl", "pl", "cs", "sv", "da", "no",
            "fi", "tr", "ru", "uk", "ja", "ko", "zh-CN", "zh-TW", "ar", "hi", "id", "vi", "th", "hu", "ro", "el"};

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private ChatTranslator() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChatTranslator")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Translator");
                    b.toggle("Click Message To Translate", () -> c.chatTranslator, v -> c.chatTranslator = v)
                            .desc("Player messages (all, party, guild, co-op, private) translate into your language when clicked. The text is sent to Google Translate.")
                            .tags("translate", "language", "google");
                    b.dropdown("  Your Language", LANGUAGES,
                            () -> c.chatTranslatorLanguage == null || c.chatTranslatorLanguage.isEmpty() ? "en" : c.chatTranslatorLanguage,
                            v -> c.chatTranslatorLanguage = v);
                    b.info("/tmap translate <text>, /tmap translateto <lang> <text> copies the result");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("translate")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                            translate(StringArgumentType.getString(c, "text"), language(), "auto", false);
                            return 1;
                        })))
                .then(ClientCommands.literal("translateto")
                        .then(ClientCommands.argument("lang", StringArgumentType.word())
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                                    translate(StringArgumentType.getString(c, "text"),
                                            StringArgumentType.getString(c, "lang"), "auto", true);
                                    return 1;
                                }))))));
    }

    private static String language() {
        String l = cfg().chatTranslatorLanguage;
        return l == null || l.isEmpty() ? "en" : l;
    }

    /** Adds the translate click to a player line; the line's own clicks (name etc.) win. */
    public static Component rewrite(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.chatTranslator || !c.section("Chat")) return message;
        PlayerChat.Msg m = PlayerChat.parse(message);
        if (m == null || m.own()) return message;
        switch (m.channel()) {
            case ALL, PARTY, COOP, GUILD, OFFICER, DM_FROM, DM_TO -> {}
            default -> { return message; }
        }
        String body = m.message().trim();
        if (body.isEmpty()) return message;
        if (body.length() > 230) body = body.substring(0, 230);
        String cmd = "/tmap translate " + body;
        return Component.empty().withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§bClick to translate!"))))
                .append(message);
    }

    private static void translate(String text, String target, String source, boolean copy) {
        TeslaMapsConfig c = cfg();
        if (!c.chatTranslator || !c.section("Chat")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Turn on Chat > Click Message To Translate first."));
            return;
        }
        String msg = com.teslamaps.utils.StringUtil.strip(text);
        CompletableFuture.supplyAsync(() -> fetch(msg, target, source)).thenAccept(r -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (r == null) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cTranslation failed."));
                return;
            }
            if (!copy && r[0].equals(msg)) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Translation is the same as the original."));
                return;
            }
            if (copy) mc.keyboardHandler.setClipboard(r[0]);
            String head = copy ? "§7Copied §e" + target + " §7translation: §f" : "§7Translation §8(" + r[1] + ")§7: §f";
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + head + r[0]).withStyle(s -> s
                    .withClickEvent(new ClickEvent.CopyToClipboard(r[0]))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy!\n§7Original: §f" + msg)))));
        }));
    }

    /** {translation, detected language} or null. */
    private static String[] fetch(String message, String target, String source) {
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl="
                    + URLEncoder.encode(source, StandardCharsets.UTF_8) + "&tl=" + URLEncoder.encode(target, StandardCharsets.UTF_8)
                    + "&q=" + URLEncoder.encode(message, StandardCharsets.UTF_8) + "&dj=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonArray sentences = json.getAsJsonArray("sentences");
            if (sentences == null) return null;
            StringBuilder out = new StringBuilder();
            for (JsonElement e : sentences) {
                JsonElement t = e.getAsJsonObject().get("trans");
                if (t != null) out.append(t.getAsString());
            }
            String src = json.has("src") ? json.get("src").getAsString() : "?";
            return new String[]{out.toString(), src};
        } catch (Exception e) {
            return null;
        }
    }
}
