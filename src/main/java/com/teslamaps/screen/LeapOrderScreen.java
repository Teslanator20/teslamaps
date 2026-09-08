package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.render.gui.TeslaGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LeapOrderScreen extends Screen {
    private static final int PANEL_W = 330, PANEL_H = 290;
    private static final int ROW_H = 26, VISIBLE = 7;

    private int panelX, panelY, listX, listY, listW;
    private int scroll = 0;

    /** Backgrounds for the borderless edit boxes, filled in while the rows are built. */
    private final List<int[]> fields = new ArrayList<>();

    public LeapOrderScreen() { super(Component.literal("Leap Custom Order")); }

    private List<String> list() { return TeslaMapsConfig.get().leapCustomOrder; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 56;
        listW = PANEL_W - 32;
        rebuild();
    

        ScreenFit.clamp(this); // keep every widget reachable at any gui scale

    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        List<String> b = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        int nameX = listX + 16;
        int nameW = listW - 16 - 48;
        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            int ry = listY + row * ROW_H;

            EditBox name = new EditBox(this.font, nameX + 4, ry + 7, nameW - 8, 12, Component.literal("name"));
            name.setMaxLength(16);
            name.setBordered(false);
            name.setHint(Component.literal("PlayerName"));
            name.setValue(b.get(idx));
            name.setResponder(v -> b.set(idx, v.toLowerCase()));
            addRenderableWidget(name);
            fields.add(new int[] {nameX, ry, nameW, 22});

            addRenderableWidget(new TeslaButton(listX + listW - 46, ry, 22, 22, "§f▲",
                    () -> { if (idx > 0) { var t = b.remove(idx); b.add(idx - 1, t); rebuild(); } }));
            addRenderableWidget(new TeslaButton(listX + listW - 22, ry, 22, 22, "§cx",
                    () -> { b.remove(idx); rebuild(); }));
        }

        addRenderableWidget(new TeslaButton(panelX + 16, panelY + PANEL_H - 34, 100, 22, "§a+ Add", () -> {
            b.add("");
            scroll = Math.max(0, b.size() - VISIBLE);
            rebuild();
        }));

        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 34, 100, 22,
                "§fDone", this::onClose));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (list().size() > VISIBLE) {
            scroll = Math.max(0, Math.min(list().size() - VISIBLE, scroll - (int) Math.signum(sy)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lLeap Custom Order", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8Top = first slot. Used when Sort Mode = Custom.",
                panelX + 16, panelY + 30, 0xFFFFFFFF);

        for (int[] f : fields) HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list().size()) break;
            ctx.text(this.font, "§8" + (idx + 1), listX, listY + row * ROW_H + 7, 0xFFFFFFFF);
        }
        if (list().isEmpty()) {
            ctx.text(this.font, "§7No names — click \"+ Add\".", listX, listY, 0xFFFFFFFF);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        list().removeIf(s -> s == null || s.isBlank());
        TeslaMapsConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
