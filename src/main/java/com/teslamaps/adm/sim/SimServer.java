package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/SimServer.kt

import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.adm.sim.run.ArenaRegion;
import com.teslamaps.adm.sim.run.EngineModule;
import com.teslamaps.adm.sim.run.EngineSpikes;
import com.teslamaps.adm.sim.run.LagRuntime;
import com.teslamaps.adm.sim.run.Runs;
import com.teslamaps.adm.sim.run.WorldRegions;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

public final class SimServer {
    private SimServer() {}

    public static volatile MinecraftServer server = null;

    public static volatile Consumer<MinecraftServer> devTick = null;

    public static boolean isSim(MinecraftServer server) {
        return server instanceof IntegratedServer && server.getWorldData().getLevelName().equals(SimWorld.NAME)
                && (SimWorld.opening || SimWorld.isBuilt(server.getWorldPath(LevelResource.ROOT)));
    }

    public static boolean isSimLevel(Level level) {
        MinecraftServer s = server;
        if (s == null) return false;
        return level instanceof ServerLevel sl && sl.getServer() == s;
    }

    public static ServerLevel level() {
        MinecraftServer s = server;
        return s == null ? null : s.overworld();
    }

    public static ServerPlayer player() {
        MinecraftServer s = server;
        if (s == null) return null;
        List<ServerPlayer> l = s.getPlayerList().getPlayers();
        UUID me = Minecraft.getInstance().getUser().getProfileId();
        for (ServerPlayer p : l) if (p.getUUID().equals(me)) return p;
        return l.isEmpty() ? null : l.get(0);
    }

    public static void run(String what, Consumer<MinecraftServer> block) {
        MinecraftServer s = server;
        if (s == null) return;
        s.execute(() -> {
            if (server == s) Ec.safely("p3sim " + what, () -> Sim.asPlayer(null, () -> block.accept(s)));
        });
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            if (isSim(s)) {
                server = s;
                Ec.LOG.info("[p3sim] sim server starting");
                EngineSpikes.mark("server-starting");
            }
            SimWorld.opening = false;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            if (s != server) return;
            EngineSpikes.mark("server-started");
            Ec.safely("p3sim started", () -> {
                rules(s);
                commands(s);
                SimWorld.markBuilt(s.getWorldPath(LevelResource.ROOT));
                Fight.reset(s);
            });
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (s != server) return;
            Ec.safely("p3sim stopping run", () -> Runs.stop());
            Ec.safely("p3sim stopping", () -> {
                P3Party.stopHosting(false);
                Fight.stop();
                Party.clear();
                Sim.clearEntities();
                Blocks.restoreAll();
            });
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            if (s != server) return;
            server = null;
            Arena.unload();
            WorldRegions.worldStopped();
            ArenaRegion.reset();
            LagRuntime.reset();
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s == server) {
                Ec.safely("p3sim tick", () -> Fight.tick(s));
                Consumer<MinecraftServer> t = devTick;
                if (t != null) Ec.safely("ads dev tick", () -> t.accept(s));
                Ec.safely("ads lag", () -> EngineModule.lagTick(s));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            if (s == server) Ec.safely("p3sim join", () -> Fight.join(handler.player));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            if (s == server) Ec.safely("p3sim leave", () -> Fight.leave(handler.player));
        });
        SimItems.register();
    }

    private static void commands(MinecraftServer s) {
        s.getCommands().getDispatcher().register(
                Commands.literal("pc").then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
                    String msg = StringArgumentType.getString(c, "message");
                    ServerPlayer p = c.getSource().getPlayer();
                    String name = p != null ? p.getGameProfile().name() : "You";
                    Sim.everyone(() -> Sim.chat("§9Party §8> §b[MVP§4+§b] " + name + "§f: " + msg));
                    return 1;
                })));
        s.getCommands().getDispatcher().register(
                Commands.literal("gfs").then(Commands.argument("what", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    Sim.asPlayer(p, () -> getFromSacks(p, StringArgumentType.getString(c, "what")));
                    return 1;
                })));
    }

    private static final Map<String, Triple<String, Supplier<ItemStack>, Integer>> SACK_ITEMS = Map.of(
            "ender_pearl", Triple.of("Ender Pearl", SimItems::PEARLS, 16),
            "superboom_tnt", Triple.of("Superboom TNT", SimItems::SUPERBOOM, 64),
            "superboom", Triple.of("Superboom TNT", SimItems::SUPERBOOM, 64));

    private static void getFromSacks(ServerPlayer p, String what) {
        List<String> words = new ArrayList<>(Arrays.asList(what.trim().split("\\s+")));
        Integer amount;
        try {
            amount = Integer.parseInt(words.get(words.size() - 1));
        } catch (NumberFormatException e) {
            amount = null;
        }
        List<String> keyWords = amount != null ? words.subList(0, words.size() - 1) : words;
        String key = String.join("_", keyWords).toLowerCase(Locale.ROOT);
        if (key.endsWith("s")) key = key.substring(0, key.length() - 1);
        Triple<String, Supplier<ItemStack>, Integer> it = SACK_ITEMS.get(key);
        if (it == null) {
            Sim.chat("§cCouldn't find an item in your Sacks with that name!");
            return;
        }
        String name = it.first;
        Supplier<ItemStack> make = it.second;
        int max = it.third;
        int want = Math.max(amount != null ? amount : 64, 1);
        String id = SimItems.idOf(make.get());
        Inventory inv = p.getInventory();
        int left = want;
        for (int i = 0; i < 36; i++) {
            if (left == 0) break;
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && java.util.Objects.equals(SimItems.idOf(s), id) && s.getCount() < max) {
                int n = Math.min(left, max - s.getCount());
                s.grow(n);
                left -= n;
            }
        }
        for (int i = 0; i < 36; i++) {
            if (left == 0) break;
            if (inv.getItem(i).isEmpty()) {
                int n = Math.min(left, max);
                ItemStack st = make.get();
                st.setCount(n);
                inv.setItem(i, st);
                left -= n;
            }
        }
        int moved = want - left;
        if (moved == 0) {
            Sim.chat("§cYou don't have enough space in your inventory!");
            return;
        }
        p.inventoryMenu.broadcastChanges();
        Sim.chat("§aMoved §f" + moved + " " + name + "§a from your Sacks to your inventory.");
    }

    private static void rules(MinecraftServer s) {
        GameRules r = s.getGameRules();
        r.set(GameRules.ADVANCE_TIME, false, s);
        r.set(GameRules.ADVANCE_WEATHER, false, s);
        r.set(GameRules.SPAWN_MOBS, false, s);
        r.set(GameRules.SPAWN_MONSTERS, false, s);
        r.set(GameRules.SPAWN_PHANTOMS, false, s);
        r.set(GameRules.SPAWN_PATROLS, false, s);
        r.set(GameRules.SPAWN_WANDERING_TRADERS, false, s);
        r.set(GameRules.RANDOM_TICK_SPEED, 0, s);
        r.set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, s);
        r.set(GameRules.FALL_DAMAGE, false, s);
        r.set(GameRules.FIRE_DAMAGE, false, s);
        r.set(GameRules.DROWNING_DAMAGE, false, s);
        r.set(GameRules.KEEP_INVENTORY, true, s);
        r.set(GameRules.IMMEDIATE_RESPAWN, true, s);
        r.set(GameRules.SHOW_DEATH_MESSAGES, false, s);
        r.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, s);
        r.set(GameRules.MOB_GRIEFING, false, s);
        r.set(GameRules.TNT_EXPLODES, false, s);
        r.set(GameRules.PLAYER_MOVEMENT_CHECK, false, s);
        r.set(GameRules.ELYTRA_MOVEMENT_CHECK, false, s);
        r.set(GameRules.BLOCK_DROPS, false, s);
        r.set(GameRules.ENTITY_DROPS, false, s);
        r.set(GameRules.MOB_DROPS, false, s);
        r.set(GameRules.SEND_COMMAND_FEEDBACK, false, s);
        r.set(GameRules.COMMAND_BLOCK_OUTPUT, false, s);
        r.set(GameRules.LOCATOR_BAR, false, s);
        r.set(GameRules.RESPAWN_RADIUS, 0, s);
    }
}
