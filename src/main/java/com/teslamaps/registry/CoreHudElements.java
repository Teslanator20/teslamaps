package com.teslamaps.registry;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The /tmap gui elements that existed before the registry, in their old draw order. */
final class CoreHudElements {

    private static final String PUZZLES = "Puzzles & Terminals";

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

    private static final int ROOM_SIZE = 24;
    private static final int DOOR_SIZE = 4;
    private static final int MAP_PADDING = 8;
    private static final int CELL_SIZE = ROOM_SIZE + DOOR_SIZE;
    private static final int MAP_SIZE = MAP_PADDING * 2 + ComponentGrid.GRID_SIZE * CELL_SIZE;

    private CoreHudElements() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static void register() {
        HudEditElement.of("map", "Map").ctx(HudCtx.DUNGEON).section("Map").enabled(() -> cfg().mapEnabled)
                .size(() -> MAP_SIZE, () -> MAP_SIZE).scaleRange(0.5f, 2.0f).dragColor(0xFF5865F2).plainLabel()
                .preview(CoreHudElements::drawMap);
        HudEditElement.of("splits", "Splits").ctx(HudCtx.DUNGEON).section("Score & Splits").enabled(() -> cfg().splitsEnabled)
                .size(() -> splitsNameCol() + font().width(SPLITS_SAMPLE_TIME), () -> SPLITS_SAMPLE.length * 9)
                .scaleRange(0.5f, 2.0f).dragColor(0xFF5865F2).preview(CoreHudElements::drawSplits);
        HudEditElement.of("bloodCamp", "Blood Camp").ctx(HudCtx.DUNGEON).section("Blood Camp")
                .size(() -> maxWidth(BLOOD_SAMPLE), () -> BLOOD_SAMPLE.length * 10)
                .scaleRange(0.5f, 3.0f).preview(CoreHudElements::drawBloodCamp);
        HudEditElement.of("roomSplits", "Room Splits").ctx(HudCtx.DUNGEON).section("Score & Splits").enabled(() -> cfg().roomSplits)
                .size(() -> maxWidth(ROOM_SPLITS_SAMPLE), () -> 10 + (ROOM_SPLITS_SAMPLE.length - 1) * 9)
                .scaleRange(0.5f, 2.0f).dragColor(0xFFFFAA00).preview((g, m, x, y, s) -> drawTextBlock(g, ROOM_SPLITS_SAMPLE, x, y, s));
        HudEditElement.of("itemValue", "Item Value").section("Tooltips & Value").enabled(() -> cfg().itemValueGui && cfg().itemValueFixedPos)
                .size(() -> com.teslamaps.features.ItemValueOverlay.previewSize(font())[0],
                        () -> com.teslamaps.features.ItemValueOverlay.previewSize(font())[1])
                .scaleRange(0.5f, 2.0f).dragColor(0xFF55FF55)
                .preview((g, m, x, y, s) -> drawTextBlock(g, com.teslamaps.features.ItemValueOverlay.SAMPLE.toArray(new String[0]), x, y, s));
        HudEditElement.of("watcherHud", "Watcher Stats").ctx(HudCtx.DUNGEON).section("Blood Camp").enabled(() -> cfg().watcherHud)
                .size(() -> maxWidth(WATCHER_SAMPLE), () -> 10 + (WATCHER_SAMPLE.length - 1) * 9)
                .scaleRange(0.5f, 2.0f).preview((g, m, x, y, s) -> drawTextBlock(g, WATCHER_SAMPLE, x, y, s));
        HudEditElement.of("dungeonTimers", "Dungeon Timers").ctx(HudCtx.DUNGEON).section("Timers")
                .size(() -> font().width(com.teslamaps.features.DungeonTimers.SAMPLE_TEXT), () -> font().lineHeight * 2)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.DungeonTimers::draw);
        HudEditElement.of("invincibility", "Invincibility").ctx(HudCtx.DUNGEON).section("Timers").enabled(() -> cfg().invincibilityTimer)
                .size(() -> 12 + font().width("immune 2.50s"), () -> font().lineHeight * 3)
                .scaleRange(0.5f, 5.0f).dragColor(0xFFFFD700).preview(com.teslamaps.features.DungeonTimers::drawInvincibility);
        HudEditElement.of("thornStun", "Thorn Stun").ctx(HudCtx.DUNGEON).section(PUZZLES).enabled(() -> cfg().thornStunTimer)
                .size(() -> font().width(com.teslamaps.features.ThornStunTimer.SAMPLE_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 5.0f).dragColor(0xFFAA55FF).preview(com.teslamaps.features.ThornStunTimer::draw);
        HudEditElement.of("witherDragonHud", "Dragon Timer").ctx(HudCtx.DUNGEON).section("F7 / M7")
                .enabled(() -> cfg().witherDragons && cfg().witherDragonHud)
                .size(() -> font().width(com.teslamaps.dungeon.WitherDragons.hudSample()), () -> font().lineHeight)
                .scaleRange(0.5f, 5.0f).dragColor(0xFFAA55FF)
                .preview((g, m, x, y, s) -> com.teslamaps.dungeon.WitherDragons.drawHud(g, m, x, y, s,
                        com.teslamaps.dungeon.WitherDragons.hudSample()));
        HudEditElement.of("spiritBear", "Spirit Bear").ctx(HudCtx.DUNGEON).enabled(() -> cfg().spiritBearTimer)
                .size(() -> font().width(com.teslamaps.dungeon.puzzle.SpiritBearTimer.SAMPLE_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 5.0f).dragColor(0xFFAA55FF).preview(com.teslamaps.dungeon.puzzle.SpiritBearTimer::draw);
        HudEditElement.of("bearSpawn", "Bear Warning").ctx(HudCtx.DUNGEON).section("Sounds").enabled(() -> cfg().bearSpawnWarning)
                .size(() -> font().width(com.teslamaps.features.BearSpawnWarning.ALERT_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 10.0f).preview(com.teslamaps.features.BearSpawnWarning::drawAlert);
        HudEditElement.of("overkill", "Overkill").ctx(HudCtx.DUNGEON).section("Timers").enabled(() -> cfg().overkillCounter)
                .size(() -> font().width("Chickens: 00"), () -> font().lineHeight * 7)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.OverkillCounter::draw);
        HudEditElement.of("skillTracker", "Skill Tracker").section("Skills").enabled(() -> cfg().skillTracker)
                .size(() -> com.teslamaps.features.SkillTracker.previewSize(font())[0],
                        () -> com.teslamaps.features.SkillTracker.previewSize(font())[1])
                .boxOffset(4).dragColor(0xFF30D158).preview(com.teslamaps.features.SkillTracker::drawPreview);
        HudEditElement.of("skillExpDisplay", "Skill XP").section("Skills").enabled(() -> cfg().skillExpDisplay)
                .position(() -> com.teslamaps.features.SkillExpDisplay.x(mc(), cfg()),
                        () -> com.teslamaps.features.SkillExpDisplay.y(mc(), cfg()))
                .scaledSize(s -> com.teslamaps.features.SkillExpDisplay.width(mc(), s),
                        com.teslamaps.features.SkillExpDisplay::height)
                .dragColor(0xFF30D158).preview(com.teslamaps.features.SkillExpDisplay::drawPreview);
        HudEditElement.of("petOverlay", "Pet").section("Skills").enabled(() -> cfg().petOverlay)
                .size(() -> com.teslamaps.features.pets.PetOverlay.previewSize(mc())[0],
                        () -> com.teslamaps.features.pets.PetOverlay.previewSize(mc())[1])
                .boxOffset(4).dragColor(0xFF30D158).preview(com.teslamaps.features.pets.PetOverlay::drawPreview);
        HudEditElement.of("bestiaryTracker", "Bestiary").section("Combat").enabled(() -> cfg().bestiaryTracker)
                .size(() -> com.teslamaps.features.BestiaryTracker.previewSize(font())[0],
                        () -> com.teslamaps.features.BestiaryTracker.previewSize(font())[1])
                .boxOffset(4).dragColor(0xFF30D158).preview(com.teslamaps.features.BestiaryTracker::drawPreview);
        HudEditElement.of("flareMode", "Flare Mode").section("Skills").enabled(() -> cfg().flareMode)
                .size(() -> com.teslamaps.features.hunting.FlareMode.previewSize(font())[0],
                        () -> com.teslamaps.features.hunting.FlareMode.previewSize(font())[1])
                .boxOffset(4).dragColor(0xFF30D158).preview(com.teslamaps.features.hunting.FlareMode::drawPreview);
        HudEditElement.of("pestRoutine", "Pest Routine").ctx(HudCtx.GARDEN).section("Garden").enabled(() -> cfg().pestRoutineHud)
                .scaledSize(com.teslamaps.features.PestRoutine::panelWidth, com.teslamaps.features.PestRoutine::panelHeight)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.PestRoutine::draw);
        HudEditElement.of("farmBar", "Farm Bar").ctx(HudCtx.GARDEN).section("Garden").enabled(() -> cfg().farmBar)
                .scaledSize(com.teslamaps.features.FarmProgress::barWidth, com.teslamaps.features.FarmProgress::barHeight)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.FarmProgress::draw);
        HudEditElement.of("pestOverlay", "Pests").ctx(HudCtx.GARDEN).section("Garden").enabled(() -> cfg().pestOverlay)
                .scaledSize(com.teslamaps.features.PestOverlay::width, com.teslamaps.features.PestOverlay::height)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.PestOverlay::draw);
        HudEditElement.of("farmProfit", "Profit").ctx(HudCtx.GARDEN).section("Garden").enabled(() -> cfg().farmProfit)
                .scaledSize(com.teslamaps.features.FarmProfitOverlay::width, com.teslamaps.features.FarmProfitOverlay::height)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.FarmProfitOverlay::draw);
        HudEditElement.of("charmedVisitor", "Charmed").ctx(HudCtx.GARDEN).section("Garden").enabled(() -> cfg().charmedVisitorList)
                .scaledSize(com.teslamaps.features.CharmedVisitors::previewWidth, com.teslamaps.features.CharmedVisitors::previewHeight)
                .scaleRange(0.5f, 5.0f).preview((g, m, x, y, s) -> com.teslamaps.features.CharmedVisitors.draw(g, m, x, y, s, true));
        HudEditElement.of("feastSeason", "In-Season").ctx(HudCtx.GARDEN).section("Garden").enabled(() -> cfg().feastSeasonDisplay)
                .scaledSize(com.teslamaps.features.HarvestFeast::previewWidth, com.teslamaps.features.HarvestFeast::previewHeight)
                .scaleRange(0.5f, 5.0f).preview((g, m, x, y, s) -> com.teslamaps.features.HarvestFeast.draw(g, m, x, y, s, true));
        HudEditElement.of("safariHud", "Critter Safari").ctx(HudCtx.SAFARI).section("Critter Safari")
                .enabled(() -> cfg().safariTracker && cfg().safariHud)
                .size(() -> com.teslamaps.safari.SafariTracker.previewSize(font())[0],
                        () -> com.teslamaps.safari.SafariTracker.previewSize(font())[1])
                .scaleRange(0.5f, 10.0f).preview(com.teslamaps.safari.SafariTracker::draw);
        HudEditElement.of("hotfPerkDisplay", "HotF Perks").ctx(HudCtx.FORAGING).section("Foraging").enabled(() -> cfg().hotfPerkDisplay)
                .size(() -> com.teslamaps.features.foraging.HotfPerkDisplay.previewSize(font())[0],
                        () -> com.teslamaps.features.foraging.HotfPerkDisplay.previewSize(font())[1])
                .preview(com.teslamaps.features.foraging.HotfPerkDisplay::draw);
        HudEditElement.of("latherOverlay", "Honeycomb Lather").ctx(HudCtx.FORAGING).section("Foraging").enabled(() -> cfg().latherOverlay)
                .size(() -> com.teslamaps.features.foraging.LatherOverlay.previewSize(font())[0],
                        () -> com.teslamaps.features.foraging.LatherOverlay.previewSize(font())[1])
                .preview(com.teslamaps.features.foraging.LatherOverlay::draw);
        HudEditElement.of("pickaxeAbility", "Pickaxe Ability").ctx(HudCtx.MINING).section("Mining").enabled(() -> cfg().pickaxeAbilityDisplay)
                .size(() -> com.teslamaps.features.mining.PickaxeAbility.previewSize(font())[0],
                        () -> com.teslamaps.features.mining.PickaxeAbility.previewSize(font())[1])
                .preview(com.teslamaps.features.mining.PickaxeAbility::draw);
        HudEditElement.of("mineshaftPity", "Mineshaft Pity").ctx(HudCtx.MINING).section("Mining").enabled(() -> cfg().mineshaftPityDisplay)
                .size(() -> com.teslamaps.features.mining.MineshaftPity.previewSize(font())[0],
                        () -> com.teslamaps.features.mining.MineshaftPity.previewSize(font())[1])
                .preview(com.teslamaps.features.mining.MineshaftPity::draw);
        HudEditElement.of("protectorStatus", "Protector Status").ctx(HudCtx.THE_END).section("The End")
                .enabled(() -> cfg().endProtector && cfg().endProtectorStatusHud)
                .size(() -> com.teslamaps.features.theend.ProtectorHud.previewSize(font())[0],
                        () -> com.teslamaps.features.theend.ProtectorHud.previewSize(font())[1])
                .preview(com.teslamaps.features.theend.ProtectorHud::draw);
        HudEditElement.of("protectorLoot", "Protector Loot").ctx(HudCtx.THE_END).section("The End")
                .enabled(() -> cfg().endProtector && cfg().endProtectorLootHud)
                .size(() -> com.teslamaps.features.theend.ProtectorLootHud.previewSize(font())[0],
                        () -> com.teslamaps.features.theend.ProtectorLootHud.previewSize(font())[1])
                .preview(com.teslamaps.features.theend.ProtectorLootHud::draw);
        HudEditElement.of("contestTimer", "Contest Timers").section("Contest Timers").enabled(() -> cfg().contestTimer)
                .size(() -> com.teslamaps.features.ContestTimers.previewSize(font())[0],
                        () -> com.teslamaps.features.ContestTimers.previewSize(font())[1])
                .dragColor(0xFFFFAA00).preview(com.teslamaps.features.ContestTimers::draw);
        HudEditElement.of("bobberTimer", "Bobber Timer").section("Fishing").enabled(() -> cfg().bobberTimer)
                .size(() -> font().width(com.teslamaps.features.FishingBobberTimer.SAMPLE_TEXT), () -> font().lineHeight)
                .preview(com.teslamaps.features.FishingBobberTimer::draw);
        HudEditElement.of("hookDisplay", "Hook Timer").section("Fishing").enabled(() -> cfg().hookDisplay)
                .size(() -> font().width(com.teslamaps.features.FishingHookDisplay.SAMPLE_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 8.0f).scaleStep(0.2f).preview(com.teslamaps.features.FishingHookDisplay::draw);
        HudEditElement.of("lassoDisplay", "Lasso Display").ctx(HudCtx.FORAGING).section("Foraging").enabled(() -> cfg().lassoDisplay)
                .size(() -> font().width(com.teslamaps.features.hunting.LassoDisplay.SAMPLE_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 8.0f).scaleStep(0.2f).preview(com.teslamaps.features.hunting.LassoDisplay::draw);
        HudEditElement.of("deployable", "Deployables").section("Skills").enabled(() -> cfg().deployableOverlay)
                .size(() -> com.teslamaps.features.Deployables.previewSize(font())[0],
                        () -> com.teslamaps.features.Deployables.previewSize(font())[1])
                .preview(com.teslamaps.features.Deployables::draw);
        HudEditElement.of("sprinting", "Sprinting").section("Render").enabled(() -> cfg().sprintingOverlay)
                .size(() -> font().width(com.teslamaps.features.SprintingOverlay.SAMPLE_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 10.0f).preview(com.teslamaps.features.SprintingOverlay::draw);
        HudEditElement.of("kickedWarn", "Kick Warning").section("Timers").enabled(() -> cfg().kickedTimer && cfg().kickedCenterWarning)
                .size(() -> font().width(com.teslamaps.features.KickedTimer.SAMPLE_TEXT), () -> font().lineHeight)
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.KickedTimer::draw);
        HudEditElement.of("perfHud", "Performance").section("Interface").enabled(() -> cfg().perfHud)
                .size(() -> com.teslamaps.features.PerformanceHud.width(mc()), () -> com.teslamaps.features.PerformanceHud.height(mc()))
                .scaleRange(0.5f, 5.0f).preview(com.teslamaps.features.PerformanceHud::draw);
    }

    private static int maxWidth(String[] lines) {
        int w = 0;
        for (String s : lines) w = Math.max(w, font().width(s));
        return w;
    }

    private static int splitsNameCol() {
        return maxWidth(SPLITS_SAMPLE) + 6;
    }

    private static void drawMap(GuiGraphicsExtractor g, Minecraft mc, int mapX, int mapY, float scale) {
        int mapSize = (int) (MAP_SIZE * scale);
        g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF3a3a3a);
        g.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF1a1a1a);
        int roomSizeScaled = (int) (ROOM_SIZE * scale);
        for (int gx = 0; gx < 6; gx++) {
            for (int gz = 0; gz < 6; gz++) {
                int px = mapX + (int) ((MAP_PADDING + gx * CELL_SIZE) * scale);
                int py = mapY + (int) ((MAP_PADDING + gz * CELL_SIZE) * scale);
                int color = ((gx + gz) % 2 == 0) ? 0xFF3d3d3d : 0xFF4a4a4a;
                g.fill(px, py, px + roomSizeScaled, py + roomSizeScaled, color);
            }
        }
    }

    private static void drawSplits(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        int nameCol = splitsNameCol();
        var matrices = g.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        for (int i = 0; i < SPLITS_SAMPLE.length; i++) {
            g.text(mc.font, SPLITS_SAMPLE[i], 0, i * 9, 0xFFFFFFFF);
            g.text(mc.font, SPLITS_SAMPLE_TIME, nameCol, i * 9, 0xFFFFFFFF);
        }
        matrices.popMatrix();
    }

    private static void drawBloodCamp(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        var matrices = g.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        for (int i = 0; i < BLOOD_SAMPLE.length; i++) {
            g.text(mc.font, BLOOD_SAMPLE[i], 0, i * 10, 0xFFFFFFFF);
        }
        matrices.popMatrix();
    }

    /** Left-aligned text block, the first line 10 tall and the rest 9. */
    private static void drawTextBlock(GuiGraphicsExtractor g, String[] sample, int x, int y, float scale) {
        var matrices = g.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        int ly = 0;
        for (int i = 0; i < sample.length; i++) {
            g.text(font(), sample[i], 0, ly, 0xFFFFFFFF);
            ly += i == 0 ? 10 : 9;
        }
        matrices.popMatrix();
    }
}
