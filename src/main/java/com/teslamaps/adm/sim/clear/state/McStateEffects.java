// Ported from ADM's p3sim/clear/state/McStateEffects.kt
package com.teslamaps.adm.sim.clear.state;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.CarriedBlock;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimStand;
import com.teslamaps.adm.sim.clear.blood.HeadTextures;
import com.teslamaps.adm.sim.pack.DoorModel;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.Runs;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class McStateEffects implements StateEffects {
    public static final McStateEffects INSTANCE = new McStateEffects();

    private McStateEffects() {}

    private static final HashMap<Integer, List<ArmorStand>> stands = new HashMap<>();

    @Override public void chat(String text, UUID to) {
        if (to == null) Services.channels().chat(text);
        else Services.channels().chat(text, new Audience.Only(to));
    }

    @Override public void sound(Cue cue, UUID to) {
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(cue.id));
        if (ev == null) return;
        SoundSource source = switch (cue.source) {
            case MASTER -> SoundSource.MASTER;
            case BLOCKS -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case PLAYERS -> SoundSource.PLAYERS;
        };
        for (ServerPlayer p : Sim.players()) {
            if (to == null || p.getUUID().equals(to)) Sim.asPlayer(p, () -> Sim.sound(ev, cue.volume, cue.pitch, null, source));
        }
    }

    private static BlockState blockOf(PlacedDoor door, BodyBlocks blocks) {
        return switch (blocks) {
            case BARRIER -> Blocks.BARRIER.defaultBlockState();
            case AIR -> Blocks.AIR.defaultBlockState();
            case CLOSED -> {
                String id = DoorModel.bodyBlock(door.kind);
                yield id != null ? BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
        };
    }

    private static final LinkedHashMap<Long, Pair<BlockPos, BlockState>> pendingBlocks = new LinkedHashMap<>();

    @Override public void setBody(PlacedDoor door, BodyBlocks blocks, boolean onlyFirst) {
        BlockState state = blockOf(door, blocks);
        List<Pos> cells = door.bodyCells();
        for (int i = 0; i < cells.size(); i++) {
            Pos c = cells.get(i);
            if (onlyFirst && i > 0) break;
            BlockPos pos = new BlockPos(c.x, c.y, c.z);
            if (RunBlocks.isLoaded(c.x, c.z)) { pendingBlocks.remove(pos.asLong()); RunBlocks.set(c.x, c.y, c.z, state); }
            else pendingBlocks.put(pos.asLong(), Pair.of(pos, state));
        }
    }

    private static final class DoorDebris {
        final List<Bat> bats;
        final List<CarriedBlock> blocks;
        final List<Double> baseY;
        final List<Double> x;
        final List<Double> z;
        int age = 0;

        DoorDebris(List<Bat> bats, List<CarriedBlock> blocks, List<Double> baseY, List<Double> x, List<Double> z) {
            this.bats = bats; this.blocks = blocks; this.baseY = baseY; this.x = x; this.z = z;
        }
    }

    private static final ArrayList<DoorDebris> debris = new ArrayList<>();

    @Override public void spawnDoorDebris(PlacedDoor door) {
        BlockState block = blockOf(door, BodyBlocks.CLOSED);
        if (block.isAir()) return;
        ArrayList<Bat> bats = new ArrayList<>(); ArrayList<CarriedBlock> blocks = new ArrayList<>();
        ArrayList<Double> ys = new ArrayList<>(); ArrayList<Double> xs = new ArrayList<>(); ArrayList<Double> zs = new ArrayList<>();
        for (Pos c : door.bodyCells()) {
            if (!RunBlocks.isLoaded(c.x, c.z)) continue;
            double x = c.x + 0.5; double z = c.z + 0.5; double y = c.y + StateRules.DEBRIS_DY;
            Bat b = new Bat(EntityType.BAT, Sim.level());
            b.setSilent(true); b.setInvulnerable(true); b.setInvisible(true);
            b.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
            b.setNoAi(true); b.setNoGravity(true);
            b.snapTo(x, y, z, 0f, 0f);
            CarriedBlock f = new CarriedBlock(Sim.level(), block);
            f.snapTo(x, y, z, 0f, 0f);
            Sim.spawn(b); Sim.spawn(f);
            bats.add(b); blocks.add(f); ys.add(y); xs.add(x); zs.add(z);
        }
        if (!bats.isEmpty()) debris.add(new DoorDebris(bats, blocks, ys, xs, zs));
    }

    private static void tickDebris() {
        Iterator<DoorDebris> it = debris.iterator();
        while (it.hasNext()) {
            DoorDebris d = it.next();
            d.age++;
            if (d.age >= StateRules.DEBRIS_REMOVE) {
                for (CarriedBlock b : d.blocks) b.discard();
                for (Bat b : d.bats) b.discard();
                it.remove();
            } else if (d.age == StateRules.DEBRIS_RIDE_DELAY) {
                for (int i = 0; i < d.bats.size(); i++) {
                    if (!d.bats.get(i).isRemoved() && !d.blocks.get(i).isRemoved()) d.blocks.get(i).startRiding(d.bats.get(i), true, false);
                }
            } else if (d.age >= StateRules.DEBRIS_SINK_START && d.age <= StateRules.DEBRIS_LAST_DROP_TICK) {
                double drop = StateRules.DEBRIS_FIRST_DROP + StateRules.DEBRIS_DROP_PER_TICK * Math.max(d.age - StateRules.DEBRIS_SINK_START, 0);
                for (int i = 0; i < d.bats.size(); i++) {
                    if (!d.bats.get(i).isRemoved()) d.bats.get(i).snapTo(d.x.get(i), d.baseY.get(i) - drop, d.z.get(i), 0f, 0f);
                }
            }
        }
    }

    private static final class StandSpec {
        final KeyKind kind;
        final double x;
        final double z;
        double y;
        int age = 0;

        StandSpec(KeyKind kind, double x, double z, double y) { this.kind = kind; this.x = x; this.z = z; this.y = y; }
    }

    private static final class Lingering {
        final ArmorStand stand;
        int left;

        Lingering(ArmorStand stand, int left) { this.stand = stand; this.left = left; }
    }

    private static final ArrayList<Lingering> lingering = new ArrayList<>();

    private static final HashMap<Integer, StandSpec> specs = new HashMap<>();
    private static int ticks = 0;

    @Override public void tick() {
        tickDebris();
        tickStands();
        if (++ticks % RECHECK_TICKS != 0) return;
        if (!pendingBlocks.isEmpty()) {
            Iterator<Pair<BlockPos, BlockState>> it = pendingBlocks.values().iterator();
            while (it.hasNext()) {
                Pair<BlockPos, BlockState> pe = it.next();
                BlockPos pos = pe.first;
                if (RunBlocks.isLoaded(pos.getX(), pos.getZ())) { RunBlocks.set(pos.getX(), pos.getY(), pos.getZ(), pe.second); it.remove(); }
            }
        }
        for (Map.Entry<Integer, StandSpec> e : new ArrayList<>(specs.entrySet())) {
            int id = e.getKey();
            StandSpec spec = e.getValue();
            List<ArmorStand> live = stands.get(id);
            if (live != null && live.stream().noneMatch(ArmorStand::isRemoved)) continue;
            if (!RunBlocks.isLoaded((int) Math.floor(spec.x), (int) Math.floor(spec.z))) continue;
            if (live != null) for (ArmorStand s : live) s.discard();
            materialise(id, spec);
        }
    }

    private static void tickStands() {
        for (Map.Entry<Integer, StandSpec> e : specs.entrySet()) {
            List<ArmorStand> l = stands.get(e.getKey());
            ArmorStand model = l != null && l.size() > 1 ? l.get(1) : null;
            if (model == null) continue;
            if (model.isRemoved()) continue;
            StandSpec spec = e.getValue();
            spec.age++;
            model.snapTo(spec.x, spec.y - StateRules.MODEL_STAND_BELOW + bob(spec.age), spec.z, model.getYRot() + StateRules.STAND_SPIN_DEG, 0f);
        }
        Iterator<Lingering> it = lingering.iterator();
        while (it.hasNext()) { Lingering l = it.next(); if (--l.left <= 0) { l.stand.discard(); it.remove(); } }
    }

    @Override public void reset() { pendingBlocks.clear(); clearDebris(); }

    private static void clearDebris() {
        for (DoorDebris d : debris) { for (CarriedBlock b : d.blocks) b.discard(); for (Bat b : d.bats) b.discard(); }
        debris.clear();
    }

    private static double floorY(double x, double z, double guess) {
        int bx = (int) Math.floor(x); int bz = (int) Math.floor(z);
        int from = (int) Math.floor(guess) + 3;
        for (int y = from; y >= from - 14; y--) {
            BlockState s = RunBlocks.get(bx, y, bz);
            if (s == null) return guess;
            if (!s.isAir() && !s.getCollisionShape(RunBlocks.level(), new BlockPos(bx, y, bz)).isEmpty()) return y + 1 - StateRules.STAND_Y_BELOW_SURFACE;
        }
        return guess;
    }

    @Override public double spawnStand(int id, KeyKind kind, double x, double z, double floorGuessY) {
        StandSpec spec = new StandSpec(kind, x, z, floorGuessY);
        specs.put(id, spec);
        if (!RunBlocks.isLoaded((int) Math.floor(x), (int) Math.floor(z))) return floorGuessY;
        return materialise(id, spec);
    }

    private static ArmorStand stand(double x, double z, String name, ItemStack head, double at) {
        SimStand s = new SimStand(Sim.level());
        s.snapTo(x, at, z, 0f, 0f);
        s.setInvisible(true); s.setInvulnerable(true); s.setSilent(true);
        if (name != null) { s.setCustomName(Sim.legacy(name)); s.setCustomNameVisible(true); }
        if (head != null) s.setItemSlot(EquipmentSlot.HEAD, head);
        return Sim.spawn(s);
    }

    private static double materialise(int id, StandSpec spec) {
        KeyKind kind = spec.kind;
        double x = Math.floor(spec.x) + 0.5; double z = Math.floor(spec.z) + 0.5;
        double y = floorY(x, z, spec.y);
        spec.y = y; spec.age = 0;
        String named = kind == KeyKind.WITHER ? "§8Wither Key" : "§cBlood Key";
        ArmorStand nameStand = stand(x, z, named, null, y);
        ArmorStand modelStand = stand(x, z, null, keyHead(kind), y - StateRules.MODEL_STAND_BELOW);
        stands.put(id, List.of(nameStand, modelStand));
        return y;
    }

    private static ItemStack keyHead(KeyKind kind) {
        Run cur = Runs.current;
        List<SkullTexture> table = cur != null && cur.packs.rooms != null ? cur.packs.rooms.textures : Collections.emptyList();
        HeadTextures.Resolved tex = HeadTextures.resolve(kind == KeyKind.WITHER ? WITHER_KEY_ROLE : BLOOD_KEY_ROLE, table);
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        if (tex != null) {
            GameProfile gp = new GameProfile(tex.profileId, "key", new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tex.value))));
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gp));
        }
        return stack;
    }

    @Override public void removeStand(int id) {
        specs.remove(id);
        List<ArmorStand> l = stands.remove(id);
        if (l == null) return;
        if (l.size() > 1) l.get(1).discard();
        if (!l.isEmpty()) lingering.add(new Lingering(l.get(0), StateRules.NAME_STAND_LINGER));
    }

    @Override public void clearStands() {
        specs.clear();
        for (List<ArmorStand> l : stands.values()) for (ArmorStand s : l) s.discard();
        stands.clear();
        for (Lingering l : lingering) l.stand.discard();
        lingering.clear();
        clearDebris();
    }

    static double bob(int age) {
        int half = StateRules.STAND_BOB_HALF_PERIOD;
        int p = age % (2 * half);
        return StateRules.STAND_BOB_MIN + StateRules.STAND_BOB_STEP * (p <= half ? p : 2 * half - p);
    }

    private static final String BLOOD_KEY_ROLE = "blood_key";
    private static final String WITHER_KEY_ROLE = "wither_key";

    private static final int RECHECK_TICKS = 20;
}
