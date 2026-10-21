// Ported from ADM's p3sim/clear/bots/BotBodies.kt
package com.teslamaps.adm.sim.clear.bots;

import com.mojang.authlib.GameProfile;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Masks;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.clear.hud.HudPackets;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.util.Pair;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * The visible half of a bot: a body dressed like a teammate of its class, standing where the plan puts it, shown only while a human is near.
 * By default an AdsBotBody (clients get a real player); with [asPlayers] false the old mannequin. Server thread only.
 */
public class BotBodies {
    private final BooleanSupplier asPlayers;

    public BotBodies(BooleanSupplier asPlayers) { this.asPlayers = asPlayers; }

    public BotBodies() { this(() -> true); }

    private static final class Body {
        final int seat;
        final Avatar entity;
        final boolean player;
        final BotLook look;
        double y;
        float yaw;
        String held;
        /** The block column the height was last worked out for. */
        long col = Long.MIN_VALUE;
        /** Where the body was put last tick. */
        double x = 0.0;
        double z = 0.0;
        /** The humans whose client has been sent this body's tab entry and name team. */
        final HashSet<UUID> announced = new HashSet<>();
        /** Ticks in a row the body has been held back by a wall. */
        int behind = 0;

        Body(int seat, Avatar entity, boolean player, BotLook look, double y, float yaw, String held) {
            this.seat = seat;
            this.entity = entity;
            this.player = player;
            this.look = look;
            this.y = y;
            this.yaw = yaw;
            this.held = held;
        }
    }

    private final HashMap<Integer, Body> bodies = new HashMap<>();

    private Body live(int seat) {
        Body b = bodies.get(seat);
        return b != null && !b.entity.isRemoved() ? b : null;
    }

    /** Whether a body exists for [seat] (and is alive). */
    public boolean has(int seat) { return live(seat) != null; }

    /** Plays the hurt animation on [seat]'s body for every human that can see it. */
    public void hurt(int seat) {
        Body b = live(seat);
        if (b == null) return;
        Avatar e = b.entity;
        sendToWatchers(e, new ClientboundHurtAnimationPacket(e.getId(), e.getYRot()));
    }

    /** The entity id of the body of [seat], or null when it has none. */
    public Integer entityId(int seat) {
        Body b = live(seat);
        return b != null ? b.entity.getId() : null;
    }

    public int count() {
        int n = 0;
        for (Body b : bodies.values()) if (!b.entity.isRemoved()) n++;
        return n;
    }

    /** Sends [pkt] to the players that see [e]. */
    private static void sendToWatchers(Entity e, Packet<? super ClientGamePacketListener> pkt) {
        try {
            if (e.level() instanceof ServerLevel sl) sl.getChunkSource().sendToTrackingPlayers(e, pkt);
        } catch (Throwable t) { /* no world (headless) */ }
    }

    /**
     * Brings the body of [seat] to ([x], [z]) facing the frame's yaw, creating it when [show] and the column is loaded, removing it when not
     * [show]. Returns the height the body stands at, or [fallbackY] when there is no body. [yOverride]: put at that height. [reseat]: the bot has
     * just come into another room; its height is looked for from [fallbackY].
     */
    public double place(int seat, String cls, BotLook look, double x, double z, BodyFrame frame, boolean show, double fallbackY, String held, Double yOverride, boolean reseat) {
        float yaw = frame.bodyYaw;
        Body existing = live(seat);
        if (!show) { if (existing != null) remove(seat); return fallbackY; }
        if (!RunBlocks.isLoaded((int) Math.floor(x), (int) Math.floor(z))) { if (existing != null) remove(seat); return fallbackY; }
        // The plan walks through walls; the body stops at a wall and slides along it.
        Clipped held0 = existing != null && yOverride == null ? clip(existing, x, z) : null;
        if (existing != null) existing.behind = held0 != null ? existing.behind + 1 : 0;
        double px = held0 != null ? held0.x : x;
        double pz = held0 != null ? held0.z : z;
        long col = ((long) Math.floor(px) << 32) ^ ((long) Math.floor(pz) & 0xffffffffL);
        double y;
        if (yOverride != null) y = yOverride;
        else if (held0 != null) y = held0.y;
        else if (!reseat && existing != null && existing.col == col) y = existing.y;
        else y = groundY(px, pz, reseat ? fallbackY : existing != null ? existing.y : fallbackY);
        Body b = existing != null ? existing : spawn(seat, cls, look, px, y, pz, yaw);
        if (b == null) return fallbackY;
        if (b.player) announce(b);
        boolean hop = existing != null && b.player && Math.hypot(px - b.x, pz - b.z) > BLINK_BLOCKS;
        b.y = y;
        b.col = col;
        b.yaw = yaw;
        if (hop) reAdd(b, px, y, pz, frame);
        else {
            b.entity.snapTo(px, y, pz, yaw, 0f);
            b.entity.yHeadRot = frame.headYaw;
            b.entity.yBodyRot = yaw;
        }
        b.x = px;
        b.z = pz;
        if (!b.held.equals(held)) { b.held = held; b.entity.setItemSlot(EquipmentSlot.MAINHAND, item(held)); }
        Avatar e = b.entity;
        if (e.isSprinting() != frame.sprint) e.setSprinting(frame.sprint);
        if (e.isShiftKeyDown() != frame.crouch) { e.setShiftKeyDown(frame.crouch); e.setPose(frame.crouch ? Pose.CROUCHING : Pose.STANDING); }
        if (frame.swing) swing(e);
        return y;
    }

    public double place(int seat, String cls, BotLook look, double x, double z, BodyFrame frame, boolean show, double fallbackY, String held, Double yOverride) {
        return place(seat, cls, look, x, z, frame, show, fallbackY, held, yOverride, false);
    }

    public double place(int seat, String cls, BotLook look, double x, double z, BodyFrame frame, boolean show, double fallbackY, String held) {
        return place(seat, cls, look, x, z, frame, show, fallbackY, held, null, false);
    }

    private Body spawn(int seat, String cls, BotLook look, double x, double y, double z, float yaw) {
        try {
            boolean player = asPlayers.getAsBoolean();
            Avatar entity;
            if (player) entity = new AdsBotBody(Sim.level(), look.profileId);
            else {
                Mannequin m = new Mannequin(EntityType.MANNEQUIN, Sim.level());
                m.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(look.profileId, look.name)));
                m.setCustomName(Component.literal(look.tag()));
                m.setCustomNameVisible(true);
                hideDescription(m);
                entity = m;
            }
            entity.setInvulnerable(true);
            entity.setNoGravity(true);
            dress(entity, cls);
            entity.snapTo(x, y, z, yaw, 0f);
            Body body = new Body(seat, entity, player, look, y, yaw, "");
            body.x = x;
            body.z = z;
            bodies.put(seat, body);
            if (player) { BotRegistry.add(look.profileId); announce(body); }
            Sim.spawn(entity);
            return body;
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] could not make the body of bot " + look.name, t);
            return null;
        }
    }

    /** Gives every human's client the unlisted tab entry and the name team before the player entity arrives. Once per human and body. */
    private void announce(Body b) {
        List<ServerPlayer> humans;
        try { humans = Sim.players(); } catch (Throwable t) { return; }
        for (ServerPlayer p : humans) {
            if (!b.announced.add(p.getUUID())) continue;
            try {
                Packet<ClientGamePacketListener> tab = HudPackets.tabAddBots(List.of(Pair.of(b.look.profileId, b.look.name)));
                if (tab != null) p.connection.send(tab);
                for (Packet<ClientGamePacketListener> pk : teamPackets(b.look, b.seat, true)) p.connection.send(pk);
            } catch (Throwable t) {
                Ec.LOG.warn("[ads] could not announce bot " + b.look.name + " to " + p.getScoreboardName() + ": " + t.getMessage());
            }
        }
    }

    /** The body leaves the clients' world and comes back at the destination in the same tick: a teammate's blink or leap from outside. */
    private void reAdd(Body b, double x, double y, double z, BodyFrame frame) {
        AdsBotBody e = (AdsBotBody) b.entity;
        float yaw = frame.bodyYaw;
        sendToWatchers(e, new ClientboundRemoveEntitiesPacket(e.getId()));
        e.snapTo(x, y, z, yaw, 0f);
        e.yHeadRot = frame.headYaw;
        e.yBodyRot = yaw;
        sendToWatchers(e, e.addPacket());
        List<SynchedEntityData.DataValue<?>> data = e.getEntityData().getNonDefaultValues();
        if (data != null) sendToWatchers(e, new ClientboundSetEntityDataPacket(e.getId(), data));
        ArrayList<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> worn = new ArrayList<>();
        for (EquipmentSlot it : EquipmentSlot.values()) {
            if (!(it.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || it == EquipmentSlot.MAINHAND || it == EquipmentSlot.OFFHAND)) continue;
            ItemStack s = e.getItemBySlot(it).copy();
            if (!s.isEmpty()) worn.add(com.mojang.datafixers.util.Pair.of(it, s));
        }
        if (!worn.isEmpty()) sendToWatchers(e, new ClientboundSetEquipmentPacket(e.getId(), worn));
        sendToWatchers(e, new ClientboundRotateHeadPacket(e, (byte) Mth.floor(frame.headYaw * 256f / 360f)));
    }

    public void remove(int seat) {
        Body b = bodies.remove(seat);
        if (b != null && !b.entity.isRemoved()) b.entity.discard();
    }

    /** Removes every body and the name teams the clients were given. */
    public void removeAll() {
        ArrayList<Pair<BotLook, Integer>> looks = new ArrayList<>();
        for (Body b : bodies.values()) if (b.player) looks.add(Pair.of(b.look, b.seat));
        for (Integer s : new ArrayList<>(bodies.keySet())) remove(s);
        BotRegistry.clear();
        if (looks.isEmpty()) return;
        List<ServerPlayer> humans;
        try { humans = Sim.players(); } catch (Throwable t) { humans = List.of(); }
        for (Pair<BotLook, Integer> l : looks) for (ServerPlayer p : humans) {
            try { for (Packet<ClientGamePacketListener> pk : teamPackets(l.first, l.second, false)) p.connection.send(pk); } catch (Throwable t) { /* the player is gone */ }
        }
    }

    /** Forgets bodies the world already removed. */
    public void prune() { bodies.entrySet().removeIf(en -> en.getValue().entity.isRemoved()); }

    /** The entity's facing for a leap onto the bot (null when it has no body). */
    public Pair<Float, Float> facing(int seat) {
        Body b = live(seat);
        return b != null ? Pair.of(b.yaw, 0f) : null;
    }

    /** The height someone standing in column ([x], [z]) would stand at, nearest [hint]. Nothing within 14 blocks: [hint]. */
    public double groundY(double x, double z, double hint) {
        Double g = findGround(x, z, hint);
        return g != null ? g : hint;
    }

    /** groundY, or null when no height within 14 blocks of [hint] can be stood at. */
    private Double findGround(double x, double z, double hint) {
        ServerLevel level = SimServer.level();
        if (level == null) return null;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!RunBlocks.isLoaded(bx, bz)) return null;
        int h = (int) Math.floor(hint);
        for (int d = 0; d <= 14; d++) {
            int[] ys = d == 0 ? new int[]{h} : new int[]{h + d, h - d};
            for (int y : ys) {
                if (standOn(level, bx, y - 1, bz) && !blocks(level, bx, y, bz) && !blocks(level, bx, y + 1, bz)) return (double) y;
            }
        }
        return null;
    }

    private static boolean blocks(ServerLevel level, int bx, int y, int bz) {
        BlockPos pos = new BlockPos(bx, y, bz);
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean standOn(ServerLevel level, int bx, int y, int bz) {
        BlockState s = level.getBlockState(new BlockPos(bx, y, bz));
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    private static final class Clipped {
        final double x, z, y;

        Clipped(double x, double z, double y) { this.x = x; this.z = z; this.y = y; }
    }

    private static final class Reach {
        final double x, z, y;
        final boolean whole;

        Reach(double x, double z, double y, boolean whole) { this.x = x; this.z = z; this.y = y; this.whole = whole; }
    }

    /** Where the body ends up this tick going towards the plan's ([x], [z]) when something is in the way. Null when the way is clear or it snaps. */
    private Clipped clip(Body b, double x, double z) {
        double dist = Math.hypot(x - b.x, z - b.z);
        if (dist < 0.05 || dist > SNAP_BLOCKS) return null;
        Reach first = reach(b.x, b.z, b.y, x, z);
        if (first.whole) return null;
        if (b.behind >= LAG_TICKS) return null;
        Reach alongX = reach(first.x, first.z, first.y, x, first.z);
        Reach alongZ = reach(alongX.x, alongX.z, alongX.y, alongX.x, z);
        return new Clipped(alongZ.x, alongZ.z, alongZ.y);
    }

    /** Walks from ([fx], [fz]) at height [fy] towards ([tx], [tz]) in steps of 0.4 blocks while each step can be stood on. */
    private Reach reach(double fx, double fz, double fy, double tx, double tz) {
        double dist = Math.hypot(tx - fx, tz - fz);
        if (dist < 1e-6) return new Reach(fx, fz, fy, true);
        int steps = Math.max(1, (int) Math.ceil(dist / 0.4));
        double cx = fx, cz = fz, cy = fy;
        for (int i = 1; i <= steps; i++) {
            double sx = fx + (tx - fx) * i / steps, sz = fz + (tz - fz) * i / steps;
            Double gy = findGround(sx, sz, cy);
            if (gy == null) return new Reach(cx, cz, cy, false);
            if (gy - cy > STEP_UP || cy - gy > STEP_DOWN) return new Reach(cx, cz, cy, false);
            cx = sx;
            cz = sz;
            cy = gy;
        }
        return new Reach(cx, cz, cy, true);
    }

    /** The arm swing of [seat]'s body, shown to everyone who can see it. */
    public void swing(int seat) {
        Body b = live(seat);
        if (b != null) swing(b.entity);
    }

    private static void swing(Avatar e) {
        try { e.swing(InteractionHand.MAIN_HAND); } catch (Throwable t) { /* no world (headless) */ }
    }

    /** One of each item a teammate holds, made once; a body gets a copy. */
    private final HashMap<String, ItemStack> stacks = new HashMap<>();

    private ItemStack item(String key) {
        return stacks.computeIfAbsent(key, k -> switch (k) {
            case "terminator" -> SimItems.TERMINATOR();
            case "breaker" -> SimItems.DUNGEONBREAKER();
            case "aotv" -> SimItems.AOTV();
            case "leap" -> SimItems.LEAP();
            case "superboom" -> SimItems.SUPERBOOM();
            case "batwand" -> SimItems.SPIRIT_SCEPTRE();
            default -> SimItems.HYPERION();
        }).copy();
    }

    /** What a class holds standing about outside P3. */
    public String restingItem(String cls) {
        return switch (cls) {
            case "HEALER" -> "breaker";
            case "ARCHER" -> "terminator";
            default -> "hyperion";
        };
    }

    private static ItemStack dyed(Item item, int rgb) {
        if (rgb < 0) return new ItemStack(Items.CHAINMAIL_CHESTPLATE);
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return s;
    }

    /** Teammates' gear: a mask and dyed leather; each class wears one set seen on that class. */
    private static void dress(Avatar m, String cls) {
        m.setItemSlot(EquipmentSlot.HEAD, cls.equals("BERSERK") ? Masks.BONZO_MASK() : Masks.SPIRIT_MASK());
        int chest, legs, boots;
        switch (cls) {
            case "HEALER" -> { chest = 0x1793c4; legs = 0x17a8c4; boots = 0x8969c8; }
            case "MAGE" -> { chest = 0x1793c4; legs = 0x17a8c4; boots = 0x1cd4e4; }
            case "TANK" -> { chest = -1; legs = 0x5d2fb9; boots = 0x8969c8; }
            case "ARCHER" -> { chest = 0xe7413c; legs = 0xe75c3c; boots = 0x8969c8; }
            default -> { chest = 0x3e05af; legs = 0x5d23d1; boots = 0x7c44ec; }
        }
        m.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, chest));
        m.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, legs));
        m.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, boots));
    }

    private static Method hideMethod;

    private static synchronized Method hideMethod() throws NoSuchMethodException {
        if (hideMethod == null) {
            Method m = Mannequin.class.getDeclaredMethod("setHideDescription", boolean.class);
            m.setAccessible(true);
            hideMethod = m;
        }
        return hideMethod;
    }

    private static void hideDescription(Mannequin m) {
        try { hideMethod().invoke(m, true); } catch (Throwable t) { /* ignored */ }
    }

    /** Highest step a body takes without a jump, and the deepest drop it takes in one step. */
    public static final double STEP_UP = 1.3;
    public static final double STEP_DOWN = 4.0;

    /** A body held back by a wall goes to the plan when it is this many blocks from it, or has been held back this many ticks in a row. */
    public static final double SNAP_BLOCKS = 16.0;
    public static final int LAG_TICKS = 40;

    /** A body that moves farther than this in one tick got there by a blink or a leap, and is taken out of the world and put back. */
    public static final double BLINK_BLOCKS = 6.0;

    private static final Scoreboard board = new Scoreboard();

    /** The name of the scoreboard team of a bot seat. */
    public static String teamName(int seat) { return "adsbot" + seat; }

    private static ChatFormatting colour(String code) {
        return switch (code) {
            case "§b" -> ChatFormatting.AQUA;
            case "§a" -> ChatFormatting.GREEN;
            case "§6" -> ChatFormatting.GOLD;
            case "§c" -> ChatFormatting.RED;
            case "§f" -> ChatFormatting.WHITE;
            default -> null;
        };
    }

    /** The packets that make a client show [look]'s text round the name (a team that never collides); or, with [add] false, the team's removal. */
    public static List<Packet<ClientGamePacketListener>> teamPackets(BotLook look, int seat, boolean add) {
        PlayerTeam team = new PlayerTeam(board, teamName(seat));
        if (!add) return List.of(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        team.setPlayerPrefix(Sim.legacy(look.prefix));
        team.setPlayerSuffix(Sim.legacy(look.suffix));
        ChatFormatting c = colour(look.nameColour);
        if (c != null) team.setColor(c);
        team.setNameTagVisibility(Team.Visibility.ALWAYS);
        team.setCollisionRule(Team.CollisionRule.NEVER);
        team.setAllowFriendlyFire(true);
        team.setSeeFriendlyInvisibles(true);
        return List.of(
                ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true),
                ClientboundSetPlayerTeamPacket.createPlayerPacket(team, look.name, ClientboundSetPlayerTeamPacket.Action.ADD)
        );
    }
}
