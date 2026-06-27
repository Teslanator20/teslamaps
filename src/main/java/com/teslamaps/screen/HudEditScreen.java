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
import com.teslamaps.features.BearSpawnWarning;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean wasMouseDown = false;

    private enum DragTarget { NONE, MAP, SLAYER_HUD, BEAR_SPAWN, SPLITS, BLOOD_CAMP, SPRINTING, DUNGEON_TIMERS, INVINCIBILITY, THORN_STUN, SPIRIT_BEAR }

    private static final String[] SPLITS_SAMPLE = {
            "§2Blood Open", "§bBlood Clear", "§dPortal Entry", "§5Maxor", "§1Total"
    };
    private static final String SPLITS_SAMPLE_TIME = "1:23.4";

    private static final String[] BLOOD_SAMPLE = { "§eReturn to Blood: ~23.0s", "§cThe Watcher: §f13§7/§f19" };
    private DragTarget dragging = DragTarget.NONE;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;

    private static final int SLAYER_WIDTH = 140;
    private static final int SLAYER_HEIGHT = 46;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD Positions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> onClose()
        ).bounds(this.width - 110, 6, 100, 20).build());
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

    private int bearSpawnWidth() {
        return (int)(this.font.width(BearSpawnWarning.ALERT_TEXT) * TeslaMapsConfig.get().bearSpawnScale);
    }

    private int bearSpawnHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().bearSpawnScale);
    }

    private boolean isMouseOverBearSpawn(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = bearSpawnWidth();
        int h = bearSpawnHeight();
        return mouseX >= config.bearSpawnX && mouseX <= config.bearSpawnX + w &&
               mouseY >= config.bearSpawnY && mouseY <= config.bearSpawnY + h;
    }

    private int sprintingWidth() {
        return (int)(this.font.width(com.teslamaps.features.SprintingOverlay.SAMPLE_TEXT) * TeslaMapsConfig.get().sprintingScale);
    }

    private int sprintingHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().sprintingScale);
    }

    private boolean isMouseOverSprinting(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = sprintingWidth();
        int h = sprintingHeight();
        return mouseX >= config.sprintingX && mouseX <= config.sprintingX + w &&
               mouseY >= config.sprintingY && mouseY <= config.sprintingY + h;
    }

    private int dungeonTimersWidth() {
        return (int)(this.font.width(com.teslamaps.features.DungeonTimers.SAMPLE_TEXT) * TeslaMapsConfig.get().dungeonTimersScale);
    }
    private int dungeonTimersHeight() {
        return (int)(this.font.lineHeight * 2 * TeslaMapsConfig.get().dungeonTimersScale);
    }
    private boolean isMouseOverDungeonTimers(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = dungeonTimersWidth(), h = dungeonTimersHeight();
        return mouseX >= config.dungeonTimersX && mouseX <= config.dungeonTimersX + w &&
               mouseY >= config.dungeonTimersY && mouseY <= config.dungeonTimersY + h;
    }

    private int invincibilityWidth() {
        return (int)((12 + this.font.width("immune 2.50s")) * TeslaMapsConfig.get().invincibilityScale);
    }
    private int invincibilityHeight() {
        return (int)(this.font.lineHeight * 3 * TeslaMapsConfig.get().invincibilityScale);
    }
    private boolean isMouseOverInvincibility(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = invincibilityWidth(), h = invincibilityHeight();
        return mouseX >= config.invincibilityX && mouseX <= config.invincibilityX + w &&
               mouseY >= config.invincibilityY && mouseY <= config.invincibilityY + h;
    }

    private int thornStunWidth() {
        return (int)(this.font.width(com.teslamaps.features.ThornStunTimer.SAMPLE_TEXT) * TeslaMapsConfig.get().thornStunScale);
    }
    private int thornStunHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().thornStunScale);
    }
    private boolean isMouseOverThornStun(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = thornStunWidth(), h = thornStunHeight();
        return mouseX >= config.thornStunX && mouseX <= config.thornStunX + w &&
               mouseY >= config.thornStunY && mouseY <= config.thornStunY + h;
    }

    private int spiritBearWidth() {
        return (int)(this.font.width(com.teslamaps.dungeon.puzzle.SpiritBearTimer.SAMPLE_TEXT) * TeslaMapsConfig.get().spiritBearScale);
    }
    private int spiritBearHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().spiritBearScale);
    }
    private boolean isMouseOverSpiritBear(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = spiritBearWidth(), h = spiritBearHeight();
        return mouseX >= config.spiritBearX && mouseX <= config.spiritBearX + w &&
               mouseY >= config.spiritBearY && mouseY <= config.spiritBearY + h;
    }

    private int splitsNameCol() {
        int nameCol = 0;
        for (String s : SPLITS_SAMPLE) nameCol = Math.max(nameCol, this.font.width(s));
        return nameCol + 6;
    }

    private int splitsWidth() {
        return (int)((splitsNameCol() + this.font.width(SPLITS_SAMPLE_TIME)) * TeslaMapsConfig.get().splitsScale);
    }

    private int splitsHeight() {
        return (int)(SPLITS_SAMPLE.length * 9 * TeslaMapsConfig.get().splitsScale);
    }

    private boolean isMouseOverSplits(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = splitsWidth();
        int h = splitsHeight();
        return mouseX >= config.splitsX && mouseX <= config.splitsX + w &&
               mouseY >= config.splitsY && mouseY <= config.splitsY + h;
    }

    private int bloodCampWidth() {
        int w = 0;
        for (String s : BLOOD_SAMPLE) w = Math.max(w, this.font.width(s));
        return (int)(w * TeslaMapsConfig.get().bloodCampScale);
    }

    private int bloodCampHeight() {
        return (int)(BLOOD_SAMPLE.length * 10 * TeslaMapsConfig.get().bloodCampScale);
    }

    private boolean isMouseOverBloodCamp(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = bloodCampWidth();
        int h = bloodCampHeight();
        return mouseX >= config.bloodCampX && mouseX <= config.bloodCampX + w &&
               mouseY >= config.bloodCampY && mouseY <= config.bloodCampY + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (isMouseDown && !wasMouseDown) {
            if (isMouseOverBloodCamp(mouseX, mouseY)) {
                dragging = DragTarget.BLOOD_CAMP;
                dragOffsetX = mouseX - config.bloodCampX;
                dragOffsetY = mouseY - config.bloodCampY;
            } else if (isMouseOverSplits(mouseX, mouseY)) {
                dragging = DragTarget.SPLITS;
                dragOffsetX = mouseX - config.splitsX;
                dragOffsetY = mouseY - config.splitsY;
            } else if (isMouseOverBearSpawn(mouseX, mouseY)) {
                dragging = DragTarget.BEAR_SPAWN;
                dragOffsetX = mouseX - config.bearSpawnX;
                dragOffsetY = mouseY - config.bearSpawnY;
            } else if (isMouseOverSprinting(mouseX, mouseY)) {
                dragging = DragTarget.SPRINTING;
                dragOffsetX = mouseX - config.sprintingX;
                dragOffsetY = mouseY - config.sprintingY;
            } else if (isMouseOverDungeonTimers(mouseX, mouseY)) {
                dragging = DragTarget.DUNGEON_TIMERS;
                dragOffsetX = mouseX - config.dungeonTimersX;
                dragOffsetY = mouseY - config.dungeonTimersY;
            } else if (isMouseOverInvincibility(mouseX, mouseY)) {
                dragging = DragTarget.INVINCIBILITY;
                dragOffsetX = mouseX - config.invincibilityX;
                dragOffsetY = mouseY - config.invincibilityY;
            } else if (isMouseOverThornStun(mouseX, mouseY)) {
                dragging = DragTarget.THORN_STUN;
                dragOffsetX = mouseX - config.thornStunX;
                dragOffsetY = mouseY - config.thornStunY;
            } else if (isMouseOverSpiritBear(mouseX, mouseY)) {
                dragging = DragTarget.SPIRIT_BEAR;
                dragOffsetX = mouseX - config.spiritBearX;
                dragOffsetY = mouseY - config.spiritBearY;
            } else if (isMouseOverSlayerHud(mouseX, mouseY)) {
                dragging = DragTarget.SLAYER_HUD;
                dragOffsetX = mouseX - config.slayerHudX;
                dragOffsetY = mouseY - config.slayerHudY;
            } else if (isMouseOverMap(mouseX, mouseY)) {
                dragging = DragTarget.MAP;
                dragOffsetX = mouseX - config.mapX;
                dragOffsetY = mouseY - config.mapY;
            }
        }

        if (!isMouseDown && wasMouseDown && dragging != DragTarget.NONE) {
            dragging = DragTarget.NONE;
            TeslaMapsConfig.save();
        }

        wasMouseDown = isMouseDown;

        if (dragging == DragTarget.MAP) {
            int mapSize = getMapSize();
            int newX = Math.max(0, Math.min(this.width - mapSize, mouseX - dragOffsetX));
            int newY = Math.max(0, Math.min(this.height - mapSize, mouseY - dragOffsetY));
            config.mapX = newX;
            config.mapY = newY;
        } else if (dragging == DragTarget.SLAYER_HUD) {
            int newX = Math.max(0, Math.min(this.width - SLAYER_WIDTH, mouseX - dragOffsetX));
            int newY = Math.max(0, Math.min(this.height - SLAYER_HEIGHT, mouseY - dragOffsetY));
            config.slayerHudX = newX;
            config.slayerHudY = newY;
        } else if (dragging == DragTarget.BEAR_SPAWN) {
            int w = bearSpawnWidth();
            int h = bearSpawnHeight();
            config.bearSpawnX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.bearSpawnY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SPLITS) {
            int w = splitsWidth();
            int h = splitsHeight();
            config.splitsX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.splitsY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.BLOOD_CAMP) {
            int w = bloodCampWidth();
            int h = bloodCampHeight();
            config.bloodCampX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.bloodCampY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SPRINTING) {
            int w = sprintingWidth();
            int h = sprintingHeight();
            config.sprintingX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.sprintingY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.DUNGEON_TIMERS) {
            int w = dungeonTimersWidth(), h = dungeonTimersHeight();
            config.dungeonTimersX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.dungeonTimersY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.INVINCIBILITY) {
            int w = invincibilityWidth(), h = invincibilityHeight();
            config.invincibilityX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.invincibilityY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.THORN_STUN) {
            int w = thornStunWidth(), h = thornStunHeight();
            config.thornStunX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.thornStunY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SPIRIT_BEAR) {
            int w = spiritBearWidth(), h = spiritBearHeight();
            config.spiritBearX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.spiritBearY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);

        drawMapPreview(context, mouseX, mouseY);

        drawSlayerHudPreview(context, mouseX, mouseY);

        drawBearSpawnPreview(context, mouseX, mouseY);

        drawSplitsPreview(context, mouseX, mouseY);

        drawBloodCampPreview(context, mouseX, mouseY);

        drawSprintingPreview(context, mouseX, mouseY);

        drawDungeonTimersPreview(context, mouseX, mouseY);

        drawInvincibilityPreview(context, mouseX, mouseY);

        drawThornStunPreview(context, mouseX, mouseY);

        drawSpiritBearPreview(context, mouseX, mouseY);

        String instructions = dragging != DragTarget.NONE ? "Release to place" : "Drag elements to move them | Scroll on map to resize";
        int textWidth = this.font.width(instructions);
        context.fill(this.width / 2 - textWidth / 2 - 8, 8, this.width / 2 + textWidth / 2 + 8, 26, 0xC0000000);
        context.centeredText(this.font, instructions, this.width / 2, 12, 0xFFE0E0E0);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawMapPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
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
        if (hovering || dragging == DragTarget.MAP) {
            int borderColor = dragging == DragTarget.MAP ? 0xFF5865F2 : 0xFF888888;
            context.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY, borderColor);
            context.fill(mapX - 2, mapY + mapSize, mapX + mapSize + 2, mapY + mapSize + 2, borderColor);
            context.fill(mapX - 2, mapY, mapX, mapY + mapSize, borderColor);
            context.fill(mapX + mapSize, mapY, mapX + mapSize + 2, mapY + mapSize, borderColor);
        }

        context.text(this.font, "Map", mapX, mapY - 12, 0xFFFFFFFF);
    }

    private void drawSlayerHudPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.slayerHudX;
        int y = config.slayerHudY;
        float scale = config.slayerHudScale;
        int padding = 6;

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        matrices.translate(-x, -y);

        int centerX = x + SLAYER_WIDTH / 2;

        drawRoundedRect(context, x, y, SLAYER_WIDTH, SLAYER_HEIGHT, 4, 0xDD000000);

        int currentY = y + padding;

        String bossName = "Inferno Demonlord IV";
        int nameWidth = this.font.width(bossName);
        context.text(this.font, bossName, centerX - nameWidth / 2, currentY, 0xFFFF5555);
        currentY += 12;

        int barWidth = SLAYER_WIDTH - padding * 2;
        int barX = x + padding;
        int barHeight = 12;
        drawRoundedRect(context, barX, currentY, barWidth, barHeight, 2, 0xFF333333);
        drawRoundedRect(context, barX, currentY, (int)(barWidth * 0.7), barHeight, 2, 0xFF55FFFF);

        String hpText = "35M / 50M";
        int hpWidth = this.font.width(hpText);
        context.text(this.font, hpText, centerX - hpWidth / 2, currentY + 2, 0xFFFFFFFF);
        currentY += barHeight + 2;

        String phaseText = "CRYSTAL x2";
        int phaseWidth = this.font.width(phaseText);
        context.text(this.font, phaseText, centerX - phaseWidth / 2, currentY, 0xFF55FFFF);

        matrices.popMatrix();

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

        String label = String.format("Slayer HUD (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawBearSpawnPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.bearSpawnX;
        int y = config.bearSpawnY;
        float scale = config.bearSpawnScale;

        BearSpawnWarning.drawAlert(context, this.minecraft, x, y, scale);

        int w = bearSpawnWidth();
        int h = bearSpawnHeight();

        boolean hovering = isMouseOverBearSpawn(mouseX, mouseY);
        if (hovering || dragging == DragTarget.BEAR_SPAWN) {
            int borderColor = dragging == DragTarget.BEAR_SPAWN ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Bear Warning (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawSprintingPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.sprintingX;
        int y = config.sprintingY;
        float scale = config.sprintingScale;

        com.teslamaps.features.SprintingOverlay.draw(context, this.minecraft, x, y, scale);

        int w = sprintingWidth();
        int h = sprintingHeight();

        boolean hovering = isMouseOverSprinting(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SPRINTING) {
            int borderColor = dragging == DragTarget.SPRINTING ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Sprinting (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawDungeonTimersPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.dungeonTimersX, y = config.dungeonTimersY;
        float scale = config.dungeonTimersScale;
        com.teslamaps.features.DungeonTimers.draw(context, this.minecraft, x, y, scale);
        int w = dungeonTimersWidth(), h = dungeonTimersHeight();
        boolean hovering = isMouseOverDungeonTimers(mouseX, mouseY);
        if (hovering || dragging == DragTarget.DUNGEON_TIMERS) {
            int borderColor = dragging == DragTarget.DUNGEON_TIMERS ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Dungeon Timers (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawInvincibilityPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.invincibilityX, y = config.invincibilityY;
        float scale = config.invincibilityScale;
        com.teslamaps.features.DungeonTimers.drawInvincibility(context, this.minecraft, x, y, scale);
        int w = invincibilityWidth(), h = invincibilityHeight();
        boolean hovering = isMouseOverInvincibility(mouseX, mouseY);
        if (hovering || dragging == DragTarget.INVINCIBILITY) {
            int borderColor = dragging == DragTarget.INVINCIBILITY ? 0xFFFFD700 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Invincibility (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawThornStunPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.thornStunX, y = config.thornStunY;
        float scale = config.thornStunScale;
        com.teslamaps.features.ThornStunTimer.draw(context, this.minecraft, x, y, scale);
        int w = thornStunWidth(), h = thornStunHeight();
        boolean hovering = isMouseOverThornStun(mouseX, mouseY);
        if (hovering || dragging == DragTarget.THORN_STUN) {
            int borderColor = dragging == DragTarget.THORN_STUN ? 0xFFAA55FF : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Thorn Stun (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawSpiritBearPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.spiritBearX, y = config.spiritBearY;
        float scale = config.spiritBearScale;
        com.teslamaps.dungeon.puzzle.SpiritBearTimer.draw(context, this.minecraft, x, y, scale);
        int w = spiritBearWidth(), h = spiritBearHeight();
        boolean hovering = isMouseOverSpiritBear(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SPIRIT_BEAR) {
            int borderColor = dragging == DragTarget.SPIRIT_BEAR ? 0xFFAA55FF : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Spirit Bear (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawSplitsPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.splitsX;
        int y = config.splitsY;
        float scale = config.splitsScale;
        int nameCol = splitsNameCol();

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        for (int i = 0; i < SPLITS_SAMPLE.length; i++) {
            context.text(this.font, SPLITS_SAMPLE[i], 0, i * 9, 0xFFFFFFFF);
            context.text(this.font, SPLITS_SAMPLE_TIME, nameCol, i * 9, 0xFFFFFFFF);
        }
        matrices.popMatrix();

        int w = splitsWidth();
        int h = splitsHeight();
        boolean hovering = isMouseOverSplits(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SPLITS) {
            int borderColor = dragging == DragTarget.SPLITS ? 0xFF5865F2 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Splits (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawBloodCampPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.bloodCampX;
        int y = config.bloodCampY;
        float scale = config.bloodCampScale;

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        for (int i = 0; i < BLOOD_SAMPLE.length; i++) {
            context.text(this.font, BLOOD_SAMPLE[i], 0, i * 10, 0xFFFFFFFF);
        }
        matrices.popMatrix();

        int w = bloodCampWidth();
        int h = bloodCampHeight();
        boolean hovering = isMouseOverBloodCamp(mouseX, mouseY);
        if (hovering || dragging == DragTarget.BLOOD_CAMP) {
            int borderColor = dragging == DragTarget.BLOOD_CAMP ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Blood Camp (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawRoundedRect(GuiGraphicsExtractor context, int x, int y, int width, int height, int radius, int color) {
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

        if (isMouseOverBloodCamp((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.bloodCampScale = Math.min(3.0f, config.bloodCampScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.bloodCampScale = Math.max(0.5f, config.bloodCampScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSplits((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.splitsScale = Math.min(2.0f, config.splitsScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.splitsScale = Math.max(0.5f, config.splitsScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverBearSpawn((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.bearSpawnScale = Math.min(10.0f, config.bearSpawnScale + 0.5f);
            } else if (verticalAmount < 0) {
                config.bearSpawnScale = Math.max(0.5f, config.bearSpawnScale - 0.5f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSprinting((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.sprintingScale = Math.min(10.0f, config.sprintingScale + 0.5f);
            } else if (verticalAmount < 0) {
                config.sprintingScale = Math.max(0.5f, config.sprintingScale - 0.5f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverDungeonTimers((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.dungeonTimersScale = Math.min(5.0f, config.dungeonTimersScale + 0.1f);
            else if (verticalAmount < 0) config.dungeonTimersScale = Math.max(0.5f, config.dungeonTimersScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverInvincibility((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.invincibilityScale = Math.min(5.0f, config.invincibilityScale + 0.1f);
            else if (verticalAmount < 0) config.invincibilityScale = Math.max(0.5f, config.invincibilityScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverThornStun((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.thornStunScale = Math.min(5.0f, config.thornStunScale + 0.1f);
            else if (verticalAmount < 0) config.thornStunScale = Math.max(0.5f, config.thornStunScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSpiritBear((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.spiritBearScale = Math.min(5.0f, config.spiritBearScale + 0.1f);
            else if (verticalAmount < 0) config.spiritBearScale = Math.max(0.5f, config.spiritBearScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSlayerHud((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.slayerHudScale = Math.min(2.0f, config.slayerHudScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.slayerHudScale = Math.max(0.5f, config.slayerHudScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

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
    public void onClose() {
        TeslaMapsConfig.save();
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
