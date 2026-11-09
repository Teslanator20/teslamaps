// Ported from ADM's p3sim/clear/blood/WatcherEntity.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.Station;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The Watcher: an invisible zombie wearing a head over the middle of the blood room facing the door, 3000 health, a name tag, and
 * two Watchful Eyes (1000 health) near him. He speaks in chat only. Server thread only.
 */
public class WatcherEntity {
    private final double x, y, z;
    private final float yaw;
    private final List<SkullTexture> textures;
    private final KRandom random;

    /** A body, the tag stand over it, and its offset from the Watcher. */
    private static final class Part {
        final Zombie body;
        final ArmorStand tag;
        final double dx, dy, dz;

        Part(Zombie body, ArmorStand tag, double dx, double dy, double dz) {
            this.body = body;
            this.tag = tag;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    private Part main = null;
    private List<Part> eyes = new ArrayList<>();

    private float curYaw;

    public WatcherEntity(double x, double y, double z, float yaw, List<SkullTexture> textures, KRandom random) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.textures = textures;
        this.random = random;
        this.curYaw = yaw;
    }

    public boolean alive() { return main != null && !main.body.isRemoved(); }

    public void spawn() {
        if (alive()) return;
        main = part(0.0, 0.0, 0.0, WatcherStats.MAX_HEALTH, ROLE_WATCHER, WATCHER_TAG);
        List<Part> e = new ArrayList<>(EYES);
        for (int i = 0; i < EYES; i++) {
            double[] o = WatcherStats.eyeOffset(random);
            e.add(part(o[0], o[1], o[2], WatcherStats.EYE_HEALTH, ROLE_EYE, EYE_TAG));
        }
        eyes = e;
    }

    /** Sets his health for kills counted kills. */
    public void setKills(int kills) {
        if (main != null && !main.body.isRemoved()) main.body.setHealth(WatcherStats.health(kills));
    }

    /** He says line: the chat line only. */
    public void say(String line) {
        Services.channels().chat(WatcherLines.chat(line));
    }

    public void moveTo(double nx, double ny, double nz) { moveTo(nx, ny, nz, null); }

    /** Moves him (null yaw: along the way he moved, or unchanged); the Eyes and tags follow. */
    public void moveTo(double nx, double ny, double nz, Float nyaw) {
        if (main == null) return;
        Zombie m = main.body;
        if (m.isRemoved()) return;
        float newYaw;
        if (nyaw != null) newYaw = nyaw;
        else {
            double dx = nx - m.getX(), dz = nz - m.getZ();
            newYaw = dx * dx + dz * dz > 1e-4 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : curYaw;
        }
        curYaw = newYaw;
        for (Part p : parts()) place(p, nx + p.dx, ny + p.dy, nz + p.dz, newYaw);
    }

    private List<Part> parts() {
        List<Part> out = new ArrayList<>();
        if (main != null) out.add(main);
        out.addAll(eyes);
        return out;
    }

    /** Whether e is the Watcher, an Eye or one of their tags. */
    public boolean owns(Entity e) {
        for (Part p : parts()) if (p.body == e || p.tag == e) return true;
        return false;
    }

    /** Takes the Watcher, his Eyes and their tags away (no death animation). */
    public void remove() {
        for (Part p : parts()) { p.tag.discard(); p.body.discard(); }
        main = null; eyes = new ArrayList<>();
    }

    private Part part(double dx, double dy, double dz, float health, String role, String tagText) {
        Zombie m = new Zombie(EntityTypes.ZOMBIE, Sim.level());
        m.setNoAi(true); m.setSilent(true); m.setPermanentlyInvulnerable(true); m.setPersistenceRequired(); m.setNoGravity(true);
        AttributeInstance maxHp = m.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(health);
        AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(WatcherStats.MOVEMENT_SPEED);
        m.setHealth(health);
        m.setItemSlot(EquipmentSlot.HEAD, HeadItems.stack(role, textures));
        // the grey-blue swirl: endless Invisibility, particles shown, ambient look
        m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, true, true, false));
        m.setInvisible(true);
        ArmorStand s = new ArmorStand(EntityTypes.ARMOR_STAND, Sim.level());
        s.setInvisible(true); s.setNoGravity(true); s.setPermanentlyInvulnerable(true); s.setSilent(true);
        Station.setMarker(s);
        s.setCustomName(Sim.legacy(tagText));
        s.setCustomNameVisible(true);
        Part p = new Part(m, s, dx, dy, dz);
        place(p, x + dx, y + dy, z + dz, yaw);
        Sim.spawn(m);
        Sim.spawn(s);
        return p;
    }

    private void place(Part p, double px, double py, double pz, float pyaw) {
        p.body.snapTo(px, py, pz, pyaw, 0f);
        p.body.setYHeadRot(pyaw); p.body.setYBodyRot(pyaw);
        p.tag.snapTo(px, py + WatcherStats.TAG_DY, pz, 0f, 0f);
    }

    /** Rooms-pack texture roles of the two heads. */
    public static final String ROLE_WATCHER = "watcher";
    public static final String ROLE_EYE = "watchful_eye";

    /** Name tags: an icon glyph, then the name. */
    public static final String WATCHER_TAG = "§5 §r§cThe Watcher";
    public static final String EYE_TAG = "§9 §r§3§lWatchful Eye";

    public static final int EYES = 2;
}
