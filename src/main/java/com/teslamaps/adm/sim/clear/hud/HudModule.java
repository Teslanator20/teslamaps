// Ported from ADM's p3sim/clear/hud/HudModule.kt
package com.teslamaps.adm.sim.clear.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.pack.runstate.SbStatPreset;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.MobDamaged;
import com.teslamaps.adm.sim.run.PlayerJoined;
import com.teslamaps.adm.sim.run.PlayerLeft;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PuzzleFailed;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.ChannelService;
import com.teslamaps.adm.sim.run.api.HudRegistry;
import com.teslamaps.adm.sim.run.api.MapContributor;
import com.teslamaps.adm.sim.run.api.SidebarContributor;
import com.teslamaps.adm.sim.run.api.TabContributor;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.Pair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The client channels as Hypixel sends them, driven from RunState: the sidebar (`team_N` teams), the 80 tab entries, the dungeon map
 * item, the action bar, plus the chat / title / action-bar API the other modules call through `Services.channels()`. Runs last in the
 * module order. All state here is cosmetic and rebuilt from RunState.
 */
public final class HudModule implements AdsModule, ChannelService {
    public static final HudModule INSTANCE = new HudModule();

    private HudModule() {}

    @Override public String id() { return "hud"; }

    @Override public String owner() { return "hud"; }

    private static final org.slf4j.Logger log = com.teslamaps.adm.sim.Ec.LOG;

    public static BoolSetting sidebarOn;
    public static BoolSetting tabOn;
    public static BoolSetting mapOn;
    public static BoolSetting barOn;

    @Override
    public void registerSettings() {
        sidebarOn = SimSettings.bool("Hud Sidebar", true, "Send the Hypixel dungeon sidebar during a run (floor, time, cleared percent, party). Mods read the floor and the cleared percent from it.");
        tabOn = SimSettings.bool("Hud Tab List", true, "Send the Hypixel dungeon tab list during a run (party, secrets, rooms, puzzles). Mods read the party and the stats from it.");
        mapOn = SimSettings.bool("Hud Map", true, "Send the dungeon map to the Magical Map during a run (rooms, doors, check marks, party markers).");
        barOn = SimSettings.bool("Hud Action Bar", true, "Send the action bar during a run (health, defense, mana, and the secrets of the room you stand in).");
    }

    private static boolean enabled(BoolSetting s) { return s == null || s.get(); }

    private static boolean sidebarEnabled() { return enabled(sidebarOn); }
    private static boolean tabEnabled() { return enabled(tabOn); }
    private static boolean mapEnabled() { return enabled(mapOn); }
    private static boolean barEnabled() { return enabled(barOn); }

    /** Ticks between the sidebar refreshes that carry a changed key count. */
    private static final int SIDEBAR_REFRESH_TICKS = 20;

    /** The channels of the run that is on (server thread only); null when no run. */
    private static Session session = null;

    // ---- the API other modules call (ChannelService)

    /** A chat line as Hypixel's styled system chat. */
    @Override
    public void chat(String text, Audience to) { each(to, () -> Sim.chatStyled(text)); }

    @Override
    public void title(String title, String subtitle, int fadeIn, int stay, int fadeOut, Audience to) {
        each(to, () -> Sim.title(title, subtitle, fadeIn, stay, fadeOut));
    }

    @Override
    public void actionBarSegment(String key, String text, int priority, int ttlTicks, Audience to) {
        Session s = session;
        if (s == null) return;
        if (to instanceof Audience.Everyone) s.segments.set(key, text, priority, s.run.serverTick, ttlTicks, null);
        else if (to instanceof Audience.Only o) s.segments.set(key, text, priority, s.run.serverTick, ttlTicks, o.player);
        else if (to instanceof Audience.AllBut a) {
            for (ServerPlayer p : Sim.players()) if (!Objects.equals(p.getUUID(), a.player)) s.segments.set(key, text, priority, s.run.serverTick, ttlTicks, p.getUUID());
        }
    }

    @Override
    public void clearActionBarSegment(String key) { if (session != null) session.segments.clear(key); }

    private static void each(Audience to, Runnable block) {
        if (to instanceof Audience.Everyone) Sim.everyone(block);
        else if (to instanceof Audience.Only o) {
            for (ServerPlayer p : Sim.players()) if (Objects.equals(p.getUUID(), o.player)) { Sim.asPlayer(p, block); break; }
        } else if (to instanceof Audience.AllBut a) {
            for (ServerPlayer p : Sim.players()) if (!Objects.equals(p.getUUID(), a.player)) Sim.asPlayer(p, block);
        }
    }

    // ---- run lifecycle

    @Override
    public void onRunStart(Run run) {
        if (session != null) { try { session.close(); } catch (Throwable ignored) {} }
        session = new Session(run);
    }

    @Override
    public void onTick(Run run) {
        Session s = session;
        if (s == null || s.run != run) return;
        s.tick();
    }

    @Override
    public void onEvent(Run run, RunEvent event) {
        Session s = session;
        if (s == null) return;
        if (event instanceof MobDamaged e) s.teamDamage += (long) Math.max(0.0, e.amount);
        else if (event instanceof PlayerJoined || event instanceof PlayerLeft) s.membershipChanged = true;
        else if (event instanceof PuzzleFailed e) s.puzzleFailed(e.puzzle, e.player);
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        Session s = session;
        if (s == null || s.run != run) return;
        try { s.close(); } catch (Throwable ignored) {}
        session = null;
    }

    // ---- /ads dev hud

    @Override
    public List<AdsCommand> devCommands() {
        return List.of(new AdsCommand("hud", "/ads dev hud <dump|bar <text>|chat <text>|title <text>>",
                "Shows what the sidebar, tab list, map and action bar are sending (dump), or tries the chat, title and action bar API.",
                true, null, ctx -> {
                    Run run = ctx.run();
                    Session s = session;
                    String a0 = ctx.arg(0);
                    String rest = String.join(" ", ctx.args.subList(Math.min(1, ctx.args.size()), ctx.args.size()));
                    switch (a0 == null ? "" : a0) {
                        case "dump" -> {
                            if (run == null || s == null) ctx.reply("No run is on, so the channels are not sending anything.");
                            else ctx.reply(s.dump(ctx.player));
                        }
                        case "bar" -> {
                            if (run == null) ctx.reply("No run is on.");
                            else {
                                actionBarSegment("dev-test", "§6" + (rest.isBlank() ? "Test segment" : rest), ActionBarModel.REPLACES_DEFENSE, 60, new Audience.Only(ctx.player.getUUID()));
                                ctx.reply("Action bar segment set for 3 seconds (it replaces the defense segment).");
                            }
                        }
                        case "chat" -> chat("§a" + (rest.isBlank() ? "Test line" : rest), new Audience.Only(ctx.player.getUUID()));
                        case "title" -> title("§e" + (rest.isBlank() ? "Test title" : rest), "§7Test subtitle", 0, 30, 5, new Audience.Only(ctx.player.getUUID()));
                        default -> ctx.reply("Usage: /ads dev hud dump | bar <text> | chat <text> | title <text>");
                    }
                }));
    }

    // ---- the per-run channels

    /** What one viewer has been sent, so only changes go out. */
    private static final class ViewerHud {
        Map<Integer, String> sidebar = null;
        List<String> tabRows = null;
        String tabFooter = null;
        Set<UUID> unlisted = Set.of();
        Set<UUID> bots = Set.of();
        byte[] frame = null;
        boolean dirty = true;
    }

    private static final class Session {
        final Run run;
        final BarSegments segments = new BarSegments();
        long teamDamage = 0L;
        long teamHealing = 0L;
        boolean membershipChanged = false;
        private final HashMap<UUID, ViewerHud> viewers = new HashMap<>();
        private final HudStats stats;
        private final String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy"));
        private final int tabRefresh;
        private final JsonObject colours;
        private final int begin;
        private final HashSet<String> warned = new HashSet<>();

        /** Who failed each puzzle (by index in the state's puzzle list). */
        private final HashMap<Integer, String> failedBy = new HashMap<>();

        /** The Revive Stones each human carries, counted again at every tab refresh (null: not counted yet). */
        private Map<UUID, Integer> stones = null;

        Session(Run run) {
            this.run = run;
            this.stats = statsFor(run);
            RunStatePack rs = run.packs.runstate;
            Integer tr = rs != null && rs.counters != null ? rs.counters.tabRefreshTicks : null;
            this.tabRefresh = tr != null && tr > 0 ? tr : HudConstants.TAB_REFRESH_TICKS;
            JsonObject hud = rs != null && rs.raw != null ? rs.raw.getAsJsonObject("hud") : null;
            this.colours = hud != null ? hud.getAsJsonObject("clearedColour") : null;
            this.begin = run.serverTick;
        }

        /** One tick's gathered inputs. */
        private HudContext context() {
            List<String> extraSidebar;
            if (HudRegistry.sidebar.isEmpty()) extraSidebar = List.of();
            else {
                extraSidebar = new ArrayList<>();
                for (SidebarContributor c : HudRegistry.sidebar) {
                    List<String> l = guarded("sidebar contributor", () -> c.lines(run));
                    if (l != null) extraSidebar.addAll(l);
                }
            }
            List<String> extraTab;
            if (HudRegistry.tab.isEmpty()) extraTab = List.of();
            else {
                extraTab = new ArrayList<>();
                for (TabContributor c : HudRegistry.tab) {
                    List<String> l = guarded("tab contributor", () -> c.rows(run));
                    if (l != null) extraTab.addAll(l);
                }
            }
            return new HudContext(run.state, run.stage, run.config.floor, run.t(), run.t0Tick != null, run.serverTick - begin, stats, teamDamage, teamHealing, date,
                    band("gold", HudConstants.CLEARED_GOLD_FROM), band("green", HudConstants.CLEARED_GREEN_FROM), extraSidebar, extraTab,
                    stones != null ? stones : Map.of(), failedBy.isEmpty() ? Map.of() : new HashMap<>(failedBy));
        }

        /** Remembers who failed puzzle index (nobody: the row goes without a name) and has the tab redrawn at once. */
        void puzzleFailed(int index, UUID player) {
            if (index < 0) return;
            String name = null;
            if (player != null) {
                PlayerRecord r = run.state.players.get(player);
                if (r != null) name = r.name;
                else for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(player)) { name = p.getGameProfile().name(); break; }
            }
            if (name != null) failedBy.put(index, name);
            else failedBy.remove(index);
            for (ViewerHud v : viewers.values()) v.dirty = true;
        }

        /** The Revive Stones in p's inventory. */
        private int carriedStones(ServerPlayer p) {
            int n = 0;
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && DamageModule.REVIVE_STONE.equals(SimItems.idOf(stack))) n += stack.getCount();
            }
            return n;
        }

        private int band(String name, int def) {
            if (colours == null) return def;
            JsonObject o = colours.getAsJsonObject(name);
            if (o == null) return def;
            JsonElement e = o.get("fromPct");
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
        }

        private <T> T guarded(String what, Supplier<T> block) {
            try {
                return block.get();
            } catch (Throwable e) {
                if (warned.add(what)) log.warn("[ads] hud: " + what + " failed", e);
                return null;
            }
        }

        private void guardedRun(String what, Runnable block) { guarded(what, () -> { block.run(); return Boolean.TRUE; }); }

        void tick() {
            List<ServerPlayer> players = Sim.players();
            if (players.isEmpty()) return;
            if (stones == null || run.serverTick % tabRefresh == 0) {
                Map<UUID, Integer> m = new LinkedHashMap<>();
                for (ServerPlayer p : players) m.put(p.getUUID(), carriedStones(p));
                stones = m;
            }
            HudContext ctx = context();
            int tick = run.serverTick;
            // Only when the count changed (someone left).
            if (viewers.size() != players.size()) {
                Set<UUID> ids = new HashSet<>();
                for (ServerPlayer p : players) ids.add(p.getUUID());
                viewers.keySet().retainAll(ids);
            }
            Set<UUID>[] realIds = new Set[1];
            Supplier<Set<UUID>> real = () -> {
                if (realIds[0] == null) {
                    Set<UUID> s = new HashSet<>();
                    for (ServerPlayer p : players) s.add(p.getUUID());
                    realIds[0] = s;
                }
                return realIds[0];
            };
            byte[] raster = ctx.started() && ctx.stage.inDungeon() && mapEnabled() && tick % HudConstants.MAP_TICKS == 0 ? guarded("map render", () -> renderMap(ctx)) : null;
            for (ServerPlayer p : players) {
                ViewerHud v = viewers.computeIfAbsent(p.getUUID(), k -> new ViewerHud());
                if (membershipChanged) v.dirty = true;
                if (sidebarEnabled()) guardedRun("sidebar", () -> sidebar(p, v, ctx, tick));
                if (tabEnabled()) guardedRun("tab", () -> tab(p, v, ctx, players, real, tick));
                // The recorded map stops a few ticks before the boss entry and sends nothing during the fight.
                if (mapEnabled() && ctx.started() && ctx.stage.inDungeon()) guardedRun("map", () -> map(p, v, ctx, raster, tick));
                if (barEnabled() && tick % HudConstants.ACTION_BAR_TICKS == 0) guardedRun("action bar", () -> bar(p, ctx, tick));
            }
            membershipChanged = false;
            for (ViewerHud v : viewers.values()) v.dirty = false;
        }

        private void send(ServerPlayer p, List<Packet<ClientGamePacketListener>> packets) {
            for (Packet<ClientGamePacketListener> pk : packets) if (pk != null) p.connection.send(pk);
        }

        @SafeVarargs
        private void send(ServerPlayer p, Packet<ClientGamePacketListener>... packets) { send(p, Arrays.asList(packets)); }

        private void sidebar(ServerPlayer p, ViewerHud v, HudContext ctx, int tick) {
            Map<Integer, String> prev = v.sidebar;
            // A key count changes on the next sidebar refresh, not in the tick the key arrives.
            Map<Integer, String> next = SidebarModel.holdKeys(prev, SidebarDiff.scored(SidebarModel.lines(ctx, p.getUUID())), tick % SIDEBAR_REFRESH_TICKS == 0);
            if (prev == null) {
                send(p, HudPackets.sidebarShow());
                send(p, HudPackets.sidebarOps(SidebarDiff.diff(Map.of(), next)));
            } else {
                SidebarOps ops = SidebarDiff.diff(prev, next);
                if (!ops.isEmpty()) send(p, HudPackets.sidebarOps(ops));
            }
            v.sidebar = next;
        }

        private void tab(ServerPlayer p, ViewerHud v, HudContext ctx, List<ServerPlayer> online, Supplier<Set<UUID>> realIds, int tick) {
            if (!HudPackets.tabWorks) return;
            boolean first = v.tabRows == null;
            if (!first && !v.dirty && tick % tabRefresh != 0) return;
            List<String> rows = TabModel.rows(ctx, p.getUUID());
            String footer = TabModel.footer(ctx);
            if (first) {
                send(p, HudPackets.tabAddFakes(rows));
                send(p, HudPackets.tabHeaderFooter(TabModel.HEADER, footer));
            } else {
                List<String> prev = v.tabRows;
                List<Integer> changed = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) if (!Objects.equals(prev.get(i), rows.get(i))) changed.add(i);
                send(p, HudPackets.tabText(rows, changed));
                if (!Objects.equals(footer, v.tabFooter)) send(p, HudPackets.tabHeaderFooter(TabModel.HEADER, footer));
            }
            v.tabRows = rows;
            v.tabFooter = footer;
            // Real players are hidden from the visible list; bots get unlisted entries so mods can look them up.
            List<ServerPlayer> toHide = new ArrayList<>();
            for (ServerPlayer o : online) if (!v.unlisted.contains(o.getUUID())) toHide.add(o);
            if (!toHide.isEmpty()) {
                send(p, HudPackets.tabListed(toHide, false));
                Set<UUID> u = new HashSet<>(v.unlisted);
                for (ServerPlayer o : toHide) u.add(o.getUUID());
                v.unlisted = u;
            }
            LinkedHashMap<UUID, String> bots = new LinkedHashMap<>();
            for (PlayerRecord r : run.state.players.values()) if (r.isBot && !realIds.get().contains(r.uuid)) bots.put(r.uuid, r.name);
            List<Pair<UUID, String>> newBots = new ArrayList<>();
            for (Map.Entry<UUID, String> e : bots.entrySet()) if (!v.bots.contains(e.getKey())) newBots.add(Pair.of(e.getKey(), e.getValue()));
            if (!newBots.isEmpty()) send(p, HudPackets.tabAddBots(newBots));
            List<UUID> goneBots = new ArrayList<>();
            for (UUID id : v.bots) if (!bots.containsKey(id)) goneBots.add(id);
            if (!goneBots.isEmpty()) send(p, HudPackets.tabRemoveIds(goneBots));
            v.bots = new LinkedHashSet<>(bots.keySet());
        }

        private byte[] renderMap(HudContext ctx) {
            ByteMapCanvas canvas = new ByteMapCanvas();
            MapModel.renderInto(ctx.state, canvas);
            for (MapContributor c : HudRegistry.map) guardedRun("map contributor", () -> c.paint(run, canvas));
            return canvas.bytes;
        }

        private float yawOf(UUID id) {
            for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(id)) return p.getYRot();
            PlayerRecord r = run.state.players.get(id);
            Object y = r != null ? r.extras.get("yaw") : null;
            return y instanceof Number n ? n.floatValue() : 0f;
        }

        private void map(ServerPlayer p, ViewerHud v, HudContext ctx, byte[] raster, int tick) {
            if (tick % HudConstants.MAP_TICKS != 0 && v.frame != null) return;
            byte[] picture = raster != null ? raster : guarded("map render", () -> renderMap(ctx));
            if (picture == null) return;
            List<MapMark> marks = MapModel.decorations(ctx, p.getUUID(), this::yawOf);
            byte[] last = v.frame;
            // A full frame carries no decorations; they follow in a packet of their own in the same tick.
            if (last == null || !Arrays.equals(last, picture)) {
                send(p, HudPackets.mapFrame(picture, Collections.emptyList()), HudPackets.mapMarks(marks));
                v.frame = picture;
            } else send(p, HudPackets.mapMarks(marks));
        }

        private void bar(ServerPlayer p, HudContext ctx, int tick) {
            PlayerRecord me = ctx.state.players.get(p.getUUID());
            String line = ActionBarModel.compose(stats, me, ctx.state, segments.live(tick, p.getUUID()));
            send(p, HudPackets.actionBar(line));
        }

        /** A readable copy of everything sent to p, for `/ads dev hud dump`; written to the hud dump file. */
        String dump(ServerPlayer p) {
            HudContext ctx = context();
            StringBuilder sb = new StringBuilder();
            sb.append("stage=").append(ctx.stage).append(" t=").append(ctx.t).append(" started=").append(ctx.started()).append("\n--- sidebar (top first) ---\n");
            for (String l : SidebarModel.lines(ctx, p.getUUID())) sb.append(HudFormat.strip(l)).append('\n');
            sb.append("--- tab (80 rows) ---\n");
            List<String> tabRows = TabModel.rows(ctx, p.getUUID());
            for (int i = 0; i < tabRows.size(); i++) sb.append(i).append(": ").append(tabRows.get(i)).append('\n');
            sb.append("--- action bar ---\n").append(HudFormat.strip(ActionBarModel.compose(stats, ctx.state.players.get(p.getUUID()), ctx.state, segments.live(run.serverTick, p.getUUID())))).append('\n');
            sb.append("--- map ---\n");
            byte[] px = MapModel.render(ctx.state);
            for (int y = 0; y < MapModel.SIZE; y += 2) {
                for (int x = 0; x < MapModel.SIZE; x++) {
                    int c = px[y * MapModel.SIZE + x];
                    char ch;
                    if (c == 0) ch = ' ';
                    else if (c == MapModel.C_UNKNOWN) ch = '.';
                    else if (c == MapModel.C_QUESTION) ch = '?';
                    else if (c == MapModel.C_GREEN) ch = '+';
                    else if (c == MapModel.C_WHITE) ch = 'o';
                    else ch = '#';
                    sb.append(ch);
                }
                sb.append('\n');
            }
            Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("config/teslamaps/adm/ads/hud-dump.txt");
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, sb.toString());
            } catch (Throwable ignored) {}
            return "HUD dump written to " + file + " (sidebar " + SidebarModel.lines(ctx, p.getUUID()).size() + " lines, tab 80 rows).";
        }

        /** Takes everything off the clients: the sidebar, the fake tab rows (real players listed again), the bots' entries. */
        void close() {
            List<ServerPlayer> players = Sim.players();
            for (ServerPlayer p : players) {
                ViewerHud v = viewers.get(p.getUUID());
                if (v == null) continue;
                try {
                    if (v.sidebar != null) {
                        send(p, HudPackets.sidebarTeamsRemove(v.sidebar.keySet()));
                        p.connection.send(HudPackets.sidebarHide());
                    }
                    if (v.tabRows != null) {
                        p.connection.send(HudPackets.tabRemoveFakes());
                        List<ServerPlayer> back = new ArrayList<>();
                        for (ServerPlayer o : players) if (v.unlisted.contains(o.getUUID())) back.add(o);
                        send(p, HudPackets.tabListed(back, true));
                        send(p, HudPackets.tabRemoveIds(new ArrayList<>(v.bots)));
                        p.connection.send(HudPackets.tabHeaderFooter("", ""));
                    }
                } catch (Throwable ignored) {}
            }
            viewers.clear();
        }

        private static HudStats statsFor(Run run) {
            List<SbStatPreset> presets = run.packs.runstate != null && run.packs.runstate.sbStatPresets != null ? run.packs.runstate.sbStatPresets : List.of();
            SbStatPreset pick = null;
            for (SbStatPreset s : presets) if (Objects.equals(s.id, run.config.statsPreset)) { pick = s; break; }
            if (pick == null) for (SbStatPreset s : presets) if ("dev-p50".equals(s.id)) { pick = s; break; }
            if (pick == null) for (SbStatPreset s : presets) if (s.floor == 7) { pick = s; break; }
            return pick == null ? HudStats.DEFAULT : new HudStats(pick.maxHp, pick.defense, pick.maxMana, pick.vitality != null ? pick.vitality : HudConstants.DEFAULT_VITALITY);
        }
    }
}
