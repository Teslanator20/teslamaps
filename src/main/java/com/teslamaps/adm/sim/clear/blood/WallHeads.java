// Ported from ADM's p3sim/clear/blood/WallHeads.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.plugin.HeadSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The blood room's wall heads: one invisible armor stand per station with a player head on it. A thrown head leaves on a
 * HeadFlight and is gone on arrival. Server thread only.
 */
public class WallHeads {
    public final List<HeadSlot> slots;
    private final List<SkullTexture> textures;
    private final String[] keys;
    private final ArmorStand[] stands;

    private static final class Flying {
        final HeadFlight flight;
        int age = 0;

        Flying(HeadFlight flight) { this.flight = flight; }
    }

    private final Map<Integer, Flying> flying = new HashMap<>();

    public WallHeads(List<HeadSlot> slots, List<SkullTexture> textures) {
        this.slots = slots;
        this.textures = textures;
        this.keys = new String[slots.size()];
        this.stands = new ArmorStand[slots.size()];
    }

    /** The key each slot shows now (null = empty or thrown). */
    public List<String> keys() { return new ArrayList<>(Arrays.asList(keys)); }

    public void placeAll() { placeAll(HeadTextures.PLAIN); }

    /** Puts a head with key on every station. */
    public void placeAll(String key) { for (int i = 0; i < slots.size(); i++) set(i, key); }

    public void clearAll() { for (int i = 0; i < slots.size(); i++) set(i, HeadTextures.NONE); }

    public void set(int slot, String key) {
        if (slot < 0 || slot >= slots.size()) return;
        HeadSlot s = slots.get(slot);
        if (key.equals(HeadTextures.NONE)) {
            keys[slot] = null;
            flying.remove(slot);
            if (stands[slot] != null) stands[slot].discard();
            stands[slot] = null;
            return;
        }
        keys[slot] = key;
        ArmorStand stand = stands[slot] != null && !stands[slot].isRemoved() ? stands[slot] : null;
        if (stand != null) stand.setItemSlot(EquipmentSlot.HEAD, HeadItems.stack(key, textures));
        else stands[slot] = spawn(s, key);
    }

    /** Remakes the stands from saved (a savepoint restore). */
    public void reapply(List<String> saved) {
        removeAll();
        for (int i = 0; i < slots.size(); i++) {
            String k = i < saved.size() ? saved.get(i) : null;
            if (k != null) set(i, k);
        }
    }

    /** The head at slot flies to (x, y, z) over ticks, spinning, gone on arrival. */
    public void fly(int slot, double x, double y, double z, int ticks) {
        ArmorStand stand = stand(slot);
        if (stand == null) return;
        flying.put(slot, new Flying(new HeadFlight(stand.getX(), stand.getY(), stand.getZ(), x, y, z, ticks, stand.getYRot())));
        keys[slot] = null;
    }

    /** Moves every flying head one tick; once per server tick. */
    public void tick() {
        if (flying.isEmpty()) return;
        Iterator<Map.Entry<Integer, Flying>> iter = flying.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Flying> en = iter.next();
            int slot = en.getKey();
            Flying f = en.getValue();
            ArmorStand stand = stands[slot];
            if (stand == null || stand.isRemoved()) { iter.remove(); continue; }
            f.age++;
            HeadFlight fl = f.flight;
            stand.snapTo(fl.x(f.age), fl.y(f.age), fl.z(f.age), fl.yaw(f.age), 0f);
            if (fl.syncDue(f.age)) stand.needsSync = true;
            if (fl.arrived(f.age)) { stand.discard(); stands[slot] = null; iter.remove(); }
        }
    }

    /** Whether e is one of the wall's stands. */
    public boolean owns(Entity e) {
        for (ArmorStand s : stands) if (s == e) return true;
        return false;
    }

    /** The stand at slot, or null (empty, thrown or not made). */
    public ArmorStand stand(int slot) {
        if (slot < 0 || slot >= stands.length) return null;
        ArmorStand s = stands[slot];
        return s != null && !s.isRemoved() ? s : null;
    }

    /** Discards every stand and forgets every flight; the keys stay. */
    public void removeAll() {
        flying.clear();
        for (int i = 0; i < stands.length; i++) {
            if (stands[i] != null) stands[i].discard();
            stands[i] = null;
        }
    }

    private ArmorStand spawn(HeadSlot s, String key) {
        ArmorStand st = new ArmorStand(EntityTypes.ARMOR_STAND, Sim.level());
        st.setInvisible(true); st.setSilent(true); st.setNoGravity(true); st.setInvulnerable(true);
        st.setItemSlot(EquipmentSlot.HEAD, HeadItems.stack(key, textures));
        st.snapTo(s.x + 0.5, s.y + BloodGeometry.STAND_ABOVE_CELL, s.z + 0.5, 0f, 0f);
        return Sim.spawn(st);
    }
}
