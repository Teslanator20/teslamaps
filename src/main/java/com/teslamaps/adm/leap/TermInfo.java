package com.teslamaps.adm.leap;

// Ported from ADM's practice/TermInfo.kt (Devonian's Terminal Display + section tracking)
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** F7/M7 terminal sections: the Term Info HUD, each section's time, and S3-done for Recore. */
public final class TermInfo {

    private TermInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final String CORE_OPEN = "The Core entrance is opening!";
    private static final String GATE = "The gate has been destroyed!";
    private static final Pattern TASK = Pattern.compile("^(\\w+) (?:activated|completed) a (terminal|lever|device)! \\((\\d)/\\d\\)$");
    private static final Pattern GATE_CORE_TITLE = Pattern.compile("gate (?:has been )?destroyed|core entrance is opening", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("^(\\w{1,16}) (?:activated a (?:terminal|lever)|completed a device)! \\(\\d+/\\d+\\)$");

    private static final class Section {
        final int terms, number;
        int termsDone, leversDone, lastIndex;
        boolean deviceDone, gateDestroyed;
        String lastIgn = "", lastType = "";
        Section(int terms, int number) { this.terms = terms; this.number = number; this.gateDestroyed = number == 4; }
    }

    private static final Section[] sections = {new Section(4, 1), new Section(5, 2), new Section(4, 3), new Section(4, 4)};
    private static int active = -1;
    private static long sectionStart;
    private static String shownText;
    private static long shownAt;

    private static HudEditElement hud;
    private static final List<String> SAMPLE = List.of("§eTerms: 3/4", "§aLevers: 2/2", "§aDevice: §l✔", "§cGate: §l✘");
    private static final List<String> SAMPLE_SIMPLE = List.of("§c2/7");

    public static void register() {
        hud = HudEditElement.of("admTermInfo", "Term Info").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admTermInfo)
                .size(() -> com.teslamaps.features.hud.TextHud.width(sample()), () -> com.teslamaps.features.hud.TextHud.height(sample().size()))
                .preview((g, mc, x, y, s) -> com.teslamaps.features.hud.TextHud.draw(g, mc, x, y, s, sample(), com.teslamaps.features.hud.TextHud.width(sample())));
        Feature.of("AdmTermInfo").section(Adm.SECTION)
                .serverChat(TermInfo::onChat)
                .hud("admterminfo", TermInfo::render)
                .config("ADM Leap & Recore", b -> {
                    b.label("Term Info");
                    b.toggle("Term Info", () -> cfg().admTermInfo, v -> cfg().admTermInfo = v)
                            .desc("F7 terminals: the current section's progress (terms, levers, device, gate). Position in /tmap gui.");
                    b.toggle("Simple Mode", () -> cfg().admTermInfoSimple, v -> cfg().admTermInfoSimple = v)
                            .desc("Only the total progress of the section, e.g. 3/7 (green once the gate is down). Off: terms, levers, device and gate on their own lines.");
                });
    }

    private static List<String> sample() { return cfg().admTermInfoSimple ? SAMPLE_SIMPLE : SAMPLE; }

    public static void onWorldChange() {
        reset();
        shownText = null;
    }

    private static Section current() { return active >= 0 && active < sections.length ? sections[active] : null; }

    /** The section being done (1-4), or null outside P3. */
    public static Integer sectionNumber() { Section s = current(); return s == null ? null : s.number; }

    public static boolean deviceDone(int n) { return n >= 1 && n <= 4 && sections[n - 1].deviceDone; }

    /** The last section's time while it's up, else null (Timers' Section Time). */
    public static String sectionTime(double seconds) {
        if (shownText == null) return null;
        return System.currentTimeMillis() - shownAt <= seconds * 1000 ? shownText : null;
    }

    /** P3 sim starting at section 2-4 (5 = the core): the earlier ones counted done. */
    public static void simStart(int section) {
        reset();
        shownText = null;
        Recore.simStart(section);
        if (section < 1 || section > 4) return;
        for (int i = 0; i < section - 1; i++) {
            Section s = sections[i];
            s.termsDone = s.terms; s.leversDone = 2; s.deviceDone = true; s.gateDestroyed = true;
        }
        active = section - 1;
        sectionStart = ServerTickTimer.now();
    }

    public static void simClearTime() { shownText = null; }

    /** P3 sim: a section's time as the sim measured it (server ticks). */
    public static void simSectionTime(int ticks) {
        shownText = "§5" + String.format(Locale.ROOT, "%.2f", ticks / 20.0);
        shownAt = System.currentTimeMillis();
    }

    private static void reset() {
        for (Section s : sections) {
            s.termsDone = 0; s.deviceDone = false; s.leversDone = 0; s.gateDestroyed = s.number == 4;
            s.lastIgn = ""; s.lastIndex = 0; s.lastType = "";
        }
        active = -1;
    }

    private static void start() {
        reset();
        active = 0;
        sectionStart = ServerTickTimer.now();
    }

    private static void onChat(String raw) {
        String msg = StringUtil.strip(raw);
        if (msg.equals(GOLDOR_START)) { if (active == -1) start(); }
        else if (msg.equals(CORE_OPEN)) active = -1;
        else {
            if (active == -1 && TASK.matcher(msg).matches()) start();
            onTaskChat(msg);
        }
    }

    private static void onTaskChat(String msg) {
        Section cur = current();
        if (cur == null) return;
        if (msg.equals(GATE)) cur.gateDestroyed = true;
        else {
            Matcher m = TASK.matcher(msg);
            if (!m.matches()) return;
            String ign = m.group(1), type = m.group(2);
            int index;
            try { index = Integer.parseInt(m.group(3)); } catch (NumberFormatException e) { return; }
            if (index == cur.lastIndex) {
                if (ign.equals(cur.lastIgn)) return;
                if (type.equals("device")) {
                    switch (cur.number) {
                        case 1 -> {
                            if (!sections[3].deviceDone) sections[3].deviceDone = true;
                            else if (!sections[1].deviceDone) sections[1].deviceDone = true;
                            else sections[2].deviceDone = true;
                        }
                        case 2 -> {
                            if (!sections[2].deviceDone) sections[2].deviceDone = true;
                            else sections[3].deviceDone = true;
                        }
                        case 3 -> sections[3].deviceDone = true;
                        default -> { }
                    }
                    return;
                } else if (cur.lastType.equals("device")) cur.deviceDone = false;
            } else if (index == 2 && cur.lastIndex == 0) {
                cur.deviceDone = true;
            } else if (index == 1 && !type.equals("device")) {
                cur.deviceDone = false;
            }
            switch (type) {
                case "terminal" -> cur.termsDone++;
                case "lever" -> cur.leversDone++;
                case "device" -> {
                    if (cur.deviceDone && cur.number == 2) sections[3].deviceDone = true;
                    cur.deviceDone = true;
                }
                default -> { }
            }
            cur.lastIgn = ign; cur.lastIndex = index; cur.lastType = type;
        }
        if (cur.termsDone >= cur.terms && cur.leversDone >= 2 && cur.deviceDone && cur.gateDestroyed) {
            long now = ServerTickTimer.now();
            shownText = "§5" + String.format(Locale.ROOT, "%.2f", (now - sectionStart) / 20.0);
            shownAt = System.currentTimeMillis();
            sectionStart = now;
            active++;
            if (active == 3) Recore.s3Done();
        }
    }

    /** Whether a title or subtitle is a terminal completion one. */
    public static boolean isCompletionTitle(String raw) {
        String text = StringUtil.strip(raw);
        if (GATE_CORE_TITLE.matcher(text).find()) return true;
        return active >= 0 && active <= 3 && TITLE.matcher(text).matches();
    }

    private static List<String> lines() {
        Section s = current();
        if (s == null) return List.of();
        if (cfg().admTermInfoSimple) {
            int done = s.termsDone + s.leversDone + (s.deviceDone ? 1 : 0);
            return List.of((s.gateDestroyed ? "§a" : "§c") + done + "/" + (s.terms + 3));
        }
        return List.of(
                colourFor(s.termsDone, s.terms) + "Terms: " + s.termsDone + "/" + s.terms,
                colourFor(s.leversDone, 2) + "Levers: " + s.leversDone + "/2",
                s.deviceDone ? "§aDevice: §l✔" : "§cDevice: §l✘",
                s.gateDestroyed ? "§aGate: §l✔" : "§cGate: §l✘");
    }

    private static String colourFor(int n, int max) {
        if (n == max) return "§a";
        if (n == 0) return "§c";
        return "§e";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!cfg().admTermInfo || Minecraft.getInstance().options.hideGui) return;
        drawNow(g);
    }

    /** Also redrawn over the POV previews. */
    static void drawNow(GuiGraphicsExtractor g) {
        List<String> ls = lines();
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        com.teslamaps.features.hud.TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, com.teslamaps.features.hud.TextHud.width(ls));
    }
}
