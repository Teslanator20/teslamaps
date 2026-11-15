package com.teslamaps.screen;

import com.teslamaps.config.ConfigBackups;
import com.teslamaps.render.gui.GuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lists config backups, shows one, restores it after a second click. */
public class ConfigBackupScreen extends Screen {
    private static final int ROW = 14, TOP = 34;
    private final Screen parent;
    private List<Path> backups = new ArrayList<>();
    private int selected = -1;
    private List<String> preview = new ArrayList<>();
    private int listScroll, previewScroll;
    private long restoreArmed, deleteArmed;
    private String status = "";
    private TeslaButton restoreBtn, deleteBtn;

    public ConfigBackupScreen(Screen parent) {
        super(Component.literal("Config Backups"));
        this.parent = parent;
    }

    private int listW() { return Math.min(200, width / 3); }

    @Override
    protected void init() {
        backups = ConfigBackups.list();
        if (selected >= backups.size()) selected = -1;
        int by = height - 28;
        restoreBtn = addRenderableWidget(new TeslaButton(10, by, 90, 20, "Restore", this::restore));
        deleteBtn = addRenderableWidget(new TeslaButton(106, by, 70, 20, "Delete", this::delete));
        addRenderableWidget(new TeslaButton(182, by, 90, 20, "Open Folder", () -> {
            try {
                Files.createDirectories(ConfigBackups.DIR);
                com.mojang.blaze3d.Blaze3D.openPath(ConfigBackups.DIR);
            } catch (Exception ignored) {}
        }));
        addRenderableWidget(new TeslaButton(width - 80, by, 70, 20, "Back", this::onClose));
        ScreenFit.clamp(this);
    }

    private void select(int i) {
        selected = i;
        previewScroll = 0;
        restoreArmed = deleteArmed = 0;
        preview = new ArrayList<>();
        try {
            preview.addAll(Files.readAllLines(backups.get(i)));
        } catch (Exception e) {
            preview.add("Could not read this file.");
        }
    }

    private void restore() {
        if (selected < 0) { status = "Pick a backup first."; return; }
        long now = System.currentTimeMillis();
        if (now - restoreArmed > 3000) { restoreArmed = now; status = "Click Restore again to overwrite your current config."; return; }
        restoreArmed = 0;
        boolean ok = ConfigBackups.restore(backups.get(selected));
        status = ok ? "Restored. Your previous config was backed up first." : "Restore failed, see latest.log.";
        backups = ConfigBackups.list();
        selected = -1;
        preview.clear();
    }

    private void delete() {
        if (selected < 0) return;
        long now = System.currentTimeMillis();
        if (now - deleteArmed > 3000) { deleteArmed = now; status = "Click Delete again to remove this backup."; return; }
        try { Files.deleteIfExists(backups.get(selected)); } catch (Exception ignored) {}
        backups = ConfigBackups.list();
        selected = -1;
        preview.clear();
        status = "Deleted.";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= 10 && event.x() < 10 + listW() && event.y() >= TOP && event.y() < height - 36) {
            int i = ((int) event.y() - TOP + listScroll) / ROW;
            if (i >= 0 && i < backups.size()) { select(i); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx < 10 + listW()) {
            int max = Math.max(0, backups.size() * ROW - (height - 36 - TOP));
            listScroll = (int) Math.max(0, Math.min(max, listScroll - v * ROW * 2));
        } else {
            int max = Math.max(0, preview.size() - (height - 36 - TOP) / 10);
            previewScroll = (int) Math.max(0, Math.min(max, previewScroll - v * 3));
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, GuiStyle.background());
        g.text(font, "Config Backups", 10, 10, 0xFFFFFFFF);
        g.text(font, status, 10 + listW() + 12, 10, 0xFFFFAA55);
        restoreBtn.active = selected >= 0;
        deleteBtn.active = selected >= 0;
        restoreBtn.setMessage(Component.literal(restoreArmed != 0 && System.currentTimeMillis() - restoreArmed < 3000 ? "§cConfirm" : "Restore"));

        int lw = listW(), bottom = height - 36;
        g.fill(10, TOP, 10 + lw, bottom, GuiStyle.input());
        g.enableScissor(10, TOP, 10 + lw, bottom);
        if (backups.isEmpty()) g.text(font, "No backups yet", 14, TOP + 4, GuiStyle.textDim());
        for (int i = 0; i < backups.size(); i++) {
            int y = TOP + i * ROW - listScroll;
            if (y + ROW < TOP || y > bottom) continue;
            boolean hover = mouseX >= 10 && mouseX < 10 + lw && mouseY >= y && mouseY < y + ROW;
            if (i == selected) g.fill(10, y, 10 + lw, y + ROW, GuiStyle.rowSelected());
            else if (hover) g.fill(10, y, 10 + lw, y + ROW, GuiStyle.rowHover());
            String n = backups.get(i).getFileName().toString();
            n = n.substring(7, n.length() - 5).replace('_', ' ');
            g.text(font, n, 14, y + 3, i == selected ? GuiStyle.accent() : GuiStyle.text());
        }
        g.disableScissor();

        int px = 10 + lw + 12;
        g.fill(px, TOP, width - 10, bottom, GuiStyle.input());
        g.enableScissor(px, TOP, width - 10, bottom);
        if (selected < 0) {
            g.text(font, "Select a backup to see its content", px + 4, TOP + 4, GuiStyle.textDim());
        } else {
            int rows = (bottom - TOP) / 10;
            for (int i = 0; i < rows && previewScroll + i < preview.size(); i++) {
                g.text(font, preview.get(previewScroll + i), px + 4, TOP + 3 + i * 10, GuiStyle.textDim());
            }
        }
        g.disableScissor();
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}

    @Override
    public void onClose() {
        if (parent instanceof MapConfigScreen m) {
            minecraft.gui.setScreen(MapConfigScreen.open());
        } else if (parent instanceof com.teslamaps.screen.nia.NiaConfigScreen n) {
            n.rebuild();
            minecraft.gui.setScreen(n);
        } else {
            minecraft.gui.setScreen(parent);
        }
    }
}
