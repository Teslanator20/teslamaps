package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/P3Sim.kt (the ADS module; Odin's bridge points at TeslaMaps' DungeonManager instead)

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.sim.run.AdsBootstrap;
import com.teslamaps.adm.sim.run.AdsCommands;
import com.teslamaps.adm.sim.run.ClientRun;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.KeySetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SelSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class P3Sim {
    private P3Sim() {}

    public static final String NAME = "ADS";
    public static final String FULL_NAME = "Advanced Dungeon Simulator";

    public enum ClassOption { HEALER, BERSERK, ARCHER, TANK, MAGE }
    public enum DeathTickOption { OFF, WARN, MASKS }
    public enum TerminalOption { RANDOM, ORDER, PANES, RUBIX, STARTS_WITH, SELECT, MELODY }
    public enum MaskOption { SPIRIT, BONZO }

    public static final KeySetting menuKey = SimSettings.key("Menu Keybind", -1, "Opens the ADS menu (a big Restart) in the sim world, as /ads and the SkyBlock Menu star in your hotbar do; the full menu is in Esc. Outside it, opens the sim.").onPress(P3Sim::openMenuOrSim);
    public static final KeySetting restartKey = SimSettings.key("Restart Keybind", -1, "In the sim: starts whatever you last started again (P3, S2, P2...), from scratch; in practice, the practice.").onPress(() -> {
        if (inSim()) SimActions.send("restart");
    });
    public static final KeySetting practiceKey = SimSettings.key("Practice Restart Keybind", -1, "In the sim: starts your practice (the menu's Practice tab) again. A left click with the Infinileap does too.").onPress(() -> {
        if (inSim()) SimActions.send("practiceRestart");
    });

    public static final SelSetting classS = SimSettings.sel("Your Class", ClassOption.ARCHER, "Your dungeon class (Odin's party list and leap menu). The four bots are the other classes. What you do in P3 is the menu's Plan tab.");
    public static final NumSetting speedS = SimSettings.num("Speed", 500, 100, 750, 10, "", "Your Skyblock speed without Black Cat. Black Cat adds 100 (and 100 to the speed cap), so 450 is 550 with it out, as in recorded P3 starts (1.55 blocks a tick sprinting); Phoenix out has no Black Cat bonus.");
    public static final BoolSetting botsS = SimSettings.bool("Party Bots", true, "The rest of the party's terminals, levers, devices and gates are always done at the plan's times; on, four bots show it (a late one is put on its job). Off: nobody's shown, the plan still runs.");
    public static final SelSetting deathTicksS = SimSettings.sel("Death Ticks", DeathTickOption.MASKS, "Goldor's death tick (every 60 ticks, hits anyone in a section ahead): Warn only says so; Masks uses your Spirit Mask, Bonzo's Mask and Phoenix as Hypixel does, and with none left you die (back to the section's start).");
    public static final SelSetting terminalS = SimSettings.sel("Terminals", TerminalOption.RANDOM, "Every terminal as this type, or random as on Hypixel.");
    public static final NumSetting pingS = SimSettings.num("Simulated Ping", 0, 0, 300, 10, "ms", "Delays the server's answer to your clicks and items by this much, like playing on Hypixel with that ping.");
    public static final BoolSetting jitterS = SimSettings.bool("Ping Jitter", true, "Simulated Ping varies like a real connection: usually a few ms either way, now and then 30+ ms more, rarely a lag spike (spread recorded on Hypixel). Off: a fixed delay.");
    public static final NumSetting goldorKillS = SimSettings.num("Goldor Kill Time", 20, 10, 120, 1, " ticks", "How long after Goldor starts flying to the core his \"....\" line comes, i.e. he dies (recorded runs: median 43, range 17-74).");
    public static final NumSetting shortbowCooldownS = SimSettings.num("Shortbow Cooldown", 2, 1, 20, 1, " ticks", "Ticks between shots of the Terminator, Spirit Shortbow and Mosquito Shortbow: 5 at 100% attack speed, on every bow and whatever Terror armor you wear (recordings: Terminator 5, Mosquito 5). A click inside it fires when it ends. Nasty Bite has its own 10.");
    public static final NumSetting hydraStartS = SimSettings.num("Hydra Stacks At Start", 10, 0, 10, 1, "", "Hydra Strike stacks every start from the menu (P1, P3, a section...) begins with. Going on from one phase to the next keeps what you have.");
    public static final BoolSetting clickLimitS = SimSettings.bool("Terminal Click Limit", true, "As on Hypixel: a terminal takes at most 5 clicks in any 10 ticks; the rest are dropped without an answer.");
    public static final BoolSetting noMelodiesS = SimSettings.bool("No Melodies", true, "Random terminals are never melodies.");
    public static final NumSetting termLuckS = SimSettings.num("Terminal Luck", 0, -100, 100, 5, "%", "0: terminals as Hypixel deals them. Higher: Click in Order's numbers closer together (a neat path at 100%), and fewer clicks in the others (more panes already on, fewer Rubix clicks, fewer items to pick); lower (bad luck): the other way. Toward 40% either way it picks Hypixel's easiest (or hardest) deals; past it, beyond anything Hypixel deals, down to 1 click at 100% and up to the worst at -100% (no pane on, a 10-click Rubix, 16 items to pick, 12 to select, the numbers all over). Melody: each row's purple where the green gets soonest (latest with bad luck). The terminal type: Melodies rarer (more common with bad luck), when they're on.");
    public static final NumSetting ssLuckS = SimSettings.num("SS Luck", 0, -100, 100, 5, "%", "0: Simon Says as Hypixel runs it. Higher: the 4 lamps closer together, steadily all the way up (4 neighbours in a line at 100%), and after a wrong press the quick restart (no stray lamp) more often, always at 100%. Lower (bad luck): the lamps spread across the board (corner to corner at -100%) and the slow restart, with a stray lamp.");
    public static final BoolSetting mageAllS = SimSettings.bool("Mage Beam On All Classes", false, "Mage Beam (a left click with a sword or the Bonzo Staff) and Guided Sheep whatever your class. Ctrl+Q fires the sheep (as Archer it stays Explosive Shot: 3 quick beam clicks fire the sheep).");
    public static final BoolSetting firstClickMelodyS = SimSettings.bool("Always First Click Melody", false, "Melody's first row always has its purple in the first slot, where the green starts: lock it at once. The other rows are as usual.");
    public static final BoolSetting recordS = SimSettings.bool("Record Runs", false, "Writes each run, tick by tick (you, the bots, what's left, chat), to config/teslamaps/adm/p3sim-runs, to look at what went wrong. After 50 runs in a row it turns itself off (nothing's deleted); turn it back on for 50 more.");
    public static final BoolSetting debugBotsS = SimSettings.bool("Debug Bots", false, "Chat lines for everything the P3 bots do: where they head and why, jobs, leaps, early enters (on the spot, who they wait for, why they move on).");
    public static final NumSetting breakerRefillS = SimSettings.num("Dungeonbreaker Refill", 3, 1, 10, 1, "/s", "Charges back each second (20 max), in irregular +2 steps. Main server: ~6 a second; alpha ~2.");
    public static final NumSetting breakerRegenS = SimSettings.num("Dungeonbreaker Regen", 11.0, 1.0, 30.0, 0.5, "s", "How long a broken block stays broken (recordings: ~11 s; the 21st break brings back the oldest 41 ticks later).");
    public static final BoolSetting breakerInfiniteS = SimSettings.bool("Infinite Breaker Charges", false, "The Dungeonbreaker never runs out: every break is free (20 charges always).");
    public static final BoolSetting breakerPermaS = SimSettings.bool("Perma Break", false, "Blocks the Dungeonbreaker breaks stay broken (through restarts too) until this is turned off; then they all come back.");
    public static final BoolSetting realMasksS = SimSettings.bool("Real Masks", true, "Masks are real helmets: only the one you wear can save you, swap them in /stats (cooldowns stay with each mask). Off: whichever is ready saves you.");
    public static final SelSetting wornMaskS = SimSettings.sel("Starting Mask", MaskOption.SPIRIT, "Real Masks: the mask you wear (/stats swaps it).");
    public static final BoolSetting phoenixS = SimSettings.bool("Phoenix Pet", false, "Your pet: Phoenix (saves you from a death, no Black Cat speed bonus) or Black Cat (+100 speed). The Pet Rod swaps them.");
    public static final BoolSetting realMovesS = SimSettings.bool("Real Bot Movement", true, "P3 bots move as you did: each plays your best run as its class for the skill (P3 from S1), timed so its jobs are done at the plan's times (faster where the plan's tighter). A class with no run of yours: along routes recorded in ADR runs. Off: the old straight walk and Hyperion blinks.");
    public static final BoolSetting terrorAtTermsS = SimSettings.bool("Terror At Terms", false, "Every P3 start puts the Terror loadout on (Terror armour, Bonzo's Mask, Black Cat) over your saved gear.");
    public static final BoolSetting blackCatS = SimSettings.bool("Black Cat At Start", true, "Every start and restart puts Black Cat out. Off: the pet you had stays (as your items and gear do).");
    public static final BoolSetting lavaS = SimSettings.bool("Lava Bounce", true, "Lava bounces you up as on Hypixel. Off: plain vanilla lava (no damage).");
    public static final BoolSetting termsOnlyS = SimSettings.bool("Stop After Terms", false, "The run stops (as Stop does) when the core opens: no Goldor fight.");
    public static final BoolSetting p3OnlyS = SimSettings.bool("Stop After Goldor", false, "The run stops (as Stop does) when Necron would speak, after Goldor's death, instead of going on to Necron.");
    public static final BoolSetting autoStartS = SimSettings.bool("Start On Join", false, "Start P3 as soon as you join the sim world.");
    public static final BoolSetting showTimesS = SimSettings.bool("Section Times", true, "Each section's time in chat as it ends, and a summary at the core.");

    static {
        AdsBootstrap.registerSettings();
    }

    public static boolean autoStart() { return autoStartS.get(); }

    public static boolean showTimes() { return mine("showTimes", showTimesS::get, JsonElement::getAsBoolean); }

    private static <T> T mine(String key, Supplier<T> own, Function<JsonElement, T> read) {
        Players.Profile j = Players.joiner();
        if (j != null) {
            JsonElement v = j.vals.get(key);
            if (v != null) {
                try {
                    T r = read.apply(v);
                    if (r != null) return r;
                } catch (Throwable ignored) {
                }
            }
        }
        return own.get();
    }

    public static DungeonClass ownClass() { return Party.CLASSES().get(Math.clamp(classS.index(), 0, 4)); }

    public static DungeonClass myClass() {
        ServerPlayer a = Sim.acting();
        if (a != null && !Sim.isHost(a)) return Players.classOf(a);
        return ownClass();
    }

    public static int speed() { return mine("speed", speedS::getInt, e -> (int) e.getAsDouble()); }

    public static boolean bots() { return botsS.get(); }

    public static int deathTicks() { return deathTicksS.index(); }

    public static int ping() {
        int v = mine("ping", pingS::getInt, e -> (int) e.getAsDouble());
        ServerPlayer j = Players.joiner() != null ? Sim.acting() : null;
        if (j == null) return v;
        return Math.max(v - j.connection.latency(), 0);
    }

    public static boolean jitter() { return mine("jitter", jitterS::get, JsonElement::getAsBoolean); }

    public static int goldorKill() { return goldorKillS.getInt(); }

    public static boolean p3Only() { return p3OnlyS.get(); }

    public static boolean termsOnly() { return termsOnlyS.get(); }

    public static void toggleStopAfter(boolean terms) {
        BoolSetting it = terms ? termsOnlyS : p3OnlyS;
        BoolSetting other = terms ? p3OnlyS : termsOnlyS;
        it.set(!it.get());
        if (it.get()) other.set(false);
    }

    public static int shortbowCooldown() { return mine("shortbow", shortbowCooldownS::getInt, e -> (int) e.getAsDouble()); }

    public static int terrorPieces() {
        ServerPlayer p = Sim.player();
        if (p == null) return 0;
        int n = 0;
        for (EquipmentSlot s : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            String id = SimItems.idOf(p.getItemBySlot(s));
            if (id != null && id.startsWith("TERROR_")) n++;
        }
        return n;
    }

    public static int hydraStart() { return mine("hydra", hydraStartS::getInt, e -> (int) e.getAsDouble()); }

    public static boolean lava() { return lavaS.get(); }

    public static boolean noMelodies() { return noMelodiesS.get(); }

    public static double ssLuck() { return ssLuckS.get() / 100.0; }

    public static double termLuck() { return termLuckS.get() / 100.0; }

    public static boolean mageAll() { return mine("mageAll", mageAllS::get, JsonElement::getAsBoolean); }

    public static boolean firstClickMelody() { return firstClickMelodyS.get(); }

    public static boolean clickLimit() { return clickLimitS.get(); }

    public static boolean debugBots() { return debugBotsS.get(); }

    public static boolean record() { return recordS.get(); }

    /** Odin's Hide Players in ADM; here TeslaMaps' Hide Players. */
    public static boolean hidePlayers() { return TeslaMapsConfig.get().hidePlayers; }

    public static void toggleHidePlayers() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        c.hidePlayers = !c.hidePlayers;
        TeslaMapsConfig.save();
    }

    /** A bot (a Mannequin in the sim) our Hide Players would hide if it were a player. Client thread. */
    public static boolean hideBot(Entity e) {
        if (!hidePlayers() || !inSim() || !(e instanceof Mannequin)) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.hidePlayersAll) return true;
        double d = c.hidePlayersDistance;
        var me = Minecraft.getInstance().player;
        if (me == null) return false;
        return e.distanceToSqr(me) <= d * d;
    }

    public static int breakerRefill() { return breakerRefillS.getInt(); }

    public static double breakerRegen() { return breakerRegenS.get(); }

    public static boolean breakerInfinite() { return mine("infinite", breakerInfiniteS::get, JsonElement::getAsBoolean); }

    public static boolean breakerPerma() { return breakerPermaS.get(); }

    public static void toggleBreakerInfinite() { breakerInfiniteS.toggle(); }

    public static void toggleBreakerPerma() { breakerPermaS.toggle(); }

    public static boolean realMasks() { return mine("realMasks", realMasksS::get, JsonElement::getAsBoolean); }

    public static boolean phoenix() { return mine("phoenix", phoenixS::get, JsonElement::getAsBoolean); }

    public static int wornMask() { return mine("wornMask", wornMaskS::index, JsonElement::getAsInt); }

    public static void setPhoenix(boolean on) { setMine("phoenix", new JsonPrimitive(on), () -> phoenixS.set(on)); }

    public static void setWornMask(int i) { setMine("wornMask", new JsonPrimitive(i), () -> wornMaskS.setIndex(i)); }

    private static void setMine(String key, JsonElement v, Runnable own) {
        ServerPlayer a = Sim.acting();
        ServerPlayer p = a != null && !Sim.isHost(a) ? a : null;
        if (p == null) {
            own.run();
            ServerPlayer me = Sim.player();
            if (me != null) PartyServer.notePersonal(me);
            return;
        }
        Players.profile(p).vals.put(key, v);
        JsonObject o = new JsonObject();
        o.add(key, v);
        SimNet.send(p, "set", o.toString());
        PartyServer.notePersonal(p);
    }

    public static boolean realMoves() { return realMovesS.get(); }

    public static void toggleRealMoves() { realMovesS.toggle(); }

    public static boolean terrorAtTerms() { return mine("terror", terrorAtTermsS::get, JsonElement::getAsBoolean); }

    public static void toggleTerrorAtTerms() { terrorAtTermsS.toggle(); }

    public static boolean blackCatAtStart() { return mine("blackCat", blackCatS::get, JsonElement::getAsBoolean); }

    public static void toggleBlackCatAtStart() { blackCatS.toggle(); }

    public static Terminals.Type forcedTerminal() {
        int i = terminalS.index();
        return i == 0 ? null : Terminals.Type.values()[i - 1];
    }

    /** True only in the p3sim singleplayer world, your own (client side). */
    public static boolean localSim() {
        var s = SimServer.server;
        if (s == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() == s && mc.level != null;
    }

    /** True in a sim: your own, or a party's you joined over LAN (client side). */
    public static boolean inSim() {
        return localSim() || (P3Party.joined && Minecraft.getInstance().level != null);
    }

    /** ADM master and the ADM Simulator toggle. */
    public static boolean enabled() { return Adm.on() && TeslaMapsConfig.get().admSim; }

    public static void init() {
        AdsBootstrap.init();
        SimNet.register();
        PartyServer.register();
        PartyClient.register();
        SimServer.register();
        P3Plan.load();
        Practice.load();
        com.teslamaps.adm.adr.AdrSimHooks.adsWorld = () -> SimServer.server != null && Minecraft.getInstance().hasSingleplayerServer();
        com.teslamaps.adm.adr.AdrSimHooks.adsInRun = ClientRun::inRun;
        com.teslamaps.adm.adr.AdrSimHooks.p3SimWorld = P3Sim::inSim;
        com.teslamaps.adm.adr.AdrSimHooks.lastLaunch = () -> {
            var l = com.teslamaps.adm.sim.run.RunLauncher.lastLaunch;
            return l == null ? null : new Object[]{l.first, l.second};
        };
        SimHooks.onMasksReset = com.teslamaps.adm.misc.AdmInvincibility::resetTimers;
        SimHooks.onTickTimersStop = com.teslamaps.adm.misc.AdmTickTimers::stopAll;
        SimHooks.onTermStart = n -> {
            com.teslamaps.adm.misc.Timers.clearSectionTime();
            com.teslamaps.adm.leap.TermInfo.simStart(n);
        };
        SimHooks.onTermClearTime = () -> {
            com.teslamaps.adm.misc.Timers.clearSectionTime();
            com.teslamaps.adm.leap.TermInfo.simClearTime();
        };
        SimHooks.onTermSectionTime = n -> {
            if (inSim() && !Practice.active()) {
                com.teslamaps.adm.misc.Timers.showSectionTime(n);
                com.teslamaps.adm.leap.TermInfo.simSectionTime(n);
            }
        };
        SimHooks.onSplitsStart = com.teslamaps.adm.leap.splits.DungeonSplits::simStart;
        SimHooks.onSplitsStop = com.teslamaps.adm.leap.splits.DungeonSplits::simStop;
        SimHooks.onJoinHypixel = s -> {};
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> {
            dispatcher.register(Commands.literal("stats")
                    .requires(src -> src.getServer() == SimServer.server)
                    .executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p != null) Sim.asPlayer(p, () -> Masks.openStats(p));
                        return 1;
                    }));
            dispatcher.register(Commands.literal("loadouts")
                    .requires(src -> src.getServer() == SimServer.server)
                    .executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p != null) Sim.asPlayer(p, () -> Loadouts.open(p));
                        return 1;
                    }));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            for (String name : List.of("ads", "p3sim")) {
                LiteralArgumentBuilder<FabricClientCommandSource> cmd = ClientCommands.literal(name).executes(c -> {
                    if (gate()) openMenuOrSim();
                    return 1;
                });
                for (Fight.Start s : Fight.Start.values()) {
                    cmd.then(ClientCommands.literal(s.name().toLowerCase(Locale.ROOT)).executes(c -> {
                        if (!gate()) return 1;
                        if (inSim()) SimActions.send("start", Pair.of("what", s.name()));
                        else SimWorld.open();
                        return 1;
                    }));
                }
                cmd.then(ClientCommands.literal("stop").executes(c -> { SimActions.send("stop"); return 1; }));
                cmd.then(ClientCommands.literal("rebuild").executes(c -> { if (gate()) SimWorld.rebuild(); return 1; }));
                AdsCommands.attach(cmd);
                dispatcher.register(cmd);
            }
            dispatcher.register(ClientCommands.literal("pos").executes(c -> { pos(); return 1; }));
        });
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            Ec.safely("p3sim bridge", P3Sim::bridge);
            Ec.safely("p3sim client items", SimItems::clientTick);
            Ec.safely("p3sim party client", PartyClient::tick);
            Ec.safely("p3sim ghost guard", GhostGuard::clientTick);
            if (enabled()) Ec.safely("ads keys", SimSettings::tickKeys);
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen && enabled()) Ec.safely("ads title button", () -> titleButtons(screen, w));
            if (screen instanceof PauseScreen && inSim()) Ec.safely("p3sim pause button", () -> pauseButton(screen));
        });
    }

    private static boolean gate() {
        if (enabled()) return true;
        Ec.msg("§7ADS is off: turn on §fADM§7 and §fADM Simulator§7 in /tmap adg.");
        return false;
    }

    private static String key(Button b) {
        return b.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    private static void titleButtons(Screen screen, int w) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        List<Button> buttons = new ArrayList<>();
        for (AbstractWidget a : widgets) if (a instanceof Button b) buttons.add(b);
        Button single = null;
        for (Button b : buttons) if ("menu.singleplayer".equals(key(b))) { single = b; break; }
        int runW = 44;
        int runRoom = Ec.BETA ? runW + 4 : 0;
        Button.Builder ab = Button.builder(Component.literal(FULL_NAME), b -> SimWorld.open());
        Button ads = (single != null
                ? ab.bounds(single.getX(), single.getY() - single.getHeight() - 4, single.getWidth() - runRoom, single.getHeight())
                : ab.bounds(w - 164, 4, 160 - runRoom, 16)).build();
        widgets.add(ads);
        if (Ec.BETA) {
            Button.Builder rb = Button.builder(Component.literal("§bRun"), b -> com.teslamaps.adm.sim.clear.ui.UiModule.openStartScreen())
                    .tooltip(Tooltip.create(Component.literal("Starts a full F7 run: countdown, the dungeon, the blood room, then the boss. Choose the layout, party and lag first.")));
            Button run = (single != null
                    ? rb.bounds(single.getX() + single.getWidth() - runW, single.getY() - single.getHeight() - 4, runW, single.getHeight())
                    : rb.bounds(w - 4 - runW, 4, runW, 16)).build();
            widgets.add(run);
            int gx = single != null ? run.getX() + run.getWidth() + 4 : ads.getX() - 40;
            widgets.add(Button.builder(Component.literal("GUI"), b -> Minecraft.getInstance().setScreen(new AdsHubScreen(screen)))
                    .bounds(gx, ads.getY(), 36, ads.getHeight()).build());
        }
    }

    private static final Set<String> QUIT_KEYS = Set.of("menu.returnToMenu", "menu.disconnect");
    private static final Set<String> LAN_KEYS = Set.of("menu.shareToLan", "menu.multiplayerOptions.button");

    private static void pauseButton(Screen screen) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        List<Button> buttons = new ArrayList<>();
        for (AbstractWidget a : widgets) if (a instanceof Button b) buttons.add(b);
        Button lan = null, quit = null;
        for (Button b : buttons) if (LAN_KEYS.contains(key(b))) { lan = b; break; }
        for (Button b : buttons) if (QUIT_KEYS.contains(key(b))) { quit = b; break; }
        if (quit == null) for (Button b : buttons) if (quit == null || b.getY() > quit.getY()) quit = b;
        Button.Builder main = Button.builder(Component.literal("§6" + NAME + " Menu"), b -> Minecraft.getInstance().setScreen(new SimRestartScreen()));
        Button b;
        if (lan != null) {
            widgets.remove(lan);
            b = main.bounds(lan.getX(), lan.getY(), lan.getWidth(), lan.getHeight()).build();
        } else if (quit != null) {
            b = main.bounds(quit.getX(), quit.getY() + quit.getHeight() + 4, quit.getWidth(), 20).build();
        } else {
            b = main.bounds(4, 4, 110, 20).build();
        }
        widgets.add(b);
        if (lan != null || quit != null) {
            int dy = screen.height / 2 - (b.getY() + b.getHeight() / 2);
            for (AbstractWidget x : widgets) x.setY(x.getY() + dy);
        }
    }

    private static void pos() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) return;
        double y = Math.floor(p.getY() * 100 + 1e-6) / 100;
        String line = f(p.getX()) + ", " + f(y) + ", " + f(p.getZ()) + ", yaw " + f(Mth.wrapDegrees(p.getYRot())) + ", pitch " + f(p.getXRot());
        if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            var b = bh.getBlockPos();
            line += ", looking at " + b.getX() + ", " + b.getY() + ", " + b.getZ();
        }
        mc.keyboardHandler.setClipboard(line);
        Ec.msg("§f" + line + " §8(copied)");
    }

    private static String f(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    public static void openMenuOrSim() {
        if (!gate()) return;
        Minecraft mc = Minecraft.getInstance();
        if (inSim()) mc.execute(() -> mc.setScreen(new SimRestartScreen()));
        else SimWorld.open();
    }

    // TeslaMaps bridge (ADM: Odin's DungeonListener)

    private static volatile boolean clearMode = false;

    private static void bridge() {
        clearMode = inSim() && ClientRun.bridgeAsClear();
    }

    /**
     * What DungeonManager should believe in the sim: F7's boss in the practice and the boss stages of a run; null in the clear of a
     * run (there the sidebar and tab the sim sends are parsed as on Hypixel) and outside the sim.
     */
    public static DungeonState bridgeState() {
        if (!inSim() || clearMode) return null;
        return DungeonState.BOSS_FIGHT;
    }

    /** The sim pretends to be Hypixel for our location checks. */
    public static boolean pretendHypixel() { return inSim(); }

    // HUDs: the practice's splits, final time and start timer

    public static List<String> practiceLines(boolean example) {
        if (example) return List.of("§6Practice S2 §f6.45", "§7Lights §a2.10", "§7T3 §a4.85", "§7EE3 §e...");
        String mode = Practice.mode;
        if (!inSim() || mode == null || Practice.ticks < 0 || Practice.tasks.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add("§6Practice " + mode + " " + (Practice.endTicks >= 0 ? "§a" : "§f") + Practice.secs(Practice.ticks));
        Practice.Task next = null;
        for (Practice.Task t : Practice.tasks) if (t.at < 0) { next = t; break; }
        for (Practice.Task t : Practice.tasks)
            lines.add("§7" + t.label + " " + (t.at >= 0 ? "§a" + Practice.secs(t.at) : t == next ? "§e..." : "§8-"));
        return lines;
    }

    public static String practiceTime(boolean example) {
        if (example) return "§514.35";
        int e = Practice.endTicks;
        if (inSim() && Practice.active() && e >= 0 && System.currentTimeMillis() - Practice.endMs < 4000) return "§5" + Practice.secs(e);
        return null;
    }

    public static String practiceStart(boolean example) {
        if (example) return "§e2.4";
        int t = Practice.ticks;
        if (inSim() && Practice.active() && t < 0) return "§e" + String.format(Locale.ROOT, "%.1f", -t / 20.0);
        return null;
    }
}
