package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Phases.kt

import java.util.HashMap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

public class BossWither {
    public final String name;
    public final WitherBoss e;
    private ArmorStand tag = null;
    private ArmorStand speech = null;
    private String speechText = "";
    private int speechUntil = -1;
    private boolean removed = false;
    public Vec3 pos;

    public BossWither(String name, Vec3 at) { this(name, at, 1, true, 0); }

    public BossWither(String name, Vec3 at, int inv) { this(name, at, inv, true, 0); }

    public BossWither(String name, Vec3 at, int inv, boolean armoured) { this(name, at, inv, armoured, 0); }

    public BossWither(String name, Vec3 at, int inv, boolean armoured, int tagDelay) {
        this.name = name;
        WitherBoss w = new SimWither(Sim.level());
        w.setNoAi(true); w.setSilent(true); w.setInvulnerable(true); w.setNoGravity(true);
        w.setInvulnerableTicks(inv);
        w.setHealth(armoured ? 1f : w.getMaxHealth());
        w.snapTo(at.x, at.y, at.z, 0f, 0f);
        BossBar.hideOwn(w);
        Sim.spawn(w);
        this.e = w;
        this.pos = at;
        live.put(name, this);
        if (tagDelay <= 0) showTag(); else Fight.later(tagDelay, name + " name", () -> { if (!removed) showTag(); });
    }

    private void showTag() {
        tag = marker("§e﴾ §r§8§r§5 §r§c§l" + name + "§r §e﴿", TAG_Y);
        if (Fight.serverTick < speechUntil) showSpeech();
    }

    private ArmorStand marker(String text, double dy) {
        ArmorStand s = new ArmorStand(EntityTypes.ARMOR_STAND, Sim.level());
        s.setInvisible(true); s.setNoGravity(true); s.setInvulnerable(true); s.setSilent(true);
        Station.setMarker(s);
        s.setCustomName(Sim.legacy(text));
        s.setCustomNameVisible(true);
        s.snapTo(pos.x, pos.y + dy, pos.z, 0f, 0f);
        Sim.spawn(s);
        return s;
    }

    public void speak(String line) {
        if (removed) return;
        String text = "§4§l" + line;
        speechText = text;
        speechUntil = Fight.serverTick + (name.equals("Goldor") ? GOLDOR_SPEECH_TICKS : SPEECH_TICKS);
        if (speech != null) speech.discard();
        speech = null;
        if (tag != null) showSpeech();
    }

    private void showSpeech() {
        if (speech != null) speech.discard();
        ArmorStand s = marker(speechText, SPEECH_Y);
        speech = s;
        Fight.later(speechUntil - Fight.serverTick, name + " speech end", () -> { if (speech == s) { s.discard(); speech = null; } });
    }

    public void armour(boolean on) { if (e.getHealth() > 0f) e.setHealth(on ? 1f : e.getMaxHealth()); }

    public boolean armoured() { return e.getHealth() > 0f && e.getHealth() <= e.getMaxHealth() / 2; }

    public void dieAnim() {
        e.setHealth(0f);
        if (tag != null) tag.discard();
        if (speech != null) speech.discard();
    }

    public void moveTo(Vec3 p) { moveTo(p, null); }

    public void moveTo(Vec3 p, Vec3 faceTo) {
        pos = p;
        Vec3 f = faceTo != null ? faceTo : p;
        float yaw = faceTo != null ? (float) Math.toDegrees(Math.atan2(-(f.x - p.x), f.z - p.z)) : e.getYRot();
        e.snapTo(p.x, p.y, p.z, yaw, 0f);
        e.yHeadRot = yaw; e.yBodyRot = yaw;
        if (tag != null) tag.snapTo(p.x, p.y + TAG_Y, p.z, 0f, 0f);
        if (speech != null) speech.snapTo(p.x, p.y + SPEECH_Y, p.z, 0f, 0f);
    }

    public boolean step(Vec3 target, double speed) { return step(target, speed, target); }

    public boolean step(Vec3 target, double speed, Vec3 face) {
        Vec3 d = target.subtract(pos);
        double len = d.length();
        if (len <= speed) { moveTo(target, face); return true; }
        moveTo(pos.add(d.scale(speed / len)), face);
        return false;
    }

    public void remove() {
        removed = true;
        e.discard();
        if (tag != null) tag.discard();
        if (speech != null) speech.discard();
        if (live.get(name) == this) live.remove(name);
    }

    public static final double TAG_Y = 3.6875;
    public static final double SPEECH_Y = 4.09375;
    public static final int SPEECH_TICKS = 41;
    public static final int GOLDOR_SPEECH_TICKS = 61;
    private static final HashMap<String, BossWither> live = new HashMap<>();

    public static void speak(String name, String line) {
        BossWither w = live.get(name);
        if (w != null && !w.removed && !w.e.isRemoved()) w.speak(line);
    }
}
