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

public class ShortcutScreen extends Screen {
    private static final int PANEL_W = 430, PANEL_H = 290;
    private static final int ROW_H = 28, VISIBLE = 6, ALIAS_W = 80;

    private int panelX, panelY, listX, listY, listW;
    private int scroll = 0;

    /** Backgrounds for the borderless edit boxes, filled in while the rows are built. */
    private final List<int[]> fields = new ArrayList<>();

    public ShortcutScreen() { super(Component.literal("Command Shortcuts")); }

    private List<TeslaMapsConfig.Shortcut> list() { return TeslaMapsConfig.get().shortcuts; }

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
        List<TeslaMapsConfig.Shortcut> b = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        int cmdW = listW - ALIAS_W - 6 - 28;
        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            TeslaMapsConfig.Shortcut sc = b.get(idx);
            int ry = listY + row * ROW_H;
            int cmdX = listX + ALIAS_W + 6;

            EditBox alias = new EditBox(this.font, listX + 4, ry + 7, ALIAS_W - 8, 12, Component.literal("alias"));
            alias.setMaxLength(32);
            alias.setBordered(false);
            alias.setHint(Component.literal("pk"));
            alias.setValue(sc.alias == null ? "" : sc.alias);
            alias.setResponder(v -> sc.alias = v);
            addRenderableWidget(alias);
            fields.add(new int[] {listX, ry, ALIAS_W, 22});

            EditBox cmd = new EditBox(this.font, cmdX + 4, ry + 7, cmdW - 8, 12, Component.literal("command"));
            cmd.setMaxLength(256);
            cmd.setBordered(false);
            cmd.setHint(Component.literal("party kick"));
            cmd.setValue(sc.command == null ? "" : sc.command);
            cmd.setResponder(v -> sc.command = v);
            addRenderableWidget(cmd);
            fields.add(new int[] {cmdX, ry, cmdW, 22});

            addRenderableWidget(new TeslaButton(listX + listW - 22, ry, 22, 22, "§cx",
                    () -> { b.remove(idx); rebuild(); }));
        }

        addRenderableWidget(new TeslaButton(panelX + 16, panelY + PANEL_H - 34, 130, 22, "§a+ Add Shortcut", () -> {
            b.add(new TeslaMapsConfig.Shortcut("", ""));
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

        ctx.text(this.font, "§f§lCommand Shortcuts", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8alias  command (no slashes)" + (com.teslamaps.features.CommandChains.active() ? ", chain with ; and wait <ms>" : "") + ". §eRelog to apply changes.",
                panelX + 16, panelY + 30, 0xFFFFFFFF);

        for (int[] f : fields) HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);

        if (list().isEmpty()) {
            ctx.text(this.font, "§7No shortcuts — click \"+ Add Shortcut\".", panelX + 16, panelY + 62, 0xFFFFFFFF);
        }
        if (list().size() > VISIBLE) {
            ctx.text(this.font, "§8" + list().size() + " total · scroll",
                    panelX + PANEL_W - 110, panelY + PANEL_H - 50, 0xFFFFFFFF);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
