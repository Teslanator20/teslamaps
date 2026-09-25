package com.teslamaps.features.kuudra;

// Ported from Odin's FreshTools.kt (+ KuudraUtils.freshers), overhead timers and glow from IQ
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KuudraFresh {

    private KuudraFresh() {}

    private static final String OWN = "Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!";
    private static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^\\]]+\\] ?)?(\\w{1,16}): FRESH\\b.*");
    private static final long DURATION = 10_000;
    private static final String[] SAMPLE = {"§6Player1§f: 9.0s", "§6Player2§f: 5.2s"};

    private static final Map<String, Long> FRESHERS = new LinkedHashMap<>();
    private static String[] hudLines = null;
    private static List<Player> overheadPlayers = List.of();
    private static List<String> overheadText = List.of();
    private static HudEditElement hud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuuFreshHud", "Fresh Times").section("Kuudra")
                .enabled(() -> cfg().kuuFreshTools && cfg().kuuFreshHud)
                .size(() -> KuudraBuild.width(SAMPLE), () -> SAMPLE.length * 10 + 4)
                .preview((g, mc, x, y, s) -> KuudraBuild.draw(g, mc, x, y, s, SAMPLE));
        Feature.of("KuudraFresh").section("Kuudra").when(() -> cfg().kuuFreshTools)
                .chat(KuudraFresh::onChat)
                .tick(KuudraFresh::tick)
                .world(KuudraFresh::render)
                .hud("kuudrafresh", KuudraFresh::renderHud)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Fresh Tools", () -> c.kuuFreshTools, v -> c.kuuFreshTools = v)
                            .desc("Tracks your party's Fresh Tools procs during the build");
                    b.toggle("  Announce FRESH", () -> c.kuuFreshAnnounce, v -> c.kuuFreshAnnounce = v)
                            .desc("Sends \"FRESH\" to party chat when your Fresh Tools perk procs");
                    b.toggle("    Include Build %", () -> c.kuuFreshAnnouncePercent, v -> c.kuuFreshAnnouncePercent = v);
                    b.toggle("  Fresh Times HUD", () -> c.kuuFreshHud, v -> c.kuuFreshHud = v)
                            .desc("Freshers and their remaining 10 s. Move it in /tmap gui");
                    b.toggle("  Timer Above Head", () -> c.kuuFreshOverhead, v -> c.kuuFreshOverhead = v);
                    b.toggle("  Glow Freshers", () -> c.kuuFreshGlow, v -> c.kuuFreshGlow = v);
                    b.color("    Glow Color", () -> c.kuuFreshGlowColor, v -> c.kuuFreshGlowColor = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals(OWN)) {
            if (!StringUtil.isServerMessage(raw) || !KuudraBuild.inKuudra()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mark(mc.player.getName().getString());
            if (cfg().kuuFreshAnnounce) {
                Announce.party(cfg().kuuFreshAnnouncePercent ? "FRESH! (" + KuudraBuild.buildPercent + "%)" : "FRESH");
            }
            return;
        }
        if (!t.startsWith("Party > ") || !t.contains("FRESH")) return;
        Matcher m = PARTY.matcher(t);
        if (m.matches() && KuudraBuild.inKuudra()) mark(m.group(1));
    }

    private static void mark(String name) {
        long now = System.currentTimeMillis();
        Long prev = FRESHERS.get(name);
        if (prev != null && now - prev < 2000) return;
        FRESHERS.put(name, now);
    }

    public static int activeCount() {
        return FRESHERS.size();
    }

    static void reset() {
        FRESHERS.clear();
        hudLines = null;
        overheadPlayers = List.of();
        overheadText = List.of();
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Long> it = FRESHERS.values().iterator(); it.hasNext(); ) {
            if (now - it.next() >= DURATION) it.remove();
        }
        if (FRESHERS.isEmpty()) {
            hudLines = null;
            if (!overheadPlayers.isEmpty()) { overheadPlayers = List.of(); overheadText = List.of(); }
            return;
        }
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        String[] lines = new String[FRESHERS.size()];
        List<Player> ps = new ArrayList<>();
        List<String> txt = new ArrayList<>();
        int i = 0;
        int glow = TeslaMapsConfig.parseColor(c.kuuFreshGlowColor) & 0xFFFFFF;
        for (Map.Entry<String, Long> e : FRESHERS.entrySet()) {
            String secs = String.format(Locale.ROOT, "%.1fs", (DURATION - (now - e.getValue())) / 1000.0);
            lines[i++] = "§6" + e.getKey() + "§f: " + secs;
            if (mc.level == null || (!c.kuuFreshOverhead && !c.kuuFreshGlow)) continue;
            for (Player p : mc.level.players()) {
                if (!p.getName().getString().equals(e.getKey())) continue;
                if (c.kuuFreshGlow) EntityGlow.mark(p, glow);
                if (c.kuuFreshOverhead && p != mc.player) {
                    ps.add(p);
                    txt.add("§a" + secs);
                }
                break;
            }
        }
        hudLines = lines;
        overheadPlayers = ps;
        overheadText = txt;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Player> ps = overheadPlayers;
        List<String> txt = overheadText;
        if (ps.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (int i = 0; i < ps.size() && i < txt.size(); i++) {
            Player p = ps.get(i);
            if (p.isRemoved()) continue;
            ESPRenderer.drawText(pose, txt.get(i), p.getPosition(pt).add(0, p.getBbHeight() + 0.9, 0), 1.2f, cam);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        String[] lines = hudLines;
        if (lines == null || hud == null || !cfg().kuuFreshHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        KuudraBuild.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
    }
}
