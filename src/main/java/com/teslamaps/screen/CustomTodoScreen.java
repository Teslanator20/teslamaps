package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.CustomTodo;
import com.teslamaps.features.CustomTodos;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class CustomTodoScreen extends Screen {
    private static final int PANEL_W = 464, PANEL_H = 300;
    private static final int ROW_H = 24, VISIBLE = 8;

    private final Screen parent;
    private int panelX, panelY, listX, listY;
    private int scroll = 0;
    private final List<int[]> fields = new ArrayList<>();
    private String status = "";

    public CustomTodoScreen(Screen parent) {
        super(Component.literal("Custom Todos"));
        this.parent = parent;
    }

    private List<CustomTodo> list() { return TeslaMapsConfig.get().customTodoList; }

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
        int i = Arrays.asList(CustomTodos.MATCHERS).indexOf(cur);
        return CustomTodos.MATCHERS[(i + 1) % CustomTodos.MATCHERS.length];
    }

    private EditBox box(int x, int y, int w, String hint, String value, Consumer<String> set, int max) {
        EditBox b = new EditBox(font, x + 4, y + 5, w - 8, 12, Component.literal(hint));
        b.setMaxLength(max);
        b.setBordered(false);
        b.setHint(Component.literal(hint));
        b.setValue(value);
        b.setResponder(set);
        addRenderableWidget(b);
        fields.add(new int[]{x, y, w, 20});
        return b;
    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        List<CustomTodo> l = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= l.size()) break;
            CustomTodo t = l.get(idx);
            int ry = listY + row * ROW_H;
            int x = listX;

            addRenderableWidget(new TeslaButton(x, ry, 20, 20, t.enabled ? "§a✔" : "§8✘", () -> {
                t.enabled = !t.enabled;
                rebuild();
            }));
            x += 22;
            addRenderableWidget(new TeslaButton(x, ry, 62, 20, t.matcher, () -> {
                t.matcher = next(t.matcher);
                rebuild();
            }));
            x += 64;
            box(x, ry, 80, "Name", t.label, v -> t.label = v, 32);
            x += 82;
            box(x, ry, 170, "Chat line...", t.trigger, v -> t.trigger = v, 256);
            x += 172;
            box(x, ry, 48, "1h30m", t.time, v -> t.time = v, 16);
            x += 50;
            addRenderableWidget(new TeslaButton(x, ry, 20, 20, "§e⟳", () -> {
                CustomTodos.markDone(t);
                status = "§7" + t.label + " §7restarted";
            }));
            x += 22;
            addRenderableWidget(new TeslaButton(x, ry, 16, 20, "§cx", () -> { list().remove(idx); rebuild(); }));
        }

        int by = panelY + PANEL_H - 30;
        addRenderableWidget(new TeslaButton(panelX + 12, by, 90, 20, "§a+ Add Todo", () -> {
            list().add(new CustomTodo());
            scroll = Math.max(0, list().size() - VISIBLE);
            rebuild();
        }));
        addRenderableWidget(new TeslaButton(panelX + 106, by, 80, 20, "§fCopy All", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(CustomTodos.export(list()));
            status = "§aCopied " + list().size() + " todos";
        }));
        addRenderableWidget(new TeslaButton(panelX + 190, by, 80, 20, "§fImport", () -> {
            List<CustomTodo> in = CustomTodos.parse(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (in == null) { status = "§cNo todo export in the clipboard"; return; }
            list().addAll(in);
            status = "§aImported " + in.size() + " todos";
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
        ctx.text(font, "§f§lCustom Todos", panelX + 12, panelY + 10, 0xFFFFFFFF);
        ctx.text(font, "§8on · match · name · chat line (colors stripped) · time · restart", panelX + 12, panelY + 24, 0xFFFFFFFF);
        List<CustomTodo> l = list();
        for (int i = 0; i < fields.size(); i++) {
            int[] f = fields.get(i);
            HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);
            int row = scroll + i / 3;
            if (i % 3 == 2 && row < l.size() && CustomTodos.seconds(l.get(row).time) < 0) {
                ctx.fill(f[0], f[1] + f[3] - 1, f[0] + f[2], f[1] + f[3], 0xFFFF5555);
            }
        }
        if (l.isEmpty()) ctx.text(font, "§7No todos - click \"+ Add Todo\".", panelX + 12, listY + 6, 0xFFFFFFFF);
        if (!status.isEmpty()) ctx.text(font, status, panelX + 12, panelY + PANEL_H - 44, 0xFFFFFFFF);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        list().removeIf(t -> t.label.isBlank() && t.trigger.isBlank());
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
