// Ported from ADM's p3sim/clear/secrets/FloorDrops.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimStand;
import com.teslamaps.adm.sim.clear.blood.HeadTextures;
import com.teslamaps.adm.sim.clear.state.StateRules;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public final class FloorDrops {
    private FloorDrops() {}

    public static final double NAME_ABOVE_FEET = 0.531;
    public static final double MODEL_BELOW_NAME = 0.237;
    public static final double RADIUS = 2.5;
    public static final String BLESSING_TEXTURE = "ab436edb7b805e133f7d3489d46ce0636f7e1cb37b67689faa12e698dbbd7c66";
    public static final int LONGEST = 500;

    private static final class Drop {
        final String name;
        final ItemStack model;
        final boolean held;
        final double x, y, z;
        final int bornT;
        final int dueT;
        final UUID owner;
        final Consumer<UUID> take;
        List<ArmorStand> stands = List.of();
        int age = 0;

        Drop(String name, ItemStack model, boolean held, double x, double y, double z, int bornT, int dueT, UUID owner, Consumer<UUID> take) {
            this.name = name;
            this.model = model;
            this.held = held;
            this.x = x;
            this.y = y;
            this.z = z;
            this.bornT = bornT;
            this.dueT = dueT;
            this.owner = owner;
            this.take = take;
        }
    }

    private static final ArrayList<Drop> drops = new ArrayList<>();

    public static int botWait(double u) {
        double[] q = {0.0, 0.1, 0.5, 0.9, 1.0};
        double[] v = {0.0, 0.0, 9.0, 62.0, 120.0};
        for (int i = 1; i < q.length; i++) if (u <= q[i]) return (int) Math.round(v[i - 1] + (v[i] - v[i - 1]) * (u - q[i - 1]) / (q[i] - q[i - 1]));
        return 120;
    }

    public static ItemStack head(String key, Run run) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        List<SkullTexture> table = run != null && run.packs.rooms != null ? run.packs.rooms.textures : List.of();
        HeadTextures.Resolved tex = HeadTextures.resolve(key, table);
        if (tex != null) {
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(tex.profileId, "drop",
                    new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tex.value))))));
        }
        return stack;
    }

    public static void drop(Run run, String name, ItemStack model, boolean held, double x, double feetY, double z, int slot, UUID owner, KRandom rnd, Consumer<UUID> take) {
        PlayerRecord rec = owner != null ? run.state.players.get(owner) : null;
        boolean bot = rec != null && rec.isBot;
        int wait = bot ? botWait(rnd.nextDouble()) : LONGEST;
        Drop d = new Drop(name, model, held, Math.floor(x) + 0.5 + 0.5 * slot, feetY + NAME_ABOVE_FEET, Math.floor(z) + 0.5 + 0.5 * slot, run.t(), run.t() + wait, owner, take);
        Ec.safely("ads floor drop", () -> d.stands = build(d));
        drops.add(d);
    }

    private static ArmorStand stand(Drop d, double y, String name) {
        SimStand s = new SimStand(Sim.level());
        s.snapTo(d.x, y, d.z, 0f, 0f);
        s.setInvisible(true); s.setInvulnerable(true); s.setSilent(true);
        if (name != null) { s.setCustomName(Sim.legacy(name)); s.setCustomNameVisible(true); }
        return s;
    }

    private static List<ArmorStand> build(Drop d) {
        ArmorStand named = Sim.spawn(stand(d, d.y, d.name));
        ArmorStand model = stand(d, d.y - MODEL_BELOW_NAME, null);
        model.setItemSlot(d.held ? EquipmentSlot.MAINHAND : EquipmentSlot.HEAD, d.model.copy());
        return List.of(named, Sim.spawn(model));
    }

    public static void tick(Run run) {
        if (drops.isEmpty()) return;
        int t = run.t();
        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            d.age++;
            if (d.stands.size() > 1) {
                ArmorStand m = d.stands.get(1);
                if (!m.isRemoved()) m.snapTo(d.x, d.y - MODEL_BELOW_NAME + bob(d.age), d.z, m.getYRot() + StateRules.STAND_SPIN_DEG, 0f);
            }
            PlayerRecord best = null;
            double bestDist = 0;
            for (PlayerRecord p : run.state.players.values()) {
                if (p.ghost || p.isBot || Math.abs(p.y - (d.y - NAME_ABOVE_FEET)) >= 4.0 || Math.hypot(p.x - d.x, p.z - d.z) > RADIUS) continue;
                double dist = Math.hypot(p.x - d.x, p.z - d.z);
                if (best == null || dist < bestDist) { best = p; bestDist = dist; }
            }
            UUID taker;
            if (best != null) taker = best.uuid;
            else if (t >= d.dueT) {
                if (d.owner != null) taker = d.owner;
                else {
                    Iterator<UUID> keys = run.state.players.keySet().iterator();
                    taker = keys.hasNext() ? keys.next() : null;
                }
            } else taker = null;
            if (taker != null || t >= d.dueT) {
                for (ArmorStand s : d.stands) s.discard();
                it.remove();
                UUID by = taker;
                Ec.safely("ads floor drop take", () -> d.take.accept(by));
            }
        }
    }

    private static double bob(int age) {
        int half = StateRules.STAND_BOB_HALF_PERIOD;
        int p = age % (2 * half);
        return StateRules.STAND_BOB_MIN + StateRules.STAND_BOB_STEP * (p <= half ? p : 2 * half - p);
    }

    public static void clear() {
        for (Drop d : drops) for (ArmorStand s : d.stands) s.discard();
        drops.clear();
    }
}
