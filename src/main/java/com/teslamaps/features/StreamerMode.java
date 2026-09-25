package com.teslamaps.features;

// FloydAddons' FloydServerIdHider / FloydProfileIdHider (FloydNickHider, FloydServerIdAccumulator)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces Hypixel server ids and profile UUIDs in every piece of rendered text. Hooked from StreamerFontMixin. */
public final class StreamerMode {

    public static final String[] MODES = {"Hidden", "???", "Random"};

    private static final Pattern SERVER = Pattern.compile(
            "(?i)(?<![a-z0-9])((?:mini|mega|lobby|limbo|housing|prototype|node|legacylobby)\\d{1,4}[a-z]{0,4})(?![a-z0-9])");
    private static final Pattern DATE_ID = Pattern.compile(
            "\\d{2}/\\d{2}/\\d{2}(?:\\s|§.)+([a-zA-Z]{0,2}\\d{1,4}[a-zA-Z]{0,4})(?![a-zA-Z0-9])");
    private static final Pattern PROFILE = Pattern.compile(
            "(?i)profile\\s*id:\\s*(?:§.)*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private static final Map<String, String> RANDOM = new HashMap<>();

    public static volatile boolean on;
    private static volatile boolean servers, profiles;

    private StreamerMode() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("StreamerMode")
                .tickAlways(() -> {
                    TeslaMapsConfig c = cfg();
                    servers = c.hideServerId;
                    profiles = c.hideProfileId;
                    on = servers || profiles;
                })
                .config("Hide", b -> {
                    b.label("Streamer Mode");
                    b.toggle("Hide Server ID", () -> cfg().hideServerId, v -> cfg().hideServerId = v)
                            .desc("Replaces Hypixel server ids (mini123A, m45DE after the scoreboard date, Sending to server...) in all text");
                    b.dropdown("  Replace With", MODES, () -> cfg().hideServerIdMode, v -> {
                        cfg().hideServerIdMode = v;
                        synchronized (RANDOM) { RANDOM.clear(); }
                    });
                    b.toggle("Hide Profile ID", () -> cfg().hideProfileId, v -> cfg().hideProfileId = v)
                            .desc("Replaces the profile UUID (Profile ID: ...) in menus, tooltips and chat");
                });
    }

    private static boolean candidate(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') return true;
        }
        return false;
    }

    public static String replace(String text) {
        if (text == null || text.isEmpty() || !candidate(text)) return text;
        Edit e = new Edit(text, null);
        run(e);
        return e.changed ? e.text : text;
    }

    public static FormattedCharSequence replace(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        seq.accept((index, style, cp) -> {
            int before = sb.length();
            sb.appendCodePoint(cp);
            for (int i = before; i < sb.length(); i++) styles.add(style);
            return true;
        });
        String text = sb.toString();
        if (!candidate(text)) return seq;
        Edit e = new Edit(text, styles);
        run(e);
        if (!e.changed) return seq;
        String out = e.text;
        Style[] st = e.styles.toArray(new Style[0]);
        return sink -> {
            int idx = 0;
            for (int i = 0; i < out.length(); ) {
                int cp = out.codePointAt(i);
                if (!sink.accept(idx++, st[i], cp)) return false;
                i += Character.charCount(cp);
            }
            return true;
        };
    }

    private static void run(Edit e) {
        if (servers) {
            e.replace(DATE_ID, StreamerMode::serverReplacement);
            e.replace(SERVER, StreamerMode::serverReplacement);
        }
        if (profiles) e.replace(PROFILE, id -> "[hidden]");
    }

    private static String serverReplacement(String id) {
        String mode = cfg().hideServerIdMode;
        if ("???".equals(mode)) return "???";
        if (!"Random".equals(mode)) return "hidden";
        synchronized (RANDOM) {
            return RANDOM.computeIfAbsent(id.toLowerCase(Locale.ROOT), StreamerMode::randomLike);
        }
    }

    private static String randomLike(String id) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int digit = 0;
        while (digit < id.length() && !Character.isDigit(id.charAt(digit))) digit++;
        StringBuilder sb = new StringBuilder(id.substring(0, digit));
        for (int i = digit; i < id.length(); i++) {
            char ch = id.charAt(i);
            sb.append(Character.isDigit(ch) ? (char) ('0' + r.nextInt(10)) : (char) ('A' + r.nextInt(26)));
        }
        return sb.toString();
    }

    /** Replaces group 1 of each match; the replacement takes the style of the first replaced char. */
    private static final class Edit {
        String text;
        List<Style> styles;
        boolean changed;

        Edit(String text, List<Style> styles) {
            this.text = text;
            this.styles = styles;
        }

        void replace(Pattern p, java.util.function.UnaryOperator<String> repl) {
            Matcher m = p.matcher(text);
            if (!m.find()) return;
            StringBuilder sb = new StringBuilder(text.length());
            List<Style> st = styles == null ? null : new ArrayList<>(styles.size());
            int last = 0;
            do {
                int s = m.start(1), end = m.end(1);
                sb.append(text, last, s);
                if (st != null) st.addAll(styles.subList(last, s));
                String r = repl.apply(m.group(1));
                sb.append(r);
                if (st != null) {
                    Style style = styles.get(s);
                    for (int i = 0; i < r.length(); i++) st.add(style);
                }
                last = end;
            } while (m.find());
            sb.append(text, last, text.length());
            if (st != null) st.addAll(styles.subList(last, text.length()));
            text = sb.toString();
            styles = st;
            changed = true;
        }
    }
}
