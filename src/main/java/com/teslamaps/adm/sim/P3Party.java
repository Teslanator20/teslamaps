// Ported from ADM's p3sim/P3Party.kt (no relay: parties over the game's Open to LAN)
package com.teslamaps.adm.sim;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.HttpUtil;

public final class P3Party {
    private P3Party() {}

    /** Read by SimPartyLanMixin; never set without the relay, so the LAN listener stays as vanilla opens it. */
    public static volatile boolean loopbackOnly = false;

    /** This game's server is a sim (it said so on joining). */
    public static volatile boolean simServer = false;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static class Host {
        final int port;
        volatile String code;

        Host(int port) { this.port = port; }
    }

    private static volatile Host host = null;

    public static boolean hosting() { return host != null; }

    private static final String PARTY_NAME = "ADS party";

    public static boolean joined() {
        Minecraft mc = mc();
        if (mc.hasSingleplayerServer()) return false;
        ServerData s = mc.getCurrentServer();
        if (s != null && s.name != null && s.name.startsWith(PARTY_NAME)) return true;
        return simServer && mc.getConnection() != null;
    }

    /** The address friends join (LAN ip:port), null when not hosting. */
    public static String code() {
        Host h = host;
        return h == null ? null : h.code;
    }

    public static List<String> players() {
        try {
            MinecraftServer s = SimServer.server;
            if (s == null) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) out.add(p.getGameProfile().name());
            return out;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public static void host() {
        if (host != null) return;
        IntegratedServer server = mc().getSingleplayerServer();
        if (server == null) return;
        if (!SimServer.isSim(server)) return;
        int port = server.isPublished() ? server.getPort() : HttpUtil.getAvailablePort();
        if (!server.isPublished() && !server.publishServer(server.getDefaultGameType(), false, port)) {
            Ec.msg("§cCouldn't open your sim to a party.");
            return;
        }
        Host h = new Host(port);
        h.code = lanAddress() + ":" + port;
        host = h;
        Ec.msg("§7Party open on your LAN: §b§l" + h.code + "§7 (friends join it with Join, Direct Connect or the LAN list).");
    }

    /** Closes the party: joiners are dropped, your sim goes on alone. */
    public static void stopHosting() { stopHosting(true); }

    public static void stopHosting(boolean unpublish) {
        Host h = host;
        if (h == null) return;
        host = null;
        // Minecraft 26.1.2 can't close an opened world again: it stays open until the world is left.
        MinecraftServer s = SimServer.server;
        if (unpublish && s != null) s.execute(() -> {
            for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) {
                if (!Sim.isHost(p)) p.connection.disconnect(Component.literal("The party closed."));
            }
        });
        loopbackOnly = false;
    }

    /** Joins the party at [code] (the host's ip:port; the menu's Join): leaves where you are and connects to the host. */
    public static void join(String code) {
        String c = code.trim();
        if (!ServerAddress.isValidAddress(c)) {
            Ec.msg("§cA party is the host's address: ip:port, as their game showed it.");
            return;
        }
        if (host != null) {
            Ec.msg("§cClose your own party first.");
            return;
        }
        ServerAddress addr = ServerAddress.parseString(c);
        Minecraft mc = mc();
        mc.execute(() -> {
            if (mc.level != null) mc.disconnectFromWorld(Component.literal("Joining party " + c));
            ConnectScreen.startConnecting(new TitleScreen(), mc, addr,
                    new ServerData(PARTY_NAME + " " + c, c, ServerData.Type.OTHER), false, null);
        });
    }

    /** This computer's address on the LAN (the first site-local IPv4), else the loopback. */
    private static String lanAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable t) {
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }
}
