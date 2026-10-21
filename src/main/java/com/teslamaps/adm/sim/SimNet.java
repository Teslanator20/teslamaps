// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SimNet {
    private SimNet() {}

    public static class Msg implements CustomPacketPayload {
        public final String kind;
        public final String data;

        public Msg(String kind, String data) {
            this.kind = kind;
            this.data = data;
        }

        @Override
        public CustomPacketPayload.Type<Msg> type() { return TYPE; }
    }

    public static final CustomPacketPayload.Type<Msg> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("teslamaps", "ads_net"));
    private static final int MAX = 1 << 21;
    private static final StreamCodec<RegistryFriendlyByteBuf, Msg> CODEC = StreamCodec.of(
            (b, m) -> { b.writeUtf(m.kind, 64); b.writeUtf(m.data, MAX); },
            b -> new Msg(b.readUtf(64), b.readUtf(MAX)));

    private static final HashMap<String, BiConsumer<ServerPlayer, String>> serverSide = new HashMap<>();
    private static final HashMap<String, Consumer<String>> clientSide = new HashMap<>();

    public static void onServer(String kind, BiConsumer<ServerPlayer, String> run) { serverSide.put(kind, run); }

    public static void onClient(String kind, Consumer<String> run) { clientSide.put(kind, run); }

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (m, ctx) -> {
            ServerPlayer p = ctx.player();
            MinecraftServer s = SimServer.server;
            if (s == null || p.level().getServer() != s) return;
            BiConsumer<ServerPlayer, String> run = serverSide.get(m.kind);
            if (run == null) return;
            Ec.safely("p3sim net " + m.kind, () -> Sim.asPlayer(p, () -> { run.accept(p, m.data); }));
        });
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (m, ctx) -> {
            Consumer<String> run = clientSide.get(m.kind);
            if (run != null) Ec.safely("p3sim net " + m.kind, () -> run.accept(m.data));
        });
        // LAN parties: the sim's server tells a joining game it is a sim (P3Party.joined), as no relay names the server entry.
        onClient("sim", d -> P3Party.simServer = true);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (server != SimServer.server || !SimServer.isSim(server)) return;
            send(handler.player, "sim", "");
        });
    }

    public static void send(String kind) { send(kind, ""); }

    public static void send(String kind, String data) {
        if (!P3Sim.inSim()) return;
        try {
            if (ClientPlayNetworking.canSend(TYPE)) ClientPlayNetworking.send(new Msg(kind, data));
        } catch (Throwable ignored) {
        }
    }

    public static void send(ServerPlayer p, String kind) { send(p, kind, ""); }

    public static void send(ServerPlayer p, String kind, String data) {
        try {
            if (ServerPlayNetworking.canSend(p, TYPE)) ServerPlayNetworking.send(p, new Msg(kind, data));
        } catch (Throwable ignored) {
        }
    }

    public static void sendAll(String kind) { sendAll(kind, ""); }

    public static void sendAll(String kind, String data) {
        for (ServerPlayer p : Sim.players()) send(p, kind, data);
    }
}
