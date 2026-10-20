package com.teslamaps.adm.leap;

// Ported from ADM's misc/LeapOrder.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.Adm;
import com.teslamaps.adm.adr.PartyTracker;
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** ADM's custom leap order: kept in the config, /lo editor, auto Custom sorting until the party changes. */
public final class AdmLeapOrder {

    private AdmLeapOrder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final String CUSTOM = "Custom sorting";

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(ClientCommands.literal("lo")
                .executes(c -> { open(); return 1; })
                .then(ClientCommands.argument("member1", StringArgumentType.word())
                        .then(ClientCommands.argument("member2", StringArgumentType.word())
                                .then(ClientCommands.argument("member3", StringArgumentType.word())
                                        .then(ClientCommands.argument("member4", StringArgumentType.word()).executes(c -> {
                                            List<String> names = new ArrayList<>();
                                            for (int i = 1; i <= 4; i++) names.add(StringArgumentType.getString(c, "member" + i));
                                            set(names);
                                            msg("§aLeap order set: §f" + String.join(" §7> §f", names));
                                            return 1;
                                        })))))));
    }

    private static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(new AdmLeapOrderScreen()));
    }

    /** The order, lower case, ranks 0-3. */
    public static List<String> order() {
        String raw = cfg().admLeapOrder;
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : raw.trim().split("\\s+")) if (!s.isEmpty() && out.size() < 4) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    public static void set(List<String> names) {
        List<String> clean = new ArrayList<>();
        for (String n : names) if (n != null && !n.isBlank()) clean.add(n.toLowerCase(Locale.ROOT));
        cfg().admLeapOrder = String.join(" ", clean);
        if (!clean.isEmpty()) useCustom();
        TeslaMapsConfig.save();
    }

    private static void useCustom() {
        TeslaMapsConfig c = cfg();
        if (!CUSTOM.equals(c.admLeapSorting)) {
            c.admLeapOrderRevert = c.admLeapSorting;
            c.admLeapSorting = CUSTOM;
            msg("§7Leap Menu sorting: §fCustom§7 (back to " + c.admLeapOrderRevert + " when the party changes)");
        } else if (c.admLeapOrderRevert == null || c.admLeapOrderRevert.isEmpty()) c.admLeapOrderRevert = "Odin Sorting";
        Set<String> p = party();
        c.admLeapOrderParty = p == null ? "?" : String.join(" ", new TreeSet<>(p));
    }

    private static int partyCheck;

    /** Client tick: the party changed since the order was set, back to the sorting from before. */
    public static void tick() {
        if (++partyCheck % 10 != 0 || !Adm.on()) return;
        TeslaMapsConfig c = cfg();
        String back = c.admLeapOrderRevert;
        if (back == null || back.isEmpty()) return;
        if (!CUSTOM.equals(c.admLeapSorting)) { c.admLeapOrderRevert = ""; c.admLeapOrderParty = ""; TeslaMapsConfig.save(); return; }
        Set<String> now = party();
        if (now == null) return;
        String was = c.admLeapOrderParty;
        String nowText = String.join(" ", new TreeSet<>(now));
        if (was == null || was.isEmpty() || was.equals("?")) { c.admLeapOrderParty = nowText; TeslaMapsConfig.save(); return; }
        if (was.equals(nowText)) return;
        c.admLeapSorting = back;
        c.admLeapOrderRevert = "";
        c.admLeapOrderParty = "";
        TeslaMapsConfig.save();
        msg("§7Party changed, Leap Menu sorting back to §f" + back);
    }

    /** Your party besides you (lower case), or null while that isn't known: ADR's tracker, else ours. */
    static Set<String> party() {
        String me = Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT);
        List<String> names = null;
        synchronized (PartyTracker.state) {
            if (PartyTracker.state.known()) names = new ArrayList<>(PartyTracker.state.members.keySet());
        }
        if (names == null) {
            List<String> ours = com.teslamaps.utils.PartyTracker.members();
            if (ours == null || ours.isEmpty()) return null;
            names = new ArrayList<>(ours);
        }
        Set<String> out = new HashSet<>();
        for (String n : names) { String l = n.toLowerCase(Locale.ROOT); if (!l.equals(me)) out.add(l); }
        return out;
    }

    /** The people you're with: dungeon teammates in one, else the party. */
    static List<String> partyNames() {
        List<String> team = new ArrayList<>();
        for (var dp : com.teslamaps.player.PlayerTracker.getPlayers()) team.add(dp.getName());
        if (com.teslamaps.dungeon.DungeonManager.isInDungeon() && !team.isEmpty()) return team;
        synchronized (PartyTracker.state) {
            if (PartyTracker.state.known()) {
                List<String> out = new ArrayList<>();
                for (var m : PartyTracker.state.members.values()) out.add(m.name);
                return out;
            }
        }
        List<String> ours = com.teslamaps.utils.PartyTracker.members();
        return ours == null ? List.of() : new ArrayList<>(new java.util.LinkedHashSet<>(ours));
    }

    private static final ConcurrentHashMap<String, PlayerSkin> skins = new ConcurrentHashMap<>();
    private static final Set<String> looking = ConcurrentHashMap.newKeySet();

    /** [name]'s skin: tab list, else looked up by name in the background; Steve's while it loads. */
    public static PlayerSkin skin(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfoIgnoreCase(name);
            if (info != null) return info.getSkin();
        }
        if (mc.player != null && name.equalsIgnoreCase(mc.player.getName().getString())) return mc.player.getSkin();
        String key = name.toLowerCase(Locale.ROOT);
        PlayerSkin s = skins.get(key);
        if (s != null) return s;
        if (looking.add(key)) {
            CompletableFuture.supplyAsync(() -> mc.services().profileResolver().fetchByName(name), Util.nonCriticalIoPool())
                    .thenCompose(p -> p.map(prof -> mc.getSkinManager().get(prof)).orElse(CompletableFuture.completedFuture(Optional.empty())))
                    .whenComplete((sk, e) -> {
                        if (e != null) TeslaMaps.LOGGER.warn("[adm] leap order: no skin for {} ({})", name, e.toString());
                        if (sk != null) sk.ifPresent(v -> skins.put(key, v));
                    });
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    static void msg(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§8[§6ADM§8] " + s));
    }
}
