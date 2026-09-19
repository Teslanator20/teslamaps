package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.BearSpawnWarning;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean wasMouseDown = false;

    /** Which situation an element belongs to, so the editor can hide the rest. */
    private enum Ctx { ANY, DUNGEON, GARDEN, SAFARI, FORAGING, MINING, THE_END }

    private boolean showAll = false;

    /** Elements drawn this frame. Anything not in here cannot be hovered or dragged either. */
    private final java.util.EnumSet<DragTarget> visible = java.util.EnumSet.noneOf(DragTarget.class);

    /**
     * An element only shows up here when it would show up in game: right situation, section master
     * on, own toggle on. TAB still reveals everything.
     */
    private void mark(DragTarget target, Ctx ctx, String section, boolean enabled) {
        // TAB drops the situation check only, a switched off element stays hidden either way
        boolean on = enabled && TeslaMapsConfig.get().section(section) && (showAll || show(ctx));
        if (on) visible.add(target); else visible.remove(target);
    }

    private void updateVisible() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        mark(DragTarget.MAP, Ctx.DUNGEON, "Map", c.mapEnabled);
        mark(DragTarget.SPLITS, Ctx.DUNGEON, "Score & Splits", c.splitsEnabled);
        mark(DragTarget.ROOM_SPLITS, Ctx.DUNGEON, "Score & Splits", c.roomSplits);
        mark(DragTarget.ITEM_VALUE, Ctx.ANY, "Tooltips & Value", c.itemValueGui && c.itemValueFixedPos);
        mark(DragTarget.BLOOD_CAMP, Ctx.DUNGEON, "Blood Camp", true);
        mark(DragTarget.WATCHER_HUD, Ctx.DUNGEON, "Blood Camp", c.watcherHud);
        mark(DragTarget.DUNGEON_TIMERS, Ctx.DUNGEON, "Timers", true);
        mark(DragTarget.INVINCIBILITY, Ctx.DUNGEON, "Timers", c.invincibilityTimer);
        mark(DragTarget.THORN_STUN, Ctx.DUNGEON, "Puzzles & Terminals", c.thornStunTimer);
        mark(DragTarget.DRAGON_TIMER, Ctx.DUNGEON, "F7 / M7", c.witherDragons && c.witherDragonHud);
        mark(DragTarget.KICKED, Ctx.ANY, "Timers", c.kickedTimer && c.kickedCenterWarning);
        mark(DragTarget.SPIRIT_BEAR, Ctx.DUNGEON, "Puzzles & Terminals", c.spiritBearTimer);
        mark(DragTarget.BEAR_SPAWN, Ctx.DUNGEON, "Sounds", c.bearSpawnWarning);
        mark(DragTarget.OVERKILL, Ctx.DUNGEON, "Timers", c.overkillCounter);
        mark(DragTarget.SKILL_TRACKER, Ctx.ANY, "Skills", c.skillTracker);
        mark(DragTarget.SKILL_EXP, Ctx.ANY, "Skills", c.skillExpDisplay);
        mark(DragTarget.PET_OVERLAY, Ctx.ANY, "Skills", c.petOverlay);
        mark(DragTarget.BESTIARY, Ctx.ANY, "Combat", c.bestiaryTracker);
        mark(DragTarget.FLARE_MODE, Ctx.ANY, "Skills", c.flareMode);
        mark(DragTarget.PEST_ROUTINE, Ctx.GARDEN, "Garden", c.pestRoutineHud);
        mark(DragTarget.FARM_BAR, Ctx.GARDEN, "Garden", c.farmBar);
        mark(DragTarget.PEST_OVERLAY, Ctx.GARDEN, "Garden", c.pestOverlay);
        mark(DragTarget.FARM_PROFIT, Ctx.GARDEN, "Garden", c.farmProfit);
        mark(DragTarget.CHARMED, Ctx.GARDEN, "Garden", c.charmedVisitorList);
        mark(DragTarget.FEAST, Ctx.GARDEN, "Garden", c.feastSeasonDisplay);
        mark(DragTarget.SAFARI, Ctx.SAFARI, "Critter Safari", c.safariTracker && c.safariHud);
        mark(DragTarget.HOTF_PERKS, Ctx.FORAGING, "Foraging", c.hotfPerkDisplay);
        mark(DragTarget.LATHER, Ctx.FORAGING, "Foraging", c.latherOverlay);
        mark(DragTarget.LASSO, Ctx.FORAGING, "Foraging", c.lassoDisplay);
        mark(DragTarget.PICKAXE_ABILITY, Ctx.MINING, "Mining", c.pickaxeAbilityDisplay);
        mark(DragTarget.MINESHAFT_PITY, Ctx.MINING, "Mining", c.mineshaftPityDisplay);
        mark(DragTarget.PROTECTOR_STATUS, Ctx.THE_END, "The End", c.endProtector && c.endProtectorStatusHud);
        mark(DragTarget.PROTECTOR_LOOT, Ctx.THE_END, "The End", c.endProtector && c.endProtectorLootHud);
        mark(DragTarget.CONTEST_TIMERS, Ctx.ANY, "Contest Timers", c.contestTimer);
        mark(DragTarget.BOBBER, Ctx.ANY, "Fishing", c.bobberTimer);
        mark(DragTarget.HOOK, Ctx.ANY, "Fishing", c.hookDisplay);
        mark(DragTarget.DEPLOYABLES, Ctx.ANY, "Skills", c.deployableOverlay);
        mark(DragTarget.SPRINTING, Ctx.ANY, "Render", c.sprintingOverlay);
        mark(DragTarget.PERF_HUD, Ctx.ANY, "Interface", c.perfHud);
    }

    private boolean show(Ctx ctx) {
        if (showAll) return true;
        return switch (ctx) {
            case ANY -> true;
            case DUNGEON -> com.teslamaps.dungeon.DungeonManager.isInDungeon();
            case GARDEN -> com.teslamaps.utils.SkyblockUtils.isInGarden();
            case SAFARI -> com.teslamaps.safari.SafariData.inSafari();
            case FORAGING -> com.teslamaps.features.foraging.Foraging.onForagingIsland();
            case MINING -> com.teslamaps.features.mining.Mining.onMiningIsland();
            case THE_END -> com.teslamaps.features.theend.EndProtector.inArea();
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

    private enum DragTarget { NONE, MAP, SKILL_TRACKER, BEAR_SPAWN, SPLITS, ROOM_SPLITS, ITEM_VALUE, BLOOD_CAMP, WATCHER_HUD, SPRINTING, DUNGEON_TIMERS, INVINCIBILITY, THORN_STUN, DRAGON_TIMER, SPIRIT_BEAR, KICKED, PERF_HUD, OVERKILL, PEST_ROUTINE, FARM_BAR, PEST_OVERLAY, FARM_PROFIT, CHARMED, SAFARI, HOTF_PERKS, LATHER, CONTEST_TIMERS, BOBBER, DEPLOYABLES, HOOK, BESTIARY, PICKAXE_ABILITY, MINESHAFT_PITY, LASSO, FEAST, SKILL_EXP, PET_OVERLAY, FLARE_MODE, PROTECTOR_STATUS, PROTECTOR_LOOT }

    private static final String[] SPLITS_SAMPLE = {
            "§2Blood Open", "§bBlood Clear", "§dPortal Entry", "§5Maxor", "§1Total"
    };
    private static final String SPLITS_SAMPLE_TIME = "1:23.4";

    private static final String[] BLOOD_SAMPLE = { "§eReturn to Blood: ~23.0s", "§cThe Watcher: §f13§7/§f19" };

    private static final String[] ROOM_SPLITS_SAMPLE = {
            "§6§lRoom Splits", "§e▶ Water Board §f4.2s", "§7Trap §f31.8s", "§7Ice Fill §f12.1s"
    };
    private static final String[] WATCHER_SAMPLE = {
            "§c§lWatcher Stats:", "§dDialogue§r: 8.3s", "§dMove§r: 2.1s", "§dCamp§r: 24.7s"
    };
    private DragTarget dragging = DragTarget.NONE;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD Positions"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(new TeslaButton(this.width - 110, 6, 100, 22, "§fDone", this::onClose));
    

        ScreenFit.clamp(this); // keep every widget reachable at any gui scale

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


    private int skillExpX() {
        return com.teslamaps.features.SkillExpDisplay.x(this.minecraft, TeslaMapsConfig.get());
    }

    private int skillExpY() {
        return com.teslamaps.features.SkillExpDisplay.y(this.minecraft, TeslaMapsConfig.get());
    }

    private int skillExpWidth() {
        return com.teslamaps.features.SkillExpDisplay.width(this.minecraft, TeslaMapsConfig.get().skillExpDisplayScale);
    }

    private int skillExpHeight() {
        return com.teslamaps.features.SkillExpDisplay.height(TeslaMapsConfig.get().skillExpDisplayScale);
    }

    private boolean isMouseOverSkillExp(int mouseX, int mouseY) {
        int bx = skillExpX(), by = skillExpY();
        return mouseX >= bx && mouseX <= bx + skillExpWidth() &&
               mouseY >= by && mouseY <= by + skillExpHeight();
    }

    private int petOverlayWidth() {
        return (int) (com.teslamaps.features.pets.PetOverlay.previewSize(this.minecraft)[0] * TeslaMapsConfig.get().petOverlayScale);
    }

    private int petOverlayHeight() {
        return (int) (com.teslamaps.features.pets.PetOverlay.previewSize(this.minecraft)[1] * TeslaMapsConfig.get().petOverlayScale);
    }

    private boolean isMouseOverPetOverlay(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float s = config.petOverlayScale;
        int bx = config.petOverlayX - (int) (4 * s), by = config.petOverlayY - (int) (4 * s);
        return mouseX >= bx && mouseX <= bx + petOverlayWidth() &&
               mouseY >= by && mouseY <= by + petOverlayHeight();
    }

    private int flareModeWidth() {
        return (int)(com.teslamaps.features.hunting.FlareMode.previewSize(this.font)[0] * TeslaMapsConfig.get().flareModeScale);
    }

    private int flareModeHeight() {
        return (int)(com.teslamaps.features.hunting.FlareMode.previewSize(this.font)[1] * TeslaMapsConfig.get().flareModeScale);
    }

    private boolean isMouseOverFlareMode(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float s = config.flareModeScale;
        int bx = config.flareModeX - (int)(4 * s), by = config.flareModeY - (int)(4 * s);
        return mouseX >= bx && mouseX <= bx + flareModeWidth() &&
               mouseY >= by && mouseY <= by + flareModeHeight();
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

    private int bestiaryWidth() {
        return (int)(com.teslamaps.features.BestiaryTracker.previewSize(this.font)[0] * TeslaMapsConfig.get().bestiaryTrackerScale);
    }

    private int bestiaryHeight() {
        return (int)(com.teslamaps.features.BestiaryTracker.previewSize(this.font)[1] * TeslaMapsConfig.get().bestiaryTrackerScale);
    }

    private boolean isMouseOverBestiary(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float s = config.bestiaryTrackerScale;
        int bx = config.bestiaryTrackerX - (int)(4 * s), by = config.bestiaryTrackerY - (int)(4 * s);
        return mouseX >= bx && mouseX <= bx + bestiaryWidth() &&
               mouseY >= by && mouseY <= by + bestiaryHeight();
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

    private int pickaxeAbilityWidth() {
        return (int)(com.teslamaps.features.mining.PickaxeAbility.previewSize(this.font)[0]
                * TeslaMapsConfig.get().pickaxeAbilityScale);
    }

    private int pickaxeAbilityHeight() {
        return (int)(com.teslamaps.features.mining.PickaxeAbility.previewSize(this.font)[1]
                * TeslaMapsConfig.get().pickaxeAbilityScale);
    }

    private boolean isMouseOverPickaxeAbility(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = pickaxeAbilityWidth(), h = pickaxeAbilityHeight();
        return mouseX >= config.pickaxeAbilityX && mouseX <= config.pickaxeAbilityX + w &&
               mouseY >= config.pickaxeAbilityY && mouseY <= config.pickaxeAbilityY + h;
    }

    private int mineshaftPityWidth() {
        return (int)(com.teslamaps.features.mining.MineshaftPity.previewSize(this.font)[0]
                * TeslaMapsConfig.get().mineshaftPityScale);
    }

    private int mineshaftPityHeight() {
        return (int)(com.teslamaps.features.mining.MineshaftPity.previewSize(this.font)[1]
                * TeslaMapsConfig.get().mineshaftPityScale);
    }

    private boolean isMouseOverMineshaftPity(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = mineshaftPityWidth(), h = mineshaftPityHeight();
        return mouseX >= config.mineshaftPityX && mouseX <= config.mineshaftPityX + w &&
               mouseY >= config.mineshaftPityY && mouseY <= config.mineshaftPityY + h;
    }

    private int protectorStatusWidth() {
        return (int)(com.teslamaps.features.theend.ProtectorHud.previewSize(this.font)[0]
                * TeslaMapsConfig.get().protectorStatusScale);
    }

    private int protectorStatusHeight() {
        return (int)(com.teslamaps.features.theend.ProtectorHud.previewSize(this.font)[1]
                * TeslaMapsConfig.get().protectorStatusScale);
    }

    private boolean isMouseOverProtectorStatus(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = protectorStatusWidth(), h = protectorStatusHeight();
        return mouseX >= config.protectorStatusX && mouseX <= config.protectorStatusX + w &&
               mouseY >= config.protectorStatusY && mouseY <= config.protectorStatusY + h;
    }

    private int protectorLootWidth() {
        return (int)(com.teslamaps.features.theend.ProtectorLootHud.previewSize(this.font)[0]
                * TeslaMapsConfig.get().protectorLootScale);
    }

    private int protectorLootHeight() {
        return (int)(com.teslamaps.features.theend.ProtectorLootHud.previewSize(this.font)[1]
                * TeslaMapsConfig.get().protectorLootScale);
    }

    private boolean isMouseOverProtectorLoot(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = protectorLootWidth(), h = protectorLootHeight();
        return mouseX >= config.protectorLootX && mouseX <= config.protectorLootX + w &&
               mouseY >= config.protectorLootY && mouseY <= config.protectorLootY + h;
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

    private int lassoWidth() {
        return (int)(this.font.width(com.teslamaps.features.hunting.LassoDisplay.SAMPLE_TEXT)
                * TeslaMapsConfig.get().lassoDisplayScale);
    }

    private int lassoHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().lassoDisplayScale);
    }

    private boolean isMouseOverLasso(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = lassoWidth(), h = lassoHeight();
        return mouseX >= config.lassoDisplayX && mouseX <= config.lassoDisplayX + w &&
               mouseY >= config.lassoDisplayY && mouseY <= config.lassoDisplayY + h;
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

    private int contestTimersWidth() {
        return (int)(com.teslamaps.features.ContestTimers.previewSize(this.font)[0]
                * TeslaMapsConfig.get().contestTimerScale);
    }

    private int contestTimersHeight() {
        return (int)(com.teslamaps.features.ContestTimers.previewSize(this.font)[1]
                * TeslaMapsConfig.get().contestTimerScale);
    }

    private boolean isMouseOverContestTimers(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = contestTimersWidth(), h = contestTimersHeight();
        return mouseX >= config.contestTimerX && mouseX <= config.contestTimerX + w
                && mouseY >= config.contestTimerY && mouseY <= config.contestTimerY + h;
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

    private int charmedWidth() {
        return com.teslamaps.features.CharmedVisitors.previewWidth(TeslaMapsConfig.get().charmedVisitorScale);
    }
    private int charmedHeight() {
        return com.teslamaps.features.CharmedVisitors.previewHeight(TeslaMapsConfig.get().charmedVisitorScale);
    }
    private boolean isMouseOverCharmed(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = charmedWidth(), h = charmedHeight();
        return mouseX >= config.charmedVisitorX && mouseX <= config.charmedVisitorX + w &&
               mouseY >= config.charmedVisitorY && mouseY <= config.charmedVisitorY + h;
    }

    private int feastWidth() {
        return com.teslamaps.features.HarvestFeast.previewWidth(TeslaMapsConfig.get().feastSeasonScale);
    }
    private int feastHeight() {
        return com.teslamaps.features.HarvestFeast.previewHeight(TeslaMapsConfig.get().feastSeasonScale);
    }
    private boolean isMouseOverFeast(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = feastWidth(), h = feastHeight();
        return mouseX >= config.feastSeasonX && mouseX <= config.feastSeasonX + w &&
               mouseY >= config.feastSeasonY && mouseY <= config.feastSeasonY + h;
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
        return (int)(this.font.width(com.teslamaps.features.KickedTimer.SAMPLE_TEXT) * TeslaMapsConfig.get().kickedWarnScale);
    }
    private int kickedHeight() {
        return (int)(this.font.lineHeight * TeslaMapsConfig.get().kickedWarnScale);
    }
    private boolean isMouseOverKicked(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = kickedWidth(), h = kickedHeight();
        return mouseX >= config.kickedWarnX && mouseX <= config.kickedWarnX + w &&
               mouseY >= config.kickedWarnY && mouseY <= config.kickedWarnY + h;
    }

    private int perfHudWidth() {
        return (int)(com.teslamaps.features.PerformanceHud.width(this.minecraft) * TeslaMapsConfig.get().perfHudScale);
    }
    private int perfHudHeight() {
        return (int)(com.teslamaps.features.PerformanceHud.height(this.minecraft) * TeslaMapsConfig.get().perfHudScale);
    }
    private boolean isMouseOverPerfHud(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = perfHudWidth(), h = perfHudHeight();
        return mouseX >= config.perfHudX && mouseX <= config.perfHudX + w &&
               mouseY >= config.perfHudY && mouseY <= config.perfHudY + h;
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

    private int sampleWidth(String[] sample, float scale) {
        int w = 0;
        for (String line : sample) w = Math.max(w, this.font.width(line));
        return (int)(w * scale);
    }

    private int itemValueWidth() {
        return (int)(com.teslamaps.features.ItemValueOverlay.previewSize(this.font)[0]
                * TeslaMapsConfig.get().itemValueScale);
    }

    private int itemValueHeight() {
        return (int)(com.teslamaps.features.ItemValueOverlay.previewSize(this.font)[1]
                * TeslaMapsConfig.get().itemValueScale);
    }

    private boolean isMouseOverItemValue(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = itemValueWidth(), h = itemValueHeight();
        return mouseX >= config.itemValueX && mouseX <= config.itemValueX + w &&
               mouseY >= config.itemValueY && mouseY <= config.itemValueY + h;
    }

    private int roomSplitsWidth() {
        return sampleWidth(ROOM_SPLITS_SAMPLE, TeslaMapsConfig.get().roomSplitsScale);
    }

    private int roomSplitsHeight() {
        // header is 10 tall, the rows below are 9
        return (int)((10 + (ROOM_SPLITS_SAMPLE.length - 1) * 9) * TeslaMapsConfig.get().roomSplitsScale);
    }

    private boolean isMouseOverRoomSplits(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = roomSplitsWidth(), h = roomSplitsHeight();
        return mouseX >= config.roomSplitsX && mouseX <= config.roomSplitsX + w &&
               mouseY >= config.roomSplitsY && mouseY <= config.roomSplitsY + h;
    }

    private int watcherHudWidth() {
        return sampleWidth(WATCHER_SAMPLE, TeslaMapsConfig.get().watcherHudScale);
    }

    private int watcherHudHeight() {
        return (int)((10 + (WATCHER_SAMPLE.length - 1) * 9) * TeslaMapsConfig.get().watcherHudScale);
    }

    private boolean isMouseOverWatcherHud(int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int w = watcherHudWidth(), h = watcherHudHeight();
        return mouseX >= config.watcherHudX && mouseX <= config.watcherHudX + w &&
               mouseY >= config.watcherHudY && mouseY <= config.watcherHudY + h;
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
        updateVisible();

        if (isMouseDown && !wasMouseDown) {
            if (visible.contains(DragTarget.ITEM_VALUE) && isMouseOverItemValue(mouseX, mouseY)) {
                dragging = DragTarget.ITEM_VALUE;
                dragOffsetX = mouseX - config.itemValueX;
                dragOffsetY = mouseY - config.itemValueY;
            } else if (visible.contains(DragTarget.ROOM_SPLITS) && isMouseOverRoomSplits(mouseX, mouseY)) {
                dragging = DragTarget.ROOM_SPLITS;
                dragOffsetX = mouseX - config.roomSplitsX;
                dragOffsetY = mouseY - config.roomSplitsY;
            } else if (visible.contains(DragTarget.WATCHER_HUD) && isMouseOverWatcherHud(mouseX, mouseY)) {
                dragging = DragTarget.WATCHER_HUD;
                dragOffsetX = mouseX - config.watcherHudX;
                dragOffsetY = mouseY - config.watcherHudY;
            } else if (visible.contains(DragTarget.BLOOD_CAMP) && isMouseOverBloodCamp(mouseX, mouseY)) {
                dragging = DragTarget.BLOOD_CAMP;
                dragOffsetX = mouseX - config.bloodCampX;
                dragOffsetY = mouseY - config.bloodCampY;
            } else if (visible.contains(DragTarget.SPLITS) && isMouseOverSplits(mouseX, mouseY)) {
                dragging = DragTarget.SPLITS;
                dragOffsetX = mouseX - config.splitsX;
                dragOffsetY = mouseY - config.splitsY;
            } else if (visible.contains(DragTarget.BEAR_SPAWN) && isMouseOverBearSpawn(mouseX, mouseY)) {
                dragging = DragTarget.BEAR_SPAWN;
                dragOffsetX = mouseX - config.bearSpawnX;
                dragOffsetY = mouseY - config.bearSpawnY;
            } else if (visible.contains(DragTarget.SAFARI) && isMouseOverSafari(mouseX, mouseY)) {
                dragging = DragTarget.SAFARI;
                dragOffsetX = mouseX - config.safariHudX;
                dragOffsetY = mouseY - config.safariHudY;
            } else if (visible.contains(DragTarget.LASSO) && isMouseOverLasso(mouseX, mouseY)) {
                dragging = DragTarget.LASSO;
                dragOffsetX = mouseX - config.lassoDisplayX;
                dragOffsetY = mouseY - config.lassoDisplayY;
            } else if (visible.contains(DragTarget.HOOK) && isMouseOverHook(mouseX, mouseY)) {
                dragging = DragTarget.HOOK;
                dragOffsetX = mouseX - config.hookDisplayX;
                dragOffsetY = mouseY - config.hookDisplayY;
            } else if (visible.contains(DragTarget.DEPLOYABLES) && isMouseOverDeployables(mouseX, mouseY)) {
                dragging = DragTarget.DEPLOYABLES;
                dragOffsetX = mouseX - config.deployableX;
                dragOffsetY = mouseY - config.deployableY;
            } else if (visible.contains(DragTarget.BOBBER) && isMouseOverBobber(mouseX, mouseY)) {
                dragging = DragTarget.BOBBER;
                dragOffsetX = mouseX - config.bobberTimerX;
                dragOffsetY = mouseY - config.bobberTimerY;
            } else if (visible.contains(DragTarget.LATHER) && isMouseOverLather(mouseX, mouseY)) {
                dragging = DragTarget.LATHER;
                dragOffsetX = mouseX - config.latherOverlayX;
                dragOffsetY = mouseY - config.latherOverlayY;
            } else if (visible.contains(DragTarget.CONTEST_TIMERS) && isMouseOverContestTimers(mouseX, mouseY)) {
                dragging = DragTarget.CONTEST_TIMERS;
                dragOffsetX = mouseX - config.contestTimerX;
                dragOffsetY = mouseY - config.contestTimerY;
            } else if (visible.contains(DragTarget.HOTF_PERKS) && isMouseOverHotfPerks(mouseX, mouseY)) {
                dragging = DragTarget.HOTF_PERKS;
                dragOffsetX = mouseX - config.hotfPerkDisplayX;
                dragOffsetY = mouseY - config.hotfPerkDisplayY;
            } else if (visible.contains(DragTarget.PICKAXE_ABILITY) && isMouseOverPickaxeAbility(mouseX, mouseY)) {
                dragging = DragTarget.PICKAXE_ABILITY;
                dragOffsetX = mouseX - config.pickaxeAbilityX;
                dragOffsetY = mouseY - config.pickaxeAbilityY;
            } else if (visible.contains(DragTarget.MINESHAFT_PITY) && isMouseOverMineshaftPity(mouseX, mouseY)) {
                dragging = DragTarget.MINESHAFT_PITY;
                dragOffsetX = mouseX - config.mineshaftPityX;
                dragOffsetY = mouseY - config.mineshaftPityY;
            } else if (visible.contains(DragTarget.PROTECTOR_STATUS) && isMouseOverProtectorStatus(mouseX, mouseY)) {
                dragging = DragTarget.PROTECTOR_STATUS;
                dragOffsetX = mouseX - config.protectorStatusX;
                dragOffsetY = mouseY - config.protectorStatusY;
            } else if (visible.contains(DragTarget.PROTECTOR_LOOT) && isMouseOverProtectorLoot(mouseX, mouseY)) {
                dragging = DragTarget.PROTECTOR_LOOT;
                dragOffsetX = mouseX - config.protectorLootX;
                dragOffsetY = mouseY - config.protectorLootY;
            } else if (visible.contains(DragTarget.SPRINTING) && isMouseOverSprinting(mouseX, mouseY)) {
                dragging = DragTarget.SPRINTING;
                dragOffsetX = mouseX - config.sprintingX;
                dragOffsetY = mouseY - config.sprintingY;
            } else if (visible.contains(DragTarget.DUNGEON_TIMERS) && isMouseOverDungeonTimers(mouseX, mouseY)) {
                dragging = DragTarget.DUNGEON_TIMERS;
                dragOffsetX = mouseX - config.dungeonTimersX;
                dragOffsetY = mouseY - config.dungeonTimersY;
            } else if (visible.contains(DragTarget.INVINCIBILITY) && isMouseOverInvincibility(mouseX, mouseY)) {
                dragging = DragTarget.INVINCIBILITY;
                dragOffsetX = mouseX - config.invincibilityX;
                dragOffsetY = mouseY - config.invincibilityY;
            } else if (visible.contains(DragTarget.THORN_STUN) && isMouseOverThornStun(mouseX, mouseY)) {
                dragging = DragTarget.THORN_STUN;
                dragOffsetX = mouseX - config.thornStunX;
                dragOffsetY = mouseY - config.thornStunY;
            } else if (visible.contains(DragTarget.DRAGON_TIMER) && isMouseOverDragonTimer(mouseX, mouseY)) {
                dragging = DragTarget.DRAGON_TIMER;
                dragOffsetX = mouseX - config.witherDragonHudX;
                dragOffsetY = mouseY - config.witherDragonHudY;
            } else if (visible.contains(DragTarget.SPIRIT_BEAR) && isMouseOverSpiritBear(mouseX, mouseY)) {
                dragging = DragTarget.SPIRIT_BEAR;
                dragOffsetX = mouseX - config.spiritBearX;
                dragOffsetY = mouseY - config.spiritBearY;
            } else if (visible.contains(DragTarget.KICKED) && isMouseOverKicked(mouseX, mouseY)) {
                dragging = DragTarget.KICKED;
                dragOffsetX = mouseX - config.kickedWarnX;
                dragOffsetY = mouseY - config.kickedWarnY;
            } else if (visible.contains(DragTarget.PERF_HUD) && isMouseOverPerfHud(mouseX, mouseY)) {
                dragging = DragTarget.PERF_HUD;
                dragOffsetX = mouseX - config.perfHudX;
                dragOffsetY = mouseY - config.perfHudY;
            } else if (visible.contains(DragTarget.OVERKILL) && isMouseOverOverkill(mouseX, mouseY)) {
                dragging = DragTarget.OVERKILL;
                dragOffsetX = mouseX - config.overkillX;
                dragOffsetY = mouseY - config.overkillY;
            } else if (visible.contains(DragTarget.PEST_ROUTINE) && isMouseOverPestRoutine(mouseX, mouseY)) {
                dragging = DragTarget.PEST_ROUTINE;
                dragOffsetX = mouseX - config.pestRoutineX;
                dragOffsetY = mouseY - config.pestRoutineY;
            } else if (visible.contains(DragTarget.FARM_BAR) && isMouseOverFarmBar(mouseX, mouseY)) {
                dragging = DragTarget.FARM_BAR;
                dragOffsetX = mouseX - config.farmBarX;
                dragOffsetY = mouseY - config.farmBarY;
            } else if (visible.contains(DragTarget.PEST_OVERLAY) && isMouseOverPestOverlay(mouseX, mouseY)) {
                dragging = DragTarget.PEST_OVERLAY;
                dragOffsetX = mouseX - config.pestOverlayX;
                dragOffsetY = mouseY - config.pestOverlayY;
            } else if (visible.contains(DragTarget.FARM_PROFIT) && isMouseOverFarmProfit(mouseX, mouseY)) {
                dragging = DragTarget.FARM_PROFIT;
                dragOffsetX = mouseX - config.farmProfitX;
                dragOffsetY = mouseY - config.farmProfitY;
            } else if (visible.contains(DragTarget.CHARMED) && isMouseOverCharmed(mouseX, mouseY)) {
                dragging = DragTarget.CHARMED;
                dragOffsetX = mouseX - config.charmedVisitorX;
                dragOffsetY = mouseY - config.charmedVisitorY;
            } else if (visible.contains(DragTarget.FEAST) && isMouseOverFeast(mouseX, mouseY)) {
                dragging = DragTarget.FEAST;
                dragOffsetX = mouseX - config.feastSeasonX;
                dragOffsetY = mouseY - config.feastSeasonY;
            } else if (visible.contains(DragTarget.BESTIARY) && isMouseOverBestiary(mouseX, mouseY)) {
                dragging = DragTarget.BESTIARY;
                dragOffsetX = mouseX - config.bestiaryTrackerX;
                dragOffsetY = mouseY - config.bestiaryTrackerY;
            } else if (visible.contains(DragTarget.FLARE_MODE) && isMouseOverFlareMode(mouseX, mouseY)) {
                dragging = DragTarget.FLARE_MODE;
                dragOffsetX = mouseX - config.flareModeX;
                dragOffsetY = mouseY - config.flareModeY;
            } else if (visible.contains(DragTarget.PET_OVERLAY) && isMouseOverPetOverlay(mouseX, mouseY)) {
                dragging = DragTarget.PET_OVERLAY;
                dragOffsetX = mouseX - config.petOverlayX;
                dragOffsetY = mouseY - config.petOverlayY;
            } else if (visible.contains(DragTarget.SKILL_EXP) && isMouseOverSkillExp(mouseX, mouseY)) {
                dragging = DragTarget.SKILL_EXP;
                dragOffsetX = mouseX - skillExpX();
                dragOffsetY = mouseY - skillExpY();
            } else if (visible.contains(DragTarget.SKILL_TRACKER) && isMouseOverSkillTracker(mouseX, mouseY)) {
                dragging = DragTarget.SKILL_TRACKER;
                dragOffsetX = mouseX - config.skillTrackerX;
                dragOffsetY = mouseY - config.skillTrackerY;
            } else if (visible.contains(DragTarget.MAP) && isMouseOverMap(mouseX, mouseY)) {
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
        } else if (dragging == DragTarget.ITEM_VALUE) {
            int w = itemValueWidth(), h = itemValueHeight();
            config.itemValueX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.itemValueY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.ROOM_SPLITS) {
            int w = roomSplitsWidth(), h = roomSplitsHeight();
            config.roomSplitsX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.roomSplitsY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.WATCHER_HUD) {
            int w = watcherHudWidth(), h = watcherHudHeight();
            config.watcherHudX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.watcherHudY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
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
        } else if (dragging == DragTarget.LASSO) {
            int w = lassoWidth(), h = lassoHeight();
            config.lassoDisplayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.lassoDisplayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
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
        } else if (dragging == DragTarget.CONTEST_TIMERS) {
            int w = contestTimersWidth(), h = contestTimersHeight();
            config.contestTimerX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.contestTimerY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.HOTF_PERKS) {
            int w = hotfPerksWidth(), h = hotfPerksHeight();
            config.hotfPerkDisplayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.hotfPerkDisplayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PICKAXE_ABILITY) {
            int w = pickaxeAbilityWidth(), h = pickaxeAbilityHeight();
            config.pickaxeAbilityX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.pickaxeAbilityY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.MINESHAFT_PITY) {
            int w = mineshaftPityWidth(), h = mineshaftPityHeight();
            config.mineshaftPityX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.mineshaftPityY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PROTECTOR_STATUS) {
            int w = protectorStatusWidth(), h = protectorStatusHeight();
            config.protectorStatusX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.protectorStatusY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PROTECTOR_LOOT) {
            int w = protectorLootWidth(), h = protectorLootHeight();
            config.protectorLootX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.protectorLootY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
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
            config.kickedWarnX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.kickedWarnY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PERF_HUD) {
            int w = perfHudWidth(), h = perfHudHeight();
            config.perfHudX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.perfHudY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
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
        } else if (dragging == DragTarget.CHARMED) {
            int w = charmedWidth(), h = charmedHeight();
            config.charmedVisitorX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.charmedVisitorY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.FEAST) {
            int w = feastWidth(), h = feastHeight();
            config.feastSeasonX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.feastSeasonY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.PET_OVERLAY) {
            int w = petOverlayWidth(), h = petOverlayHeight();
            config.petOverlayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.petOverlayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SKILL_EXP) {
            int w = skillExpWidth(), h = skillExpHeight();
            config.skillExpDisplayX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.skillExpDisplayY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.SKILL_TRACKER) {
            int w = skillTrackerWidth(), h = skillTrackerHeight();
            config.skillTrackerX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.skillTrackerY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.BESTIARY) {
            int w = bestiaryWidth(), h = bestiaryHeight();
            config.bestiaryTrackerX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.bestiaryTrackerY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        } else if (dragging == DragTarget.FLARE_MODE) {
            int w = flareModeWidth(), h = flareModeHeight();
            config.flareModeX = Math.max(0, Math.min(this.width - w, mouseX - dragOffsetX));
            config.flareModeY = Math.max(0, Math.min(this.height - h, mouseY - dragOffsetY));
        }

        context.fill(0, 0, this.width, this.height, 0xC0000000);

        if (visible.contains(DragTarget.MAP)) drawMapPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.SPLITS)) drawSplitsPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.BLOOD_CAMP)) drawBloodCampPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.ROOM_SPLITS)) drawRoomSplitsPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.ITEM_VALUE)) drawItemValuePreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.WATCHER_HUD)) drawWatcherHudPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.DUNGEON_TIMERS)) drawDungeonTimersPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.INVINCIBILITY)) drawInvincibilityPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.THORN_STUN)) drawThornStunPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.DRAGON_TIMER)) drawDragonTimerPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.SPIRIT_BEAR)) drawSpiritBearPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.BEAR_SPAWN)) drawBearSpawnPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.OVERKILL)) drawOverkillPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.SKILL_TRACKER)) drawSkillTrackerPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.SKILL_EXP)) drawSkillExpPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PET_OVERLAY)) drawPetOverlayPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.BESTIARY)) drawBestiaryPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.FLARE_MODE)) drawFlareModePreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PEST_ROUTINE)) drawPestRoutinePreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.FARM_BAR)) drawFarmBarPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PEST_OVERLAY)) drawPestOverlayPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.FARM_PROFIT)) drawFarmProfitPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.CHARMED)) drawCharmedPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.FEAST)) drawFeastPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.SAFARI)) drawSafariPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.HOTF_PERKS)) drawHotfPerksPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.LATHER)) drawLatherPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PICKAXE_ABILITY)) drawPickaxeAbilityPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.MINESHAFT_PITY)) drawMineshaftPityPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PROTECTOR_STATUS)) drawProtectorStatusPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PROTECTOR_LOOT)) drawProtectorLootPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.CONTEST_TIMERS)) drawContestTimersPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.BOBBER)) drawBobberPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.HOOK)) drawHookPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.LASSO)) drawLassoPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.DEPLOYABLES)) drawDeployablesPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.SPRINTING)) drawSprintingPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.KICKED)) drawKickedPreview(context, mouseX, mouseY);
        if (visible.contains(DragTarget.PERF_HUD)) drawPerfHudPreview(context, mouseX, mouseY);

        String instructions = dragging != DragTarget.NONE
                ? "Release to place"
                : (showAll
                        ? "Showing every element | TAB for current situation only"
                        : "Drag to move | Scroll to resize | TAB shows all elements");
        int textWidth = this.font.width(instructions);
        com.teslamaps.render.gui.HudStyle.screenPanel(context,
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

        boolean hovering = visible.contains(DragTarget.MAP) && isMouseOverMap(mouseX, mouseY);
        if (hovering || dragging == DragTarget.MAP) {
            int borderColor = dragging == DragTarget.MAP ? 0xFF5865F2 : 0xFF888888;
            context.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY, borderColor);
            context.fill(mapX - 2, mapY + mapSize, mapX + mapSize + 2, mapY + mapSize + 2, borderColor);
            context.fill(mapX - 2, mapY, mapX, mapY + mapSize, borderColor);
            context.fill(mapX + mapSize, mapY, mapX + mapSize + 2, mapY + mapSize, borderColor);
        }

        context.text(this.font, "Map", mapX, mapY - 12, 0xFFFFFFFF);
    }

    private void drawBearSpawnPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.bearSpawnX;
        int y = config.bearSpawnY;
        float scale = config.bearSpawnScale;

        BearSpawnWarning.drawAlert(context, this.minecraft, x, y, scale);

        int w = bearSpawnWidth();
        int h = bearSpawnHeight();

        boolean hovering = visible.contains(DragTarget.BEAR_SPAWN) && isMouseOverBearSpawn(mouseX, mouseY);
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

        boolean hovering = visible.contains(DragTarget.SAFARI) && isMouseOverSafari(mouseX, mouseY);
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

    private void drawLassoPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.lassoDisplayX, y = config.lassoDisplayY;
        float scale = config.lassoDisplayScale;

        com.teslamaps.features.hunting.LassoDisplay.draw(context, this.minecraft, x, y, scale);

        int w = lassoWidth(), h = lassoHeight();
        boolean hovering = visible.contains(DragTarget.LASSO) && isMouseOverLasso(mouseX, mouseY);
        if (hovering || dragging == DragTarget.LASSO) {
            int borderColor = dragging == DragTarget.LASSO ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Lasso Display (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawHookPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.hookDisplayX, y = config.hookDisplayY;
        float scale = config.hookDisplayScale;

        com.teslamaps.features.FishingHookDisplay.draw(context, this.minecraft, x, y, scale);

        int w = hookWidth(), h = hookHeight();
        boolean hovering = visible.contains(DragTarget.HOOK) && isMouseOverHook(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.DEPLOYABLES) && isMouseOverDeployables(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.BOBBER) && isMouseOverBobber(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.LATHER) && isMouseOverLather(mouseX, mouseY);
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

    private void drawContestTimersPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.contestTimerX, y = config.contestTimerY;
        float scale = config.contestTimerScale;

        com.teslamaps.features.ContestTimers.draw(context, this.minecraft, x, y, scale);

        int w = contestTimersWidth(), h = contestTimersHeight();
        boolean hovering = visible.contains(DragTarget.CONTEST_TIMERS)
                && isMouseOverContestTimers(mouseX, mouseY);
        if (hovering || dragging == DragTarget.CONTEST_TIMERS) {
            int borderColor = dragging == DragTarget.CONTEST_TIMERS ? 0xFFFFAA00 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        context.text(this.font, String.format("Contest Timers (%.1fx)", scale),
                x, y - 12, 0xFFFFFFFF);
    }

    private void drawHotfPerksPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.hotfPerkDisplayX, y = config.hotfPerkDisplayY;
        float scale = config.hotfPerkDisplayScale;

        com.teslamaps.features.foraging.HotfPerkDisplay.draw(context, this.minecraft, x, y, scale);

        int w = hotfPerksWidth(), h = hotfPerksHeight();
        boolean hovering = visible.contains(DragTarget.HOTF_PERKS) && isMouseOverHotfPerks(mouseX, mouseY);
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

    private void drawPickaxeAbilityPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.pickaxeAbilityX, y = config.pickaxeAbilityY;
        float scale = config.pickaxeAbilityScale;

        com.teslamaps.features.mining.PickaxeAbility.draw(context, this.minecraft, x, y, scale);

        int w = pickaxeAbilityWidth(), h = pickaxeAbilityHeight();
        boolean hovering = visible.contains(DragTarget.PICKAXE_ABILITY) && isMouseOverPickaxeAbility(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PICKAXE_ABILITY) {
            int borderColor = dragging == DragTarget.PICKAXE_ABILITY ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Pickaxe Ability (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawMineshaftPityPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.mineshaftPityX, y = config.mineshaftPityY;
        float scale = config.mineshaftPityScale;

        com.teslamaps.features.mining.MineshaftPity.draw(context, this.minecraft, x, y, scale);

        int w = mineshaftPityWidth(), h = mineshaftPityHeight();
        boolean hovering = visible.contains(DragTarget.MINESHAFT_PITY) && isMouseOverMineshaftPity(mouseX, mouseY);
        if (hovering || dragging == DragTarget.MINESHAFT_PITY) {
            int borderColor = dragging == DragTarget.MINESHAFT_PITY ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        String label = String.format("Mineshaft Pity (%.1fx)", scale);
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawProtectorStatusPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.protectorStatusX, y = config.protectorStatusY;
        float scale = config.protectorStatusScale;

        com.teslamaps.features.theend.ProtectorHud.draw(context, this.minecraft, x, y, scale);

        int w = protectorStatusWidth(), h = protectorStatusHeight();
        boolean hovering = visible.contains(DragTarget.PROTECTOR_STATUS) && isMouseOverProtectorStatus(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PROTECTOR_STATUS) {
            int borderColor = dragging == DragTarget.PROTECTOR_STATUS ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        context.text(this.font, String.format("Protector Status (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawProtectorLootPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.protectorLootX, y = config.protectorLootY;
        float scale = config.protectorLootScale;

        com.teslamaps.features.theend.ProtectorLootHud.draw(context, this.minecraft, x, y, scale);

        int w = protectorLootWidth(), h = protectorLootHeight();
        boolean hovering = visible.contains(DragTarget.PROTECTOR_LOOT) && isMouseOverProtectorLoot(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PROTECTOR_LOOT) {
            int borderColor = dragging == DragTarget.PROTECTOR_LOOT ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }

        context.text(this.font, String.format("Protector Loot (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawSprintingPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.sprintingX;
        int y = config.sprintingY;
        float scale = config.sprintingScale;

        com.teslamaps.features.SprintingOverlay.draw(context, this.minecraft, x, y, scale);

        int w = sprintingWidth();
        int h = sprintingHeight();

        boolean hovering = visible.contains(DragTarget.SPRINTING) && isMouseOverSprinting(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.DUNGEON_TIMERS) && isMouseOverDungeonTimers(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.INVINCIBILITY) && isMouseOverInvincibility(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.THORN_STUN) && isMouseOverThornStun(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.DRAGON_TIMER) && isMouseOverDragonTimer(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.SPIRIT_BEAR) && isMouseOverSpiritBear(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.FARM_PROFIT) && isMouseOverFarmProfit(mouseX, mouseY);
        if (hovering || dragging == DragTarget.FARM_PROFIT) {
            int borderColor = dragging == DragTarget.FARM_PROFIT ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Profit (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawCharmedPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.charmedVisitorX, y = config.charmedVisitorY;
        float scale = config.charmedVisitorScale;
        com.teslamaps.features.CharmedVisitors.draw(context, this.minecraft, x, y, scale, true);
        int w = charmedWidth(), h = charmedHeight();
        boolean hovering = visible.contains(DragTarget.CHARMED) && isMouseOverCharmed(mouseX, mouseY);
        if (hovering || dragging == DragTarget.CHARMED) {
            int borderColor = dragging == DragTarget.CHARMED ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Charmed (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawFeastPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.feastSeasonX, y = config.feastSeasonY;
        float scale = config.feastSeasonScale;
        com.teslamaps.features.HarvestFeast.draw(context, this.minecraft, x, y, scale, true);
        int w = feastWidth(), h = feastHeight();
        boolean hovering = visible.contains(DragTarget.FEAST) && isMouseOverFeast(mouseX, mouseY);
        if (hovering || dragging == DragTarget.FEAST) {
            int borderColor = dragging == DragTarget.FEAST ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("In-Season (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawPestOverlayPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.pestOverlayX, y = config.pestOverlayY;
        float scale = config.pestOverlayScale;
        com.teslamaps.features.PestOverlay.draw(context, this.minecraft, x, y, scale);
        int w = pestOverlayWidth(), h = pestOverlayHeight();
        boolean hovering = visible.contains(DragTarget.PEST_OVERLAY) && isMouseOverPestOverlay(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.FARM_BAR) && isMouseOverFarmBar(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.PEST_ROUTINE) && isMouseOverPestRoutine(mouseX, mouseY);
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
        boolean hovering = visible.contains(DragTarget.OVERKILL) && isMouseOverOverkill(mouseX, mouseY);
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
        int x = config.kickedWarnX, y = config.kickedWarnY;
        float scale = config.kickedWarnScale;
        com.teslamaps.features.KickedTimer.draw(context, this.minecraft, x, y, scale);
        int w = kickedWidth(), h = kickedHeight();
        boolean hovering = visible.contains(DragTarget.KICKED) && isMouseOverKicked(mouseX, mouseY);
        if (hovering || dragging == DragTarget.KICKED) {
            int borderColor = dragging == DragTarget.KICKED ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Kick Warning (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawPerfHudPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.perfHudX, y = config.perfHudY;
        float scale = config.perfHudScale;
        com.teslamaps.features.PerformanceHud.draw(context, this.minecraft, x, y, scale);
        int w = perfHudWidth(), h = perfHudHeight();
        boolean hovering = visible.contains(DragTarget.PERF_HUD) && isMouseOverPerfHud(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PERF_HUD) {
            int borderColor = dragging == DragTarget.PERF_HUD ? 0xFFFF5555 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Performance (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawBestiaryPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.bestiaryTrackerX, y = config.bestiaryTrackerY;
        float scale = config.bestiaryTrackerScale;
        com.teslamaps.features.BestiaryTracker.drawPreview(context, this.minecraft, x, y, scale);
        int bx = x - (int)(4 * scale), by = y - (int)(4 * scale);
        int w = bestiaryWidth(), h = bestiaryHeight();
        boolean hovering = visible.contains(DragTarget.BESTIARY) && isMouseOverBestiary(mouseX, mouseY);
        if (hovering || dragging == DragTarget.BESTIARY) {
            int borderColor = dragging == DragTarget.BESTIARY ? 0xFF30D158 : 0xFF888888;
            context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
            context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
            context.fill(bx - 2, by, bx, by + h, borderColor);
            context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
        }
        context.text(this.font, String.format("Bestiary (%.1fx)", scale), bx, by - 12, 0xFFFFFFFF);
    }

    private void drawFlareModePreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.flareModeX, y = config.flareModeY;
        float scale = config.flareModeScale;
        com.teslamaps.features.hunting.FlareMode.drawPreview(context, this.minecraft, x, y, scale);
        int bx = x - (int)(4 * scale), by = y - (int)(4 * scale);
        int w = flareModeWidth(), h = flareModeHeight();
        boolean hovering = visible.contains(DragTarget.FLARE_MODE) && isMouseOverFlareMode(mouseX, mouseY);
        if (hovering || dragging == DragTarget.FLARE_MODE) {
            int borderColor = dragging == DragTarget.FLARE_MODE ? 0xFF30D158 : 0xFF888888;
            context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
            context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
            context.fill(bx - 2, by, bx, by + h, borderColor);
            context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
        }
        context.text(this.font, String.format("Flare Mode (%.1fx)", scale), bx, by - 12, 0xFFFFFFFF);
    }

    private void drawSkillTrackerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.skillTrackerX, y = config.skillTrackerY;
        float scale = config.skillTrackerScale;
        com.teslamaps.features.SkillTracker.drawPreview(context, this.minecraft, x, y, scale);
        int bx = x - (int)(4 * scale), by = y - (int)(4 * scale);
        int w = skillTrackerWidth(), h = skillTrackerHeight();
        boolean hovering = visible.contains(DragTarget.SKILL_TRACKER) && isMouseOverSkillTracker(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SKILL_TRACKER) {
            int borderColor = dragging == DragTarget.SKILL_TRACKER ? 0xFF30D158 : 0xFF888888;
            context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
            context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
            context.fill(bx - 2, by, bx, by + h, borderColor);
            context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
        }
        context.text(this.font, String.format("Skill Tracker (%.1fx)", scale), bx, by - 12, 0xFFFFFFFF);
    }

    private void drawSkillExpPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = skillExpX(), y = skillExpY();
        float scale = config.skillExpDisplayScale;
        com.teslamaps.features.SkillExpDisplay.drawPreview(context, this.minecraft, x, y, scale);
        int w = skillExpWidth(), h = skillExpHeight();
        boolean hovering = visible.contains(DragTarget.SKILL_EXP) && isMouseOverSkillExp(mouseX, mouseY);
        if (hovering || dragging == DragTarget.SKILL_EXP) {
            int borderColor = dragging == DragTarget.SKILL_EXP ? 0xFF30D158 : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, String.format("Skill XP (%.1fx)", scale), x, y - 12, 0xFFFFFFFF);
    }

    private void drawPetOverlayPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        int x = config.petOverlayX, y = config.petOverlayY;
        float scale = config.petOverlayScale;
        com.teslamaps.features.pets.PetOverlay.drawPreview(context, this.minecraft, x, y, scale);
        int bx = x - (int) (4 * scale), by = y - (int) (4 * scale);
        int w = petOverlayWidth(), h = petOverlayHeight();
        boolean hovering = visible.contains(DragTarget.PET_OVERLAY) && isMouseOverPetOverlay(mouseX, mouseY);
        if (hovering || dragging == DragTarget.PET_OVERLAY) {
            int borderColor = dragging == DragTarget.PET_OVERLAY ? 0xFF30D158 : 0xFF888888;
            context.fill(bx - 2, by - 2, bx + w + 2, by, borderColor);
            context.fill(bx - 2, by + h, bx + w + 2, by + h + 2, borderColor);
            context.fill(bx - 2, by, bx, by + h, borderColor);
            context.fill(bx + w, by, bx + w + 2, by + h, borderColor);
        }
        context.text(this.font, String.format("Pet (%.1fx)", scale), bx, by - 12, 0xFFFFFFFF);
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
        boolean hovering = visible.contains(DragTarget.SPLITS) && isMouseOverSplits(mouseX, mouseY);
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

    /** Both the room splits and the watcher HUD are a plain left-aligned text block. */
    private void drawTextBlockPreview(GuiGraphicsExtractor context, String[] sample, int x, int y,
                                      float scale, int w, int h, boolean hovering,
                                      DragTarget target, int dragColor, String label) {
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        int ly = 0;
        for (int i = 0; i < sample.length; i++) {
            context.text(this.font, sample[i], 0, ly, 0xFFFFFFFF);
            ly += i == 0 ? 10 : 9;
        }
        matrices.popMatrix();

        if (hovering || dragging == target) {
            int borderColor = dragging == target ? dragColor : 0xFF888888;
            context.fill(x - 2, y - 2, x + w + 2, y, borderColor);
            context.fill(x - 2, y + h, x + w + 2, y + h + 2, borderColor);
            context.fill(x - 2, y, x, y + h, borderColor);
            context.fill(x + w, y, x + w + 2, y + h, borderColor);
        }
        context.text(this.font, label, x, y - 12, 0xFFFFFFFF);
    }

    private void drawItemValuePreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.itemValueScale;
        drawTextBlockPreview(context,
                com.teslamaps.features.ItemValueOverlay.SAMPLE.toArray(new String[0]),
                config.itemValueX, config.itemValueY, scale,
                itemValueWidth(), itemValueHeight(),
                visible.contains(DragTarget.ITEM_VALUE) && isMouseOverItemValue(mouseX, mouseY),
                DragTarget.ITEM_VALUE, 0xFF55FF55,
                String.format("Item Value (%.1fx)", scale));
    }

    private void drawRoomSplitsPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.roomSplitsScale;
        drawTextBlockPreview(context, ROOM_SPLITS_SAMPLE, config.roomSplitsX, config.roomSplitsY, scale,
                roomSplitsWidth(), roomSplitsHeight(),
                visible.contains(DragTarget.ROOM_SPLITS) && isMouseOverRoomSplits(mouseX, mouseY),
                DragTarget.ROOM_SPLITS, 0xFFFFAA00,
                String.format("Room Splits (%.1fx)", scale));
    }

    private void drawWatcherHudPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        float scale = config.watcherHudScale;
        drawTextBlockPreview(context, WATCHER_SAMPLE, config.watcherHudX, config.watcherHudY, scale,
                watcherHudWidth(), watcherHudHeight(),
                visible.contains(DragTarget.WATCHER_HUD) && isMouseOverWatcherHud(mouseX, mouseY),
                DragTarget.WATCHER_HUD, 0xFFFF5555,
                String.format("Watcher Stats (%.1fx)", scale));
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
        boolean hovering = visible.contains(DragTarget.BLOOD_CAMP) && isMouseOverBloodCamp(mouseX, mouseY);
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

        if (visible.contains(DragTarget.ITEM_VALUE) && isMouseOverItemValue((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.itemValueScale = Math.min(2.0f, config.itemValueScale + 0.05f);
            else if (verticalAmount < 0) config.itemValueScale = Math.max(0.5f, config.itemValueScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }
        if (visible.contains(DragTarget.ROOM_SPLITS) && isMouseOverRoomSplits((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.roomSplitsScale = Math.min(2.0f, config.roomSplitsScale + 0.05f);
            else if (verticalAmount < 0) config.roomSplitsScale = Math.max(0.5f, config.roomSplitsScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }
        if (visible.contains(DragTarget.WATCHER_HUD) && isMouseOverWatcherHud((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.watcherHudScale = Math.min(2.0f, config.watcherHudScale + 0.05f);
            else if (verticalAmount < 0) config.watcherHudScale = Math.max(0.5f, config.watcherHudScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }
        if (visible.contains(DragTarget.BLOOD_CAMP) && isMouseOverBloodCamp((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.bloodCampScale = Math.min(3.0f, config.bloodCampScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.bloodCampScale = Math.max(0.5f, config.bloodCampScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.SPLITS) && isMouseOverSplits((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.splitsScale = Math.min(2.0f, config.splitsScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.splitsScale = Math.max(0.5f, config.splitsScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.BEAR_SPAWN) && isMouseOverBearSpawn((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.bearSpawnScale = Math.min(10.0f, config.bearSpawnScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.bearSpawnScale = Math.max(0.5f, config.bearSpawnScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.LASSO) && isMouseOverLasso((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.lassoDisplayScale = Math.min(8.0f, config.lassoDisplayScale + 0.2f);
            } else if (verticalAmount < 0) {
                config.lassoDisplayScale = Math.max(0.5f, config.lassoDisplayScale - 0.2f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.HOOK) && isMouseOverHook((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.hookDisplayScale = Math.min(8.0f, config.hookDisplayScale + 0.2f);
            } else if (verticalAmount < 0) {
                config.hookDisplayScale = Math.max(0.5f, config.hookDisplayScale - 0.2f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.DEPLOYABLES) && isMouseOverDeployables((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.deployableScale = Math.min(3.0f, config.deployableScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.deployableScale = Math.max(0.5f, config.deployableScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.BOBBER) && isMouseOverBobber((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.bobberTimerScale = Math.min(3.0f, config.bobberTimerScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.bobberTimerScale = Math.max(0.5f, config.bobberTimerScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.LATHER) && isMouseOverLather((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.latherOverlayScale = Math.min(3.0f, config.latherOverlayScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.latherOverlayScale = Math.max(0.5f, config.latherOverlayScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.CONTEST_TIMERS)
                && isMouseOverContestTimers((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.contestTimerScale = Math.min(3.0f, config.contestTimerScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.contestTimerScale = Math.max(0.5f, config.contestTimerScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.HOTF_PERKS) && isMouseOverHotfPerks((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.hotfPerkDisplayScale = Math.min(3.0f, config.hotfPerkDisplayScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.hotfPerkDisplayScale = Math.max(0.5f, config.hotfPerkDisplayScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.PICKAXE_ABILITY) && isMouseOverPickaxeAbility((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.pickaxeAbilityScale = Math.min(3.0f, config.pickaxeAbilityScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.pickaxeAbilityScale = Math.max(0.5f, config.pickaxeAbilityScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.MINESHAFT_PITY) && isMouseOverMineshaftPity((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.mineshaftPityScale = Math.min(3.0f, config.mineshaftPityScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.mineshaftPityScale = Math.max(0.5f, config.mineshaftPityScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.PROTECTOR_STATUS) && isMouseOverProtectorStatus((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.protectorStatusScale = Math.min(3.0f, config.protectorStatusScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.protectorStatusScale = Math.max(0.5f, config.protectorStatusScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.PROTECTOR_LOOT) && isMouseOverProtectorLoot((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.protectorLootScale = Math.min(3.0f, config.protectorLootScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.protectorLootScale = Math.max(0.5f, config.protectorLootScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.SAFARI) && isMouseOverSafari((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.safariHudScale = Math.min(10.0f, config.safariHudScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.safariHudScale = Math.max(0.5f, config.safariHudScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.SPRINTING) && isMouseOverSprinting((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.sprintingScale = Math.min(10.0f, config.sprintingScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.sprintingScale = Math.max(0.5f, config.sprintingScale - 0.05f);
            }
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.DUNGEON_TIMERS) && isMouseOverDungeonTimers((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.dungeonTimersScale = Math.min(5.0f, config.dungeonTimersScale + 0.05f);
            else if (verticalAmount < 0) config.dungeonTimersScale = Math.max(0.5f, config.dungeonTimersScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.INVINCIBILITY) && isMouseOverInvincibility((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.invincibilityScale = Math.min(5.0f, config.invincibilityScale + 0.05f);
            else if (verticalAmount < 0) config.invincibilityScale = Math.max(0.5f, config.invincibilityScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.THORN_STUN) && isMouseOverThornStun((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.thornStunScale = Math.min(5.0f, config.thornStunScale + 0.05f);
            else if (verticalAmount < 0) config.thornStunScale = Math.max(0.5f, config.thornStunScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.DRAGON_TIMER) && isMouseOverDragonTimer((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.witherDragonHudScale = Math.min(5.0f, config.witherDragonHudScale + 0.05f);
            else if (verticalAmount < 0) config.witherDragonHudScale = Math.max(0.5f, config.witherDragonHudScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.SPIRIT_BEAR) && isMouseOverSpiritBear((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.spiritBearScale = Math.min(5.0f, config.spiritBearScale + 0.05f);
            else if (verticalAmount < 0) config.spiritBearScale = Math.max(0.5f, config.spiritBearScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.PERF_HUD) && isMouseOverPerfHud((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.perfHudScale = Math.min(5.0f, config.perfHudScale + 0.05f);
            else if (verticalAmount < 0) config.perfHudScale = Math.max(0.5f, config.perfHudScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.KICKED) && isMouseOverKicked((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.kickedWarnScale = Math.min(5.0f, config.kickedWarnScale + 0.05f);
            else if (verticalAmount < 0) config.kickedWarnScale = Math.max(0.5f, config.kickedWarnScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.OVERKILL) && isMouseOverOverkill((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.overkillScale = Math.min(5.0f, config.overkillScale + 0.05f);
            else if (verticalAmount < 0) config.overkillScale = Math.max(0.5f, config.overkillScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.PEST_ROUTINE) && isMouseOverPestRoutine((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.pestRoutineScale = Math.min(5.0f, config.pestRoutineScale + 0.05f);
            else if (verticalAmount < 0) config.pestRoutineScale = Math.max(0.5f, config.pestRoutineScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.FARM_BAR) && isMouseOverFarmBar((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.farmBarScale = Math.min(5.0f, config.farmBarScale + 0.05f);
            else if (verticalAmount < 0) config.farmBarScale = Math.max(0.5f, config.farmBarScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.FEAST) && isMouseOverFeast((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.feastSeasonScale = Math.min(5.0f, config.feastSeasonScale + 0.05f);
            else if (verticalAmount < 0) config.feastSeasonScale = Math.max(0.5f, config.feastSeasonScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.CHARMED) && isMouseOverCharmed((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.charmedVisitorScale = Math.min(5.0f, config.charmedVisitorScale + 0.05f);
            else if (verticalAmount < 0) config.charmedVisitorScale = Math.max(0.5f, config.charmedVisitorScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.PEST_OVERLAY) && isMouseOverPestOverlay((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.pestOverlayScale = Math.min(5.0f, config.pestOverlayScale + 0.05f);
            else if (verticalAmount < 0) config.pestOverlayScale = Math.max(0.5f, config.pestOverlayScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.FARM_PROFIT) && isMouseOverFarmProfit((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.farmProfitScale = Math.min(5.0f, config.farmProfitScale + 0.05f);
            else if (verticalAmount < 0) config.farmProfitScale = Math.max(0.5f, config.farmProfitScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.BESTIARY) && isMouseOverBestiary((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.bestiaryTrackerScale = Math.min(3.0f, config.bestiaryTrackerScale + 0.05f);
            else if (verticalAmount < 0) config.bestiaryTrackerScale = Math.max(0.5f, config.bestiaryTrackerScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.FLARE_MODE) && isMouseOverFlareMode((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.flareModeScale = Math.min(3.0f, config.flareModeScale + 0.05f);
            else if (verticalAmount < 0) config.flareModeScale = Math.max(0.5f, config.flareModeScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.SKILL_EXP) && isMouseOverSkillExp((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.skillExpDisplayScale = Math.min(3.0f, config.skillExpDisplayScale + 0.05f);
            else if (verticalAmount < 0) config.skillExpDisplayScale = Math.max(0.5f, config.skillExpDisplayScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }
        if (visible.contains(DragTarget.PET_OVERLAY) && isMouseOverPetOverlay((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.petOverlayScale = Math.min(3.0f, config.petOverlayScale + 0.05f);
            else if (verticalAmount < 0) config.petOverlayScale = Math.max(0.5f, config.petOverlayScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }
        if (visible.contains(DragTarget.SKILL_TRACKER) && isMouseOverSkillTracker((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) config.skillTrackerScale = Math.min(3.0f, config.skillTrackerScale + 0.05f);
            else if (verticalAmount < 0) config.skillTrackerScale = Math.max(0.5f, config.skillTrackerScale - 0.05f);
            TeslaMapsConfig.save();
            return true;
        }

        if (visible.contains(DragTarget.MAP) && isMouseOverMap((int) mouseX, (int) mouseY)) {
            if (verticalAmount > 0) {
                config.mapScale = Math.min(2.0f, config.mapScale + 0.05f);
            } else if (verticalAmount < 0) {
                config.mapScale = Math.max(0.5f, config.mapScale - 0.05f);
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
