// Ported from ADM's p3sim/clear/state/MortNpc.kt
package com.teslamaps.adm.sim.clear.state;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimStand;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;

public final class MortNpc {
    private MortNpc() {}

    private static final String SKIN = "9b56895b9659896ad647f58599238af532d46db9c1b0389b8bbeb70999dab33d";

    private static final int FAR_CORE = 77375583;

    private static final double LOOK_RANGE = 12.0;

    public static final int BLANK_AFTER_SECOND_LINE = 2;

    private static final ArrayList<Entity> made = new ArrayList<>();
    private static final ArrayList<Entity> names = new ArrayList<>();
    private static int blankIn = -1;
    private static boolean blanked = false;
    private static Mannequin body = null;
    private static double[] home = null;

    public static double[] spot(DungeonPlan plan) {
        PlacedRoom room = plan.entrance();
        if (room == null) return null;
        if (room.tiles.isEmpty()) return null;
        Tile tile = room.tiles.get(0);
        if (tile == null) return null;
        int back = room.layoutRoom != null && room.layoutRoom.core != null && room.layoutRoom.core == FAR_CORE ? 12 : 11;
        int cx = DungeonFrame.tileCentre(tile.x); int cz = DungeonFrame.tileCentre(tile.z);
        int bx, bz;
        float yaw;
        switch (room.rotation) {
            case SOUTH -> { bx = cx; bz = cz - back; yaw = 0f; }
            case NORTH -> { bx = cx; bz = cz + back; yaw = 180f; }
            case EAST -> { bx = cx - back; bz = cz; yaw = -90f; }
            case WEST -> { bx = cx + back; bz = cz; yaw = 90f; }
            default -> throw new IllegalStateException();
        }
        return new double[]{bx + 0.5, 69.0, bz + 0.5, (double) yaw};
    }

    public static boolean owns(Entity e) {
        for (Entity m : made) if (m == e) return true;
        return false;
    }

    public static void spawn(DungeonPlan plan) {
        boolean wasBlank = blanked;
        remove();
        ReadyUpMenu.reset();
        double[] at = spot(plan);
        if (at == null) return;
        home = at;
        Ec.safely("ads mort", () -> {
            Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, Sim.level());
            String skin = Base64.getEncoder().encodeToString(
                    ("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + SKIN + "\",\"metadata\":{\"model\":\"slim\"}}}}").getBytes(StandardCharsets.UTF_8));
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("ads:mort".getBytes(StandardCharsets.UTF_8)), "Mort",
                    new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", skin))));
            m.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            m.setPermanentlyInvulnerable(true);
            m.setSilent(true);
            m.setNoGravity(true);
            hideDescription(m);
            m.snapTo(at[0], at[1], at[2], (float) at[3], 0f);
            m.setYHeadRot((float) at[3]); m.setYBodyRot((float) at[3]);
            body = Sim.spawn(m);
            made.add(m);
            names.add(stand(at[0], at[1], at[2], "§bMort"));
            names.add(stand(at[0], at[1] - 0.25, at[2], "§e§lCLICK"));
            made.addAll(names);
        });
        if (wasBlank) blankNow();
    }

    private static Entity stand(double x, double y, double z, String name) {
        SimStand s = new SimStand(Sim.level());
        s.snapTo(x, y, z, 0f, 0f);
        s.setInvisible(true);
        s.setPermanentlyInvulnerable(true);
        s.setSilent(true);
        s.setNoGravity(true);
        try { s.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) 2); } catch (Throwable ignored) {}
        s.setCustomName(Sim.legacy(name));
        s.setCustomNameVisible(true);
        return Sim.spawn(s);
    }

    public static void blankNames(int ticks) { if (ticks <= 0) blankNow(); else blankIn = ticks; }

    private static void blankNow() {
        blankIn = -1; blanked = true;
        for (Entity s : names) if (!s.isRemoved()) s.setCustomName(Sim.legacy(""));
    }

    public static void tick() {
        if (blankIn > 0 && --blankIn == 0) blankNow();
        Mannequin m = body;
        if (m == null) return;
        if (m.isRemoved()) { body = null; return; }
        double[] h = home;
        if (h == null) return;
        ServerPlayer near = null;
        double nearD = 0;
        for (ServerPlayer p : Sim.players()) {
            double d = p.distanceToSqr(m);
            if (d < LOOK_RANGE * LOOK_RANGE && (near == null || d < nearD)) { near = p; nearD = d; }
        }
        float yaw = near == null ? (float) h[3] : (float) (Math.toDegrees(Math.atan2(near.getZ() - m.getZ(), near.getX() - m.getX())) - 90.0);
        if (yaw != m.getYHeadRot()) { m.setYRot(yaw); m.setYHeadRot(yaw); m.setYBodyRot(yaw); }
    }

    public static void remove() {
        for (Entity e : made) if (!e.isRemoved()) e.discard();
        made.clear(); names.clear(); body = null; blankIn = -1; blanked = false;
    }

    private static Method hideMethod;

    private static synchronized Method hideMethod() throws NoSuchMethodException {
        if (hideMethod == null) {
            Method mm = Mannequin.class.getDeclaredMethod("setHideDescription", boolean.class);
            mm.setAccessible(true);
            hideMethod = mm;
        }
        return hideMethod;
    }

    private static void hideDescription(Mannequin m) {
        try { hideMethod().invoke(m, true); } catch (Throwable ignored) {}
    }
}
