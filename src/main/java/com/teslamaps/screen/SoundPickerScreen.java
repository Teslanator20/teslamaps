package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class SoundPickerScreen extends Screen {
    private static final int PANEL_W = 320, PANEL_H = 300;
    private static final int ROW_H = 20, VISIBLE = 9;

    private final Screen parent;
    private String[] sounds;
    private final Consumer<String> onApply;
    private final String original;
    private String selected;
    private int scroll = 0;
    private boolean wasMouseDown = false;

    private int panelX, panelY, listX, listY, listW;

    public SoundPickerScreen(Screen parent, String label, String[] sounds, String current, Consumer<String> onApply) {
        super(Component.literal(label));
        this.parent = parent;
        this.sounds = sounds;
        this.onApply = onApply;
        this.original = current;
        this.selected = current;
        int idx = indexOf(current);
        if (idx >= 0) scroll = Math.max(0, Math.min(idx - VISIBLE / 2, Math.max(0, sounds.length - VISIBLE)));
    }

    private int indexOf(String s) {
        for (int i = 0; i < sounds.length; i++) if (sounds[i].equals(s)) return i;
        return -1;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 50;
        listW = PANEL_W - 32;

        int btnW = (PANEL_W - 40) / 2;
        addRenderableWidget(new TeslaButton(panelX + 16, panelY + PANEL_H - 34, btnW, 22, "§aApply", () -> {
            onApply.accept(selected);
            TeslaMapsConfig.save();
            minecraft.gui.setScreen(parent);
        }));

        addRenderableWidget(new TeslaButton(panelX + 24 + btnW, panelY + PANEL_H - 34, btnW, 22, "§cNevermind",
                () -> minecraft.gui.setScreen(parent)));

        if (TeslaMapsConfig.get().userSoundFolder) {
            java.util.List<String> merged = new java.util.ArrayList<>();
            for (String k : sounds) if (!k.startsWith(com.teslamaps.utils.UserSounds.PREFIX)) merged.add(k);
            merged.addAll(com.teslamaps.utils.UserSounds.keys());
            if (selected != null && selected.startsWith(com.teslamaps.utils.UserSounds.ID_PREFIX) && !merged.contains(selected)) merged.add(0, selected);
            sounds = merged.toArray(new String[0]);
            int bw = 64, by = panelY + 10, bx = panelX + PANEL_W - 16 - bw * 2 - 4;
            addRenderableWidget(new TeslaButton(bx, by, bw, 16, "Sound ID", () -> {
                com.teslamaps.utils.UserSounds.reload();
                minecraft.gui.setScreen(new TextPromptScreen("Sound id, e.g. block.bell.use", v -> {
                    selected = com.teslamaps.utils.UserSounds.ID_PREFIX + v;
                    LoudSound.play(SoundOptions.resolve(selected), 1.0f, 1.0f);
                    minecraft.gui.setScreen(this);
                }));
            }));
            addRenderableWidget(new TeslaButton(bx + bw + 4, by, bw, 16, "Folder", () -> {
                com.teslamaps.utils.UserSounds.reload();
                net.minecraft.util.Util.getPlatform().openPath(com.teslamaps.utils.UserSounds.dir());
            }));
        }

        ScreenFit.clamp(this); // keep every widget reachable at any gui scale

    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, sounds.length - VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) == com.mojang.blaze3d.platform.InputConstants.PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§l" + getTitle().getString(), panelX + 16, panelY + 16, 0xFFFFFFFF);
        ctx.text(this.font, "§8Click a sound to preview · " + selected, panelX + 16, panelY + 30, 0xFFFFFFFF);

        int rowY = listY;
        for (int i = scroll; i < Math.min(scroll + VISIBLE, sounds.length); i++) {
            String s = sounds[i];
            boolean rowHover = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            if (clicked && rowHover) {
                selected = s;
                LoudSound.play(SoundOptions.resolve(s), 1.0f, 1.0f);
            }
            HudStyle.screenButton(ctx, listX, rowY, listW, ROW_H - 2, rowHover, true);
            if (s.equals(selected)) {
                ctx.fill(listX, rowY, listX + 2, rowY + ROW_H - 2, 0xFF30D158);
            }
            ctx.text(this.font, (s.equals(selected) ? "§a" : "§f") + s, listX + 6, rowY + 5, 0xFFFFFFFF);
            rowY += ROW_H;
        }

        if (sounds.length > VISIBLE) {
            ctx.text(this.font, "§8" + (scroll + 1) + "-" + Math.min(scroll + VISIBLE, sounds.length) + " / " + sounds.length + " · scroll",
                    panelX + 16, panelY + PANEL_H - 48, 0xFFFFFFFF);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        wasMouseDown = isMouseDown;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
