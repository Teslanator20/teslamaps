// Ported from ADM's p3sim/clear/replay/ReplayBodies.kt
package com.teslamaps.adm.sim.clear.replay;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.clear.bots.AdsBotBody;
import com.teslamaps.adm.sim.clear.bots.BotRegistry;
import com.teslamaps.adm.sim.clear.hud.HudPackets;
import com.teslamaps.adm.sim.run.RunBlocks;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The recorded teammates' bodies: real players on the client with the recorded names and signed skins (the bots' body class), placed
 * every tick where the recording has them, turned, crouching and sprinting as recorded, holding what they held and swinging when they
 * swung. Server thread.
 */
public class ReplayBodies {
    private final List<Mate> mates;

    public ReplayBodies(List<Mate> mates) { this.mates = mates; }

    public static class Mate {
        public final String name;
        public final UUID uuid;
        public final String cls;
        public final String tex;
        public final String sig;
        public double x = Double.NaN;
        public double y = 0.0;
        public double z = 0.0;
        public float yaw = 0f;
        public float pitch = 0f;
        public float head = 0f;
        public int flags = 0;
        public boolean present = false;
        public final HashMap<EquipmentSlot, ItemStack> held = new HashMap<>();
        public AdsBotBody body = null;
        public final HashSet<UUID> told = new HashSet<>();
        public boolean swing = false;

        public Mate(String name, UUID uuid, String cls, String tex, String sig) {
            this.name = name;
            this.uuid = uuid;
            this.cls = cls;
            this.tex = tex;
            this.sig = sig;
        }
    }

    public Mate byName(String name) {
        for (Mate m : mates) if (m.name.equals(name)) return m;
        return null;
    }

    public List<Mate> all() { return mates; }

    public GameProfile profile(Mate m) {
        if (m.tex == null) return new GameProfile(m.uuid, m.name);
        LinkedHashMultimap<String, Property> mm = LinkedHashMultimap.create();
        mm.put("textures", new Property("textures", m.tex, m.sig));
        return new GameProfile(m.uuid, m.name, new PropertyMap(mm));
    }

    /** Every human's client gets the tab entry (with the skin) before the body is added. */
    private void announce(Mate m) {
        for (ServerPlayer p : Sim.players()) {
            if (!m.told.add(p.getUUID())) continue;
            try {
                Packet<ClientGamePacketListener> pk = HudPackets.info(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY),
                        List.of(HudPackets.entry(m.uuid, profile(m), false, null)));
                if (pk != null) p.connection.send(pk);
            } catch (Throwable t) { /* gone */ }
        }
    }

    /** Puts every body where its mate is now (after the tick's events were applied). */
    public void place() {
        for (Mate m : mates) {
            boolean show = m.present && !Double.isNaN(m.x) && RunBlocks.isLoaded((int) Math.floor(m.x), (int) Math.floor(m.z));
            AdsBotBody b = m.body != null && !m.body.isRemoved() ? m.body : null;
            if (!show) {
                if (b != null) { b.discard(); m.body = null; }
                continue;
            }
            AdsBotBody body = b != null ? b : spawn(m);
            if (body == null) continue;
            body.snapTo(m.x, m.y, m.z, m.yaw, m.pitch);
            body.yBodyRot = m.yaw;
            if (body.yHeadRot != m.head) {
                body.yHeadRot = m.head;
                ClientboundRotateHeadPacket pk = new ClientboundRotateHeadPacket(body, (byte) Mth.floor(m.head * 256f / 360f));
                for (ServerPlayer p : Sim.players()) p.connection.send(pk);
            }
            boolean crouch = (m.flags & 0x02) != 0;
            if (body.isShiftKeyDown() != crouch) { body.setShiftKeyDown(crouch); body.setPose(crouch ? Pose.CROUCHING : Pose.STANDING); }
            boolean sprint = (m.flags & 0x08) != 0;
            if (body.isSprinting() != sprint) body.setSprinting(sprint);
            for (Map.Entry<EquipmentSlot, ItemStack> e : m.held.entrySet())
                if (!ItemStack.matches(body.getItemBySlot(e.getKey()), e.getValue())) body.setItemSlot(e.getKey(), e.getValue().copy());
            if (m.swing) {
                m.swing = false;
                try { body.swing(InteractionHand.MAIN_HAND, false); } catch (Throwable t) {}
            }
        }
    }

    private AdsBotBody spawn(Mate m) {
        try {
            announce(m);
            AdsBotBody e = new AdsBotBody(Sim.level(), m.uuid);
            e.setInvulnerable(true);
            e.setNoGravity(true);
            e.snapTo(m.x, m.y, m.z, m.yaw, m.pitch);
            BotRegistry.add(m.uuid);
            Sim.spawn(e);
            m.body = e;
            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    public void removeAll() {
        for (Mate m : mates) {
            if (m.body != null && !m.body.isRemoved()) m.body.discard();
            m.body = null;
        }
        BotRegistry.clear();
    }

    /** A recorded item as the body wears it: the vanilla item with the recorded name. */
    public static ItemStack stack(String itemId, String name) {
        if (itemId == null) return ItemStack.EMPTY;
        Item item;
        try { item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)); } catch (Throwable t) { item = Items.AIR; }
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack st = new ItemStack(item);
        if (name != null) st.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return st;
    }

    public static EquipmentSlot slot(String name) {
        if (name == null) return null;
        return switch (name) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
