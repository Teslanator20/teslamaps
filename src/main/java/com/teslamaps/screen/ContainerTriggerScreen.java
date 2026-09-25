package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.ContainerTrigger;
import com.teslamaps.features.ContainerTriggers;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ContainerTriggerScreen extends Screen {
    private static final int PANEL_W = 360, PANEL_H = 300;
    private static final int ROW_H = 24, VISIBLE = 8;

    private final Screen parent;
    private int panelX, panelY, listX, listY;
    private int scroll = 0;
    private final List<int[]> fields = new ArrayList<>();

    public ContainerTriggerScreen(Screen parent) {
        super(Component.literal("Container Triggers"));
        this.parent = parent;
    }

    private List<ContainerTrigger> list() { return TeslaMapsConfig.get().containerTriggerList; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 12;
        listY = panelY + 40;
        rebuild();
        ScreenFit.clamp(this);
    }

    private static String next(String cur) {
        int i = Arrays.asList(ContainerTriggers.ACTIONS).indexOf(cur);
        return ContainerTriggers.ACTIONS[(i + 1) % ContainerTriggers.ACTIONS.length];
    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        List<ContainerTrigger> l = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= l.size()) break;
            ContainerTrigger t = l.get(idx);
            int ry = listY + row * ROW_H;
            int x = listX;

            addRenderableWidget(new TeslaButton(x, ry, 56, 20, t.action, () -> {
                t.action = next(t.action);
                rebuild();
            }));
            x += 60;

            EditBox box = new EditBox(font, x + 4, ry + 5, 252, 12, Component.literal("title"));
            box.setMaxLength(128);
            box.setBordered(false);
            box.setHint(Component.literal("Title contains..."));
            box.setValue(t.title == null ? "" : t.title);
            box.setResponder(v -> t.title = v);
            addRenderableWidget(box);
            fields.add(new int[]{x, ry, 260, 20});
            x += 264;

            addRenderableWidget(new TeslaButton(x, ry, 16, 20, "§cx", () -> { list().remove(idx); rebuild(); }));
        }

        int by = panelY + PANEL_H - 30;
        addRenderableWidget(new TeslaButton(panelX + 12, by, 100, 20, "§a+ Add Trigger", () -> {
            list().add(new ContainerTrigger("Sound", ""));
            scroll = Math.max(0, list().size() - VISIBLE);
            rebuild();
        }));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 12 - 80, by, 80, 20, "§fDone", this::onClose));
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
        ctx.text(font, "§f§lContainer Triggers", panelX + 12, panelY + 10, 0xFFFFFFFF);
        ctx.text(font, "§8action · menu title contains (case-insensitive)", panelX + 12, panelY + 24, 0xFFFFFFFF);
        for (int[] f : fields) HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);
        if (list().isEmpty()) ctx.text(font, "§7No triggers - click \"+ Add Trigger\".", panelX + 12, listY + 6, 0xFFFFFFFF);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        list().removeIf(t -> t.title == null || t.title.isBlank());
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
