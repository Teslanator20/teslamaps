package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.Splits;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Per-floor split names + trigger messages for {@link Splits}. */
public class CustomSplitsScreen extends Screen {
    private static final int ROWS = 8;
    private static final int PANEL_W = 420;
    private static final DungeonFloor[] FLOORS = {
            DungeonFloor.F1, DungeonFloor.F2, DungeonFloor.F3, DungeonFloor.F4, DungeonFloor.F5, DungeonFloor.F6, DungeonFloor.F7,
            DungeonFloor.M1, DungeonFloor.M2, DungeonFloor.M3, DungeonFloor.M4, DungeonFloor.M5, DungeonFloor.M6, DungeonFloor.M7};

    private static DungeonFloor floor = DungeonFloor.F7;

    private final Screen parent;
    private int panelX, panelY, panelH;

    public CustomSplitsScreen(Screen parent) {
        super(Component.literal("Custom Splits"));
        this.parent = parent;
    }

    private static List<TeslaMapsConfig.CustomSplit> rows() {
        List<TeslaMapsConfig.CustomSplit> list = TeslaMapsConfig.get().customSplitDefs
                .computeIfAbsent(floor.name(), k -> new ArrayList<>());
        while (list.size() < ROWS) list.add(new TeslaMapsConfig.CustomSplit());
        return list;
    }

    @Override
    protected void init() {
        panelH = 84 + ROWS * 20 + 40;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        for (int i = 0; i < FLOORS.length; i++) {
            DungeonFloor f = FLOORS[i];
            int bx = panelX + 16 + (i % 7) * 40, by = panelY + 30 + (i / 7) * 18;
            addRenderableWidget(new TeslaButton(bx, by, 36, 16, (f == floor ? "§e§l" : "§f") + f.name(), () -> {
                trim();
                floor = f;
                rebuildWidgets();
            }));
        }
        List<TeslaMapsConfig.CustomSplit> list = rows();
        for (int i = 0; i < ROWS; i++) {
            TeslaMapsConfig.CustomSplit cs = list.get(i);
            int y = rowY(i);
            addRenderableWidget(box(panelX + 20, y, 100, cs.name, v -> cs.name = v));
            addRenderableWidget(box(panelX + 132, y, 270, cs.message, v -> cs.message = v));
        }
        int by = panelY + panelH - 30;
        addRenderableWidget(new TeslaButton(panelX + 16, by, 100, 20, "§fLoad Defaults", this::loadDefaults));
        addRenderableWidget(new TeslaButton(panelX + 122, by, 80, 20, "§fClear", () -> {
            TeslaMapsConfig.get().customSplitDefs.remove(floor.name());
            rebuildWidgets();
        }));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 90, by, 90, 20, "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private EditBox box(int x, int y, int w, String value, java.util.function.Consumer<String> set) {
        EditBox box = new EditBox(this.font, x, y + 4, w, 12, Component.literal("text"));
        box.setMaxLength(256);
        box.setBordered(false);
        box.setValue(value == null ? "" : value);
        box.setResponder(set);
        return box;
    }

    private void loadDefaults() {
        List<TeslaMapsConfig.CustomSplit> list = new ArrayList<>();
        for (String[] d : Splits.defaultBossSplits(floor)) {
            TeslaMapsConfig.CustomSplit cs = new TeslaMapsConfig.CustomSplit();
            cs.name = d[1].replace('§', '&');
            cs.message = "/" + d[0] + "/";
            list.add(cs);
        }
        TeslaMapsConfig.get().customSplitDefs.put(floor.name(), list);
        rebuildWidgets();
    }

    private static void trim() {
        List<TeslaMapsConfig.CustomSplit> list = TeslaMapsConfig.get().customSplitDefs.get(floor.name());
        if (list == null) return;
        list.removeIf(cs -> (cs.name == null || cs.name.isBlank()) && (cs.message == null || cs.message.isBlank()));
        if (list.isEmpty()) TeslaMapsConfig.get().customSplitDefs.remove(floor.name());
    }

    private int rowY(int row) {
        return panelY + 84 + row * 20;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, panelH, 8);
        ctx.text(this.font, "§f§lCustom Splits", panelX + 16, panelY + 12, 0xFFFFFFFF);
        ctx.text(this.font, "§8text = contains, /regex/ = regex, & = color", panelX + 120, panelY + 12, 0xFFFFFFFF);
        ctx.text(this.font, "§7Name", panelX + 20, panelY + 72, 0xFFFFFFFF);
        ctx.text(this.font, "§7Chat message that ends the split", panelX + 132, panelY + 72, 0xFFFFFFFF);
        for (int i = 0; i < ROWS; i++) {
            int y = rowY(i);
            HudStyle.screenButton(ctx, panelX + 16, y, 108, 18, false, true);
            HudStyle.screenButton(ctx, panelX + 128, y, 278, 18, false, true);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        trim();
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
