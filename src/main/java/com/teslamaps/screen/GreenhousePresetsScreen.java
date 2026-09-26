package com.teslamaps.screen;

// After Skyblocker's GreenhousePresetsScreen
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.GreenhousePlanner;
import com.teslamaps.render.gui.GuiStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Named greenhouse layouts: save the current one, load, delete, paste a new one. */
public class GreenhousePresetsScreen extends Screen {
    private static final int ROW = 14, TOP = 34;
    private final Screen parent;
    private List<String> names = new ArrayList<>();
    private int selected = -1, scroll;
    private long deleteArmed;
    private String status = "";
    private EditBox nameBox;
    private TeslaButton loadBtn, deleteBtn;

    public GreenhousePresetsScreen(Screen parent) {
        super(Component.literal("Greenhouse Presets"));
        this.parent = parent;
    }

    private int listW() { return Math.min(260, width - 20); }

    @Override
    protected void init() {
        names = new ArrayList<>(GreenhousePlanner.presetNames());
        if (selected >= names.size()) selected = -1;
        int by = height - 28;
        nameBox = new EditBox(font, 10, by - 26, 150, 18, Component.literal("Preset name"));
        nameBox.setMaxLength(40);
        nameBox.setHint(Component.literal("§7name for the current layout"));
        addRenderableWidget(nameBox);
        addRenderableWidget(new TeslaButton(166, by - 27, 110, 20, "Save Current", this::save));
        loadBtn = addRenderableWidget(new TeslaButton(10, by, 70, 20, "Load", this::load));
        deleteBtn = addRenderableWidget(new TeslaButton(86, by, 70, 20, "Delete", this::delete));
        addRenderableWidget(new TeslaButton(162, by, 120, 20, "Paste From Clipboard", () -> {
            String err = GreenhousePlanner.load(minecraft.keyboardHandler.getClipboard());
            GreenhousePlanner.rescan();
            status = err == null ? "§aLoaded · " + GreenhousePlanner.status() : "§c" + err;
        }));
        addRenderableWidget(new TeslaButton(width - 80, by, 70, 20, "Back", this::onClose));
        ScreenFit.clamp(this);
    }

    private void save() {
        String n = nameBox.getValue().trim();
        if (n.isEmpty() && selected >= 0) n = names.get(selected);
        if (n.isEmpty()) { status = "§cType a name first."; return; }
        status = GreenhousePlanner.savePreset(n);
        names = new ArrayList<>(GreenhousePlanner.presetNames());
        selected = names.indexOf(n);
        nameBox.setValue("");
    }

    private void load() {
        if (selected < 0) return;
        status = GreenhousePlanner.loadPreset(names.get(selected));
    }

    private void delete() {
        if (selected < 0) return;
        long now = System.currentTimeMillis();
        if (now - deleteArmed > 3000) { deleteArmed = now; status = "§eClick Delete again to remove it."; return; }
        deleteArmed = 0;
        status = GreenhousePlanner.deletePreset(names.get(selected));
        names = new ArrayList<>(GreenhousePlanner.presetNames());
        selected = -1;
    }

    private int bottom() { return height - 64; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= 10 && event.x() < 10 + listW() && event.y() >= TOP && event.y() < bottom()) {
            int i = ((int) event.y() - TOP + scroll) / ROW;
            if (i >= 0 && i < names.size()) {
                selected = i;
                deleteArmed = 0;
                if (doubleClick) load();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, names.size() * ROW - (bottom() - TOP));
        scroll = (int) Math.max(0, Math.min(max, scroll - v * ROW * 2));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, GuiStyle.background());
        g.text(font, "Greenhouse Presets §7· double click loads", 10, 10, 0xFFFFFFFF);
        g.text(font, status, 10, 21, 0xFFFFAA55);
        loadBtn.active = selected >= 0;
        deleteBtn.active = selected >= 0;
        deleteBtn.setMessage(Component.literal(deleteArmed != 0 && System.currentTimeMillis() - deleteArmed < 3000 ? "§cConfirm" : "Delete"));

        int lw = listW(), bottom = bottom();
        String current = TeslaMapsConfig.get().greenhouseCode;
        g.fill(10, TOP, 10 + lw, bottom, GuiStyle.input());
        g.enableScissor(10, TOP, 10 + lw, bottom);
        if (names.isEmpty()) g.text(font, "No presets yet", 14, TOP + 4, GuiStyle.textDim());
        for (int i = 0; i < names.size(); i++) {
            int y = TOP + i * ROW - scroll;
            if (y + ROW < TOP || y > bottom) continue;
            boolean hover = mouseX >= 10 && mouseX < 10 + lw && mouseY >= y && mouseY < y + ROW;
            if (i == selected) g.fill(10, y, 10 + lw, y + ROW, GuiStyle.rowSelected());
            else if (hover) g.fill(10, y, 10 + lw, y + ROW, GuiStyle.rowHover());
            String code = TeslaMapsConfig.get().greenhousePresets.get(names.get(i));
            boolean active = code != null && code.equals(current);
            g.text(font, names.get(i) + (active ? " §a(active)" : ""), 14, y + 3, i == selected ? GuiStyle.accent() : GuiStyle.text());
        }
        g.disableScissor();
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}

    @Override
    public void onClose() {
        if (parent instanceof MapConfigScreen m) minecraft.setScreen(MapConfigScreen.open(m.view()));
        else minecraft.setScreen(parent);
    }
}
