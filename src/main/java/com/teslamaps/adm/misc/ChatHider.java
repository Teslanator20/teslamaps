package com.teslamaps.adm.misc;

import com.google.gson.Gson;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Ported from ADM's chat/ChatHider.kt + ChatRules.kt: Clean Chat hides at the chat GUI's add step only
public final class ChatHider {
    private static final Gson GSON = new Gson();
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern REPEAT_COUNTER = Pattern.compile("\\s\\(\\d+\\)$");

    private record Rule(Pattern pattern, boolean block, String label, boolean anchored) {}

    private static final class RulesFile {
        int version;
        List<Custom> custom = new ArrayList<>();
        List<Block> block = new ArrayList<>();
    }

    private static final class Custom {
        String regex = "";
        String action = "block";
    }

    private static final class Block {
        String regex = "";
        String template = "";
        String family = "";
    }

    private static volatile List<Rule> rules = List.of();
    private static volatile boolean loaded;

    private ChatHider() {}

    private static Path overrideFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("teslamaps").resolve("adm").resolve("chat-hider.json");
    }

    public static void reload() {
        load(true);
    }

    private static synchronized void load(boolean announce) {
        loaded = true;
        String from;
        List<Rule> set = new ArrayList<>();
        int invalid = 0;
        try {
            String json;
            Path file = overrideFile();
            if (Files.exists(file)) {
                json = Files.readString(file);
                from = file.getFileName().toString();
            } else {
                try (InputStream in = ChatHider.class.getResourceAsStream("/assets/teslamaps/adm/chat/hidden.json")) {
                    json = in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                from = "built-in";
            }
            RulesFile f = GSON.fromJson(json, RulesFile.class);
            if (f != null) {
                if (f.custom != null) for (Custom c : f.custom) {
                    try { set.add(new Rule(Pattern.compile(c.regex), "block".equals(c.action), "custom " + c.regex, false)); }
                    catch (PatternSyntaxException e) { invalid++; }
                }
                if (f.block != null) for (Block b : f.block) {
                    try { set.add(new Rule(Pattern.compile(b.regex), true, b.template, true)); }
                    catch (PatternSyntaxException e) { invalid++; }
                }
            }
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("[adm] chat hider rules failed to load", t);
            set.clear();
            from = "none (load failed)";
        }
        rules = set;
        TeslaMaps.LOGGER.info("[adm] chat hider: {} rules from {}{}", set.size(), from, invalid > 0 ? ", " + invalid + " invalid" : "");
        if (announce) AdmMisc.msg("§7clean chat: §f" + set.size() + "§7 rules from §f" + from + (invalid > 0 ? " §c(" + invalid + " invalid regex)" : ""));
    }

    static String stripText(String text) {
        return REPEAT_COUNTER.matcher(FORMATTING.matcher(text).replaceAll("").trim()).replaceAll("");
    }

    /** From the chat GUI's add-message funnel. True = do not display. */
    public static boolean shouldHide(Component message) {
        if (!RandomStuff.cleansChat()) return false;
        if (!loaded) load(false);
        String text = stripText(message.getString());
        String rule = hides(text);
        if (rule == null) return false;
        if (RandomStuff.logsHiddenChat()) EcLog.log("CHAT-HIDE", rule + " ⇐ " + text.replace("\n", "\\n"));
        return true;
    }

    private static String hides(String text) {
        for (Rule r : rules) {
            Matcher m = r.pattern.matcher(text);
            boolean hit = r.anchored ? m.matches() : m.find();
            if (hit) return r.block ? r.label : null;
        }
        return null;
    }
}
