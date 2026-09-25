package com.teslamaps.utils;

// Optional binding like Bax' integration/hypixelmodapi (ModApiBootstrap, ModApiLocationProvider, ModApiPartyProvider),
// by reflection so there is no build dependency. Without the Hypixel Mod API mod everything falls back to scraping.
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class HypixelModApi {

    private static final String PKG = "net.hypixel.modapi.";

    private static boolean bound, subscribed;
    private static Object api;
    private static Method sendPacket;
    private static Class<?> partyRequest;

    private static volatile boolean hello;
    private static volatile String serverType, serverName, lobbyName, mode, map;
    private static long lastPartyRequest;

    private HypixelModApi() {}

    public static boolean present() {
        return FabricLoader.getInstance().isModLoaded("hypixel-mod-api");
    }

    /** Toggle on, mod present and the server said hello this connection. */
    public static boolean live() {
        return hello && TeslaMapsConfig.get().hypixelModApi;
    }

    /** "SKYBLOCK" etc., null before the first location packet. */
    public static String serverType() { return live() ? serverType : null; }
    public static String serverName() { return live() ? serverName : null; }
    public static String lobbyName() { return live() ? lobbyName : null; }
    /** SkyBlock island id ("hub", "dungeon", "dynamic", ...). */
    public static String mode() { return live() ? mode : null; }
    public static String map() { return live() ? map : null; }

    public static void register() {
        bind();
        Feature.of("HypixelModApi").when(() -> TeslaMapsConfig.get().hypixelModApi)
                .tickAlways(() -> {
                    if (Minecraft.getInstance().getConnection() == null) reset();
                    else if (!subscribed) subscribe();
                });
    }

    private static void reset() {
        hello = false;
        serverType = serverName = lobbyName = mode = map = null;
    }

    private static void bind() {
        if (bound || !present()) return;
        bound = true;
        try {
            Class<?> apiCls = Class.forName(PKG + "HypixelModAPI");
            api = apiCls.getMethod("getInstance").invoke(null);
            Class<?> handler = Class.forName(PKG + "handler.ClientboundPacketHandler");
            Method create = apiCls.getMethod("createHandler", Class.class, handler);
            sendPacket = apiCls.getMethod("sendPacket", Class.forName(PKG + "packet.HypixelPacket"));
            partyRequest = Class.forName(PKG + "packet.impl.serverbound.ServerboundPartyInfoPacket");
            create.invoke(api, Class.forName(PKG + "packet.impl.clientbound.ClientboundHelloPacket"),
                    proxy(handler, p -> hello = true));
            create.invoke(api, Class.forName(PKG + "packet.impl.clientbound.event.ClientboundLocationPacket"),
                    proxy(handler, HypixelModApi::onLocation));
            create.invoke(api, Class.forName(PKG + "packet.impl.clientbound.ClientboundPartyInfoPacket"),
                    proxy(handler, HypixelModApi::onParty));
            if (TeslaMapsConfig.get().hypixelModApi) subscribe();
        } catch (Throwable t) {
            api = null;
            TeslaMaps.LOGGER.warn("Hypixel Mod API binding failed", t);
        }
    }

    private static void subscribe() {
        if (api == null || subscribed) return;
        subscribed = true;
        try {
            api.getClass().getMethod("subscribeToEventPacket", Class.class)
                    .invoke(api, Class.forName(PKG + "packet.impl.clientbound.event.ClientboundLocationPacket"));
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Hypixel Mod API subscribe failed", t);
        }
    }

    private static Object proxy(Class<?> handler, java.util.function.Consumer<Object> body) {
        return Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, (self, m, args) -> {
            if (m.getDeclaringClass() == Object.class) {
                return switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> "TeslaMapsModApiHandler";
                };
            }
            Object packet = args[0];
            Minecraft.getInstance().execute(() -> {
                try {
                    body.accept(packet);
                } catch (Throwable t) {
                    TeslaMaps.LOGGER.warn("Hypixel Mod API packet failed", t);
                }
            });
            return null;
        });
    }

    private static Object call(Object o, String method) throws ReflectiveOperationException {
        return o.getClass().getMethod(method).invoke(o);
    }

    private static String opt(Object o, String method) throws ReflectiveOperationException {
        Object v = call(o, method);
        return v instanceof Optional<?> op ? op.map(String::valueOf).orElse(null) : v == null ? null : v.toString();
    }

    private static void onLocation(Object p) {
        try {
            hello = true;
            serverName = (String) call(p, "getServerName");
            Object type = ((Optional<?>) call(p, "getServerType")).orElse(null);
            serverType = type == null ? null : (String) type.getClass().getMethod("name").invoke(type);
            lobbyName = opt(p, "getLobbyName");
            mode = opt(p, "getMode");
            map = opt(p, "getMap");
        } catch (ReflectiveOperationException e) {
            TeslaMaps.LOGGER.warn("Hypixel Mod API location read failed", e);
        }
        if (TeslaMapsConfig.get().hypixelModApi) requestParty();
    }

    /** Asks the server for the party; at most every 5 s. */
    public static void requestParty() {
        if (api == null || !hello) return;
        long now = System.currentTimeMillis();
        if (now - lastPartyRequest < 5000) return;
        lastPartyRequest = now;
        try {
            sendPacket.invoke(api, partyRequest.getConstructor().newInstance());
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Hypixel Mod API party request failed", t);
        }
    }

    private static void onParty(Object p) {
        if (!TeslaMapsConfig.get().hypixelModApi) return;
        try {
            boolean inParty = (Boolean) call(p, "isInParty");
            if (!inParty) {
                PartyTracker.applyApi(false, null, List.of());
                return;
            }
            UUID leader = ((Optional<?>) call(p, "getLeader")).map(u -> (UUID) u).orElse(null);
            List<String> names = new ArrayList<>();
            String leaderName = null;
            for (Object o : ((Map<?, ?>) call(p, "getMemberMap")).keySet()) {
                UUID id = (UUID) o;
                String n = name(id);
                if (n == null) continue;
                names.add(n);
                if (id.equals(leader)) leaderName = n;
            }
            PartyTracker.applyApi(true, leaderName, names);
        } catch (ReflectiveOperationException e) {
            TeslaMaps.LOGGER.warn("Hypixel Mod API party read failed", e);
        }
    }

    private static String name(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(id)) return mc.player.getGameProfile().name();
        ClientPacketListener c = mc.getConnection();
        PlayerInfo info = c == null ? null : c.getPlayerInfo(id);
        return info == null ? null : info.getProfile().name();
    }
}
