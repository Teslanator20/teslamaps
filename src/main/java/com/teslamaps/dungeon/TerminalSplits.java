package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from Odin's TerminalTimes (terminalSplits)
public final class TerminalSplits {

    private static final Pattern COMPLETE =
            Pattern.compile("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$");
    private static final String GATE = "The gate has been destroyed!";
    private static final String GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE = "The Core entrance is opening!";

    private static final List<Float> times = new ArrayList<>();
    private static long phaseStart = -1;
    private static long sectionStart;
    private static int completed = 0;
    private static int total = 7;
    private static boolean gateBlown;
    private static Object level;

    private static String pendingLine;
    private static String pendingSuffix;

    private TerminalSplits() {}

    public static void register() {
        Feature.of("TerminalSplits").section("Score & Splits")
                .when(() -> TeslaMapsConfig.get().terminalSplits)
                .serverChat(TerminalSplits::onChat);
    }

    private static long now() {
        return TeslaMapsConfig.get().terminalSplitsServerTicks ? ServerTickTimer.now() * 50L : System.currentTimeMillis();
    }

    private static float since(long start) {
        return (now() - start) / 1000f;
    }

    private static String fmt(float s) {
        return String.format(Locale.ROOT, "%.2f", s);
    }

    private static void onChat(String raw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            phaseStart = -1;
        }
        String text = StringUtil.strip(raw).trim();

        if (text.equals(GOLDOR)) {
            resetSection(true);
            return;
        }
        if (phaseStart < 0) return;

        Matcher m = COMPLETE.matcher(text);
        if (m.matches()) {
            int current = Integer.parseInt(m.group(4));
            int max = Integer.parseInt(m.group(5));
            pendingLine = text;
            pendingSuffix = " §8(§7" + fmt(since(sectionStart)) + "s §8| §7" + fmt(since(phaseStart)) + "s§8)";
            if ((current == max && gateBlown) || current < completed) resetSection(false);
            else { completed = current; total = max; }
            return;
        }
        if (text.equals(GATE)) {
            if (completed > 0 && completed == total) resetSection(false);
            else gateBlown = true;
            return;
        }
        if (text.equals(CORE)) {
            resetSection(false);
            StringBuilder sb = new StringBuilder("§bTimes: ");
            for (int i = 0; i < times.size(); i++) {
                if (i > 0) sb.append(" §8| ");
                sb.append("§a").append(fmt(times.get(i))).append('s');
            }
            sb.append("§8, §bTotal: §a").append(fmt(since(phaseStart))).append('s');
            phaseStart = -1;
            String msg = sb.toString();
            mc.execute(() -> TerminalUtils.modMessage(msg));
        }
    }

    private static void resetSection(boolean full) {
        if (full) {
            times.clear();
            phaseStart = now();
        } else {
            times.add(since(sectionStart));
        }
        completed = 0;
        total = 7;
        sectionStart = now();
        gateBlown = false;
    }

    /** ChatMixin, right before the line is added: appends the pending split times. */
    public static Component rewrite(Component message) {
        if (pendingLine == null) return message;
        String line = pendingLine;
        String suffix = pendingSuffix;
        pendingLine = null;
        if (!line.equals(StringUtil.strip(message.getString()).trim())) return message;
        return message.copy().append(Component.literal(suffix));
    }
}
