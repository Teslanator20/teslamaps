package com.teslamaps.adm.misc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.MapConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's EngineerClient.kt, ClassDetect.kt and DeadPlayers.kt (no launch stats, no config migration)
public final class AdmCore {
    public static final List<String> COMMANDS = List.of("adm", "ADM", "advanceddungeonsmod", "eclient", "ea", "eec", "em");
    public static final List<String> HELP = new ArrayList<>(List.of(
            AdmMisc.PREFIX + "§7commands:",
            "§f /adm log§7 [mark <note>] — the session log; §f/adm memreport"));
    /** More lines for /adm log mark to stamp under the note. */
    public static final List<Supplier<List<String>>> MARK_LINES = new ArrayList<>();

    private static final List<Runnable> WORLD_LOAD = new ArrayList<>();
    private static Object lastLevel;
    private static int tickCounter;
    private static boolean windowKept, exclusiveChecked, sessionLogged;
    private static String detectedClass;

    private static final Pattern DIED = Pattern.compile("^ ☠ (\\w{1,16}) (?:.*became a ghost\\.|was killed by .*)$");
    private static final Pattern REVIVED = Pattern.compile("^ ❣ (\\w{1,16}) was revived\\b.*");
    private static final Pattern SELF_REVIVED = Pattern.compile("^(?:Your .* revived you\\b.*| ❣ You were revived\\b.*)");
    private static final Set<String> dead = new HashSet<>();

    private AdmCore() {}

    /** Runs on the first tick in a new world (Odin's LevelEvent.Load). */
    public static void onWorldLoad(Runnable r) {
        WORLD_LOAD.add(r);
    }

    public static void register() {
        String version = FabricLoader.getInstance().getModContainer(TeslaMaps.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        EcLog.open(Minecraft.getInstance().gameDirectory.toPath(), List.of("teslamaps " + version + " (adm port)"));
        onWorldLoad(() -> { detectedClass = null; dead.clear(); });
        Feature.of("AdmCore").tickAlways(AdmCore::tick);
        Feature.of("AdmDeadPlayers").section(Adm.SECTION).serverChat(AdmCore::onDeathChat);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            for (String name : COMMANDS) d.register(command(name));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommands.literal(name)
                .executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.schedule(() -> mc.gui.setScreen(MapConfigScreen.openAdm()));
                    return 1;
                })
                .then(ClientCommands.literal("help").executes(c -> {
                    for (String l : HELP) c.getSource().sendFeedback(Component.literal(l));
                    return 1;
                }))
                .then(ClientCommands.literal("log")
                        .executes(c -> {
                            c.getSource().sendFeedback(Component.literal(AdmMisc.PREFIX + "§7log: §f" + (EcLog.path() == null ? "not open" : EcLog.path())));
                            c.getSource().sendFeedback(Component.literal("§7 send that file to debug a run; §f/adm log mark <note>§7 stamps a note into it"));
                            return 1;
                        })
                        .then(ClientCommands.literal("mark")
                                .executes(c -> {
                                    mark("");
                                    c.getSource().sendFeedback(Component.literal(AdmMisc.PREFIX + "§7marked."));
                                    return 1;
                                })
                                .then(ClientCommands.argument("note", StringArgumentType.greedyString()).executes(c -> {
                                    String note = StringArgumentType.getString(c, "note");
                                    mark(note);
                                    c.getSource().sendFeedback(Component.literal(AdmMisc.PREFIX + "§7marked: §f" + note));
                                    return 1;
                                }))))
                .then(ClientCommands.literal("memreport").executes(c -> {
                    MemReport.write(AdmMisc::msg);
                    return 1;
                }));
    }

    private static void mark(String note) {
        EcLog.log("MARK", note.isBlank() ? "(no note)" : note);
        for (Supplier<List<String>> lines : MARK_LINES)
            for (String l : lines.get()) EcLog.log("MARK", "  " + l.replaceAll("§.", ""));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null) for (Runnable r : WORLD_LOAD) {
                try { r.run(); } catch (Throwable t) { TeslaMaps.LOGGER.error("[adm] world load handler failed", t); }
            }
        }
        if (!sessionLogged && mc.player != null) {
            EcLog.log("SESSION", "I am " + mc.player.getName().getString());
            sessionLogged = true;
        }
        if (!Adm.on()) return;
        if (++tickCounter % 20 == 0) pollClass();
        if (!windowKept) keepWindowUp(mc);
        if (!exclusiveChecked) noExclusiveFullscreen(mc);
    }

    private static boolean wayland() {
        return "wayland".equals(System.getenv("XDG_SESSION_TYPE"));
    }

    private static void noExclusiveFullscreen(Minecraft mc) {
        exclusiveChecked = true;
        if (!wayland()) return;
        var o = mc.options;
        if (!o.exclusiveFullscreen().get()) return;
        o.exclusiveFullscreen().set(false);
        if (com.teslamaps.features.WindowTweaks.isFullscreen(mc.getWindow())) o.fullscreen().set(false);
        o.save();
        TeslaMaps.LOGGER.info("[adm] exclusive fullscreen off (it breaks on Wayland once the game loses focus)");
    }

    private static void keepWindowUp(Minecraft mc) {
        if (!wayland()) { windowKept = true; return; }
        long h = mc.getWindow().handle();
        if (h == 0L) return;
        org.lwjgl.sdl.SDLHints.SDL_SetHint(org.lwjgl.sdl.SDLHints.SDL_HINT_VIDEO_MINIMIZE_ON_FOCUS_LOSS, "0");
        if ((org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(h) & org.lwjgl.sdl.SDLVideo.SDL_WINDOW_MINIMIZED) != 0) org.lwjgl.sdl.SDLVideo.SDL_RestoreWindow(h);
        windowKept = true;
    }

    private static void pollClass() {
        if (!com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;
        String clazz = PlayerTracker.getLocalClass();
        if (clazz == null || "EMPTY".equals(clazz) || "DEAD".equals(clazz) || clazz.equals(detectedClass)) return;
        detectedClass = clazz;
        TeslaMaps.LOGGER.info("[adm] detected own class from tab: {}", clazz);
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!clazz.equals(c.admLastKnownClass)) {
            c.admLastKnownClass = clazz;
            TeslaMapsConfig.save();
        }
    }

    /** Live tab detection, else the last class seen ("Mage", "Berserk", ...). Null: never seen one. */
    public static String myClass() {
        String c = detectedClass != null ? detectedClass : TeslaMapsConfig.get().admLastKnownClass;
        if (c == null) return null;
        return "Berserker".equalsIgnoreCase(c) ? "Berserk" : c;
    }

    private static void onDeathChat(String raw) {
        String line = com.teslamaps.utils.StringUtil.strip(raw);
        if (!line.startsWith(" ☠ ") && !line.startsWith(" ❣ ") && !line.startsWith("Your ")) return;
        Minecraft mc = Minecraft.getInstance();
        String me = mc.player == null ? null : mc.player.getGameProfile().name().toLowerCase(Locale.ROOT);
        Matcher m = DIED.matcher(line);
        if (m.matches()) {
            String n = "You".equals(m.group(1)) ? me : m.group(1).toLowerCase(Locale.ROOT);
            if (n != null) dead.add(n);
            return;
        }
        m = REVIVED.matcher(line);
        if (m.matches()) {
            String n = "You".equals(m.group(1)) ? me : m.group(1).toLowerCase(Locale.ROOT);
            if (n != null) dead.remove(n);
            return;
        }
        if (SELF_REVIVED.matcher(line).matches() && me != null) dead.remove(me);
    }

    /** Dead right now: the chat said so, or the tab list does. */
    public static boolean isDead(String name) {
        if (dead.contains(name.toLowerCase(Locale.ROOT))) return true;
        return PlayerTracker.getPlayer(name).map(p -> !p.isAlive()).orElse(false);
    }
}
