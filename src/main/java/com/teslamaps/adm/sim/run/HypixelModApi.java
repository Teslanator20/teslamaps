// Ported from ADM's p3sim/run/HypixelModApi.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Fight;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimNet;
import com.teslamaps.adm.sim.util.KRandom;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class HypixelModApi {
    private HypixelModApi() {}

    private static final String PKG = "net.hypixel.modapi.";
    private static final String HELLO = PKG + "packet.impl.clientbound.ClientboundHelloPacket";
    private static final String LOCATION = PKG + "packet.impl.clientbound.event.ClientboundLocationPacket";
    private static final String PARTY_INFO = PKG + "packet.impl.clientbound.ClientboundPartyInfoPacket";
    private static final String PARTY_MEMBER = PARTY_INFO + "$PartyMember";
    private static final String PARTY_ROLE = PARTY_INFO + "$PartyRole";
    private static final String SERIALIZER = PKG + "serializer.PacketSerializer";
    private static final String PAYLOAD = PKG + "fabric.payload.ClientboundHypixelPayload";
    private static final String API = PKG + "HypixelModAPI";
    private static final String ENVIRONMENT_TYPE = "net.hypixel.data.region.Environment";
    private static final String SERVER_TYPE = "net.hypixel.data.type.ServerType";

    private static final String ENVIRONMENT = "PRODUCTION";
    private static final String MODE = "dungeon";
    private static final String MAP = "Dungeon";

    private static volatile boolean unavailable = false;

    public static void install() {
        SimNet.onClient(HypixelWire.LOCATION_KIND, data -> {
            Ec.safely("ads music", () -> Minecraft.getInstance().getMusicManager().stopPlaying());
            String name = HypixelWire.decodeLocation(data);
            if (name != null) deliverLocation(name);
        });
        SimNet.onClient(HypixelWire.PARTY_KIND, data -> deliverParty(HypixelWire.decodeParty(data)));
        SimNet.onServer(HypixelWire.PARTY_REQUEST_KIND, (player, d) -> partyRequested(player));
    }

    // AdsEngineHypixelPacketMixin, outgoing: true cancels
    public static boolean cancelSend(Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket c)) return false;
        CustomPacketPayload payload = c.payload();
        if (payload == null) return false;
        Identifier id = payload.type().id();
        if (!id.getNamespace().equals("hypixel") || !P3Sim.inSim()) return false;
        if (id.getPath().equals("party_info")) SimNet.send(HypixelWire.PARTY_REQUEST_KIND);
        return true;
    }

    // AdsEngineHypixelPacketMixin, incoming: true cancels
    public static boolean cancelReceive(Packet<?> packet) {
        return packet instanceof ClientboundUpdateAdvancementsPacket && P3Sim.inSim();
    }

    private static volatile String told = null;

    public static String status() {
        if (unavailable) return "Hypixel mod API: not installed, or it would not take the packets (see the log)";
        if (told != null) return "Hypixel mod API told: " + told;
        return "Hypixel mod API: nothing told yet (a run tells it at its start)";
    }

    public static void deliverLocation(String serverName) {
        if (unavailable) return;
        try {
            deliver(helloPacket());
            deliver(locationPacket(serverName));
            told = "hello, then location " + serverName + " (SKYBLOCK, " + MODE + ")";
        } catch (ClassNotFoundException e) {
            unavailable = true;
            Ec.LOG.info("[ads] the Hypixel mod API is not installed; mods that read it will not see the dungeon");
        } catch (Throwable t) {
            unavailable = true;
            Ec.LOG.warn("[ads] could not hand the location to the Hypixel mod API", t);
        }
    }

    public static void deliverParty(List<HypixelWire.Member> members) {
        if (unavailable) return;
        try {
            deliver(partyPacket(members));
            String t = told;
            String before = "";
            if (t != null) {
                int i = t.indexOf("; party");
                before = i < 0 ? t : t.substring(0, i);
            }
            int leaders = 0;
            for (HypixelWire.Member m : members) if (m.leader) leaders++;
            told = before + "; party of " + members.size() + " (" + leaders + " leader)";
        } catch (ClassNotFoundException e) {
            unavailable = true;
        } catch (Throwable t) {
            unavailable = true;
            Ec.LOG.warn("[ads] could not hand the party to the Hypixel mod API", t);
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object c : type.getEnumConstants()) if (((Enum<?>) c).name().equals(name)) return c;
        throw new java.util.NoSuchElementException("Array contains no element matching the predicate.");
    }

    public static Object helloPacket() throws Exception {
        Class<?> environment = Class.forName(ENVIRONMENT_TYPE);
        return Class.forName(HELLO).getConstructor(environment).newInstance(enumConstant(environment, ENVIRONMENT));
    }

    public static Object locationPacket(String serverName) throws Exception {
        Class<?> serverType = Class.forName(SERVER_TYPE);
        Object skyblock = ((java.util.Optional<?>) serverType.getMethod("valueOf", String.class).invoke(null, "SKYBLOCK")).get();
        return Class.forName(LOCATION).getConstructor(String.class, serverType, String.class, String.class, String.class)
                .newInstance(serverName, skyblock, null, MODE, MAP);
    }

    public static Object partyPacket(List<HypixelWire.Member> members) throws Exception {
        Class<?> role = Class.forName(PARTY_ROLE);
        java.lang.reflect.Constructor<?> memberCtor = Class.forName(PARTY_MEMBER).getConstructor(UUID.class, role);
        LinkedHashMap<UUID, Object> map = new LinkedHashMap<>();
        for (HypixelWire.Member m : members) map.put(m.uuid, memberCtor.newInstance(m.uuid, enumConstant(role, m.leader ? "LEADER" : "MEMBER")));
        return Class.forName(PARTY_INFO).getConstructor(int.class, boolean.class, java.util.Map.class).newInstance(2, true, map);
    }

    @SuppressWarnings("unchecked")
    public static CustomPacketPayload payloadOf(Object packet) throws Exception {
        Class<?> serializer = Class.forName(SERIALIZER);
        String id = (String) packet.getClass().getMethod("getIdentifier").invoke(packet);
        ByteBuf buf = Unpooled.buffer();
        Object writer = serializer.getConstructor(ByteBuf.class).newInstance(buf);
        serializer.getMethod("writeBoolean", boolean.class).invoke(writer, true);
        packet.getClass().getMethod("write", serializer).invoke(packet, writer);
        CustomPacketPayload.Type<CustomPacketPayload> type = new CustomPacketPayload.Type<>(Identifier.parse(id));
        StreamCodec<ByteBuf, CustomPacketPayload> codec = (StreamCodec<ByteBuf, CustomPacketPayload>)
                Class.forName(PAYLOAD).getMethod("buildCodec", CustomPacketPayload.Type.class).invoke(null, type);
        return codec.decode(buf);
    }

    private static void deliver(Object packet) throws Exception {
        CustomPacketPayload payload = payloadOf(packet);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.handleCustomPayload(new ClientboundCustomPayloadPacket(payload));
        else {
            Object api = Class.forName(API).getMethod("getInstance").invoke(null);
            api.getClass().getMethod("handle", Class.forName(PKG + "packet.ClientboundHypixelPacket")).invoke(api, packet);
        }
    }

    private static final class Pending {
        final UUID player;
        final int dueTick;
        Pending(UUID player, int dueTick) { this.player = player; this.dueTick = dueTick; }
    }

    private static final ArrayList<Pending> pending = new ArrayList<>();
    private static String serverName = null;
    private static KRandom random = new KRandom(0);

    private static int startTick = 0;
    private static long keepAliveId = 0L;

    public static String serverStatus() {
        return "Run server name " + (serverName != null ? serverName : "none") + ", " + keepAliveId + " keep-alives sent, " + pending.size() + " party answers waiting";
    }

    public static void runStarted(Run run) {
        pending.clear();
        startTick = Fight.serverTick; keepAliveId = 0;
        random = run.subRandom("hypixel");
        serverName = HypixelWire.serverName(random);
        for (ServerPlayer p : Sim.players()) sendLocation(p);
    }

    public static void playerJoined(ServerPlayer player) { if (serverName != null) sendLocation(player); }

    public static void runEnded() { pending.clear(); serverName = null; }

    private static void sendLocation(ServerPlayer p) {
        String name = serverName;
        if (name == null) return;
        p.connection.send(new ClientboundCustomPayloadPacket(new BrandPayload(HypixelWire.BRAND)));
        SimNet.send(p, HypixelWire.LOCATION_KIND, HypixelWire.encodeLocation(name));
    }

    private static void partyRequested(ServerPlayer player) {
        Run run = Runs.current;
        if (run == null) return;
        if (run.isOver()) return;
        pending.add(new Pending(player.getUUID(), Fight.serverTick + HypixelWire.partyDelayTicks(random)));
    }

    public static void tick(Run run) {
        keepAlive();
        if (pending.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        for (Pending p : pending) if (p.dueTick <= Fight.serverTick) due.add(p);
        if (due.isEmpty()) return;
        pending.removeAll(new HashSet<>(due));
        List<ServerPlayer> humans = Sim.players();
        List<UUID> humanIds = new ArrayList<>();
        for (ServerPlayer h : humans) humanIds.add(h.getUUID());
        List<UUID> botIds = new ArrayList<>();
        for (PlayerRecord r : run.state.players.values()) if (r.isBot) botIds.add(r.uuid);
        List<HypixelWire.Member> members = HypixelWire.partyMembers(humanIds, botIds);
        String text = HypixelWire.encodeParty(members);
        for (Pending d : due) {
            for (ServerPlayer h : humans) {
                if (h.getUUID().equals(d.player)) { SimNet.send(h, HypixelWire.PARTY_KIND, text); break; }
            }
        }
    }

    private static void keepAlive() {
        int dt = Fight.serverTick - startTick - KEEP_ALIVE_FIRST;
        if (dt < 0 || dt % KEEP_ALIVE_EVERY != 0) return;
        ServerPlayer host = Sim.host();
        if (host != null && host.connection != null) host.connection.send(new ClientboundKeepAlivePacket(++keepAliveId));
    }

    public static final int KEEP_ALIVE_FIRST = 14;
    public static final int KEEP_ALIVE_EVERY = 11;
}
