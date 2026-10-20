package com.teslamaps.adm.leap.splits;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.screen.TeslaButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The Pace target boxes, one per split; a blank box shows its PB (or dark green) greyed. */
public class AdmSplitTargetsScreen extends Screen {

    private static final int PANEL_W = 300, ROW_H = 22;
    private final Screen parent;
    private boolean master;
    private int panelX, panelY, panelH;
    private final List<int[]> fields = new ArrayList<>();

    public AdmSplitTargetsScreen(Screen parent, boolean master) {
        super(Component.literal("ADM Pace Targets"));
        this.parent = parent;
        this.master = master;
    }

    @Override
    protected void init() {
        clearWidgets();
        fields.clear();
        List<String> labels = EngineerLook.targetLabels(master);
        panelH = 70 + labels.size() * ROW_H + 34;
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        int y = panelY + 46;
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            int fx = panelX + 110, fw = PANEL_W - 126;
            EditBox box = new EditBox(font, fx + 4, y + 5, fw - 8, 12, Component.literal(labels.get(i)));
            box.setMaxLength(12);
            box.setBordered(false);
            box.setHint(Component.literal("§8" + placeholder(i)));
            box.setValue(AdmSplitsHud.box(master, i));
            box.setResponder(v -> AdmSplitsHud.setBox(master, idx, v));
            addRenderableWidget(box);
            fields.add(new int[]{fx, y, fw, 18});
            y += ROW_H;
        }
        int by = panelY + panelH - 30;
        addRenderableWidget(new TeslaButton(panelX + 16, by, 80, 20, master ? "§fF7" : "§fM7", () -> { master = !master; init(); }));
        addRenderableWidget(new TeslaButton(panelX + 104, by, 92, 20, "§eFill From PBs", () -> { AdmSplitsHud.fillFromPbs(master); init(); }));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 96, by, 80, 20, "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private String placeholder(int i) {
        SplitPace.Clocks pb = AdmSplitsHud.pb(i, master);
        if (pb != null) return "PB " + EngineerLook.formatSeconds(pb.ms() / 1000.0);
        SplitPace.Clocks dg = AdmSplitsHud.darkGreen(i, master);
        return dg != null ? EngineerLook.formatSeconds(dg.ms() / 1000.0) + " (no PB)" : "";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, panelH, 8);
        ctx.text(font, "§6§lPace Targets §7(" + (master ? "M7" : "F7") + ")", panelX + 16, panelY + 14, 0xFFFFFFFF);
        ctx.text(font, "§8Seconds (61.5) or minutes (1:01.5). Blank = PB.", panelX + 16, panelY + 28, 0xFFFFFFFF);
        List<String> labels = EngineerLook.targetLabels(master);
        for (int i = 0; i < fields.size(); i++) {
            int[] f = fields.get(i);
            HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);
            ctx.text(font, "§f" + master7(master) + " " + labels.get(i), panelX + 16, f[1] + 5, 0xFFFFFFFF);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private static String master7(boolean m) { return m ? "M7" : "F7"; }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
