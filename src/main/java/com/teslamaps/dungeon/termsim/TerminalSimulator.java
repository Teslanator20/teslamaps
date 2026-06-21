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
package com.teslamaps.dungeon.termsim;

import com.teslamaps.TeslaMaps;
import org.lwjgl.glfw.GLFW;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public abstract class TerminalSimulator extends Screen {
    protected static final int SLOT_SIZE = 18;
    protected static final int GRID_PADDING = 8;

    protected final int rows;
    protected final int cols;
    protected ItemStack[] slots;
    protected long openTime;
    protected long lastClickTime;
    protected int clickCount;
    protected boolean solved;
    protected Random random = new Random();

    private boolean wasLeftMouseDown = false;
    private boolean wasRightMouseDown = false;

    public TerminalSimulator(String title, int rows, int cols) {
        super(Component.literal(title));
        this.rows = rows;
        this.cols = cols;
        this.slots = new ItemStack[rows * cols];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        }
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        clickCount = 0;
        solved = false;
        initializeTerminal();

        int btnX = (width - 100) / 2;
        int btnY = height / 2 + (rows * SLOT_SIZE) / 2 + 20;
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            initializeTerminal();
            openTime = System.currentTimeMillis();
            clickCount = 0;
            solved = false;
        }).bounds(btnX, btnY, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> {
            onClose();
        }).bounds(btnX, btnY + 25, 100, 20).build());
    }

    protected abstract void initializeTerminal();

    protected abstract boolean onSlotClick(int slotIndex, int button);

    protected abstract boolean checkSolved();

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        handleMouseInput(mouseX, mouseY);

        context.fill(0, 0, width, height, 0xC0101010);

        context.centeredText(font, title, width / 2, 20, 0xFFFFFF);

        int gridWidth = cols * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int startX = (width - gridWidth) / 2;
        int startY = (height - gridHeight) / 2 - 20;

        context.fill(startX - GRID_PADDING, startY - GRID_PADDING,
                startX + gridWidth + GRID_PADDING, startY + gridHeight + GRID_PADDING,
                0xFF2C2C2E);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slotIndex = row * cols + col;
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;

                boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE &&
                        mouseY >= y && mouseY < y + SLOT_SIZE;
                context.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                        hovered ? 0xFF4A4A4C : 0xFF3A3A3C);

                ItemStack stack = slots[slotIndex];
                if (!stack.isEmpty()) {
                    context.item(stack, x + 1, y + 1);
                    if (stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        int textX = x + SLOT_SIZE - 2 - font.width(countStr);
                        int textY = y + SLOT_SIZE - 10;
                        context.text(font, countStr, textX, textY, 0xFFFFFF);
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - openTime;
        String timeStr = String.format("Time: %.2fs", elapsed / 1000.0);
        String clickStr = "Clicks: " + clickCount;
        context.text(font, timeStr, startX, startY + gridHeight + GRID_PADDING + 5, 0xAAAAAA);
        context.text(font, clickStr, startX, startY + gridHeight + GRID_PADDING + 17, 0xAAAAAA);

        if (solved) {
            context.centeredText(font, "SOLVED!", width / 2, startY - 25, 0x55FF55);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        if (minecraft == null) return;

        long windowHandle = minecraft.getWindow().handle();
        boolean isLeftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean isRightDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (isLeftDown && !wasLeftMouseDown && !solved) {
            handleGridClick(mouseX, mouseY, 0);
        }

        if (isRightDown && !wasRightMouseDown && !solved) {
            handleGridClick(mouseX, mouseY, 1);
        }

        wasLeftMouseDown = isLeftDown;
        wasRightMouseDown = isRightDown;
    }

    private void handleGridClick(int mouseX, int mouseY, int button) {
        int gridWidth = cols * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int startX = (width - gridWidth) / 2;
        int startY = (height - gridHeight) / 2 - 20;

        if (mouseX >= startX && mouseX < startX + gridWidth &&
                mouseY >= startY && mouseY < startY + gridHeight) {

            int col = (mouseX - startX) / SLOT_SIZE;
            int row = (mouseY - startY) / SLOT_SIZE;
            int slotIndex = row * cols + col;

            if (slotIndex >= 0 && slotIndex < slots.length) {
                if (onSlotClick(slotIndex, button)) {
                    clickCount++;
                    lastClickTime = System.currentTimeMillis();

                    if (checkSolved()) {
                        solved = true;
                        long elapsed = System.currentTimeMillis() - openTime;
                        TeslaMaps.LOGGER.info("[TermSim] Solved in {}ms with {} clicks!",
                                elapsed, clickCount);
                    }
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected int getSlotIndex(int row, int col) {
        return row * cols + col;
    }

    protected int getRow(int slotIndex) {
        return slotIndex / cols;
    }

    protected int getCol(int slotIndex) {
        return slotIndex % cols;
    }
}
