// Ported from ADM's p3sim/clear/mobs/Disguise.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobSkin;
import com.teslamaps.adm.sim.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class Disguise {
    private Disguise() {}

    public static String profileName(MobArchetype arch) {
        if (BloodLooks.isBlood(arch)) return BloodLooks.profileName(arch);
        return switch (arch.id) {
            case "angry_archaeologist" -> "Diamond Guy";
            case "frozen_adventurer" -> "Lost Adventurer";
            default -> arch.name;
        };
    }

    public static String teamName(String profileName) {
        return "fkt_" + profileName.substring(0, Math.min(11, profileName.length()));
    }

    public static GameProfile profile(UUID uuid, String name, MobSkin skin) {
        MobSkin chosen = skin != null ? pickVariant(skin, uuid) : null;
        Pair<String, String> signed = chosen != null ? SignedSkins.of(chosen.id) : null;
        PropertyMap props;
        if (signed != null) {
            Multimap<String, Property> mm = LinkedHashMultimap.create();
            mm.put("textures", new Property("textures", signed.first, signed.second));
            props = new PropertyMap(mm);
        } else if (chosen != null && chosen.value != null) {
            Multimap<String, Property> mm = LinkedHashMultimap.create();
            mm.put("textures", new Property("textures", chosen.value));
            props = new PropertyMap(mm);
        } else {
            props = PropertyMap.EMPTY;
        }
        return new GameProfile(uuid, name, props);
    }

    private static MobSkin pickVariant(MobSkin skin, UUID uuid) {
        if (skin.variants.size() < 2) return skin;
        String want = SignedSkins.trimLeadingZeros(MobGear.lostSet(uuid).skin);
        for (MobSkin v : skin.variants) if (SignedSkins.trimLeadingZeros(v.id).equals(want)) return v;
        return skin;
    }

    public static MobSkin missingSkin(MobArchetype arch) {
        return arch.id.equals("shadow_assassin") ? new MobSkin("3399e00f404411e465d74388df132d51fe868ecf86f1c073faffa1d9172ec0f3", null, null) : null;
    }

    public static List<Packet<ClientGamePacketListener>> announcePackets(GameProfile profile, ServerPlayer base) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(profile.id(), profile, false, 1, GameType.SURVIVAL, null, true, 0, null);
        ClientboundPlayerInfoUpdatePacket info = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, base);
        replaceField(info, "actions", EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER));
        replaceField(info, "entries", List.of(entry));
        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName(profile.name()));
        team.setNameTagVisibility(Team.Visibility.NEVER);
        team.setCollisionRule(Team.CollisionRule.NEVER);
        return List.of(
                info,
                ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true),
                ClientboundSetPlayerTeamPacket.createPlayerPacket(team, profile.name(), ClientboundSetPlayerTeamPacket.Action.ADD)
        );
    }

    public static List<Packet<ClientGamePacketListener>> retractPackets(UUID uuid, String profileName) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName(profileName));
        return List.of(new ClientboundPlayerInfoRemovePacket(List.of(uuid)), ClientboundSetPlayerTeamPacket.createRemovePacket(team));
    }

    private static void replaceField(Object target, String name, Object value) {
        try {
            Field f = ClientboundPlayerInfoUpdatePacket.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean announce(GameProfile profile) {
        MinecraftServer server = SimServer.server;
        if (server == null) return false;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return false;
        ServerPlayer base = players.get(0);
        try {
            for (Packet<ClientGamePacketListener> p : announcePackets(profile, base)) server.getPlayerList().broadcastAll(p);
            return true;
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] cannot announce a fake player: " + t.getMessage());
            return false;
        }
    }

    public static void announceTo(ServerPlayer player, GameProfile profile) {
        try {
            for (Packet<ClientGamePacketListener> p : announcePackets(profile, player)) player.connection.send(p);
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] cannot announce a fake player to " + player.getScoreboardName() + ": " + t.getMessage());
        }
    }

    public static void retract(UUID uuid, String profileName) {
        MinecraftServer server = SimServer.server;
        if (server == null) return;
        try {
            for (Packet<ClientGamePacketListener> p : retractPackets(uuid, profileName)) server.getPlayerList().broadcastAll(p);
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] cannot retract a fake player: " + t.getMessage());
        }
    }
}
