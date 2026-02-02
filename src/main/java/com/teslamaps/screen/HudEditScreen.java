package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Screen for editing HUD element positions by dragging.
 * Supports both dungeon map and slayer HUD.
 */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean wasMouseDown = false;

    // Which element is being dragged
    private enum DragTarget { NONE, MAP, SLAYER_HUD }
    private DragTarget dragging = DragTarget.NONE;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // Map preview constants
    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;

    // Slayer HUD preview constants
    private static final int SLAYER_WIDTH = 140;
    private static final int SLAYER_HEIGHT = 46;

    public HudEditScreen(Screen parent) {
        super(Text.literal("Edit HUD Positions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Done"),
                button -> close()
        ).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
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

    private boolean isMouseOverSlayerHud(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int scaledWidth = (int)(SLAYER_WIDTH * config.slayerHudScale);
        int scaledHeight = (int)(SLAYER_HEIGHT * config.slayerHudScale);
        return mouseX >= config.slayerHudX && mouseX <= config.slayerHudX + scaledWidth &&
               mouseY >= config.slayerHudY && mouseY <= config.slayerHudY + scaledHeight;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // Handle mouse press - start dragging
        if (isMouseDown && !wasMouseDown) {
            if (isMouseOverSlayerHud(mouseX, mouseY)) {
                dragging = DragTarget.SLAYER_HUD;
                dragOffsetX = mouseX - config.slayerHudX;
                dragOffsetY = mouseY - config.slayerHudY;
            } else if (isMouseOverMap(mouseX, mouseY)) {
                dragging = DragTarget.MAP;
                dragOffsetX = mouseX - config.mapX;
                dragOffsetY = mouseY - config.mapY;
            }
        }

        // Handle mouse release
        if (!isMouseDown && wasMouseDown && dragging != DragTarget.NONE) {
            dragging = DragTarget.NONE;
            TeslaMapsConfig.save();
        }

        wasMouseDown = isMouseDown;

        // Handle dragging
        if (dragging == DragTarget.MAP) {
            int mapSize = getMapSize();
            int newX = Math.max(0, Math.min(this.width - mapSize, mouseX - dragOffsetX));
            int newY = Math.max(0, Math.min(this.height - mapSize - 40, mouseY - dragOffsetY));
            config.mapX = newX;
            config.mapY = newY;
        } else if (dragging == DragTarget.SLAYER_HUD) {
            int newX = Math.max(0, Math.min(this.width - SLAYER_WIDTH, mouseX - dragOffsetX));
            int newY = Math.max(0, Math.min(this.height - SLAYER_HEIGHT - 40, mouseY - dragOffsetY));
            config.slayerHudX = newX;
            config.slayerHudY = newY;
        }

        // Dark background
        context.fill(0, 0, this.width, this.height, 0xC0000000);

        // Draw map preview
        drawMapPreview(context, mouseX, mouseY);

        // Draw slayer HUD preview
        drawSlayerHudPreview(context, mouseX, mouseY);

        // Instructions
        String instructions = dragging != DragTarget.NONE ? "Release to place" : "Drag elements to move them | Scroll on map to resize";
        int textWidth = this.textRenderer.getWidth(instructions);
        context.fill(this.width / 2 - textWidth / 2 - 8, 8, this.width / 2 + textWidth / 2 + 8, 26, 0xC0000000);
        context.drawCenteredTextWithShadow(this.textRenderer, instructions, this.width / 2, 12, 0xFFE0E0E0);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawMapPreview(DrawContext context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.mapScale;
        int mapSize = getMapSize();
        int mapX = config.mapX;
        int mapY = config.mapY;

        // Background
        context.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF3a3a3a);
        context.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF1a1a1a);

        // Grid preview
        int roomSizeScaled = (int)(ROOM_SIZE * scale);
        for (int gx = 0; gx < 6; gx++) {
            for (int gz = 0; gz < 6; gz++) {
                int px = mapX + (int)((MAP_PADDING + gx * CELL_SIZE) * scale);
                int py = mapY + (int)((MAP_PADDING + gz * CELL_SIZE) * scale);
                int color = ((gx + gz) % 2 == 0) ? 0xFF3d3d3d : 0xFF4a4a4a;
                context.fill(px, py, px + roomSizeScaled, py + roomSizeScaled, color);
            }
        }

        // Border when hovering/dragging
        boolean hovering = isMouseOverMap(mouseX, mouseY);
        if (hovering || dragging == DragTarget.MAP) {
            int borderColor = dragging == DragTarget.MAP ? 0xFF5865F2 : 0xFF888888;
            context.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY, borderColor);
            context.fill(mapX - 2, mapY + mapSize, mapX + mapSize + 2, mapY + mapSize + 2, borderColor);
            context.fill(mapX - 2, mapY, mapX, mapY + mapSize, borderColor);
            context.fill(mapX + mapSize, mapY, mapX + mapSize + 2, mapY + mapSize, borderColor);
        }

        // Label
        context.drawTextWithShadow(this.textRenderer, "Map", mapX, mapY - 12, 0xFFFFFFFF);
    }

    private void drawSlayerHudPreview(DrawContext context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.slayerHudX;
        int y = config.slayerHudY;
        float scale = config.slayerHudScale;
        int padding = 6;

        // Apply scaling
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        matrices.translate(-x, -y);

        int centerX = x + SLAYER_WIDTH / 2;

        // Rounded background
        drawRoundedRect(context, x, y, SLAYER_WIDTH, SLAYER_HEIGHT, 4, 0xDD000000);

        int currentY = y + padding;

        // Boss name (centered)
        String bossName = "Inferno Demonlord IV";
        int nameWidth = this.textRenderer.getWidth(bossName);
        context.drawTextWithShadow(this.textRenderer, bossName, centerX - nameWidth / 2, currentY, 0xFFFF5555);
        currentY += 12;

        // Health bar
        int barWidth = SLAYER_WIDTH - padding * 2;
        int barX = x + padding;
        int barHeight = 12;
        drawRoundedRect(context, barX, currentY, barWidth, barHeight, 2, 0xFF333333);
        drawRoundedRect(context, barX, currentY, (int)(barWidth * 0.7), barHeight, 2, 0xFF55FFFF);

        String hpText = "35M / 50M";
        int hpWidth = this.textRenderer.getWidth(hpText);
        context.drawTextWithShadow(this.textRenderer, hpText, centerX - hpWidth / 2, currentY + 2, 0xFFFFFFFF);
        currentY += barHeight + 2;

        // Phase (centered)
        String phaseText = "CRYSTAL x2";
        int phaseWidth = this.textRenderer.getWidth(phaseText);
        context.drawTextWithShadow(this.textRenderer, phaseText, centerX - phaseWidth / 2, currentY, 0xFF55FFFF);

        matrices.popMatrix();

        // Border when hovering/dragging (drawn at actual scaled size)
        int scaledWidth = (int)(SLAYER_WIDTH * scale);
        int scaledHeight = (int)(SLAYER_HEIGHT * scale);
        boolean hovering = isMouseOverSlayerHud(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SLAYER_HUD) {
            int borderColor = dragging == DragTarget.SLAYER_HUD ? 0xFF30D158 : 0xFF888888;
            context.fill(x - 2, y - 2, x + scaledWidth + 2, y, borderColor);
            context.fill(x - 2, y + scaledHeight, x + scaledWidth + 2, y + scaledHeight + 2, borderColor);
            context.fill(x - 2, y, x, y + scaledHeight, borderColor);
            context.fill(x + scaledWidth, y, x + scaledWidth + 2, y + scaledHeight, borderColor);
        }

        // Label with scale info
        String label = String.format("Slayer HUD (%.1fx)", scale);
        context.drawTextWithShadow(this.textRenderer, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawRoundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + radius, y + height - radius, color);
        context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
        context.fill(x + 1, y + 1, x + radius, y + radius, color);
        context.fill(x + width - radius, y + 1, x + width - 1, y + radius, color);
        context.fill(x + 1, y + height - radius, x + radius, y + height - 1, color);
        context.fill(x + width - radius, y + height - radius, x + width - 1, y + height - 1, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // Scale slayer HUD when mouse is over it
        if (isMouseOverSlayerHud((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.slayerHudScale = Math.min(2.0f, config.slayerHudScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.slayerHudScale = Math.max(0.5f, config.slayerHudScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        // Scale map when mouse is over it
        if (isMouseOverMap((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.mapScale = Math.min(2.0f, config.mapScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.mapScale = Math.max(0.5f, config.mapScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        TeslaMapsConfig.save();
        if (parent != null) {
            client.setScreen(parent);
        } else {
            super.close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
