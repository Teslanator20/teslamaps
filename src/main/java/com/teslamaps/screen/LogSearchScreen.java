package com.teslamaps.screen;

import com.teslamaps.features.LogSearch;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** /tmap logsearch - searches the chat lines of every log file. */
public class LogSearchScreen extends Screen {

    private static final int ROW_H = 11, MARGIN = 10;

    private static String lastQuery = "";
    private static boolean regex = false, ignoreCase = true;

    private final ConcurrentLinkedQueue<LogSearch.Result> queue = new ConcurrentLinkedQueue<>();
    private final List<LogSearch.Result> results = new ArrayList<>();
    private EditBox field;
    private TeslaButton regexBtn, caseBtn;
    private int scroll = 0;
    private String status = "";
    private boolean searched;
    private boolean autoSearch;

    private int panelX, panelY, panelW, panelH, listY, visibleRows;

    public LogSearchScreen(String query, boolean rx, boolean ic) {
        super(Component.literal("Log Search"));
        if (query != null && !query.isBlank()) {
            lastQuery = query;
            regex = rx;
            ignoreCase = ic;
            autoSearch = true;
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(width - MARGIN * 2, 620);
        panelH = Math.min(height - MARGIN * 2, 340);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listY = panelY + 58;
        visibleRows = Math.max(1, (panelH - 58 - 34) / ROW_H);

        int bw = 70;
        field = new EditBox(font, panelX + 12, panelY + 32, panelW - 24 - (bw + 4) * 3, 16, Component.literal("Search"));
        field.setHint(Component.literal("Text or regex..."));
        field.setMaxLength(256);
        field.setValue(lastQuery);
        field.setResponder(q -> lastQuery = q);
        addRenderableWidget(field);
        setInitialFocus(field);

        int bx = panelX + panelW - 12 - (bw + 4) * 3 + 4;
        regexBtn = addRenderableWidget(new TeslaButton(bx, panelY + 32, bw, 16, regexLabel(), () -> {
            regex = !regex;
            regexBtn.setMessage(Component.literal(regexLabel()));
        }));
        caseBtn = addRenderableWidget(new TeslaButton(bx + bw + 4, panelY + 32, bw, 16, caseLabel(), () -> {
            ignoreCase = !ignoreCase;
            caseBtn.setMessage(Component.literal(caseLabel()));
        }));
        addRenderableWidget(new TeslaButton(bx + (bw + 4) * 2, panelY + 32, bw, 16, "§fSearch", this::search));
        addRenderableWidget(new TeslaButton(panelX + panelW - 74, panelY + panelH - 26, 62, 18, "§fClose", this::onClose));

        if (autoSearch) {
            autoSearch = false;
            search();
        }
        ScreenFit.clamp(this);
    }

    private static String regexLabel() { return regex ? "Regex: §aOn" : "Regex: §7Off"; }

    private static String caseLabel() { return ignoreCase ? "Case: §7Any" : "Case: §eExact"; }

    private void search() {
        results.clear();
        queue.clear();
        scroll = 0;
        String err = LogSearch.search(lastQuery, regex, ignoreCase, queue);
        searched = err == null;
        status = err == null ? "" : "§c" + err;
    }

    @Override
    public void tick() {
        super.tick();
        LogSearch.Result r;
        int n = 0;
        while (n++ < 2000 && results.size() < LogSearch.MAX_RESULTS && (r = queue.poll()) != null) results.add(r);
        if (searched) {
            String more = results.size() >= LogSearch.MAX_RESULTS ? " (limit)" : "";
            status = LogSearch.running() ? "§7Searching... §f" + results.size() + " §7hits"
                    : "§7" + results.size() + " hits" + more + " §8· left-click copies, right-click opens the log";
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, results.size() - visibleRows);
        scroll = Math.clamp(scroll - (int) Math.signum(sy) * 3, 0, max);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = rowAt(event.x(), event.y());
        if (index >= 0) {
            LogSearch.Result r = results.get(index);
            if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
                com.mojang.blaze3d.Blaze3D.openPath(r.file().toPath());
            } else {
                minecraft.keyboardHandler.setClipboard(r.text());
                status = "§aCopied";
                searched = false;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && field != null && field.isFocused()) {
            search();
            return true;
        }
        return super.keyPressed(event);
    }

    private int rowAt(double mx, double my) {
        if (mx < panelX + 8 || mx > panelX + panelW - 8) return -1;
        if (my < listY || my >= listY + visibleRows * ROW_H) return -1;
        int index = scroll + (int) ((my - listY) / ROW_H);
        return index >= 0 && index < results.size() ? index : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);
        ctx.text(font, "§f§lLog Search", panelX + 12, panelY + 12, 0xFFFFFFFF);

        int hovered = rowAt(mouseX, mouseY);
        int textW = panelW - 24;
        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= results.size()) break;
            LogSearch.Result r = results.get(index);
            int y = listY + i * ROW_H;
            if (index == hovered) ctx.fill(panelX + 8, y - 1, panelX + panelW - 8, y + ROW_H - 1, 0x30FFFFFF);
            String line = "§8" + r.line() + " §f" + r.text();
            ctx.text(font, font.plainSubstrByWidth(line, textW), panelX + 12, y, 0xFFFFFFFF);
        }

        if (!status.isEmpty()) ctx.text(font, status, panelX + 12, panelY + panelH - 21, 0xFFAAAAAA);
        if (results.size() > visibleRows) {
            String pos = "§8" + (scroll + 1) + "-" + Math.min(results.size(), scroll + visibleRows) + "/" + results.size();
            ctx.text(font, pos, panelX + panelW - 84 - font.width(pos), panelY + panelH - 21, 0xFF808080);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (hovered >= 0) {
            LogSearch.Result r = results.get(hovered);
            List<Component> lines = new ArrayList<>();
            for (String part : font.getSplitter().splitLines(r.text(), 300, net.minecraft.network.chat.Style.EMPTY)
                    .stream().map(net.minecraft.network.chat.FormattedText::getString).toList()) {
                lines.add(Component.literal("§f" + part));
            }
            lines.add(Component.literal("§8" + r.file().getName() + ", line " + r.line()));
            ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public void removed() {
        super.removed();
        LogSearch.cancel();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
