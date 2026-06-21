/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MapEditScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean wasMouseDown = false;

    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;

    public MapEditScreen(Screen parent) {
        super(Component.literal("Edit Map Position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> onClose()
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private int getMapSize() {
        float scale = TeslaMapsConfig.get().mapScale;
        return (int)((MAP_PADDING * 2 + ComponentGrid.GRID_SIZE * CELL_SIZE) * scale);
    }

    private boolean isMouseOverMap(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int mapSize = getMapSize();
        return mouseX >= config.mapX && mouseX <= config.mapX + mapSize &&
               mouseY >= config.mapY && mouseY <= config.mapY + mapSize;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isMouseDown && !wasMouseDown) {
            if (isMouseOverMap(mouseX, mouseY)) {
                TeslaMapsConfig config = TeslaMapsConfig.get();
                dragging = true;
                dragOffsetX = mouseX - config.mapX;
                dragOffsetY = mouseY - config.mapY;
            }
        }

        if (!isMouseDown && wasMouseDown && dragging) {
            dragging = false;
            TeslaMapsConfig.save();
        }

        wasMouseDown = isMouseDown;

        if (dragging) {
            TeslaMapsConfig config = TeslaMapsConfig.get();
            int mapSize = getMapSize();

            int newX = mouseX - dragOffsetX;
            int newY = mouseY - dragOffsetY;

            newX = Math.max(0, Math.min(this.width - mapSize, newX));
            newY = Math.max(0, Math.min(this.height - mapSize - 40, newY));

            config.mapX = newX;
            config.mapY = newY;
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);

        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.mapScale;
        int mapSize = getMapSize();

        int mapX = config.mapX;
        int mapY = config.mapY;

        context.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF3a3a3a);
        context.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF1a1a1a);

        int roomSizeScaled = (int)(ROOM_SIZE * scale);
        for (int gx = 0; gx < 6; gx++) {
            for (int gz = 0; gz < 6; gz++) {
                int px = mapX + (int)((MAP_PADDING + gx * CELL_SIZE) * scale);
                int py = mapY + (int)((MAP_PADDING + gz * CELL_SIZE) * scale);
                int color = ((gx + gz) % 2 == 0) ? 0xFF3d3d3d : 0xFF4a4a4a;
                context.fill(px, py, px + roomSizeScaled, py + roomSizeScaled, color);
            }
        }

        boolean hovering = isMouseOverMap(mouseX, mouseY);
        if (hovering || dragging) {
            int borderColor = dragging ? 0xFF5865F2 : 0xFF888888;
            context.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY, borderColor);
            context.fill(mapX - 2, mapY + mapSize, mapX + mapSize + 2, mapY + mapSize + 2, borderColor);
            context.fill(mapX - 2, mapY, mapX, mapY + mapSize, borderColor);
            context.fill(mapX + mapSize, mapY, mapX + mapSize + 2, mapY + mapSize, borderColor);
        }

        String instructions = dragging ? "Release to place" : "Drag the map to move it";
        int textWidth = this.font.width(instructions);
        context.fill(this.width / 2 - textWidth / 2 - 8, 8, this.width / 2 + textWidth / 2 + 8, 26, 0xC0000000);
        context.centeredText(this.font, instructions, this.width / 2, 12, 0xFFE0E0E0);

        String posInfo = String.format("Position: %d, %d | Scale: %.1fx", config.mapX, config.mapY, config.mapScale);
        int posWidth = this.font.width(posInfo);
        context.fill(this.width / 2 - posWidth / 2 - 8, 30, this.width / 2 + posWidth / 2 + 8, 48, 0x80000000);
        context.centeredText(this.font, posInfo, this.width / 2, 34, 0xFF888888);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (verticalAmount > 0) {
            config.mapScale = Math.min(2.0f, config.mapScale + 0.1f);
        } else if (verticalAmount < 0) {
            config.mapScale = Math.max(0.5f, config.mapScale - 0.1f);
        }

        TeslaMapsConfig.save();
        return true;
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
