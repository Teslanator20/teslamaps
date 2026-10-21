// Ported from ADM's p3sim/clear/hud/HudPackets.kt
package com.teslamaps.adm.sim.clear.hud;

import com.mojang.authlib.GameProfile;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.util.Pair;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Builds the real packets for the sidebar, the tab list, the map and the action bar from the pure models. Nothing is attached to the
 * server's own scoreboard. Server thread only. Text goes through Sim.legacy so a `§` code becomes a style.
 */
public final class HudPackets {
    private HudPackets() {}

    private static final Scoreboard board = new Scoreboard();
    private static final org.slf4j.Logger log = com.teslamaps.adm.sim.Ec.LOG;

    private static Component text(String s) { return Sim.legacy(s); }

    // ---- sidebar

    private static Objective objective() {
        return new Objective(board, SidebarModel.OBJECTIVE, ObjectiveCriteria.DUMMY, text(HudConstants.TITLE), ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }

    /** Creates the `SBScoreboard` objective and shows it in the sidebar slot. */
    public static List<Packet<ClientGamePacketListener>> sidebarShow() {
        Objective o = objective();
        return List.of(new ClientboundSetObjectivePacket(o, ClientboundSetObjectivePacket.METHOD_ADD), new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, o));
    }

    public static Packet<ClientGamePacketListener> sidebarHide() { return new ClientboundSetObjectivePacket(objective(), ClientboundSetObjectivePacket.METHOD_REMOVE); }

    private static PlayerTeam team(int score, String line) {
        Pair<String, String> split = HudFormat.splitLine(line);
        PlayerTeam t = new PlayerTeam(board, SidebarModel.teamName(score));
        t.setPlayerPrefix(text(split.first));
        t.setPlayerSuffix(text(split.second));
        // Recorded options byte 3: friendly fire and see friendly invisibles; name tags always shown, no collision.
        t.setAllowFriendlyFire(true);
        t.setSeeFriendlyInvisibles(true);
        t.setNameTagVisibility(Team.Visibility.ALWAYS);
        t.setCollisionRule(Team.CollisionRule.NEVER);
        return t;
    }

    /** The packets for a sidebar change, in Hypixel's order: removals, then new lines, then text changes. */
    public static List<Packet<ClientGamePacketListener>> sidebarOps(SidebarOps ops) {
        List<Packet<ClientGamePacketListener>> out = new ArrayList<>();
        for (int score : ops.remove) {
            out.add(new ClientboundResetScorePacket(SidebarModel.holder(score), SidebarModel.OBJECTIVE));
            out.add(ClientboundSetPlayerTeamPacket.createRemovePacket(new PlayerTeam(board, SidebarModel.teamName(score))));
        }
        for (Pair<Integer, String> a : ops.add) {
            int score = a.first;
            PlayerTeam t = team(score, a.second);
            String holder = SidebarModel.holder(score);
            out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true));
            out.add(new ClientboundSetScorePacket(holder, SidebarModel.OBJECTIVE, score, Optional.empty(), Optional.empty()));
            out.add(ClientboundSetPlayerTeamPacket.createPlayerPacket(t, holder, ClientboundSetPlayerTeamPacket.Action.ADD));
        }
        for (Pair<Integer, String> c : ops.change) out.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(c.first, c.second), false));
        return out;
    }

    /** Removes every sidebar team that is up. */
    public static List<Packet<ClientGamePacketListener>> sidebarTeamsRemove(Collection<Integer> scores) {
        List<Packet<ClientGamePacketListener>> out = new ArrayList<>();
        for (int s : scores) out.add(ClientboundSetPlayerTeamPacket.createRemovePacket(new PlayerTeam(board, SidebarModel.teamName(s))));
        return out;
    }

    // ---- tab list

    private static EnumMap<Action, Action.Writer> writers = null;

    /** Each action's private entry writer (the packet's own write uses them), found by reflection once. */
    private static synchronized EnumMap<Action, Action.Writer> writers() throws ReflectiveOperationException {
        if (writers == null) {
            Field f = Action.class.getDeclaredField("writer");
            f.setAccessible(true);
            EnumMap<Action, Action.Writer> m = new EnumMap<>(Action.class);
            for (Action a : Action.values()) m.put(a, (Action.Writer) f.get(a));
            writers = m;
        }
        return writers;
    }

    /** The registries the packet text is written with; tests replace it. */
    public static Supplier<RegistryAccess> registries = () -> Sim.level().registryAccess();

    /** Whether the tab packets can be built here. */
    public static volatile boolean tabWorks = true;

    public static ClientboundPlayerInfoUpdatePacket.Entry entry(UUID id, GameProfile profile, boolean listed, Component name) {
        return new ClientboundPlayerInfoUpdatePacket.Entry(id, profile, listed, 1, GameType.SURVIVAL, name, false, 0, null);
    }

    /**
     * A player-info update with hand-made entries. The packet class only has constructors that read real players, so the packet is
     * written the way the packet writes itself (the enum set, then each entry through each action's own writer) and read back through
     * the public stream codec. Returns null, and turns the tab off for this session, if that fails.
     */
    public static Packet<ClientGamePacketListener> info(EnumSet<Action> actions, List<ClientboundPlayerInfoUpdatePacket.Entry> entries) {
        if (!tabWorks) return null;
        try {
            EnumMap<Action, Action.Writer> w = writers();
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries.get());
            buf.writeEnumSet(actions, Action.class);
            buf.writeVarInt(entries.size());
            for (ClientboundPlayerInfoUpdatePacket.Entry e : entries) {
                buf.writeUUID(e.profileId());
                for (Action a : actions) w.get(a).write(buf, e);
            }
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf);
        } catch (Throwable e) {
            tabWorks = false;
            log.warn("[ads] hud: cannot build tab list packets; the tab list is off for this session", e);
            return null;
        }
    }

    private static GameProfile fakeProfile(int i) { return new GameProfile(TabModel.profileId(i), TabModel.profileName(i)); }

    /** Adds the 80 invisible players that carry the tab text. */
    public static Packet<ClientGamePacketListener> tabAddFakes(List<String> rows) {
        List<ClientboundPlayerInfoUpdatePacket.Entry> es = new ArrayList<>();
        for (int i = 0; i < TabModel.ROWS; i++) es.add(entry(TabModel.profileId(i), fakeProfile(i), true, text(rows.get(i))));
        return info(EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_GAME_MODE, Action.UPDATE_LISTED, Action.UPDATE_LATENCY, Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LIST_ORDER), es);
    }

    /** New text for the rows at indexes. */
    public static Packet<ClientGamePacketListener> tabText(List<String> rows, List<Integer> indexes) {
        if (indexes.isEmpty()) return null;
        List<ClientboundPlayerInfoUpdatePacket.Entry> es = new ArrayList<>();
        for (int i : indexes) es.add(entry(TabModel.profileId(i), fakeProfile(i), true, text(rows.get(i))));
        return info(EnumSet.of(Action.UPDATE_DISPLAY_NAME), es);
    }

    public static Packet<ClientGamePacketListener> tabRemoveFakes() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < TabModel.ROWS; i++) ids.add(TabModel.profileId(i));
        return new ClientboundPlayerInfoRemovePacket(ids);
    }

    /** Real players leave the visible list (listed false) so only the 80 rows show, or come back at the end. */
    public static Packet<ClientGamePacketListener> tabListed(List<ServerPlayer> who, boolean listed) {
        if (who.isEmpty()) return null;
        List<ClientboundPlayerInfoUpdatePacket.Entry> es = new ArrayList<>();
        for (ServerPlayer p : who) es.add(entry(p.getUUID(), p.getGameProfile(), listed, null));
        return info(EnumSet.of(Action.UPDATE_LISTED), es);
    }

    /** Entries for bots, so `getPlayerInfo(name)` finds them; they are not listed. */
    public static Packet<ClientGamePacketListener> tabAddBots(List<Pair<UUID, String>> bots) {
        if (bots.isEmpty()) return null;
        List<ClientboundPlayerInfoUpdatePacket.Entry> es = new ArrayList<>();
        for (Pair<UUID, String> b : bots) es.add(entry(b.first, new GameProfile(b.first, b.second), false, null));
        return info(EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_GAME_MODE, Action.UPDATE_LISTED, Action.UPDATE_LATENCY), es);
    }

    public static Packet<ClientGamePacketListener> tabRemoveIds(List<UUID> ids) { return ids.isEmpty() ? null : new ClientboundPlayerInfoRemovePacket(ids); }

    public static Packet<ClientGamePacketListener> tabHeaderFooter(String header, String footer) { return new ClientboundTabListPacket(text(header), text(footer)); }

    // ---- map

    private static List<MapDecoration> decos(List<MapMark> marks) {
        List<MapDecoration> out = new ArrayList<>();
        for (MapMark m : marks) out.add(new MapDecoration(m.frame ? MapDecorationTypes.FRAME : MapDecorationTypes.BLUE_MARKER, (byte) m.x, (byte) m.y, (byte) m.rot, Optional.empty()));
        return out;
    }

    /** A full 128 x 128 frame (with the current decorations). */
    public static Packet<ClientGamePacketListener> mapFrame(byte[] colors, List<MapMark> marks) {
        return new ClientboundMapItemDataPacket(new MapId(HudConstants.MAP_ID), (byte) 2, false, decos(marks), new MapItemSavedData.MapPatch(0, 0, MapModel.SIZE, MapModel.SIZE, colors));
    }

    /** Decorations only: the markers move, the picture is left alone. */
    public static Packet<ClientGamePacketListener> mapMarks(List<MapMark> marks) {
        return new ClientboundMapItemDataPacket(new MapId(HudConstants.MAP_ID), (byte) 2, false, Optional.of(decos(marks)), Optional.empty());
    }

    // ---- action bar

    /** Hypixel sends the action bar as an overlay system chat, one literal string with the `§` codes in it. */
    public static Packet<ClientGamePacketListener> actionBar(String line) { return new ClientboundSystemChatPacket(Component.literal(line), true); }
}
