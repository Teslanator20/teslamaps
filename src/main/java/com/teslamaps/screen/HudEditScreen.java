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

    /** Which situation an element belongs to, so the editor can hide the rest. */
    private enum Ctx { ANY, DUNGEON, GARDEN, SLAYER, SAFARI, FORAGING }

    private boolean showAll = false;

    private boolean show(Ctx ctx) {
        if (showAll) return true;
        return switch (ctx) {
            case ANY -> true;
            case DUNGEON -> com.teslamaps.dungeon.DungeonManager.isInDungeon();
            case GARDEN -> com.teslamaps.utils.SkyblockUtils.isInGarden();
            case SLAYER -> com.teslamaps.slayer.SlayerHUD.isActive();
            case SAFARI -> com.teslamaps.safari.SafariData.inSafari();
            case FORAGING -> com.teslamaps.features.foraging.Foraging.onForagingIsland();
        };
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            showAll = !showAll;
            return true;
        }
        return super.keyPressed(event);
    }

    private enum DragTarget { NONE, MAP, SLAYER_HUD, SKILL_TRACKER, BEAR_SPAWN, SPLITS, BLOOD_CAMP, SPRINTING, DUNGEON_TIMERS, INVINCIBILITY, THORN_STUN, DRAGON_TIMER, SPIRIT_BEAR, KICKED, OVERKILL, PEST_ROUTINE, FARM_BAR, PEST_OVERLAY, FARM_PROFIT, SAFARI, HOTF_PERKS, LATHER, BOBBER, DEPLOYABLES, HOOK }

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
    private static final int SLAYER_HEIGHT = 52;

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

    private int skillTrackerWidth() {
        return (int)(com.teslamaps.features.SkillTracker.previewSize(this.font)[0] * TeslaMapsConfig.get().skillTrackerScale);
    }

    private int skillTrackerHeight() {
        return (int)(com.teslamaps.features.SkillTracker.previewSize(this.font)[1] * TeslaMapsConfig.get().skillTrackerScale);
    }

    private boolean isMouseOverSkillTracker(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float s = config.skillTrackerScale;
        int bx = config.skillTrackerX - (int)(4 * s), by = config.skillTrackerY - (int)(4 * s);
        return mouseX >= bx && mouseX <= bx + skillTrackerWidth() &&
               mouseY >= by && mouseY <= by + skillTrackerHeight();
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

    private int safariWidth() {
        return (int)(com.teslamaps.safari.SafariTracker.previewSize(this.font)[0] * TeslaMapsConfig.get().safariHudScale);
    }

    private int safariHeight() {
        return (int)(com.teslamaps.safari.SafariTracker.previewSize(this.font)[1] * TeslaMapsConfig.get().safariHudScale);
    }

    private boolean isMouseOverSafari(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = safariWidth();
        int h = safariHeight();
        return mouseX >= config.safariHudX && mouseX <= config.safariHudX + w &&
               mouseY >= config.safariHudY && mouseY <= config.safariHudY + h;
    }

    private int hotfPerksWidth() {
        return (int)(com.teslamaps.features.foraging.HotfPerkDisplay.previewSize(this.font)[0]
                * TeslaMapsConfig.get().hotfPerkDisplayScale);
    }

    private int hotfPerksHeight() {
        return (int)(com.teslamaps.features.foraging.HotfPerkDisplay.previewSize(this.font)[1]
                * TeslaMapsConfig.get().hotfPerkDisplayScale);
    }

    private boolean isMouseOverHotfPerks(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = hotfPerksWidth(), h = hotfPerksHeight();
        return mouseX >= config.hotfPerkDisplayX && mouseX <= config.hotfPerkDisplayX + w &&
               mouseY >= config.hotfPerkDisplayY && mouseY <= config.hotfPerkDisplayY + h;
    }

    private int hookWidth() {
        return (int)(this.font.width(com.teslamaps.features.FishingHookDisplay.SAMPLE_TEXT)
                * TeslaMapsConfig.get().hookDisplayScale);
    }

    private int hookHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().hookDisplayScale);
    }

    private boolean isMouseOverHook(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = hookWidth(), h = hookHeight();
        return mouseX >= config.hookDisplayX && mouseX <= config.hookDisplayX + w &&
               mouseY >= config.hookDisplayY && mouseY <= config.hookDisplayY + h;
    }

    private int deployablesWidth() {
        return (int)(com.teslamaps.features.Deployables.previewSize(this.font)[0]
                * TeslaMapsConfig.get().deployableScale);
    }

    private int deployablesHeight() {
        return (int)(com.teslamaps.features.Deployables.previewSize(this.font)[1]
                * TeslaMapsConfig.get().deployableScale);
    }

    private boolean isMouseOverDeployables(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = deployablesWidth(), h = deployablesHeight();
        return mouseX >= config.deployableX && mouseX <= config.deployableX + w &&
               mouseY >= config.deployableY && mouseY <= config.deployableY + h;
    }

    private int bobberWidth() {
        return (int)(this.font.width(com.teslamaps.features.FishingBobberTimer.SAMPLE_TEXT)
                * TeslaMapsConfig.get().bobberTimerScale);
    }

    private int bobberHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().bobberTimerScale);
    }

    private boolean isMouseOverBobber(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = bobberWidth(), h = bobberHeight();
        return mouseX >= config.bobberTimerX && mouseX <= config.bobberTimerX + w &&
               mouseY >= config.bobberTimerY && mouseY <= config.bobberTimerY + h;
    }

    private int latherWidth() {
        return (int)(com.teslamaps.features.foraging.LatherOverlay.previewSize(this.font)[0]
                * TeslaMapsConfig.get().latherOverlayScale);
    }

    private int latherHeight() {
        return (int)(com.teslamaps.features.foraging.LatherOverlay.previewSize(this.font)[1]
                * TeslaMapsConfig.get().latherOverlayScale);
    }

    private boolean isMouseOverLather(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = latherWidth(), h = latherHeight();
        return mouseX >= config.latherOverlayX && mouseX <= config.latherOverlayX + w &&
               mouseY >= config.latherOverlayY && mouseY <= config.latherOverlayY + h;
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

    private int dragonTimerWidth() {
        return (int)(this.font.width(com.teslamaps.dungeon.WitherDragons.hudSample()) * TeslaMapsConfig.get().witherDragonHudScale);
    }
    private int dragonTimerHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().witherDragonHudScale);
    }
    private boolean isMouseOverDragonTimer(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = dragonTimerWidth(), h = dragonTimerHeight();
        return mouseX >= config.witherDragonHudX && mouseX <= config.witherDragonHudX + w &&
               mouseY >= config.witherDragonHudY && mouseY <= config.witherDragonHudY + h;
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

    private int farmProfitWidth() {
        return com.teslamaps.features.FarmProfitOverlay.width(TeslaMapsConfig.get().farmProfitScale);
    }
    private int farmProfitHeight() {
        return com.teslamaps.features.FarmProfitOverlay.height(TeslaMapsConfig.get().farmProfitScale);
    }
    private boolean isMouseOverFarmProfit(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = farmProfitWidth(), h = farmProfitHeight();
        return mouseX >= config.farmProfitX && mouseX <= config.farmProfitX + w &&
               mouseY >= config.farmProfitY && mouseY <= config.farmProfitY + h;
    }

    private int pestOverlayWidth() {
        return com.teslamaps.features.PestOverlay.width(TeslaMapsConfig.get().pestOverlayScale);
    }
    private int pestOverlayHeight() {
        return com.teslamaps.features.PestOverlay.height(TeslaMapsConfig.get().pestOverlayScale);
    }
    private boolean isMouseOverPestOverlay(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = pestOverlayWidth(), h = pestOverlayHeight();
        return mouseX >= config.pestOverlayX && mouseX <= config.pestOverlayX + w &&
               mouseY >= config.pestOverlayY && mouseY <= config.pestOverlayY + h;
    }

    private int farmBarWidth() {
        return com.teslamaps.features.FarmProgress.barWidth(TeslaMapsConfig.get().farmBarScale);
    }
    private int farmBarHeight() {
        return com.teslamaps.features.FarmProgress.barHeight(TeslaMapsConfig.get().farmBarScale);
    }
    private boolean isMouseOverFarmBar(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = farmBarWidth(), h = farmBarHeight();
        return mouseX >= config.farmBarX && mouseX <= config.farmBarX + w &&
               mouseY >= config.farmBarY && mouseY <= config.farmBarY + h;
    }

    private int pestRoutineWidth() {
        return com.teslamaps.features.PestRoutine.panelWidth(TeslaMapsConfig.get().pestRoutineScale);
    }
    private int pestRoutineHeight() {
        return com.teslamaps.features.PestRoutine.panelHeight(TeslaMapsConfig.get().pestRoutineScale);
    }
    private boolean isMouseOverPestRoutine(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = pestRoutineWidth(), h = pestRoutineHeight();
        return mouseX >= config.pestRoutineX && mouseX <= config.pestRoutineX + w &&
               mouseY >= config.pestRoutineY && mouseY <= config.pestRoutineY + h;
    }

    private int overkillWidth() {
        return (int)(this.font.width("Chickens: 00") * TeslaMapsConfig.get().overkillScale);
    }
    private int overkillHeight() {
        return (int)(this.font.lineHeight * 7 * TeslaMapsConfig.get().overkillScale);
    }
    private boolean isMouseOverOverkill(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = overkillWidth(), h = overkillHeight();
        return mouseX >= config.overkillX && mouseX <= config.overkillX + w &&
               mouseY >= config.overkillY && mouseY <= config.overkillY + h;
    }

    private int kickedWidth() {
        return (int)(this.font.width(com.teslamaps.features.KickedTimer.SAMPLE_TEXT) * TeslaMapsConfig.get().kickedScale);
    }
    private int kickedHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().kickedScale);
    }
    private boolean isMouseOverKicked(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = kickedWidth(), h = kickedHeight();
        return mouseX >= config.kickedX && mouseX <= config.kickedX + w &&
               mouseY >= config.kickedY && mouseY <= config.kickedY + h;
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
            } else if (isMouseOverSafari(mouseX, mouseY)) {
                dragging = DragTarget.SAFARI;
                dragOffsetX = mouseX - config.safariHudX;
                dragOffsetY = mouseY - config.safariHudY;
            } else if (isMouseOverHook(mouseX, mouseY)) {
                dragging = DragTarget.HOOK;
                dragOffsetX = mouseX - config.hookDisplayX;
                dragOffsetY = mouseY - config.hookDisplayY;
            } else if (isMouseOverDeployables(mouseX, mouseY)) {
                dragging = DragTarget.DEPLOYABLES;
                dragOffsetX = mouseX - config.deployableX;
                dragOffsetY = mouseY - config.deployableY;
            } else if (isMouseOverBobber(mouseX, mouseY)) {
                dragging = DragTarget.BOBBER;
                dragOffsetX = mouseX - config.bobberTimerX;
                dragOffsetY = mouseY - config.bobberTimerY;
            } else if (isMouseOverLather(mouseX, mouseY)) {
                dragging = DragTarget.LATHER;
                dragOffsetX = mouseX - config.latherOverlayX;
                dragOffsetY = mouseY - config.latherOverlayY;
            } else if (isMouseOverHotfPerks(mouseX, mouseY)) {
                dragging = DragTarget.HOTF_PERKS;
                dragOffsetX = mouseX - config.hotfPerkDisplayX;
                dragOffsetY = mouseY - config.hotfPerkDisplayY;
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
            } else if (isMouseOverDragonTimer(mouseX, mouseY)) {
                dragging = DragTarget.DRAGON_TIMER;
                dragOffsetX = mouseX - config.witherDragonHudX;
                dragOffsetY = mouseY - config.witherDragonHudY;
            } else if (isMouseOverSpiritBear(mouseX, mouseY)) {
                dragging = DragTarget.SPIRIT_BEAR;
                dragOffsetX = mouseX - config.spiritBearX;
                dragOffsetY = mouseY - config.spiritBearY;
            } else if (isMouseOverKicked(mouseX, mouseY)) {
                dragging = DragTarget.KICKED;
                dragOffsetX = mouseX - config.kickedX;
                dragOffsetY = mouseY - config.kickedY;
            } else if (isMouseOverOverkill(mouseX, mouseY)) {
                dragging = DragTarget.OVERKILL;
                dragOffsetX = mouseX - config.overkillX;
                dragOffsetY = mouseY - config.overkillY;
            } else if (isMouseOverPestRoutine(mouseX, mouseY)) {
                dragging = DragTarget.PEST_ROUTINE;
                dragOffsetX = mouseX - config.pestRoutineX;
                dragOffsetY = mouseY - config.pestRoutineY;
            } else if (isMouseOverFarmBar(mouseX, mouseY)) {
                dragging = DragTarget.FARM_BAR;
                dragOffsetX = mouseX - config.farmBarX;
                dragOffsetY = mouseY - config.farmBarY;
            } else if (isMouseOverPestOverlay(mouseX, mouseY)) {
                dragging = DragTarget.PEST_OVERLAY;
                dragOffsetX = mouseX - config.pestOverlayX;
                dragOffsetY = mouseY - config.pestOverlayY;
            } else if (isMouseOverFarmProfit(mouseX, mouseY)) {
                dragging = DragTarget.FARM_PROFIT;
                dragOffsetX = mouseX - config.farmProfitX;
                dragOffsetY = mouseY - config.farmProfitY;
            } else if (isMouseOverSkillTracker(mouseX, mouseY)) {
                dragging = DragTarget.SKILL_TRACKER;
                dragOffsetX = mouseX - config.skillTrackerX;
                dragOffsetY = mouseY - config.skillTrackerY;
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
        } else if (dragging == DragTarget.SAFARI) {
            int w = safariWidth();
            int h = safariHeight();
            config.safariHudX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.safariHudY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.HOOK) {
            int w = hookWidth(), h = hookHeight();
            config.hookDisplayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.hookDisplayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.DEPLOYABLES) {
            int w = deployablesWidth(), h = deployablesHeight();
            config.deployableX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.deployableY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.BOBBER) {
            int w = bobberWidth(), h = bobberHeight();
            config.bobberTimerX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.bobberTimerY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.LATHER) {
            int w = latherWidth(), h = latherHeight();
            config.latherOverlayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.latherOverlayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.HOTF_PERKS) {
            int w = hotfPerksWidth(), h = hotfPerksHeight();
            config.hotfPerkDisplayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.hotfPerkDisplayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
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
        } else if (dragging == DragTarget.DRAGON_TIMER) {
            int w = dragonTimerWidth(), h = dragonTimerHeight();
            config.witherDragonHudX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.witherDragonHudY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SPIRIT_BEAR) {
            int w = spiritBearWidth(), h = spiritBearHeight();
            config.spiritBearX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.spiritBearY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.KICKED) {
            int w = kickedWidth(), h = kickedHeight();
            config.kickedX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.kickedY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.OVERKILL) {
            int w = overkillWidth(), h = overkillHeight();
            config.overkillX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.overkillY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PEST_ROUTINE) {
            int w = pestRoutineWidth(), h = pestRoutineHeight();
            config.pestRoutineX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.pestRoutineY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.FARM_BAR) {
            int w = farmBarWidth(), h = farmBarHeight();
            config.farmBarX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.farmBarY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PEST_OVERLAY) {
            int w = pestOverlayWidth(), h = pestOverlayHeight();
            config.pestOverlayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.pestOverlayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.FARM_PROFIT) {
            int w = farmProfitWidth(), h = farmProfitHeight();
            config.farmProfitX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.farmProfitY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SKILL_TRACKER) {
            int w = skillTrackerWidth(), h = skillTrackerHeight();
            config.skillTrackerX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.skillTrackerY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);

        if (show(Ctx.DUNGEON)) {
            drawMapPreview(context, mouseX, mouseY);
            drawSplitsPreview(context, mouseX, mouseY);
            drawBloodCampPreview(context, mouseX, mouseY);
            drawDungeonTimersPreview(context, mouseX, mouseY);
            drawInvincibilityPreview(context, mouseX, mouseY);
            drawThornStunPreview(context, mouseX, mouseY);
            drawDragonTimerPreview(context, mouseX, mouseY);
            drawSpiritBearPreview(context, mouseX, mouseY);
            drawBearSpawnPreview(context, mouseX, mouseY);
            drawOverkillPreview(context, mouseX, mouseY);
        }

        if (show(Ctx.SLAYER)) {
            drawSlayerHudPreview(context, mouseX, mouseY);
        }

        drawSkillTrackerPreview(context, mouseX, mouseY);

        if (show(Ctx.GARDEN)) {
            drawPestRoutinePreview(context, mouseX, mouseY);
            drawFarmBarPreview(context, mouseX, mouseY);
            drawPestOverlayPreview(context, mouseX, mouseY);
            drawFarmProfitPreview(context, mouseX, mouseY);
        }

        if (show(Ctx.SAFARI)) drawSafariPreview(context, mouseX, mouseY);
        if (show(Ctx.FORAGING)) {
            drawHotfPerksPreview(context, mouseX, mouseY);
            drawLatherPreview(context, mouseX, mouseY);
        }

        drawBobberPreview(context, mouseX, mouseY);
        drawDeployablesPreview(context, mouseX, mouseY);
        drawHookPreview(context, mouseX, mouseY);
        drawSprintingPreview(context, mouseX, mouseY);
        drawKickedPreview(context, mouseX, mouseY);

        String instructions = dragging != DragTarget.NONE
                ? "Release to place"
                : (showAll
                        ? "Showing every element | TAB for current situation only"
                        : "Drag to move | Scroll to resize | TAB shows all elements");
        int textWidth = this.font.width(instructions);
        com.teslamaps.render.gui.TeslaGui.hudPanel(context,
                this.width / 2f - textWidth / 2f - 8, 8, textWidth + 16, 18, 9);
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

        com.teslamaps.render.gui.HudStyle.panel(context, x, y, SLAYER_WIDTH, SLAYER_HEIGHT, 4);

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

    private void drawSafariPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.safariHudX;
        int y = config.safariHudY;
        float scale = config.safariHudScale;

        com.teslamaps.safari.SafariTracker.draw(context, this.minecraft, x, y, scale);

        int w = safariWidth();
        int h = safariHeight();

        boolean hovering = isMouseOverSafari(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SAFARI) {
            int borderColor = dragging == DragTarget.SAFARI ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Critter Safari (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawHookPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.hookDisplayX, y = config.hookDisplayY;
        float scale = config.hookDisplayScale;

        com.teslamaps.features.FishingHookDisplay.draw(context, this.minecraft, x, y, scale);

        int w = hookWidth(), h = hookHeight();
        boolean hovering = isMouseOverHook(mouseX, mouseY);
        if (hovering || dragging == DragTarget.HOOK) {
            int borderColor = dragging == DragTarget.HOOK ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Hook Timer (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawDeployablesPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.deployableX, y = config.deployableY;
        float scale = config.deployableScale;

        com.teslamaps.features.Deployables.draw(context, this.minecraft, x, y, scale);

        int w = deployablesWidth(), h = deployablesHeight();
        boolean hovering = isMouseOverDeployables(mouseX, mouseY);
        if (hovering || dragging == DragTarget.DEPLOYABLES) {
            int borderColor = dragging == DragTarget.DEPLOYABLES ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Deployables (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawBobberPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.bobberTimerX, y = config.bobberTimerY;
        float scale = config.bobberTimerScale;

        com.teslamaps.features.FishingBobberTimer.draw(context, this.minecraft, x, y, scale);

        int w = bobberWidth(), h = bobberHeight();
        boolean hovering = isMouseOverBobber(mouseX, mouseY);
        if (hovering || dragging == DragTarget.BOBBER) {
            int borderColor = dragging == DragTarget.BOBBER ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Bobber Timer (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawLatherPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.latherOverlayX, y = config.latherOverlayY;
        float scale = config.latherOverlayScale;

        com.teslamaps.features.foraging.LatherOverlay.draw(context, this.minecraft, x, y, scale);

        int w = latherWidth(), h = latherHeight();
        boolean hovering = isMouseOverLather(mouseX, mouseY);
        if (hovering || dragging == DragTarget.LATHER) {
            int borderColor = dragging == DragTarget.LATHER ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Honeycomb Lather (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawHotfPerksPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.hotfPerkDisplayX, y = config.hotfPerkDisplayY;
        float scale = config.hotfPerkDisplayScale;

        com.teslamaps.features.foraging.HotfPerkDisplay.draw(context, this.minecraft, x, y, scale);

        int w = hotfPerksWidth(), h = hotfPerksHeight();
        boolean hovering = isMouseOverHotfPerks(mouseX, mouseY);
        if (hovering || dragging == DragTarget.HOTF_PERKS) {
            int borderColor = dragging == DragTarget.HOTF_PERKS ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("HotF Perks (%.1fx)", scale);
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

    private void drawDragonTimerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.witherDragonHudX, y = config.witherDragonHudY;
        float scale = config.witherDragonHudScale;
        com.teslamaps.dungeon.WitherDragons.drawHud(context, this.minecraft, x, y, scale,
                com.teslamaps.dungeon.WitherDragons.hudSample());
        int w = dragonTimerWidth(), h = dragonTimerHeight();
        boolean hovering = isMouseOverDragonTimer(mouseX, mouseY);
        if (hovering || dragging == DragTarget.DRAGON_TIMER) {
            int borderColor = dragging == DragTarget.DRAGON_TIMER ? 0xFFAA55FF : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Dragon Timer (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
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

    private void drawFarmProfitPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.farmProfitX, y = config.farmProfitY;
        float scale = config.farmProfitScale;
        com.teslamaps.features.FarmProfitOverlay.draw(context, this.minecraft, x, y, scale);
        int w = farmProfitWidth(), h = farmProfitHeight();
        boolean hovering = isMouseOverFarmProfit(mouseX, mouseY);
        if (hovering || dragging == DragTarget.FARM_PROFIT) {
            int borderColor = dragging == DragTarget.FARM_PROFIT ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Profit (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawPestOverlayPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.pestOverlayX, y = config.pestOverlayY;
        float scale = config.pestOverlayScale;
        com.teslamaps.features.PestOverlay.draw(context, this.minecraft, x, y, scale);
        int w = pestOverlayWidth(), h = pestOverlayHeight();
        boolean hovering = isMouseOverPestOverlay(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PEST_OVERLAY) {
            int borderColor = dragging == DragTarget.PEST_OVERLAY ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Pests (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawFarmBarPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.farmBarX, y = config.farmBarY;
        float scale = config.farmBarScale;
        com.teslamaps.features.FarmProgress.draw(context, this.minecraft, x, y, scale);
        int w = farmBarWidth(), h = farmBarHeight();
        boolean hovering = isMouseOverFarmBar(mouseX, mouseY);
        if (hovering || dragging == DragTarget.FARM_BAR) {
            int borderColor = dragging == DragTarget.FARM_BAR ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Farm Bar (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawPestRoutinePreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.pestRoutineX, y = config.pestRoutineY;
        float scale = config.pestRoutineScale;
        com.teslamaps.features.PestRoutine.draw(context, this.minecraft, x, y, scale);
        int w = pestRoutineWidth(), h = pestRoutineHeight();
        boolean hovering = isMouseOverPestRoutine(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PEST_ROUTINE) {
            int borderColor = dragging == DragTarget.PEST_ROUTINE ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Pest Routine (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawOverkillPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.overkillX, y = config.overkillY;
        float scale = config.overkillScale;
        com.teslamaps.features.OverkillCounter.draw(context, this.minecraft, x, y, scale);
        int w = overkillWidth(), h = overkillHeight();
        boolean hovering = isMouseOverOverkill(mouseX, mouseY);
        if (hovering || dragging == DragTarget.OVERKILL) {
            int borderColor = dragging == DragTarget.OVERKILL ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Overkill (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawKickedPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.kickedX, y = config.kickedY;
        float scale = config.kickedScale;
        com.teslamaps.features.KickedTimer.draw(context, this.minecraft, x, y, scale);
        int w = kickedWidth(), h = kickedHeight();
        boolean hovering = isMouseOverKicked(mouseX, mouseY);
        if (hovering || dragging == DragTarget.KICKED) {
            int borderColor = dragging == DragTarget.KICKED ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Kicked (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawSkillTrackerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.skillTrackerX, y = config.skillTrackerY;
        float scale = config.skillTrackerScale;
        com.teslamaps.features.SkillTracker.drawPreview(context, this.minecraft, x, y, scale);
        int bx = x - (int)(4 * scale), by = y - (int)(4 * scale);
        int w = skillTrackerWidth(), h = skillTrackerHeight();
        boolean hovering = isMouseOverSkillTracker(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SKILL_TRACKER) {
            int borderColor = dragging == DragTarget.SKILL_TRACKER ? 0xFF30D158 : 0xFF888888;
            context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
            context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
            context.fill(bx - 2, by, bx, by + h, borderColor);
            context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
        }
        context.text(this.font, String.format("Skill Tracker (%.1fx)", scale), bx, by - 12, 0xFFFFFFFF);
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
        com.teslamaps.render.gui.TeslaGui.roundedRect(context, x, y, width, height, radius, color);
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

        if (isMouseOverHook((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.hookDisplayScale = Math.min(8.0f, config.hookDisplayScale + 0.2f);
            } else if (verticalAmount < 0) {
                config.hookDisplayScale = Math.max(0.5f, config.hookDisplayScale - 0.2f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverDeployables((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.deployableScale = Math.min(3.0f, config.deployableScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.deployableScale = Math.max(0.5f, config.deployableScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverBobber((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.bobberTimerScale = Math.min(3.0f, config.bobberTimerScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.bobberTimerScale = Math.max(0.5f, config.bobberTimerScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverLather((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.latherOverlayScale = Math.min(3.0f, config.latherOverlayScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.latherOverlayScale = Math.max(0.5f, config.latherOverlayScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverHotfPerks((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.hotfPerkDisplayScale = Math.min(3.0f, config.hotfPerkDisplayScale + 0.1f);
            } else if (verticalAmount < 0) {
                config.hotfPerkDisplayScale = Math.max(0.5f, config.hotfPerkDisplayScale - 0.1f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSafari((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.safariHudScale = Math.min(10.0f, config.safariHudScale + 0.5f);
            } else if (verticalAmount < 0) {
                config.safariHudScale = Math.max(0.5f, config.safariHudScale - 0.5f);
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

        if (isMouseOverDragonTimer((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.witherDragonHudScale = Math.min(5.0f, config.witherDragonHudScale + 0.1f);
            else if (verticalAmount < 0) config.witherDragonHudScale = Math.max(0.5f, config.witherDragonHudScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSpiritBear((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.spiritBearScale = Math.min(5.0f, config.spiritBearScale + 0.1f);
            else if (verticalAmount < 0) config.spiritBearScale = Math.max(0.5f, config.spiritBearScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverKicked((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.kickedScale = Math.min(5.0f, config.kickedScale + 0.1f);
            else if (verticalAmount < 0) config.kickedScale = Math.max(0.5f, config.kickedScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverOverkill((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.overkillScale = Math.min(5.0f, config.overkillScale + 0.1f);
            else if (verticalAmount < 0) config.overkillScale = Math.max(0.5f, config.overkillScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverPestRoutine((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.pestRoutineScale = Math.min(5.0f, config.pestRoutineScale + 0.1f);
            else if (verticalAmount < 0) config.pestRoutineScale = Math.max(0.5f, config.pestRoutineScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverFarmBar((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.farmBarScale = Math.min(5.0f, config.farmBarScale + 0.1f);
            else if (verticalAmount < 0) config.farmBarScale = Math.max(0.5f, config.farmBarScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverPestOverlay((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.pestOverlayScale = Math.min(5.0f, config.pestOverlayScale + 0.1f);
            else if (verticalAmount < 0) config.pestOverlayScale = Math.max(0.5f, config.pestOverlayScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverFarmProfit((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.farmProfitScale = Math.min(5.0f, config.farmProfitScale + 0.1f);
            else if (verticalAmount < 0) config.farmProfitScale = Math.max(0.5f, config.farmProfitScale - 0.1f);
            TeslaMapsConfig.save();
            return true;
        }

        if (isMouseOverSkillTracker((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.skillTrackerScale = Math.min(3.0f, config.skillTrackerScale + 0.1f);
            else if (verticalAmount < 0) config.skillTrackerScale = Math.max(0.5f, config.skillTrackerScale - 0.1f);
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
