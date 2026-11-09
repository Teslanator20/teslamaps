package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.HotkeyCondition;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Condition, island filter and fail message for one hotkey. */
public class HotkeyConditionScreen extends Screen {
    private static final int PANEL_W = 380, ROW = 30;
    private int panelH = 230;
    private boolean listening = false;

    private final Screen parent;
    private final TeslaMapsConfig.Keybind kb;
    private int px, py;
    private final List<int[]> fields = new ArrayList<>();
    private EditBox islandBox;

    public HotkeyConditionScreen(Screen parent, TeslaMapsConfig.Keybind kb) {
        super(Component.literal("Hotkey Condition"));
        this.parent = parent;
        this.kb = kb;
    }

    private int rowY(int r) { return py + 44 + r * ROW; }

    @Override
    protected void init() {
        clearWidgets();
        fields.clear();
        boolean adv = com.teslamaps.features.HotkeyRules.enabled();
        panelH = adv ? 230 + 3 * ROW : 230;
        px = (width - PANEL_W) / 2;
        py = (height - panelH) / 2;
        int cx = px + 140, cw = PANEL_W - 156;

        String type = kb.cond == null ? "None" : kb.cond;
        addRenderableWidget(new TeslaButton(cx, rowY(0), adv ? cw - 50 : cw, 22, "§f" + type, () -> {
            kb.cond = next(HotkeyCondition.TYPES, kb.cond);
            init();
        }));
        if (adv) addRenderableWidget(new TeslaButton(cx + cw - 46, rowY(0), 46, 22, kb.negate ? "§cNOT" : "§8NOT", () -> {
            kb.negate = !kb.negate;
            init();
        }));
        if (HotkeyCondition.VALUE_TYPES.contains(type)) {
            box(cx, rowY(1), cw, kb.condValue, v -> kb.condValue = v, "comma list, empty = any");
        }
        boolean items = type.equals("Holding Item") || type.equals("Inventory Contains");
        if (items) {
            String mode = kb.condMode == null ? "Name" : kb.condMode;
            addRenderableWidget(new TeslaButton(cx, rowY(1), 90, 22, "§f" + mode, () -> {
                kb.condMode = next(HotkeyCondition.MODES, kb.condMode);
                init();
            }));
            box(cx + 94, rowY(1), cw - 94, kb.condValue, v -> kb.condValue = v, "text, empty = any");
        }
        int iw = cw - 70;
        islandBox = box(cx, rowY(2), iw, kb.islands, v -> kb.islands = v, "Hub, Garden, ... empty = any");
        addRenderableWidget(new TeslaButton(cx + iw + 4, rowY(2), 66, 22, "§a+ Here", () -> {
            String area = SkyblockUtils.getArea();
            if (area.isEmpty()) return;
            String cur = kb.islands == null ? "" : kb.islands.trim();
            kb.islands = cur.isEmpty() ? area : cur + ", " + area;
            islandBox.setValue(kb.islands);
        }));
        box(cx, rowY(3), cw, kb.failMessage, v -> kb.failMessage = v, "sent when the condition fails");
        if (adv) {
            String k2 = listening ? "§epress... (Esc = none)" : kb.seqKey == -1 ? "§8none" : "§f" + com.teslamaps.utils.KeyNames.display(kb.seqKey);
            addRenderableWidget(new TeslaButton(cx, rowY(4), cw - 74, 22, k2, () -> { listening = true; init(); }));
            box(cx + cw - 70, rowY(4), 70, String.valueOf(kb.seqWindow), v -> kb.seqWindow = num(v, 500), "ms");
            box(cx, rowY(5), cw / 2 - 2, String.valueOf(kb.cooldownMs), v -> kb.cooldownMs = num(v, 0), "cooldown ms");
            box(cx + cw / 2 + 2, rowY(5), cw / 2 - 2, String.valueOf(kb.delayMs), v -> kb.delayMs = num(v, 0), "delay ms");
            box(cx, rowY(6), cw, kb.actions, v -> kb.actions = v, "sound, title:Text, note:Text");
        }
        addRenderableWidget(new TeslaButton(px + PANEL_W - 116, py + panelH - 34, 100, 22, "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    private EditBox box(int x, int y, int w, String value, Consumer<String> set, String hint) {
        EditBox b = new EditBox(font, x + 4, y + 7, w - 8, 12, Component.literal(hint));
        b.setMaxLength(256);
        b.setBordered(false);
        b.setHint(Component.literal("§8" + hint));
        b.setValue(value == null ? "" : value);
        b.setResponder(set);
        addRenderableWidget(b);
        fields.add(new int[]{x, y, w, 22});
        return b;
    }

    private static int num(String v, int def) {
        try {
            return Math.max(0, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (listening) {
            kb.seqKey = event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? -1 : event.key();
            listening = false;
            init();
            return true;
        }
        return super.keyPressed(event);
    }

    private static String next(String[] opts, String cur) {
        for (int i = 0; i < opts.length; i++) if (opts[i].equals(cur)) return opts[(i + 1) % opts.length];
        return opts[0];
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, px, py, PANEL_W, panelH, 8);
        ctx.text(font, "§f§lHotkey Condition §7" + (kb.message == null ? "" : kb.message), px + 16, py + 16, 0xFFFFFFFF);
        ctx.text(font, "§8Pass = normal message, fail = fail message", px + 16, py + 28, 0xFFFFFFFF);
        boolean adv = com.teslamaps.features.HotkeyRules.enabled();
        String[] labels = adv
                ? new String[]{"Condition", "Item Match", "Only On Islands", "Fail Message", "Then Key / Window", "Cooldown / Delay", "Local Actions"}
                : new String[]{"Condition", "Item Match", "Only On Islands", "Fail Message"};
        boolean items = "Holding Item".equals(kb.cond) || "Inventory Contains".equals(kb.cond);
        boolean value = kb.cond != null && HotkeyCondition.VALUE_TYPES.contains(kb.cond);
        if (value) labels[1] = "Value";
        for (int i = 0; i < labels.length; i++) if (i != 1 || items || value) ctx.text(font, "§f" + labels[i], px + 16, rowY(i) + 7, 0xFFFFFFFF);
        for (int[] f : fields) HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
