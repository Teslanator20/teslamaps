package com.teslamaps.adm.sim;

import com.teslamaps.adm.Adm;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class AdmSim {
    private AdmSim() {}

    private static HudEditElement splitsHud, timeHud, startHud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Ec.safely("ads init", P3Sim::init);
        splitsHud = HudEditElement.of("admSimPracticeHud", "ADS Practice Splits").section(Adm.SECTION)
                .enabled(() -> cfg().admSim && cfg().admSimPracticeHud)
                .size(() -> width(P3Sim.practiceLines(true)), () -> 40)
                .preview((g, mc, x, y, s) -> drawLines(g, mc, x, y, s, P3Sim.practiceLines(true)));
        timeHud = HudEditElement.of("admSimPracticeTimeHud", "ADS Practice Time").section(Adm.SECTION)
                .enabled(() -> cfg().admSim && cfg().admSimPracticeTimeHud)
                .size(() -> Minecraft.getInstance().font.width("§514.35"), () -> 10)
                .scaleRange(0.5f, 10f)
                .preview((g, mc, x, y, s) -> drawLines(g, mc, x, y, s, List.of(P3Sim.practiceTime(true))));
        startHud = HudEditElement.of("admSimPracticeStartHud", "ADS Practice Start Timer").section(Adm.SECTION)
                .enabled(() -> cfg().admSim && cfg().admSimPracticeStartHud)
                .size(() -> Minecraft.getInstance().font.width("§e2.4"), () -> 10)
                .scaleRange(0.5f, 10f)
                .preview((g, mc, x, y, s) -> drawLines(g, mc, x, y, s, List.of(P3Sim.practiceStart(true))));
        Feature.of("AdmSim").section(Adm.SECTION).when(() -> cfg().admSim)
                .hud("admsimpractice", AdmSim::render)
                .config("ADM Simulator", b -> {
                    b.label("Advanced Dungeon Simulator");
                    b.toggle("ADM Simulator", () -> cfg().admSim, v -> cfg().admSim = v)
                            .desc("ADS: F7's boss (and a full run) in a singleplayer world of its own. /ads (or the title screen button) opens it. Only ever active in that world.");
                    b.button("Open ADS", P3Sim::openMenuOrSim);
                    b.toggle("Practice Splits", () -> cfg().admSimPracticeHud, v -> cfg().admSimPracticeHud = v)
                            .desc("In practice (the menu's Practice tab): the time, and each of your tasks with the time it was done. Move it in /tmap gui.");
                    b.toggle("Practice Time", () -> cfg().admSimPracticeTimeHud, v -> cfg().admSimPracticeTimeHud = v)
                            .desc("A practice's final time, large (as Term Info's Section Time), for 4 seconds.");
                    b.toggle("Practice Start Timer", () -> cfg().admSimPracticeStartHud, v -> cfg().admSimPracticeStartHud = v)
                            .desc("A practice's start timer counting down, large, until terminals and the rest are up.");
                    SimSettings.rows(b);
                });
    }

    private static int width(List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, Minecraft.getInstance().font.width(l));
        return w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || !P3Sim.inSim()) return;
        if (cfg().admSimPracticeHud) {
            List<String> l = P3Sim.practiceLines(false);
            if (!l.isEmpty()) drawLines(g, mc, splitsHud.x(), splitsHud.y(), splitsHud.scale(), l);
        }
        if (cfg().admSimPracticeTimeHud) {
            String t = P3Sim.practiceTime(false);
            if (t != null) drawLines(g, mc, timeHud.x(), timeHud.y(), timeHud.scale(), List.of(t));
        }
        if (cfg().admSimPracticeStartHud) {
            String t = P3Sim.practiceStart(false);
            if (t != null) drawLines(g, mc, startHud.x(), startHud.y(), startHud.scale(), List.of(t));
        }
    }

    private static void drawLines(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> lines) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        for (int i = 0; i < lines.size(); i++) g.text(mc.font, lines.get(i), 0, i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
