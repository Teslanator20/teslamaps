package com.teslamaps.features;

// Idea from SkyHanni's DebugCommand.kt / ComputerEnvDebug.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DebugDump {

    private DebugDump() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("debug")
                        .then(ClientCommands.literal("dump")
                                .executes(c -> dump(c.getSource(), ""))
                                .then(ClientCommands.argument("search", StringArgumentType.greedyString())
                                        .suggests((c, sb) -> {
                                            sb.suggest("all");
                                            return sb.buildFuture();
                                        })
                                        .executes(c -> dump(c.getSource(), StringArgumentType.getString(c, "search"))))))));
    }

    private record Section(String title, List<String> lines, boolean normal) {}

    private static int dump(FabricClientCommandSource src, String arg) {
        String search = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
        boolean all = search.equals("all");
        List<Section> sections = collect();

        StringBuilder sb = new StringBuilder("```\nTeslaMaps debug dump");
        if (!search.isEmpty() && !all) sb.append(" (search: ").append(search).append(")");
        sb.append("\n");
        int shown = 0, hidden = 0;
        for (Section s : sections) {
            boolean match = search.isEmpty() || all || s.title.toLowerCase(Locale.ROOT).contains(search);
            if (!match) continue;
            if (s.normal && search.isEmpty()) {
                hidden++;
                continue;
            }
            shown++;
            sb.append("\n= ").append(s.title).append(" =\n");
            for (String l : s.lines) sb.append(l).append("\n");
        }
        if (hidden > 0) sb.append("\n(").append(hidden).append(" normal sections hidden, use /tmap debug dump all)\n");
        sb.append("```");

        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        src.sendFeedback(Component.literal("§b[TeslaMaps] §fCopied debug dump §7(" + shown + " sections)§f to the clipboard."));
        return 1;
    }

    private static List<Section> collect() {
        List<Section> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();

        List<String> env = new ArrayList<>();
        env.add("TeslaMaps: " + version("teslamaps"));
        env.add("Minecraft: " + version("minecraft") + ", Fabric Loader: " + version("fabricloader")
                + ", Fabric API: " + version("fabric-api"));
        env.add("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        env.add("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        Runtime rt = Runtime.getRuntime();
        env.add("Memory: " + (rt.totalMemory() - rt.freeMemory()) / 1048576 + " / " + rt.maxMemory() / 1048576 + " MB, "
                + rt.availableProcessors() + " cores");
        env.add("FPS: " + mc.getFps() + ", GUI scale: " + mc.getWindow().getGuiScale());
        env.add("Dev environment: " + FabricLoader.getInstance().isDevelopmentEnvironment());
        out.add(new Section("Environment", env, false));

        List<String> loc = new ArrayList<>();
        loc.add("In world: " + (mc.level != null) + ", server: " + (mc.getCurrentServer() == null ? "-" : mc.getCurrentServer().ip));
        loc.add("Hypixel: " + SkyblockUtils.isOnHypixel() + ", SkyBlock: " + SkyblockUtils.isInSkyblock());
        loc.add("Area: " + SkyblockUtils.getArea());
        if (mc.player != null) {
            var p = mc.player.blockPosition();
            loc.add("Position: " + p.getX() + " " + p.getY() + " " + p.getZ());
        }
        out.add(new Section("Location", loc, false));

        List<String> dungeon = new ArrayList<>();
        boolean inDungeon = DungeonManager.isInDungeon();
        dungeon.add("State: " + DungeonManager.getCurrentState() + ", floor: " + DungeonManager.getFloorName());
        dungeon.add("Boss area: " + DungeonManager.isInBossArea() + ", boss flag: " + DungeonManager.isInBoss());
        out.add(new Section("Dungeon", dungeon, !inDungeon));

        List<String> cfg = new ArrayList<>();
        cfg.add("Profile: " + c.activeProfile);
        cfg.add("Legit Mode: " + c.legitMode + (c.legitMapOnly ? " (map only)" : "") + ", debug mode: " + c.debugMode);
        List<String> off = new ArrayList<>();
        for (String s : TeslaMapsConfig.SECTIONS) if (!c.section(s)) off.add(s);
        cfg.add("Sections off: " + (off.isEmpty() ? "none" : String.join(", ", off)));
        out.add(new Section("Config", cfg, !c.legitMode && !c.debugMode && off.isEmpty()));

        List<String> repo = new ArrayList<>();
        boolean repoOk = true;
        long now = System.currentTimeMillis();
        for (RepoCache.Status s : RepoCache.status()) {
            if (!"live".equals(s.source()) || s.error() != null) repoOk = false;
            repo.add(s.name() + ": " + s.source() + ", " + Math.max(0, (now - s.time()) / 60_000) + " min ago"
                    + (s.error() == null ? "" : " (" + s.error() + ")"));
        }
        if (repo.isEmpty()) repo.add("nothing loaded");
        out.add(new Section("Repo", repo, repoOk));

        List<String> board = new ArrayList<>();
        for (String l : ScoreboardUtils.getScoreboardLines()) {
            if (l != null) board.add(StringUtil.strip(l));
        }
        if (board.isEmpty()) board.add("empty");
        out.add(new Section("Scoreboard", board, false));

        List<String> mods = new ArrayList<>();
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            String id = m.getMetadata().getId();
            if (id.startsWith("fabric-") || id.equals("java") || id.equals("mixinextras")) continue;
            if (m.getContainingMod().isPresent()) continue;
            mods.add(id + " " + m.getMetadata().getVersion().getFriendlyString());
        }
        mods.sort(String::compareTo);
        out.add(new Section("Mods", mods, false));
        return out;
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("-");
    }
}
