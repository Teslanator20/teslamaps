package com.teslamaps.adm.misc;

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.dungeon.termsim.TermSimGUI;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Ported from ADM's misc/RandomStuff.kt (Hide Numbers' Numbers lives with the practice port)
public final class RandomStuff {
    private static final Pattern PARTY_LEAVE = Pattern.compile("^(?:\\[[^]]*?] ?)?\\w{1,16} has left the party\\.$");
    private static final long BLESS_COOLDOWN_MS = 10_000L;
    private static final int RETRY_TICKS = 60;
    private static final int TRANSFER_TICKS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final Set<String> TITLE_ICON_KEYS = Set.of("options.language", "options.accessibility", "accessibility.onboarding.accessibility.button");
    public static final String[] PB_FLOORS = {"E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7"};

    private static volatile boolean on;
    private static long lastBless;
    private static boolean pendingSkyblockJoin;
    private static Connection skyblockConnection;
    private static int ticksUntilSkyblock = -1;
    private static int attempts;
    private static Screen cursorScreen;
    private static int cursorTicks;

    /** Set while Gui.extractRenderState / GameRenderer.extractGui builds the frame's GUI. Render thread. */
    public static boolean buildingGui;

    private RandomStuff() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        Feature.of("AdmRandomStuff.flags").tickAlways(RandomStuff::tick);
        Feature.of("AdmRandomStuff").section(Adm.SECTION).when(() -> cfg().admRandomStuff)
                .serverChat(RandomStuff::onChat)
                .config(Adm.SECTION, RandomStuff::config);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen t && cleansMenus()) cleanTitleScreen(t);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(handler.getConnection(), client));
        AdmCore.onWorldLoad(() -> ScoreboardLines.hideLines = false);
    }

    private static void config(com.teslamaps.screen.config.ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.label("Random Stuff");
        b.toggle("Random Stuff", () -> c.admRandomStuff, v -> c.admRandomStuff = v)
                .desc("A collection of small unrelated QoL toggles. While it is on, the i4 Complete title is too.");
        b.toggle("  Force Cursor Reset", () -> c.admForceCursorReset, v -> c.admForceCursorReset = v)
                .desc("Opening a terminal always puts the cursor in the middle of the screen. On Linux (Xwayland) the game sometimes leaves it where it was; this moves it again. Terminals (and /termsim) only, no other menu.");
        b.toggle("  Party Finder Stats", () -> c.admPartyFinderStats, v -> c.admPartyFinderStats = v)
                .desc("In the Party Finder, each listed player's Catacombs level, secrets and S+ PB for the floor, and which classes the party is missing (yours in bold).");
        b.toggle("  Enter Confirms Sign", () -> c.admEnterConfirmsSign, v -> c.admEnterConfirmsSign = v)
                .desc("On a sign edit screen, Enter finishes it instead of starting a new line — so a Bazaar or Auction House search is type-and-Enter.");
        b.toggle("  Hide Item Names", () -> c.admHideItemNames, v -> c.admHideItemNames = v)
                .desc("Hides the item name that pops up above the hotbar when you switch to a different item.");
        b.toggle("  Hide Action Bar", () -> c.admHideActionBar, v -> c.admHideActionBar = v)
                .desc("Hides the entire action bar (the overlay text above the hotbar) — health/mana/defense text, level up messages, all of it.");
        b.toggle("  Bless On Party Leave", () -> c.admBlessOnLeave, v -> c.admBlessOnLeave = v)
                .desc("Sends \"bless\" in party chat whenever someone leaves the party.");
        b.toggle("  Black Sky", () -> c.admBlackSky, v -> c.admBlackSky = v)
                .desc("Makes the sky (and distant fog) black instead of blue. Pairs with Sodium Extra's Sky toggle.");
        b.toggle("  Blur In GUI", () -> c.admBlurInGui, v -> c.admBlurInGui = v)
                .desc("Blurs the world behind any open GUI — a chest, the Bazaar, your inventory. The HUD and the GUI itself stay sharp.");
        b.slider("    Blur Strength", 1f, 10f, () -> (float) c.admBlurStrength, v -> c.admBlurStrength = Math.round(v), 0)
                .desc("How far the blur reaches, in pixels. 10 is as far as the game's own blur shader goes.");
        b.toggle("  No Enchant Glint", () -> c.admNoGlint, v -> c.admNoGlint = v)
                .desc("Removes the enchantment glint from items, so colours and textures stay readable.");
        b.toggle("  Hide Own Arrows Nearby", () -> c.admHideOwnArrows, v -> c.admHideOwnArrows = v)
                .desc("Arrows you shoot are not drawn while they fly within 5 blocks of you.");
        b.toggle("  Auto Join Skyblock", () -> c.admAutoJoinSkyblock, v -> c.admAutoJoinSkyblock = v)
                .desc("Each time you connect to Hypixel (Prism's join on launch, the server list...), /skyblock goes out right after you land, and again until you're on Skyblock.");
        b.slider("    Skyblock Join Delay (s)", 0f, 3f, () -> c.admSkyblockJoinDelay, v -> c.admSkyblockJoinDelay = Math.round(v * 20f) / 20f, 2)
                .desc("How long after landing on Hypixel the first /skyblock goes out.");
        b.toggle("  Hub Nametag Stats", () -> c.admHubNametags, v -> c.admHubNametags = v)
                .desc("In the Dungeon Hub, Catacombs level, secrets and the S+ PB on the PB Floor over players' nametags.");
        b.dropdown("    PB Floor", PB_FLOORS, () -> c.admPbFloor, v -> c.admPbFloor = v)
                .desc("Which floor's S+ PB the nametag stats show.");
        b.slider("    Gap", 0f, 1f, () -> c.admNametagGap, v -> c.admNametagGap = Math.round(v * 20f) / 20f, 2)
                .desc("Extra space between a player's nametag and the stat line, in blocks.");
        b.toggle("  Clean Menus", () -> c.admCleanMenus, v -> c.admCleanMenus = v)
                .desc("Title screen: no Friends, Language or Accessibility buttons. Esc menu: no Advancements, Statistics, Server Links or icon row (bugs, feedback, friends, player reporting), and Options shares its row with a Mods button.");
        b.toggle("  Clean Loading Screen", () -> c.admCleanLoadingScreen, v -> c.admCleanLoadingScreen = v)
                .desc("No \"Loading terrain\" screen when joining or changing world: the world shows as it loads in.");
        b.toggle("  Clean Scoreboard", () -> c.admCleanScoreboard, v -> c.admCleanScoreboard = v)
                .desc("Hides the noisy sidebar lines: date and server, clock, season, other locations, objective, Keys, pre-start countdown, Solo, www.hypixel.net, blank spacers and the title, plus the three below. Purse, Bits and teammates always stay. Off, the sidebar is left alone.");
        b.toggle("    Scoreboard: Hide Catacombs Location", () -> c.admSbHideCatacombs, v -> c.admSbHideCatacombs = v)
                .desc("Hides the location line in dungeons: The Catacombs (F1-F7, M1-M7, E).");
        b.toggle("    Scoreboard: Hide Time Elapsed", () -> c.admSbHideElapsed, v -> c.admSbHideElapsed = v)
                .desc("Hides the dungeon's Time Elapsed line.");
        b.toggle("    Scoreboard: Hide Cleared %", () -> c.admSbHideCleared, v -> c.admSbHideCleared = v)
                .desc("Hides the dungeon's Cleared: #% (#) line.");
        b.toggle("  Clean Chat", () -> c.admCleanChat, v -> c.admCleanChat = v)
                .desc("Hides the chat lines its rules mark Block (built in, or config/teslamaps/adm/chat-hider.json). Other mods still see every line.");
        b.toggle("    Log Hidden Lines", () -> c.admLogHiddenLines, v -> c.admLogHiddenLines = v)
                .desc("Writes every hidden line and the rule that hid it to the ADM session log, for checking a rule.");
        b.button("    Reload Rules", ChatHider::reload)
                .desc("Re-reads config/teslamaps/adm/chat-hider.json (or the built-in rules if that file is absent).");
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        on = c.admRandomStuff && Adm.on();
        ScoreboardLines.hideLines = on && c.admCleanScoreboard;
        ScoreboardLines.hideCatacombsLocation = c.admSbHideCatacombs;
        ScoreboardLines.hideTimeElapsed = c.admSbHideElapsed;
        ScoreboardLines.hideCleared = c.admSbHideCleared;
        tickSkyblockJoin();
        tickCursorReset();
    }

    public static boolean on() {
        return on;
    }

    private static void onChat(String raw) {
        if (!cfg().admBlessOnLeave) return;
        String msg = AdmMisc.strip(raw);
        if (!PARTY_LEAVE.matcher(msg).matches()) return;
        long now = System.currentTimeMillis();
        if (now - lastBless < BLESS_COOLDOWN_MS) return;
        lastBless = now;
        com.teslamaps.features.ChatQueue.command("pc bless");
    }

    // --- Auto Join Skyblock ---

    private static void onJoin(Connection connection, Minecraft client) {
        String ip = client.getCurrentServer() == null ? "" : client.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
        pendingSkyblockJoin = false;
        ticksUntilSkyblock = -1;
        if (!on || !cfg().admAutoJoinSkyblock || !ip.contains("hypixel")) return;
        if (connection == skyblockConnection) {
            if (pendingSkyblockJoin && attempts > 0) ticksUntilSkyblock = TRANSFER_TICKS;
            return;
        }
        skyblockConnection = connection;
        pendingSkyblockJoin = true;
        attempts = 0;
        ticksUntilSkyblock = (int) (cfg().admSkyblockJoinDelay * 20);
    }

    /** ClientboundRespawnPacket handled: the transfer after a /skyblock is under way. */
    public static void onRespawn() {
        if (!on || !cfg().admAutoJoinSkyblock || !pendingSkyblockJoin || attempts == 0) return;
        ticksUntilSkyblock = TRANSFER_TICKS;
    }

    private static void tickSkyblockJoin() {
        if (!pendingSkyblockJoin || ticksUntilSkyblock < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!on || !cfg().admAutoJoinSkyblock || SkyblockUtils.isInSkyblock() || attempts >= MAX_ATTEMPTS) {
            pendingSkyblockJoin = false;
            ticksUntilSkyblock = -1;
            return;
        }
        if (mc.player == null || ticksUntilSkyblock-- > 0) return;
        attempts++;
        if (mc.getConnection() != null) mc.getConnection().sendCommand("skyblock");
        ticksUntilSkyblock = RETRY_TICKS;
    }

    // --- Force Cursor Reset ---

    private static void tickCursorReset() {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.gui.screen();
        if (s == null || !termCursorResetNow()) { cursorScreen = null; return; }
        if (s != cursorScreen) { cursorScreen = s; cursorTicks = 0; }
        if (cursorTicks++ >= 2) return;
        var w = mc.getWindow();
        ((com.teslamaps.utils.CursorAccess) mc.mouseHandler).teslamaps$setCursor(w.getScreenWidth() / 2.0, w.getScreenHeight() / 2.0);
    }

    public static boolean forcesTermCursorReset() {
        return on && cfg().admForceCursorReset;
    }

    public static boolean termCursorResetNow() {
        return forcesTermCursorReset() && inTerminal();
    }

    private static boolean inTerminal() {
        return TerminalUtils.getCurrentTerm() != null || Minecraft.getInstance().gui.screen() instanceof TermSimGUI;
    }

    // --- read by the mixins ---

    public static boolean skipsLoadingScreen() {
        return on && cfg().admCleanLoadingScreen;
    }

    public static boolean cleansMenus() {
        return on && cfg().admCleanMenus;
    }

    public static boolean cleansChat() {
        return on && cfg().admCleanChat;
    }

    public static boolean logsHiddenChat() {
        return cfg().admLogHiddenLines;
    }

    public static boolean showsPartyFinderStats() {
        return on && cfg().admPartyFinderStats;
    }

    public static boolean showsHubNametags() {
        return on && cfg().admHubNametags;
    }

    public static boolean blackSkyActive() {
        return on && cfg().admBlackSky;
    }

    public static boolean hidesActionBar() {
        return on && cfg().admHideActionBar;
    }

    public static boolean signEnterFinishes(int key) {
        return on && cfg().admEnterConfirmsSign && SkyblockUtils.inSkyblockCached()
                && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER);
    }

    public static boolean hidesGlint(ItemDisplayContext context) {
        if (!on || !cfg().admNoGlint) return false;
        return !(context == ItemDisplayContext.GUI && inTerminal());
    }

    public static boolean hidesArmorGlint() {
        return on && cfg().admNoGlint;
    }

    public static boolean hidesOwnArrow(AbstractArrow arrow, boolean inGround) {
        if (!on || !cfg().admHideOwnArrows || inGround) return false;
        var me = Minecraft.getInstance().player;
        return me != null && arrow.getOwner() == me && arrow.distanceToSqr(me) < 25.0;
    }

    public static boolean blursGui() {
        Minecraft mc = Minecraft.getInstance();
        return on && cfg().admBlurInGui && mc.gui.screen() != null && mc.level != null;
    }

    public static boolean blursAfterReset() {
        return buildingGui && blursGui();
    }

    public static int blurRadius() {
        return Math.max(1, Math.min(10, cfg().admBlurStrength));
    }

    public static boolean hidesItemNames() {
        return on && cfg().admHideItemNames;
    }

    /** Mod Menu's mod list over {@code parent}, or null without Mod Menu. */
    public static Screen modsScreen(Screen parent) {
        try {
            return (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(parent);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void cleanTitleScreen(TitleScreen screen) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        List<AbstractWidget> icons = new ArrayList<>();
        for (AbstractWidget w : widgets) {
            if (w instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t
                    && TITLE_ICON_KEYS.contains(t.getKey())) icons.add(w);
        }
        if (icons.isEmpty()) return;
        int row = icons.get(0).getY();
        widgets.removeAll(icons);
        for (AbstractWidget w : widgets) if (w.getY() > row && !(w instanceof PlainTextButton)) w.setY(w.getY() - 24);
    }
}
