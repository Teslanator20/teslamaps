package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.KeybindMessage;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.render.gui.TeslaGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class KeybindMessageScreen extends Screen {
    private static final int PANEL_W = 420, PANEL_H = 290;
    private static final int ROW_H = 28, VISIBLE = 6, KEY_W = 92, MOD_W = 18;

    private int panelX, panelY, listX, listY, listW;
    private int scroll = 0;
    private int listening = -1; // index of the keybind currently waiting for a key press
    private long listenStart = 0; // the click that starts listening must not bind itself

    /** Backgrounds for the borderless edit boxes, filled in while the rows are built. */
    private final List<int[]> fields = new ArrayList<>();

    public KeybindMessageScreen() { super(Component.literal("Keybind Messages")); }

    private List<TeslaMapsConfig.Keybind> binds() { return TeslaMapsConfig.get().keybinds; }

    @Override
    protected void init() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (binds().isEmpty() && c.keybindChatMessage != null && !c.keybindChatMessage.isBlank()) {
            binds().add(new TeslaMapsConfig.Keybind(-1, c.keybindChatMessage));
            c.keybindChatMessage = "";
        }
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
        List<TeslaMapsConfig.Keybind> b = binds();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        int modsW = 3 * (MOD_W + 2);
        boolean conds = TeslaMapsConfig.get().hotkeyConditions;
        int boxW = listW - 24 - KEY_W - 4 - modsW - 4 - 28 - (conds ? 24 : 0);
        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            TeslaMapsConfig.Keybind kb = b.get(idx);
            int ry = listY + row * ROW_H;
            int keyX = listX + 24;
            int modX = keyX + KEY_W + 4;
            int boxX = modX + modsW + 2;

            addRenderableWidget(new TeslaButton(listX, ry, 22, 22, kb.enabled ? "§a✔" : "§c✖",
                    () -> { kb.enabled = !kb.enabled; rebuild(); }));

            String label = listening == idx ? "§epress..."
                    : (kb.enabled ? "§f" : "§8") + KeybindMessage.modifierPrefix(kb.modifiers) + keyName(kb.key);
            addRenderableWidget(new TeslaButton(keyX, ry, KEY_W, 22, label,
                    () -> { listening = idx; listenStart = System.currentTimeMillis(); rebuild(); }));

            String[] modNames = {"C", "A", "S"};
            int[] modBits = {GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT};
            for (int m = 0; m < 3; m++) {
                int bit = modBits[m];
                boolean on = (kb.modifiers & bit) != 0;
                addRenderableWidget(new TeslaButton(modX + m * (MOD_W + 2), ry, MOD_W, 22,
                        (on ? "§b" : "§8") + modNames[m], () -> { kb.modifiers ^= bit; rebuild(); }));
            }

            EditBox box = new EditBox(this.font, boxX + 4, ry + 7, boxW - 8, 12, Component.literal("Message"));
            box.setMaxLength(256);
            box.setBordered(false);
            box.setHint(Component.literal("/pc gg  ·  good luck"));
            box.setValue(kb.message == null ? "" : kb.message);
            box.setResponder(v -> kb.message = v);
            addRenderableWidget(box);
            fields.add(new int[] {boxX, ry, boxW, 22});

            if (conds) addRenderableWidget(new TeslaButton(listX + listW - 46, ry, 22, 22,
                    com.teslamaps.features.HotkeyCondition.summary(kb),
                    () -> minecraft.setScreen(new HotkeyConditionScreen(this, kb))));

            addRenderableWidget(new TeslaButton(listX + listW - 22, ry, 22, 22, "§cx",
                    () -> { b.remove(idx); listening = -1; rebuild(); }));
        }

        addRenderableWidget(new TeslaButton(panelX + 16, panelY + PANEL_H - 34, 130, 22, "§a+ Add Hotkey", () -> {
            b.add(new TeslaMapsConfig.Keybind(-1, ""));
            scroll = Math.max(0, b.size() - VISIBLE);
            rebuild();
        }));

        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 16 - 100, panelY + PANEL_H - 34, 100, 22,
                "§fDone", this::onClose));
    }

    private static final int MOD_MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT;

    private static boolean isModifierKey(int k) {
        return k == GLFW.GLFW_KEY_LEFT_SHIFT || k == GLFW.GLFW_KEY_RIGHT_SHIFT
                || k == GLFW.GLFW_KEY_LEFT_CONTROL || k == GLFW.GLFW_KEY_RIGHT_CONTROL
                || k == GLFW.GLFW_KEY_LEFT_ALT || k == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    private String keyName(int key) {
        return com.teslamaps.utils.KeyNames.display(key);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening >= 0) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE && listening < binds().size()) {
                TeslaMapsConfig.Keybind kb = binds().get(listening);
                kb.key = event.key();
                kb.modifiers = isModifierKey(event.key()) ? 0 : event.modifiers() & MOD_MASK;
            }
            listening = -1; // ESC just cancels
            rebuild();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // Binding a mouse button: the click that armed the row arrives here too, so ignore
        // anything within a moment of arming or every bind would end up as Mouse Left.
        if (listening >= 0 && System.currentTimeMillis() - listenStart > 250) {
            if (listening < binds().size()) {
                TeslaMapsConfig.Keybind kb = binds().get(listening);
                kb.key = com.teslamaps.utils.KeyNames.forMouse(event.button());
                kb.modifiers = KeybindMessage.heldModifiers(minecraft);
            }
            listening = -1;
            rebuild();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (binds().size() > VISIBLE) {
            scroll = Math.max(0, Math.min(binds().size() - VISIBLE, scroll - (int) Math.signum(sy)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lHotkeys §7Chat Messages", panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8Hold Ctrl/Alt/Shift while binding for a combo. / = command.",
                panelX + 16, panelY + 30, 0xFFFFFFFF);

        for (int[] f : fields) HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);

        if (binds().isEmpty()) {
            ctx.text(this.font, "§7No hotkeys yet — click \"+ Add Hotkey\".", panelX + 16, panelY + 62, 0xFFFFFFFF);
        }
        if (binds().size() > VISIBLE) {
            ctx.text(this.font, "§8" + binds().size() + " total · scroll",
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
