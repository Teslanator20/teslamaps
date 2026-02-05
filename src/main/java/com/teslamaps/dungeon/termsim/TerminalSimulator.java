package com.teslamaps.dungeon.termsim;

import com.teslamaps.TeslaMaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Base class for terminal simulators.
 * Allows practicing terminals without being in a dungeon.
 */
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

    // Mouse state tracking
    private boolean wasLeftMouseDown = false;
    private boolean wasRightMouseDown = false;

    public TerminalSimulator(String title, int rows, int cols) {
        super(Text.literal(title));
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

        // Reset button
        int btnX = (width - 100) / 2;
        int btnY = height / 2 + (rows * SLOT_SIZE) / 2 + 20;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> {
            initializeTerminal();
            openTime = System.currentTimeMillis();
            clickCount = 0;
            solved = false;
        }).dimensions(btnX, btnY, 100, 20).build());

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> {
            close();
        }).dimensions(btnX, btnY + 25, 100, 20).build());
    }

    /**
     * Initialize the terminal with random state.
     */
    protected abstract void initializeTerminal();

    /**
     * Handle a click on a slot.
     * @return true if the click was valid
     */
    protected abstract boolean onSlotClick(int slotIndex, int button);

    /**
     * Check if the terminal is solved.
     */
    protected abstract boolean checkSolved();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Handle mouse clicks using GLFW polling (1.21.10 compatible)
        handleMouseInput(mouseX, mouseY);

        // Dark background
        context.fill(0, 0, width, height, 0xC0101010);

        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);

        // Grid background
        int gridWidth = cols * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int startX = (width - gridWidth) / 2;
        int startY = (height - gridHeight) / 2 - 20;

        context.fill(startX - GRID_PADDING, startY - GRID_PADDING,
                startX + gridWidth + GRID_PADDING, startY + gridHeight + GRID_PADDING,
                0xFF2C2C2E);

        // Render slots
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int slotIndex = row * cols + col;
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;

                // Slot background
                boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE &&
                        mouseY >= y && mouseY < y + SLOT_SIZE;
                context.fill(x, y, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                        hovered ? 0xFF4A4A4C : 0xFF3A3A3C);

                // Render item
                ItemStack stack = slots[slotIndex];
                if (!stack.isEmpty()) {
                    context.drawItem(stack, x + 1, y + 1);
                    // Draw item count manually if > 1
                    if (stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        int textX = x + SLOT_SIZE - 2 - textRenderer.getWidth(countStr);
                        int textY = y + SLOT_SIZE - 10;
                        context.drawTextWithShadow(textRenderer, countStr, textX, textY, 0xFFFFFF);
                    }
                }
            }
        }

        // Stats
        long elapsed = System.currentTimeMillis() - openTime;
        String timeStr = String.format("Time: %.2fs", elapsed / 1000.0);
        String clickStr = "Clicks: " + clickCount;
        context.drawTextWithShadow(textRenderer, timeStr, startX, startY + gridHeight + GRID_PADDING + 5, 0xAAAAAA);
        context.drawTextWithShadow(textRenderer, clickStr, startX, startY + gridHeight + GRID_PADDING + 17, 0xAAAAAA);

        if (solved) {
            context.drawCenteredTextWithShadow(textRenderer, "SOLVED!", width / 2, startY - 25, 0x55FF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Handle mouse input using GLFW polling (1.21.10 compatible approach).
     */
    private void handleMouseInput(int mouseX, int mouseY) {
        if (client == null) return;

        long windowHandle = client.getWindow().getHandle();
        boolean isLeftDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean isRightDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        // Detect left click (press down)
        if (isLeftDown && !wasLeftMouseDown && !solved) {
            handleGridClick(mouseX, mouseY, 0);
        }

        // Detect right click (press down)
        if (isRightDown && !wasRightMouseDown && !solved) {
            handleGridClick(mouseX, mouseY, 1);
        }

        wasLeftMouseDown = isLeftDown;
        wasRightMouseDown = isRightDown;
    }

    /**
     * Handle a click on the grid.
     */
    private void handleGridClick(int mouseX, int mouseY, int button) {
        int gridWidth = cols * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int startX = (width - gridWidth) / 2;
        int startY = (height - gridHeight) / 2 - 20;

        // Check if click is within grid
        if (mouseX >= startX && mouseX < startX + gridWidth &&
                mouseY >= startY && mouseY < startY + gridHeight) {

            int col = (mouseX - startX) / SLOT_SIZE;
            int row = (mouseY - startY) / SLOT_SIZE;
            int slotIndex = row * cols + col;

            if (slotIndex >= 0 && slotIndex < slots.length) {
                if (onSlotClick(slotIndex, button)) {
                    clickCount++;
                    lastClickTime = System.currentTimeMillis();

                    // Check if solved
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
    public boolean shouldPause() {
        return false;
    }

    /**
     * Get slot index from row/col.
     */
    protected int getSlotIndex(int row, int col) {
        return row * cols + col;
    }

    /**
     * Get row from slot index.
     */
    protected int getRow(int slotIndex) {
        return slotIndex / cols;
    }

    /**
     * Get column from slot index.
     */
    protected int getCol(int slotIndex) {
        return slotIndex % cols;
    }
}
