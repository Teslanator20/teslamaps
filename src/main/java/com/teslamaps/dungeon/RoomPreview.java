package com.teslamaps.dungeon;

// Ported from Skyblocker's RoomPreview.java / RoomPreviewServer.java / RoomStructure.java
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

public final class RoomPreview {

    private static final String SAVE = "teslamaps-room-preview";
    // pinned so the block palette below always matches the data
    private static final String BASE = "https://raw.githubusercontent.com/SkyblockerMod/Skyblocker/"
            + "087e1224763debcbb4863abdb35c2a7b09fa433b/src/main/resources/assets/skyblocker/dungeons/catacombs/";
    private static final Path CACHE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("room_preview");
    private static final String[] PALETTE = {
            "minecraft:stone", "minecraft:diorite", "minecraft:polished_diorite", "minecraft:andesite",
            "minecraft:polished_andesite", "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
            "minecraft:cobblestone", "minecraft:bedrock", "minecraft:oak_leaves", "minecraft:gray_wool",
            "minecraft:smooth_stone", "minecraft:mossy_cobblestone", "minecraft:clay", "minecraft:stone_bricks",
            "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:gray_terracotta",
            "minecraft:cyan_terracotta", "minecraft:black_terracotta"};
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private record Secret(String name, String category, BlockPos pos, AABB box, Vec3 label) {
        Secret(String name, String category, BlockPos pos) {
            this(name, category, pos, new AABB(pos), new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5));
        }
    }

    private static List<String[]> index;
    private static boolean active, loading, teleported;
    private static String roomName = "";
    private static final List<Secret> secrets = new ArrayList<>();
    private static Vec3 center = Vec3.ZERO;

    private RoomPreview() {}

    public static void register() {
        Feature.of("RoomPreview").when(() -> active)
                .tick(RoomPreview::tick)
                .world(RoomPreview::render)
                .config("Secrets & Waypoints", b -> {
                    b.label("Room Preview");
                    b.info("/tmap roompreview <room> builds a dungeon room in an empty world");
                    b.info("Room data is downloaded once from Skyblocker's GitHub");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("roompreview")
                        .then(ClientCommands.argument("room", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                    for (String[] r : index()) {
                                        if (r[1].toLowerCase(Locale.ROOT).startsWith(rem) && !r[1].equalsIgnoreCase(rem)) b.suggest(r[1]);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> start(c.getSource(), StringArgumentType.getString(c, "room")))))));
    }

    private static List<String[]> index() {
        if (index != null) return index;
        index = new ArrayList<>();
        try (InputStream in = RoomPreview.class.getResourceAsStream("/assets/teslamaps/rooms/preview_index.txt")) {
            if (in == null) return index;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                int bar = line.indexOf('|');
                if (bar <= 0) continue;
                String path = line.substring(0, bar).trim();
                String name = line.substring(bar + 1).trim();
                index.add(new String[]{path, name.isEmpty() ? path.substring(path.indexOf('/') + 1) : name});
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("room preview index failed: {}", e.toString());
        }
        return index;
    }

    private static String[] find(String q) {
        String s = q.trim();
        for (String[] r : index()) if (r[1].equalsIgnoreCase(s)) return r;
        for (String[] r : index()) {
            if (r[0].equalsIgnoreCase(s) || r[0].substring(r[0].indexOf('/') + 1).equalsIgnoreCase(s)) return r;
        }
        return null;
    }

    private static int start(FabricClientCommandSource src, String query) {
        String[] room = find(query);
        if (room == null) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] Unknown room '" + query + "'."));
            return 0;
        }
        if (loading) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] A room is already loading."));
            return 0;
        }
        if (!confirmed(src, room[1])) return 1;
        loading = true;
        src.sendFeedback(Component.literal("§b[TeslaMaps] Loading " + room[1] + "..."));
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] skel = cached(room[0] + ".skeleton");
                byte[] json = cached(room[0] + ".json");
                int[] blocks;
                try (ObjectInputStream in = new ObjectInputStream(new InflaterInputStream(new ByteArrayInputStream(skel)))) {
                    in.setObjectInputFilter(java.io.ObjectInputFilter.Config.createFilter("[I;maxdepth=1;!*"));
                    blocks = (int[]) in.readObject();
                }
                JsonObject data = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
                return new Object[]{blocks, data};
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("room preview download failed: {}", e.toString());
                return null;
            }
        }).thenAccept(res -> Minecraft.getInstance().execute(() -> {
            loading = false;
            Minecraft mc = Minecraft.getInstance();
            if (res == null) {
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] Could not load the room data."));
                return;
            }
            open(room[1], (int[]) res[0], (JsonObject) res[1]);
        }));
        return 1;
    }

    private static String confirmRoom;
    private static long confirmAt;

    /** Leaving a server needs the command twice within 10 s, or a click on the chat line. */
    private static boolean confirmed(FabricClientCommandSource src, String room) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getSingleplayerServer() != null) return true;
        long now = System.currentTimeMillis();
        if (room.equals(confirmRoom) && now - confirmAt <= 10_000) {
            confirmRoom = null;
            return true;
        }
        confirmRoom = room;
        confirmAt = now;
        src.sendFeedback(Component.literal("§e[TeslaMaps] Room preview disconnects you from this server. ")
                .append(Component.literal("§a§l[Confirm]").withStyle(st -> st
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/tmap roompreview " + room))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                Component.literal("§7Disconnect and open " + room)))))
                .append(Component.literal(" §7or run the command again within 10s.")));
        return false;
    }

    private static byte[] cached(String rel) throws Exception {
        Path f = CACHE.resolve(rel);
        if (Files.exists(f) && Files.size(f) > 0) return Files.readAllBytes(f);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + rel)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "TeslaMaps").GET().build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode() + " for " + rel);
        Files.createDirectories(f.getParent());
        Files.write(f, resp.body());
        return resp.body();
    }

    private static void open(String name, int[] blocks, JsonObject data) {
        if (blocks.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, structure(blocks));

        secrets.clear();
        if (data.has("secrets") && data.get("secrets").isJsonArray()) {
            for (JsonElement el : data.getAsJsonArray("secrets")) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    secrets.add(new Secret(o.get("secretName").getAsString(), o.get("category").getAsString(),
                            new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt())));
                } catch (Exception ignored) {
                }
            }
        }

        if (mc.level != null) mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        deleteSave(mc.getLevelSource().getLevelPath(SAVE));

        active = true;
        teleported = false;
        roomName = name;
        mc.createWorldOpenFlows().createFreshLevel(SAVE,
                new LevelSettings(SAVE, GameType.SPECTATOR, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                        true, WorldDataConfiguration.DEFAULT),
                new WorldOptions(SAVE.hashCode(), false, false),
                lookup -> WorldPresets.createFlatWorldDimensions(lookup).replaceOverworldGenerator(lookup,
                        new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(),
                                lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID), List.of()))),
                new TitleScreen());

        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            active = false;
            return;
        }
        server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
        server.getGameRules().set(GameRules.SPAWN_MOBS, false, server);

        var size = template.getSize();
        center = new Vec3((size.getX() + 1) / 2.0, 80, (size.getZ() + 1) / 2.0);
        List<Secret> marks = new ArrayList<>(secrets);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            template.placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO, new StructurePlaceSettings(), level.getRandom(), 2);
            level.getWorldBorder().setCenter(center.x, center.z);
            level.getWorldBorder().setSize(Math.max(size.getX(), size.getZ()) + 2);
            for (Secret s : marks) {
                Block b = markerBlock(s.category());
                if (b != null) level.setBlockAndUpdate(s.pos(), b.defaultBlockState());
            }
        });
    }

    private static CompoundTag structure(int[] data) {
        int minX = 255, minZ = 255, maxX = 0, maxY = 0, maxZ = 0;
        ListTag blocks = new ListTag();
        for (int n : data) {
            int x = (n >> 24) & 255, y = (n >> 16) & 255, z = (n >> 8) & 255, type = n & 255;
            if (type < 1 || type > PALETTE.length) continue;
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            CompoundTag b = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(IntTag.valueOf(x));
            pos.add(IntTag.valueOf(y));
            pos.add(IntTag.valueOf(z));
            b.put("pos", pos);
            b.put("state", IntTag.valueOf(type - 1));
            blocks.add(b);
        }
        ListTag palette = new ListTag();
        for (String id : PALETTE) {
            CompoundTag p = new CompoundTag();
            p.put("Name", StringTag.valueOf(id));
            palette.add(p);
        }
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(maxX - Math.min(minX, maxX)));
        size.add(IntTag.valueOf(maxY));
        size.add(IntTag.valueOf(maxZ - Math.min(minZ, maxZ)));
        CompoundTag root = new CompoundTag();
        root.put("size", size);
        root.put("palette", palette);
        root.put("blocks", blocks);
        return root;
    }

    private static Block markerBlock(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "bat" -> Blocks.GREEN_STAINED_GLASS;
            case "chest" -> Blocks.CHEST;
            case "fairysoul" -> Blocks.PINK_STAINED_GLASS;
            case "item" -> Blocks.BLUE_STAINED_GLASS;
            case "lever" -> Blocks.LEVER;
            case "superboom" -> Blocks.TNT;
            case "wither", "redstone_key" -> Blocks.PLAYER_HEAD;
            default -> null;
        };
    }

    private static int color(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "chest" -> 0xFF55FF55;
            case "item" -> 0xFF5555FF;
            case "bat" -> 0xFF55FFFF;
            case "wither", "redstone_key" -> 0xFF222222;
            case "lever" -> 0xFFFFAA00;
            case "superboom" -> 0xFFFF5555;
            case "fairysoul" -> 0xFFFF55FF;
            case "entrance" -> 0xFFFFFF55;
            default -> 0xFFAAAAAA;
        };
    }

    private static void deleteSave(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("room preview cleanup failed: {}", e.toString());
        }
    }

    private static boolean inPreview() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && SAVE.equals(server.getWorldData().getLevelName());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!inPreview()) {
            active = false;
            return;
        }
        if (teleported) return;
        teleported = true;
        IntegratedServer server = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        Vec3 c = center;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            sp.teleportTo(c.x, c.y, c.z);
            sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false));
        });
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] Room Preview: §f" + roomName
                + " §7(" + secrets.size() + " secrets, /gamemode works here)"));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (Secret s : secrets) {
            ESPRenderer.drawBoxOutline(pose, s.box(), color(s.category()), 2f, cam, false);
            ESPRenderer.drawText(pose, s.name(), s.label(), 1.0f, cam);
        }
    }
}
