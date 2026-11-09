package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Phases.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class P1Maxor extends Fight.Phase {
    private final com.teslamaps.adm.sim.run.BossEntryOptions entry;

    public P1Maxor() { this(null); }

    public P1Maxor(com.teslamaps.adm.sim.run.BossEntryOptions entry) {
        super("P1");
        this.entry = entry;
    }

    @Override
    public Fight.Start restart() { return Fight.Start.P1; }

    private BossWither maxor;

    private class Top {
        final int side;
        final EndCrystal e;
        ArmorStand name = null;
        ArmorStand click = null;

        Top(int side, boolean stands) {
            this.side = side;
            this.e = spawnCrystal(TOPS.get(side));
            if (stands) labels();
        }

        void labels() {
            if (name != null || e.isRemoved()) return;
            name = label(TOPS.get(side).add(0.0, 0.125, 0.0), "§bEnergy Crystal");
            click = label(TOPS.get(side).add(0.0, -0.25, 0.0), "§e§lCLICK HERE");
        }

        boolean owns(Entity x) { return x == e || x == name || x == click; }

        void remove() {
            e.discard();
            if (name != null) name.discard();
            if (click != null) click.discard();
        }
    }

    private class Pylon {
        final int side;
        EndCrystal crystal = null;
        int placedAt = -1;
        ArmorStand top = null;
        ArmorStand bottom = null;

        Pylon(int side) { this.side = side; }

        boolean placed() { return crystal != null; }

        void open() {
            if (top != null) return;
            top = label(PYLONS.get(side).add(0.0, -0.875, 0.0), "§cEnergy Crystal Missing");
            bottom = label(PYLONS.get(side).add(0.0, -1.25, 0.0), "§e§lCLICK HERE");
        }

        void active() { rename(top, "§aCrystal Active"); rename(bottom, ""); }

        void missing() { rename(top, "§cEnergy Crystal Missing"); rename(bottom, "§e§lCLICK HERE"); }

        boolean owns(Entity x) { return x == crystal || x == top || x == bottom; }

        void clear() { if (crystal != null) crystal.discard(); crystal = null; }

        void remove() {
            clear();
            if (top != null) top.discard();
            if (bottom != null) bottom.discard();
            top = null; bottom = null;
        }
    }

    private class Carrier {
        final Party.Bot bot;
        final int side;
        boolean holding = false;
        int leaveAt = 5 + KRandom.DEFAULT.nextInt(20);
        int pylon;
        int fetch;

        Carrier(Party.Bot bot, int side) {
            this.bot = bot;
            this.side = side;
            this.pylon = side;
            this.fetch = side;
        }
    }

    private int lastOpen() { return lastHit < 0 ? OPEN : Math.max(OPEN, lastHit + 40); }

    private final Top[] tops = new Top[2];
    private final Pylon[] pylons = { new Pylon(0), new Pylon(1) };
    private final ArrayList<Carrier> carriers = new ArrayList<>();
    private Party.Bot lure = null;
    private final PerPlayer<Integer> carryingBy = new PerPlayer<>(() -> 0);
    private final PerPlayer<Integer> crystalSlot = new PerPlayer<>(() -> -1);
    private final ArrayList<Integer> placeTimes = new ArrayList<>();
    private final HashMap<Integer, String> pending = new HashMap<>();
    private boolean open = false;
    private int chargeAt = -1;
    private boolean beacon = false;
    private int hits = 0;
    private int lastHit = -1000;
    private boolean stunned = false;
    private int enrageAt = -1;
    private int moveAt = MOVE;
    private int stunLines = 0;
    private int tauntAt = -1;
    private int killAt = -1;
    private float health = 1f;
    private float healthFloor = 1f;
    private float healthRate = 0f;
    private final HashMap<Integer, List<Pair<BlockPos, Boolean>>> lights = new HashMap<>();
    private boolean centreLit = false;
    private final List<String> TAUNTS_A = List.of(
        "YOUR WEAPONS CAN'T PIERCE THROUGH MY SHIELD!", "YOUR MOBILITY TRICKS DON'T WORK IN MY DOMAIN!",
        "I HOPE YOU LIKE EXPLOSIONS TOO!", "MY MINIONS WILL HAVE TO WIPE THE FLOOR AFTER I'M DONE WITH YOU ALL!"
    );
    private final List<String> TAUNTS_SKULL = List.of("How about you taste some rapid fire Wither Skulls!", "Time for me to blast you away for good!", "Eat Wither Skulls, scum!");
    private final int skullTauntAt = KRandom.DEFAULT.nextInt(100) < 11 ? 436 + KRandom.DEFAULT.nextInt(12) : -1;
    private final HashSet<Integer> clicked = new HashSet<>();
    private boolean handed = false;
    private int belowAt = -1;

    private int carrying() { return carryingBy.get(); }

    private void setCarrying(int v) { carryingBy.set(v); }

    @Override
    public void start() {
        if (Sim.players().isEmpty()) return;
        Spots.Spot s = Spots.P1;
        Sim.forEachPlayer(p -> {
            if (entry == null) Sim.tp(p, s.x, s.y, s.z, s.yaw, s.pitch);
            if (entry == null || entry.giveHotbar) SimItems.giveHotbar(p, false);
        });
        BossBar.show("§c§lMaxor", 1f);
        Sim.boss("Maxor", "WELL! WELL! WELL! LOOK WHO'S HERE!", entry != null ? new Vec3(73.5, 226.5, 53.5) : null);
        if (entry == null) { stripStart(); Blocks.play("p1strip", 0, COLUMN); }
        else Fight.later(10, "run p1 strip", () -> { stripStart(); Blocks.play("p1strip", 0, COLUMN); });
        Party.standAt(List.of(new Vec3(71.5, 221.0, 16.5), new Vec3(75.5, 221.0, 16.5), new Vec3(69.5, 221.0, 18.5), new Vec3(77.5, 221.0, 18.5)));
        List<Party.Bot> bots = new ArrayList<>();
        if (P3Sim.bots()) for (Party.Bot b : Party.bots()) if (b.entity != null) bots.add(b);
        Party.Bot berserk = of(bots, DungeonClass.BERSERK);
        if (berserk != null) carriers.add(new Carrier(berserk, 0));
        Party.Bot mage = of(bots, DungeonClass.MAGE);
        if (mage != null) carriers.add(new Carrier(mage, 1));
        lure = of(bots, DungeonClass.TANK);
        healer = of(bots, DungeonClass.HEALER);
        archer = of(bots, DungeonClass.ARCHER);
    }

    private static Party.Bot of(List<Party.Bot> bots, DungeonClass c) {
        for (Party.Bot b : bots) if (b.clazz == c) return b;
        return null;
    }

    private Party.Bot healer = null;
    private Party.Bot archer = null;

    @Override
    public void stop() {
        for (Top tp : tops) if (tp != null) tp.remove();
        for (Pylon pl : pylons) pl.remove();
        if (maxor != null && !handed) maxor.remove();
        Blocks.stop("p1strip");
        Sim.forEachPlayer(p -> { if (carrying() > 0) { setCarrying(0); giveBackMenu(); } });
    }

    private boolean check() { return t % 10 == 0; }

    @Override
    public void tick() {
        if (t == (entry != null ? 4 : 5)) {
            maxor = new BossWither("Maxor", new Vec3(73.0, 226.0, 53.0), 200, true, 17);
            maxor.speak("WELL! WELL! WELL! LOOK WHO'S HERE!");
            for (int i = 0; i <= 1; i++) tops[i] = new Top(i, false);
        } else if (t == 28) {
            for (Top tp : tops) if (tp != null) tp.labels();
        } else if (t == 42) {
            Sim.boss("Maxor", "I'VE BEEN TOLD I COULD HAVE A BIT OF FUN WITH YOU.");
        } else if (t == 84) {
            Sim.boss("Maxor", "DON'T DISAPPOINT ME, I HAVEN'T HAD A GOOD FIGHT IN A WHILE.");
        } else if (t == OPEN) {
            open = true;
            Blocks.set(new BlockPos(73, 221, 73), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); Blocks.set(new BlockPos(73, 222, 73), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            for (Pylon pl : pylons) pl.open();
            Sim.note("The pylons take crystals now: pick one up on top, right-click a pylon (or let the bots).");
        } else if (t == BEACON) {
            beacon = true; Blocks.set(new BlockPos(73, 221, 73), net.minecraft.world.level.block.Blocks.BEACON.defaultBlockState());
        }
        List<Pair<BlockPos, Boolean>> due = lights.remove(t);
        if (due != null) for (Pair<BlockPos, Boolean> l : due) lamp(l.first, l.second);
        if (maxor == null) return;
        if ((t + 3) % 20 == 0) BossBar.progress(health);
        if (killAt >= 0) { killed(); return; }
        bots();
        if (open && t % 10 == 1) {
            for (var en : pending.entrySet()) place(en.getKey(), en.getValue());
            pending.clear();
        }
        if (chargeAt >= 0 && t == chargeAt) Sim.chat("§aThe Energy Laser is charging up!");
        if (check()) column();
        if (t == tauntAt) Sim.boss("Maxor", TAUNTS_A.get(KRandom.DEFAULT.nextInt(TAUNTS_A.size())));
        if (t == skullTauntAt && !stunned && !inAbility()) Sim.boss("Maxor", TAUNTS_SKULL.get(KRandom.DEFAULT.nextInt(TAUNTS_SKULL.size())));
        if (stunned && t == enrageAt) {
            Sim.chat("§c⚠ Maxor is enraged! ⚠");
            stunned = false; moveAt = t + 1 + KRandom.DEFAULT.nextInt(5); tauntAt = t + 161 + KRandom.DEFAULT.nextInt(4);
            health = 0.25f;
            maxor.armour(true);
        }
        if (t == BEACON - 1 && tauntAt < 0) tauntAt = BEACON + 161;
        if (stunned) {
            maxor.armour(KRandom.DEFAULT.nextInt(25) == 0);
            health = Math.max(healthFloor, health - healthRate);
        }
        Vec3 target = PhasesKt.closest(maxor.pos).add(0.0, 1.0, 0.0);
        if (stunned) maxor.moveTo(maxor.pos, maxor.pos.add(0.0, 0.0, 1.0));
        else if (t >= moveAt) {
            double d = maxor.pos.distanceTo(target);
            if (d > 3.0) maxor.step(target, Math.min(0.9, 0.24 + 0.02 * d)); else maxor.moveTo(maxor.pos, target);
        }
        else maxor.moveTo(maxor.pos, target);
        if (check() && charged() && beacon && inBeam()) hit();
    }

    private boolean charged() { return chargeAt >= 0 && t >= chargeAt; }

    private boolean inBeam() { return maxor.pos.y >= 225.0 && Math.hypot(maxor.pos.x - BEAM.x, maxor.pos.z - BEAM.z) <= 3.5; }

    private boolean inAbility() { return tauntAt >= 0 && t >= tauntAt - 1 && t < tauntAt + 62; }

    private EndCrystal spawnCrystal(Vec3 at) {
        EndCrystal c = new EndCrystal(EntityTypes.END_CRYSTAL, Sim.level());
        c.setShowBottom(false);
        c.setInvulnerable(true);
        c.snapTo(at.x, at.y, at.z, 0f, 0f);
        return Sim.spawn(c);
    }

    private ArmorStand label(Vec3 at, String text) {
        ArmorStand s = new ArmorStand(EntityTypes.ARMOR_STAND, Sim.level());
        s.setInvisible(true); s.setNoGravity(true); s.setInvulnerable(true); s.setSilent(true);
        rename(s, text);
        s.snapTo(at.x, at.y, at.z, 0f, 0f);
        return Sim.spawn(s);
    }

    private void rename(ArmorStand s, String text) {
        if (s == null) return;
        s.setCustomName(Sim.legacy(text));
        s.setCustomNameVisible(!text.isEmpty());
    }

    public boolean useCrystal(Entity c) {
        Top top = null;
        for (Top tp : tops) if (tp != null && tp.owns(c)) { top = tp; break; }
        if (top != null) {
            if (killAt >= 0) return true;
            take(top);
            setCarrying(carrying() + 1);
            String me = Sim.me();
            Sim.everyone(() -> { Sim.chat("§b" + me + "§r§a picked up an §r§bEnergy Crystal§r§a!"); });
            holdCrystal();
            return true;
        }
        Pylon pylon = null;
        for (Pylon pl : pylons) if (pl.owns(c)) { pylon = pl; break; }
        if (pylon == null) return false;
        if (pylon.placed() || pending.containsKey(pylon.side) || clicked.contains(pylon.side) || killAt >= 0) return true;
        if (carrying() <= 0) Sim.chat("§cYou do not current have an §r§bEnergy Crystal§r§c! Find one and bring it here!");
        else usePylon(PYLONS.get(pylon.side));
        return true;
    }

    private void take(Top top) {
        top.remove();
        tops[top.side] = null;
    }

    public boolean usePylon(Vec3 at) {
        if (carrying() <= 0 || !open || killAt >= 0) return false;
        int i = -1;
        for (int k = 0; k < PYLONS.size(); k++) {
            Vec3 it = PYLONS.get(k);
            if (Math.hypot(it.x - at.x, it.z - at.z) < 3.5 && Math.abs(it.y - at.y) < 4) { i = k; break; }
        }
        if (i < 0 || pylons[i].placed() || pending.containsKey(i) || clicked.contains(i)) return false;
        setCarrying(carrying() - 1);
        clicked.add(i);
        int idx = i;
        Fight.afterPing("maxor place", () -> { clicked.remove(idx); if (killAt < 0) place(idx, Sim.me()); });
        return true;
    }

    private void holdCrystal() {
        ServerPlayer p = Sim.player();
        if (p == null) return;
        Inventory inv = p.getInventory();
        if (crystalSlot.get() < 0) {
            int found = 8;
            for (int i = 0; i <= 8; i++) if ("SKYBLOCK_MENU".equals(SimItems.idOf(inv.getItem(i)))) { found = i; break; }
            crystalSlot.set(found);
        }
        int slot = crystalSlot.get();
        inv.setItem(slot, crystalItem());
        inv.setSelectedSlot(slot);
        p.connection.send(new ClientboundSetHeldSlotPacket(slot));
        p.inventoryMenu.broadcastChanges();
    }

    private void giveBackMenu() {
        ServerPlayer p = Sim.player();
        if (p == null) return;
        int slot = crystalSlot.get();
        if (slot < 0) return;
        crystalSlot.set(-1);
        if ("MAXOR_ENERGY_CRYSTAL".equals(SimItems.idOf(p.getInventory().getItem(slot)))) p.getInventory().setItem(slot, SimItems.MENU());
        p.inventoryMenu.broadcastChanges();
    }

    private ItemStack crystalItem() {
        ItemStack s = new ItemStack(Items.NETHER_STAR);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("§bEnergy Crystal").withStyle(st -> st.withItalic(false)));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "MAXOR_ENERGY_CRYSTAL"); tag.putBoolean("p3sim", true);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    private void place(int i, String by) {
        Pylon pylon = pylons[i];
        if (pylon.placed()) return;
        pylon.crystal = spawnCrystal(PYLONS.get(i));
        pylon.placedAt = t;
        if (by.equals(Sim.me()) && carrying() == 0) giveBackMenu();
        int count = 0;
        for (int it : placeTimes) if (t >= it + 28) count++;
        int x = 1 + count;
        placeTimes.add(t);
        Sim.everyone(() -> { Sim.chat(x >= 2 ? "§a" + x + "/2 Energy Crystals are now active!" : "§c" + x + "§r§a/2 Energy Crystals are now active!"); });
        EndCrystal c = pylon.crystal;
        Fight.later(20, "maxor pylon active", () -> { if (Fight.phase == this && pylon.crystal == c && c != null) pylon.active(); });
        light(t, i, true, !centreLit);
        centreLit = true;
        boolean all = true;
        for (Pylon pl : pylons) if (!pl.placed()) { all = false; break; }
        if (all && chargeAt < 0) chargeAt = t + 28;
    }

    private void light(int at, int side, boolean on, boolean centre) {
        List<BlockPos> floor = FLOORS.get(side);
        for (int i = 0; i < floor.size(); i++) queue(at + i, floor.get(i), on);
        List<BlockPos> ceiling = CEILINGS.get(side);
        for (int j = 0; j < ceiling.size(); j++) queue(at + 19 + j, ceiling.get(j), on);
        if (centre) for (int k = 0; k < CENTRE.size(); k++) queue(at + 24 + k, CENTRE.get(k), on);
    }

    private void queue(int at, BlockPos p, boolean on) {
        if (at <= t) lamp(p, on); else lights.computeIfAbsent(at, k -> new ArrayList<>()).add(Pair.of(p, on));
    }

    private void lamp(BlockPos p, boolean on) {
        Blocks.set(p, on ? net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState() : net.minecraft.world.level.block.Blocks.COAL_BLOCK.defaultBlockState());
    }

    private void column() {
        int n = 0;
        for (int it : placeTimes) if (t >= it + 28) n++;
        BlockState state;
        if (charged() && beacon) state = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().defaultBlockState();
        else if (n >= 1) state = net.minecraft.world.level.block.Blocks.STAINED_GLASS.yellow().defaultBlockState();
        else state = net.minecraft.world.level.block.Blocks.STAINED_GLASS.black().defaultBlockState();
        for (int y = (t >= BEACON + 120 ? 222 : 223); y <= 224; y++) {
            if (!Objects.equals(Blocks.get(new BlockPos(73, y, 73)), state)) Blocks.set(new BlockPos(73, y, 73), state);
        }
    }

    private void stripStart() {
        JsonObject o = Blocks.extra("anims-p124.json", "p1stripStart");
        JsonArray blocks = o != null ? o.getAsJsonArray("blocks") : null;
        if (blocks == null) return;
        for (JsonElement e : blocks) {
            JsonArray a = e.getAsJsonArray();
            BlockPos pos = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            if (COLUMN.contains(pos)) continue;
            Blocks.set(pos, Arena.parse(a.get(3).getAsString()));
        }
    }

    private void bots() {
        if (lure != null) { if (t >= 5) walk(lure, LURE, 0.7); }
        for (Carrier c : carriers) {
            if (c.bot.entity == null) continue;
            if (!c.holding) {
                if (c.leaveAt == Integer.MAX_VALUE && allZero(carryingBy.values()) && open) {
                    Integer orphan = null;
                    for (int s = 0; s <= 1; s++) {
                        boolean noneSide = true;
                        for (Carrier o : carriers) if (o.side == s) { noneSide = false; break; }
                        if (tops[s] != null && noneSide && t >= lastOpen() + 60) { orphan = s; break; }
                    }
                    if (orphan != null) { c.fetch = orphan; c.leaveAt = t; }
                }
                if (t < c.leaveAt) continue;
                Top top = tops[c.fetch];
                if (walk(c.bot, TOP_STANDS.get(c.fetch), 0.7) && top != null) {
                    take(top);
                    c.holding = true;
                    Sim.chat("§a" + c.bot.name + "§r§a picked up an §r§bEnergy Crystal§r§a!");
                }
            } else {
                if (pylons[c.pylon].placed() || clicked.contains(c.pylon) || (pending.containsKey(c.pylon) && !Objects.equals(pending.get(c.pylon), c.bot.name))) c.pylon = 1 - c.pylon;
                if (walk(c.bot, PYLON_STANDS.get(c.pylon), 0.7) && open && !pylons[c.pylon].placed() && !pending.containsKey(c.pylon) && !clicked.contains(c.pylon)) {
                    pending.put(c.pylon, c.bot.name);
                    c.holding = false;
                    c.leaveAt = Integer.MAX_VALUE;
                }
            }
        }
    }

    private static boolean allZero(List<Integer> vs) {
        for (Integer v : vs) if (v != 0) return false;
        return true;
    }

    private static int sum(List<Integer> vs) {
        int s = 0;
        for (Integer v : vs) s += v;
        return s;
    }

    private boolean walk(Party.Bot b, Vec3 to, double speed) {
        Vec3 d = to.subtract(b.pos);
        double len = d.length();
        if (len < 0.05) return true;
        if (Math.abs(d.x) + Math.abs(d.z) > 0.01) b.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        b.pos = len <= speed ? to : b.pos.add(d.scale(speed / len));
        var e = b.entity;
        if (e != null) { e.snapTo(b.pos.x, b.pos.y, b.pos.z, b.yaw, 0f); e.yHeadRot = b.yaw; e.yBodyRot = b.yaw; }
        return len <= speed;
    }

    public void botsChanged() {
        List<Carrier> gone = new ArrayList<>();
        for (Carrier c : carriers) if (c.bot.entity == null) gone.add(c);
        if (gone.isEmpty()) return;
        int had = 0;
        for (Carrier c : gone) if (c.holding) had++;
        carriers.removeAll(gone);
        if (had > 0 && killAt < 0) respawnMissing(had);
        if (lure == null || lure.entity == null) lure = null;
    }

    private void respawnMissing(int had) {
        List<Integer> botHeld = new ArrayList<>();
        for (Carrier c : carriers) if (c.holding) botHeld.add(c.fetch);
        List<Integer> missing = new ArrayList<>();
        for (int it = 0; it <= 1; it++) {
            if (tops[it] == null && !pylons[it].placed() && !pending.containsKey(it) && !clicked.contains(it) && !botHeld.contains(it)) missing.add(it);
        }
        int drop = sum(carryingBy.values());
        int taken = 0;
        for (int k = drop; k < missing.size() && taken < had; k++, taken++) {
            int it = missing.get(k);
            tops[it] = new Top(it, true);
        }
    }

    public void playerLeft(ServerPlayer p) {
        Sim.asPlayer(p, () -> {
            int had = carrying();
            if (had <= 0) return;
            setCarrying(0);
            if (killAt >= 0) return;
            respawnMissing(had);
        });
    }

    private void hit() {
        hits++;
        lastHit = t;
        chargeAt = -1;
        int at = t;
        if (hits == 1) { health = 0.95f; healthFloor = 0.30f + KRandom.DEFAULT.nextFloat() * 0.35f; healthRate = 0.004f + KRandom.DEFAULT.nextFloat() * 0.006f; }
        else { health = 0.12f + KRandom.DEFAULT.nextFloat() * 0.08f; healthFloor = 0.01f; healthRate = 0.01f; }
        for (Carrier c : carriers) if (!c.holding) { c.fetch = c.side; c.leaveAt = at + 15 + KRandom.DEFAULT.nextInt(10); }
        Fight.later(40, "maxor lines dark", () -> { if (Fight.phase == this) { light(t, 0, false, true); light(t, 1, false, false); centreLit = false; } });
        Fight.later(40, "maxor crystals", () -> { if (Fight.phase == this) for (int i = 0; i <= 1; i++) if (tops[i] == null) tops[i] = new Top(i, true); });
        Fight.later(41, "maxor pylons", () -> {
            if (Fight.phase == this && killAt < 0) for (Pylon pl : pylons) { if (pl.placed() && pl.placedAt <= at) { pl.clear(); pl.missing(); } }
            placeTimes.removeIf(it -> it <= at);
        });
        if (hits >= 2) killAt = t + (P3Sim.bots() ? 1 + KRandom.DEFAULT.nextInt(9) : 8 + KRandom.DEFAULT.nextInt(8));
        if (inAbility()) {
            int lineAt = tauntAt + 62;
            Fight.later(lineAt - t, "maxor late stun", () -> {
                if (Fight.phase == this || handed) stunLine();
                if (Fight.phase == this && killAt < 0) freeze();
            });
        } else {
            freeze();
            Fight.later(STUN_LINE, "maxor stun line", () -> { if (Fight.phase == this || handed) stunLine(); });
        }
    }

    private void stunLine() {
        stunLines++;
        Sim.boss("Maxor", KRandom.DEFAULT.nextBoolean() ? "THAT BEAM! IT HURTS! IT HURTS!!" : "YOU TRICKED ME!");
        int at = t;
        if (stunLines == 2 || hits >= 2) Fight.later(82, "too young", () -> {
            if ((Fight.phase == this || handed) && (killAt < 0 || at + 82 < killAt + 80)) Sim.boss("Maxor", "I'M TOO YOUNG TO DIE AGAIN!");
        });
    }

    private void freeze() {
        stunned = true;
        if (tauntAt > t) tauntAt = -1;
        if (hits >= 2) return;
        int d;
        if (P3Sim.bots() && KRandom.DEFAULT.nextInt(5) != 0) d = 6 + KRandom.DEFAULT.nextInt(7);
        else d = 100 + KRandom.DEFAULT.nextInt(100);
        enrageAt = t + Math.min(d, 240);
    }

    private void killed() {
        int since = t - killAt;
        if (since == 0) {
            health = 0f;
            maxor.armour(false);
            Blocks.set(new BlockPos(73, 221, 73), net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState());
            for (Pylon pl : pylons) pl.remove();
            Blocks.stop("p1strip");
            Sim.forEachPlayer(p -> { if (carrying() > 0) { setCarrying(0); giveBackMenu(); } });
        }
        if (since == STORM_AFTER - 3) Blocks.play("p1end", -24);
        if (since == 59) maxor.dieAnim();
        drops(since);
        if (belowAt < 0 && inStormArena()) belowAt = since;
        if (since >= STORM_AFTER && belowAt >= 0 && since >= belowAt + 2) {
            handed = true;
            BossWither body = maxor;
            Fight.later(80 - since, "maxor despawn", body::remove);
            Fight.begin(new P2Storm());
        }
    }

    private boolean inStormArena() {
        for (ServerPlayer p : Sim.players()) if (p.getY() < 196.0) return true;
        for (Party.Bot b : Party.bots()) if (b.entity != null && b.pos.y < 196.0) return true;
        return false;
    }

    private void drops(int since) {
        if (!P3Sim.bots()) return;
        List<Party.Bot> order = new ArrayList<>();
        if (healer != null) order.add(healer);
        if (archer != null) order.add(archer);
        if (lure != null) order.add(lure);
        for (Carrier c : carriers) order.add(c.bot);
        List<Vec3> spots = List.of(STORM_HEALER, STORM_ARCHER, new Vec3(73.5, 169.0, 60.5), new Vec3(73.5, 169.0, 45.5), new Vec3(73.5, 169.0, 50.5));
        for (int i = 0; i < order.size(); i++) {
            Party.Bot b = order.get(i);
            int dropAt = i < DROPS.size() ? DROPS.get(i) : 35 + 5 * i;
            if (b.entity != null && since >= dropAt) walk(b, i < spots.size() ? spots.get(i) : spots.get(spots.size() - 1), 1.5);
        }
    }

    public String status() {
        if (killAt >= 0) return "Maxor dead";
        if (hits > 0) return "Hits " + hits + "/2" + (stunned ? " (stunned)" : "");
        int placed = 0;
        for (Pylon pl : pylons) if (pl.placed()) placed++;
        return "Crystals " + placed + "/2" + (carrying() > 0 ? " (carrying)" : "");
    }

    private static final int OPEN = 80;
    private static final int BEACON = OPEN + 40;
    private static final int MOVE = 84;
    private static final int STUN_LINE = 6;
    private static final int STORM_AFTER = 62;
    private static final List<Integer> DROPS = List.of(7, 17, 26, 35);
    private static final List<Vec3> TOPS = List.of(new Vec3(64.5, 238.375, 50.5), new Vec3(82.5, 238.375, 50.5));
    private static final List<Vec3> PYLONS = List.of(new Vec3(52.5, 224.375, 41.5), new Vec3(94.5, 224.375, 41.5));
    private static final Vec3 BEAM = new Vec3(73.5, 226.0, 73.5);
    private static final List<Vec3> TOP_STANDS = List.of(new Vec3(63.5, 238.0, 49.5), new Vec3(83.5, 238.0, 49.5));
    private static final List<Vec3> PYLON_STANDS = List.of(new Vec3(51.8, 224.0, 40.6), new Vec3(95.9, 224.0, 40.4));
    private static final Vec3 LURE = new Vec3(73.6, 225.0, 77.2);
    private static final Vec3 STORM_HEALER = new Vec3(96.5, 165.0, 41.5);
    private static final Vec3 STORM_ARCHER = new Vec3(36.5, 170.0, 90.5);
    private static final Set<BlockPos> COLUMN = column221to224();
    private static final List<List<BlockPos>> FLOORS = List.of(floor0(), floor1());
    private static final List<List<BlockPos>> CEILINGS = List.of(ceiling0(), ceiling1());
    private static final List<BlockPos> CENTRE = centre();

    private static Set<BlockPos> column221to224() {
        Set<BlockPos> s = new java.util.LinkedHashSet<>();
        for (int y = 221; y <= 224; y++) s.add(new BlockPos(73, y, 73));
        return s;
    }

    private static List<BlockPos> floor0() {
        List<BlockPos> l = new ArrayList<>();
        for (int x = 56; x <= 64; x++) l.add(new BlockPos(x, 221, 41));
        for (int z = 42; z <= 51; z++) l.add(new BlockPos(64, 221, z));
        return l;
    }

    private static List<BlockPos> floor1() {
        List<BlockPos> l = new ArrayList<>();
        for (int z = 45; z <= 48; z++) l.add(new BlockPos(94, 221, z));
        for (int x = 93; x >= 82; x--) l.add(new BlockPos(x, 221, 48));
        for (int z = 49; z <= 51; z++) l.add(new BlockPos(82, 221, z));
        return l;
    }

    private static List<BlockPos> ceiling0() {
        List<BlockPos> l = new ArrayList<>();
        for (int x = 68; x <= 72; x++) l.add(new BlockPos(x, 236, 64));
        return l;
    }

    private static List<BlockPos> ceiling1() {
        List<BlockPos> l = new ArrayList<>();
        for (int x = 78; x >= 74; x--) l.add(new BlockPos(x, 236, 64));
        return l;
    }

    private static List<BlockPos> centre() {
        List<BlockPos> l = new ArrayList<>(Arrays.asList(new BlockPos(73, 236, 64)));
        for (int z = 65; z <= 69; z++) l.add(new BlockPos(73, 236, z));
        return l;
    }
}
