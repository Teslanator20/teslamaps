// Ported from ADM's p3sim/clear/mobs/MobChatter.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.TransientStand;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MobChatter {
    private static final class Talk {
        final MobEntry a, b;
        final List<String> lines;
        int step;
        int nextAt;

        Talk(MobEntry a, MobEntry b, List<String> lines, int step, int nextAt) {
            this.a = a; this.b = b; this.lines = lines; this.step = step; this.nextAt = nextAt;
        }
    }

    private Talk talk = null;
    private final ArrayList<Pair<Entity, Integer>> stands = new ArrayList<>();

    public void tick(int t, Collection<MobEntry> entries, KRandom rng) {
        stands.removeIf(p -> {
            Entity s = p.first;
            if (t >= p.second || s.isRemoved()) { s.discard(); return true; }
            return false;
        });
        Talk k = talk;
        if (k != null) {
            if (t < k.nextAt) return;
            MobEntry who = k.step == 1 ? k.b : k.a;
            if (who.alive() && who.live) say(who, k.lines.get(k.step), t);
            k.step++;
            if (k.step >= k.lines.size()) talk = null; else k.nextAt = t + LINE_GAP;
            return;
        }
        if (rng.nextDouble() >= START_CHANCE) return;
        List<MobEntry> speakers = new ArrayList<>();
        for (MobEntry it : entries) {
            if (it.alive() && it.live && !it.asleep && it.body != null && !(it.body instanceof AdsDisguise) && !it.body.isInvisible()) speakers.add(it);
        }
        if (speakers.size() < 2) return;
        MobEntry a = speakers.get(rng.nextInt(speakers.size()));
        List<MobEntry> partners = new ArrayList<>();
        for (MobEntry it : speakers) {
            if (it == a) continue;
            double d = Math.hypot(it.x - a.x, it.z - a.z);
            if (d >= 1.0 && d <= PARTNER_RANGE) partners.add(it);
        }
        if (partners.isEmpty()) return;
        MobEntry b = partners.get(rng.nextInt(partners.size()));
        List<String> lines = CONVERSATIONS.get(rng.nextInt(CONVERSATIONS.size()));
        say(a, lines.get(0), t);
        talk = new Talk(a, b, lines, 1, t + LINE_GAP);
    }

    private void say(MobEntry e, String text, int t) {
        Entity body = e.body;
        if (body == null) return;
        TransientStand s = new TransientStand(Sim.level());
        s.snapTo(body.getX(), body.getY() + HEIGHT, body.getZ(), body.getYRot(), 0f);
        s.setInvisible(true);
        s.setInvulnerable(true);
        s.setSilent(true);
        s.setNoGravity(true);
        try { s.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, FLAGS); } catch (Throwable ignored) {}
        s.setCustomName(Sim.legacy(text));
        s.setCustomNameVisible(true);
        stands.add(Pair.of(Sim.spawn(s), t + LIFE));
    }

    public void clear() {
        for (Pair<Entity, Integer> p : stands) p.first.discard();
        stands.clear();
        talk = null;
    }

    public static final List<List<String>> CONVERSATIONS = List.of(
            List.of("§6I almost died last week!", "§6Dude.. we're undead.", "§6Oh"),
            List.of("§6What was that?", "§6What?", "§6Thought I heard something...")
    );
    public static final int LINE_GAP = 30;
    public static final int LIFE = 30;
    public static final double HEIGHT = 2.375;
    public static final double PARTNER_RANGE = 7.0;
    public static final byte FLAGS = 18;
    public static final double START_CHANCE = 1.0 / 800.0;
}
