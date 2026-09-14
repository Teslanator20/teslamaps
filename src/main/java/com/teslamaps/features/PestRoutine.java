package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Walks the garden pest routine one action per key press: swap to the farming
 * loadout, farm, then set spawn, teleport to the infested plot, swap to the
 * fighting loadout and warp back.
 * <p>
 * Deliberately one action per press — the key never chains steps. Chat messages
 * only move the marker so the next press does the right thing.
 */
public class PestRoutine {

    public enum Step {
        FARM_LOADOUT_OPEN("/loadout"),
        FARM_LOADOUT_CLICK("click pest chance loadout"),
        FARM_LOADOUT_CLOSE("close menu"),
        RESUME_ATTACK("resume farming"),
        WAIT_PEST("farm until pests spawn"),
        SETSPAWN("/setspawn"),
        PLOT_TP("/plottp"),
        FIGHT_LOADOUT_OPEN("/loadout"),
        FIGHT_LOADOUT_CLICK("click rose drag loadout"),
        FIGHT_LOADOUT_CLOSE("close menu"),
        WARP_GARDEN("/warp garden"),
        WAIT_COOLDOWN("wait for pest cooldown");

        public final String label;

        Step(String label) {
            this.label = label;
        }
    }

    /** A remembered pest plot goes stale after this long. */
    private static final long PEST_VALID_MS = 60_000L;

    /**
     * Refilling the pest fortune buff at Philip. Runs as an interrupt: it takes
     * the next presses without disturbing where the main routine stands.
     */
    public enum Philip {
        NONE(""),
        CALL("/call philip"),
        EMPTY_BAG("click Empty Vacuum Bag");

        public final String label;

        Philip(String label) {
            this.label = label;
        }
    }

    /**
     * Going back to re-spray a plot. Like the Philip interrupt, it borrows the
     * next presses and leaves the main routine's position untouched.
     */
    public enum Spray {
        NONE(""),
        SETSPAWN("/setspawn"),
        PLOT_TP("/plottp");

        public final String label;

        Spray(String label) {
            this.label = label;
        }
    }

    /** The sprayonator itself cannot be used more often than this. */
    private static final long SPRAY_COOLDOWN_MS = 2 * 60 * 1000L;

    private static Spray spray = Spray.NONE;
    private static String sprayPlot = null;
    private static long lastSprayTrigger = 0L;

    // Which of the two loadouts was picked last, so the pest chance readout can
    // stay hidden while the fighting set is on.
    private static boolean pestChanceGear = false;

    public static boolean pestChanceGearOn() {
        return pestChanceGear;
    }

    private static Philip philip = Philip.NONE;

    private static Step step = Step.WAIT_COOLDOWN;
    private static String pestPlot = null;
    private static long pestPlotTime = 0L;
    private static long lastActionTime = 0L;

    private static boolean pestFresh() {
        return pestPlot != null && System.currentTimeMillis() - pestPlotTime < PEST_VALID_MS;
    }

    /** Milliseconds still to wait before the warp step will fire. */
    private static long warpCooldownLeft() {
        long delay = (long) (TeslaMapsConfig.get().pestWarpDelay * 1000.0f);
        return Math.max(0L, delay - (System.currentTimeMillis() - lastActionTime));
    }

    public static Step current() {
        return step;
    }

    public static String hint() {
        if (philip != Philip.NONE) return philip.label;
        if (spray != Spray.NONE) {
            return spray == Spray.PLOT_TP && sprayPlot != null
                    ? "/plottp " + sprayPlot + " (spray)"
                    : spray.label + " (spray)";
        }
        if (step == Step.WARP_GARDEN) {
            long left = warpCooldownLeft();
            return left > 0
                    ? String.format("/warp garden (%.1fs)", left / 1000.0f)
                    : "/warp garden";
        }
        if (step == Step.PLOT_TP && pestPlot != null) return "/plottp " + pestPlot;
        if (step == Step.FARM_LOADOUT_CLICK) return "click " + TeslaMapsConfig.get().pestChanceLoadout;
        if (step == Step.FIGHT_LOADOUT_CLICK) return "click " + TeslaMapsConfig.get().roseDragLoadout;
        return step.label;
    }

    /**
     * Spawn announcements, all five wordings Hypixel uses. Ours only matched the
     * plural "spawned" one before, so single spawns went unnoticed.
     */
    private static final java.util.regex.Pattern[] SPAWN_PATTERNS = {
            java.util.regex.Pattern.compile("^\\w+! \\d .*Pests? have spawned in Plot - (?<plot>.*)!"),
            java.util.regex.Pattern.compile("^\\w+! A .*Pest has appeared in Plot - (?<plot>.*)!"),
            java.util.regex.Pattern.compile("^\\w+! \\d .*Pests? have spawned in (?<plot>The Barn)!"),
            java.util.regex.Pattern.compile("^\\w+! A .*Pest has appeared in (?<plot>The Barn)!"),
    };

    private static final java.util.regex.Pattern SPRAY_EXPIRED = java.util.regex.Pattern.compile(
            "SPRAYONATOR! The smell of .+ on Plot - (?<plot>.+) ran out!");

    /** Called when the game itself reports the spawn cooldown as ready. */
    public static void onCooldownReady() {
        if (step == Step.WAIT_COOLDOWN) step = Step.FARM_LOADOUT_OPEN;
    }

    public static void onChatMessage(String raw) {
        String text = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");

        // SkyHanni's warning, e.g. "Pest spawn cooldown expires in 2s". It counts
        // down, so this arrives several times — only arm the routine when it is
        // actually idle, otherwise every further line rewinds the marker and the
        // next press re-clicks the loadout that was already selected.
        if (text.contains("Pest spawn cooldown expires")) {
            if (step == Step.WAIT_COOLDOWN) step = Step.FARM_LOADOUT_OPEN;
            return;
        }

        // "SPRAYONATOR! The smell of Honey Jar on Plot - 13 ran out!"
        if (text.contains("SPRAYONATOR!") && text.contains("ran out")) {
            java.util.regex.Matcher matcher = SPRAY_EXPIRED.matcher(text);
            if (matcher.find() && TeslaMapsConfig.get().sprayRoutine) {
                long now = System.currentTimeMillis();
                if (now - lastSprayTrigger >= SPRAY_COOLDOWN_MS) {
                    lastSprayTrigger = now;
                    sprayPlot = matcher.group("plot").trim();
                    spray = Spray.SETSPAWN;
                }
            }
            return;
        }

        if (text.contains("Pest fortune buff has expired")) {
            philip = Philip.CALL;
            warnBuffExpired();
            return;
        }

        for (java.util.regex.Pattern pattern : SPAWN_PATTERNS) {
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) continue;

            pestPlot = matcher.group("plot").trim();
            pestPlotTime = System.currentTimeMillis();
            PestData.onPestSpawn();
            PestOverlay.onPestSpawn(pestPlot);
            // Don't rewind if the fight half is already running.
            if (step == Step.WAIT_COOLDOWN || step.ordinal() <= Step.WAIT_PEST.ordinal()) {
                step = Step.SETSPAWN;
            }
            return;
        }
    }

    /** One key press: perform exactly the current step, then point at the next. */
    public static void advance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        if (philip != Philip.NONE) {
            advancePhilip(mc);
            return;
        }

        if (spray != Spray.NONE) {
            advanceSpray(mc);
            return;
        }

        Step before = step;

        // The two waiting states are silent no-ops: the key has to be safe to
        // press at any time, so nothing happens until the chat says it should.
        if (step == Step.WAIT_COOLDOWN) return;
        if (step == Step.WAIT_PEST) {
            if (!pestFresh()) return;
            step = Step.SETSPAWN;
        }

        switch (step) {
            case FARM_LOADOUT_OPEN, FIGHT_LOADOUT_OPEN -> {
                boolean farming = step == Step.FARM_LOADOUT_OPEN;
                if (loadoutMenuOpen()) {
                    // The menu is already up, so go straight to the click instead
                    // of spending this press on a redundant /loadout.
                    step = farming ? Step.FARM_LOADOUT_CLICK : Step.FIGHT_LOADOUT_CLICK;
                    String wanted = farming
                            ? TeslaMapsConfig.get().pestChanceLoadout
                            : TeslaMapsConfig.get().roseDragLoadout;
                    if (clickNamedSlot(wanted)) {
                        pestChanceGear = farming;
                        step = farming ? Step.FARM_LOADOUT_CLOSE : Step.FIGHT_LOADOUT_CLOSE;
                    }
                    break;
                }
                mc.getConnection().sendCommand("loadout");
                step = farming ? Step.FARM_LOADOUT_CLICK : Step.FIGHT_LOADOUT_CLICK;
            }
            case FARM_LOADOUT_CLICK, FIGHT_LOADOUT_CLICK -> {
                boolean farming = step == Step.FARM_LOADOUT_CLICK;
                if (!loadoutMenuOpen()) {
                    // Reopening is more useful than telling the user to do it.
                    mc.getConnection().sendCommand("loadout");
                    return;
                }
                String wanted = farming
                        ? TeslaMapsConfig.get().pestChanceLoadout
                        : TeslaMapsConfig.get().roseDragLoadout;
                if (clickNamedSlot(wanted)) {
                    pestChanceGear = farming;
                    step = farming ? Step.FARM_LOADOUT_CLOSE : Step.FIGHT_LOADOUT_CLOSE;
                }
            }
            case FARM_LOADOUT_CLOSE -> {
                if (mc.screen != null) mc.player.closeContainer();
                step = TeslaMapsConfig.get().pestResumeAttack ? Step.RESUME_ATTACK : Step.WAIT_PEST;
            }
            case RESUME_ATTACK -> {
                pressAttackKey();
                step = Step.WAIT_PEST;
            }
            case FIGHT_LOADOUT_CLOSE -> {
                if (mc.screen != null) mc.player.closeContainer();
                step = Step.WARP_GARDEN;
            }
            case SETSPAWN -> {
                mc.getConnection().sendCommand("setspawn");
                step = Step.PLOT_TP;
            }
            case PLOT_TP -> {
                if (!pestFresh()) {
                    pestPlot = null;
                    step = Step.WAIT_COOLDOWN;
                    return;
                }
                mc.getConnection().sendCommand("plottp " + pestPlot);
                step = Step.FIGHT_LOADOUT_OPEN;
            }
            case WARP_GARDEN -> {
                // Guard against firing the warp on a stray extra press right after
                // closing the loadout menu.
                long left = warpCooldownLeft();
                if (left > 0) {
                    mc.player.sendSystemMessage(Component.literal(
                            String.format("§b[TeslaMaps] §fWait §e%.1fs§f before warping", left / 1000.0f)));
                    return;
                }
                mc.getConnection().sendCommand("warp garden");
                pestPlot = null;
                pestPlotTime = 0L;
                step = Step.WAIT_COOLDOWN;
            }
            default -> {
            }
        }

        if (step != before) lastActionTime = System.currentTimeMillis();
    }

    private static void advancePhilip(Minecraft mc) {
        switch (philip) {
            case CALL -> {
                mc.getConnection().sendCommand("call philip");
                philip = Philip.EMPTY_BAG;
            }
            case EMPTY_BAG -> {
                if (!pesthunterMenuOpen()) {
                    // Calling again is more useful than telling the user to do it.
                    mc.getConnection().sendCommand("call philip");
                    return;
                }
                if (clickNamedSlot(TeslaMapsConfig.get().vacuumBagItem)) philip = Philip.NONE;
            }
            default -> philip = Philip.NONE;
        }
    }

    public static void skipPhilip() {
        philip = Philip.NONE;
    }

    /** Arms the farming half by hand, for when the chat trigger was missed. */
    public static void start() {
        philip = Philip.NONE;
        step = Step.FARM_LOADOUT_OPEN;
    }

    private static boolean pesthunterMenuOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return false;
        return strip(screen.getTitle().getString()).toLowerCase().contains("pesthunter");
    }

    private static void advanceSpray(Minecraft mc) {
        switch (spray) {
            case SETSPAWN -> {
                mc.getConnection().sendCommand("setspawn");
                spray = Spray.PLOT_TP;
            }
            case PLOT_TP -> {
                if (sprayPlot != null) mc.getConnection().sendCommand("plottp " + sprayPlot);
                sprayPlot = null;
                spray = Spray.NONE;
            }
            default -> spray = Spray.NONE;
        }
    }

    public static void skipSpray() {
        spray = Spray.NONE;
        sprayPlot = null;
    }

    /** Queues the Philip refill without touching the main routine's position. */
    public static void onPhilipExpired() {
        philip = Philip.CALL;
    }

    /** Title plus sound for a Philip event. */
    public static void warnPhilip(String title, String subtitle) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.pestBuffWarning) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setTimes(0, 60, 5);
            mc.gui.setTitle(Component.literal(title));
            mc.gui.setSubtitle(Component.literal(subtitle));
        }

        var sound = com.teslamaps.utils.SoundOptions.resolve(config.pestBuffSound);
        if (sound != null) {
            com.teslamaps.utils.LoudSound.play(sound, config.pestBuffVolume, 1.0f);
        }
    }

    private static void warnBuffExpired() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.pestBuffWarning) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setTimes(0, 60, 5);
            mc.gui.setTitle(Component.literal("\u00a7cPest Fortune expired"));
            mc.gui.setSubtitle(Component.literal("\u00a7e/call philip"));
        }

        var sound = com.teslamaps.utils.SoundOptions.resolve(config.pestBuffSound);
        if (sound != null) {
            com.teslamaps.utils.LoudSound.play(sound, config.pestBuffVolume, 1.0f);
        }
    }

    /**
     * Sends a single press of the attack key, so an attack toggle picks it up and
     * farming continues. One press of ours equals one press of yours; nothing is
     * held down and nothing repeats.
     */
    private static void pressAttackKey() {
        Minecraft mc = Minecraft.getInstance();
        int configured = TeslaMapsConfig.get().pestAttackKey;

        com.mojang.blaze3d.platform.InputConstants.Key key = configured >= 0
                ? com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(configured)
                : mc.options.keyAttack.key;

        if (key == null || key.getValue() < 0) {
            mc.player.sendSystemMessage(Component.literal(
                    "\u00a7b[TeslaMaps] \u00a7fNo attack key bound"));
            return;
        }

        // Press and release, the same pair KeyboardHandler sends for a real key.
        // With the vanilla attack toggle on, ToggleKeyMapping flips its state on
        // the press and ignores the release, so nothing stays held down. A bare
        // click() would only queue one swing and never flip the toggle.
        net.minecraft.client.KeyMapping.set(key, true);
        net.minecraft.client.KeyMapping.set(key, false);
    }

    /** True while a Hypixel loadout / armour set menu is on screen. */
    private static boolean loadoutMenuOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return false;
        String title = strip(screen.getTitle().getString()).toLowerCase();
        return title.contains("loadout") || title.contains("armor set")
                || title.contains("armour set") || title.contains("equipment set")
                || title.contains("wardrobe");
    }

    /**
     * Left-clicks the slot in the open menu whose name or lore contains
     * {@code wanted}. Dumps the menu contents when nothing matches, since the
     * only way to fix a mismatch is to see the real names.
     */
    private static boolean clickNamedSlot(String wanted) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return false;

        String needle = wanted.toLowerCase();
        // Several slots can mention a loadout (the equipped one is often named in
        // another slot's lore), so take the most specific match rather than the
        // first one: exact name, then name contains, then lore contains.
        int exact = -1;
        int byName = -1;
        int byLore = -1;

        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String name = strip(stack.getHoverName().getString()).trim().toLowerCase();
            if (name.equals(needle)) {
                exact = slot.index;
                break;
            }
            if (byName < 0 && name.contains(needle)) byName = slot.index;
            if (byLore < 0 && loreText(stack).contains(needle)) byLore = slot.index;
        }

        int target = exact >= 0 ? exact : byName >= 0 ? byName : byLore;
        if (target >= 0) {
            mc.gameMode.handleContainerInput(screen.getMenu().containerId, target, 0,
                    ContainerInput.PICKUP, mc.player);
            return true;
        }

        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fNo slot matching '"
                + wanted + "' — writing gui_dump.txt"));
        com.teslamaps.utils.GuiDump.dump(wanted);
        return false;
    }

    /** Lore of a stack, colour codes removed, lowercased. */
    private static String loreText(ItemStack stack) {
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder text = new StringBuilder();
        for (Component line : lore.lines()) {
            text.append(strip(line.getString())).append(' ');
        }
        return text.toString().toLowerCase();
    }

    private static String strip(String text) {
        return text.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    /** Lists the names in the open menu, so a mismatch can be spotted. */
    public static void dumpSlots() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.screen instanceof ContainerScreen screen)) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fNo menu open"));
            return;
        }

        mc.player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] §fMenu: §e" + strip(screen.getTitle().getString())));
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            mc.player.sendSystemMessage(Component.literal(
                    "§7" + slot.index + ": §f" + strip(stack.getHoverName().getString())));
        }
    }

    private static boolean keyWasDown = false;

    /** Polls the routine key. Raw GLFW so it also works with the loadout menu open. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int key = TeslaMapsConfig.get().pestRoutineKey;
        if (key == -1 || mc.getWindow() == null) return;
        // Don't steal keystrokes that are going into a text field.
        if (mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) return;

        boolean down = com.teslamaps.utils.KeyNames.isDown(key);
        if (down && !keyWasDown) advance();
        keyWasDown = down;
    }

    public static void reset() {
        philip = Philip.NONE;
        spray = Spray.NONE;
        sprayPlot = null;
        step = Step.WAIT_COOLDOWN;
        pestPlot = null;
        pestPlotTime = 0L;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().pestRoutineHud) return;
        if (!com.teslamaps.utils.SkyblockUtils.isInGarden()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, Minecraft.getInstance(), config.pestRoutineX, config.pestRoutineY, config.pestRoutineScale);
    }

    private static final int PANEL_WIDTH = 132;
    private static final int PANEL_HEIGHT = 34;
    private static final int PADDING = 10;

    public static int panelWidth(float scale) {
        return (int) (PANEL_WIDTH * scale);
    }

    public static int panelHeight(float scale) {
        return (int) (PANEL_HEIGHT * scale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        boolean waiting = step == Step.WAIT_PEST || step == Step.WAIT_COOLDOWN;
        boolean interrupt = philip != Philip.NONE;

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        com.teslamaps.render.gui.HudStyle.panel(context, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, 6);

        context.text(mc.font, Component.literal(interrupt ? "Philip" : "Routine"),
                PADDING, PADDING, com.teslamaps.screen.AppleColors.TEXT_SECONDARY, false);

        // A pip on the right shows at a glance whether a press would do anything.
        int pipColor = waiting
                ? com.teslamaps.screen.AppleColors.TEXT_TERTIARY
                : interrupt
                        ? com.teslamaps.screen.AppleColors.ACCENT_BLUE
                        : com.teslamaps.screen.AppleColors.ACCENT_GREEN;
        com.teslamaps.render.gui.TeslaGui.roundedRect(context,
                PANEL_WIDTH - PADDING - 4, PADDING + 1, 4, 4, 2, pipColor);

        String text = hint();
        int textColor = waiting
                ? com.teslamaps.screen.AppleColors.TEXT_TERTIARY
                : com.teslamaps.screen.AppleColors.TEXT_PRIMARY;
        context.text(mc.font, Component.literal(text), PADDING, PADDING + 12, textColor, false);

        context.pose().popMatrix();
    }
}
