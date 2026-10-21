package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/HotbarLayout.kt

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class HotbarLayout {
    private HotbarLayout() {}

    private static final class Layout {
        Map<String, Integer> slots = Map.of();
        int selected = 3;
        Loadouts.Worn worn = null;

        Layout() {}

        Layout(Map<String, Integer> slots, int selected, Loadouts.Worn worn) {
            this.slots = slots;
            this.selected = selected;
            this.worn = worn;
        }
    }

    private static final class Saved {
        Layout p12 = null;
        Layout p3 = null;
        Loadouts.Worn worn = null;
        Map<String, Layout> roles = null;
        Map<String, Layout> live = null;

        Saved() {}

        Saved(Layout p12, Layout p3, Loadouts.Worn worn, Map<String, Layout> roles, Map<String, Layout> live) {
            this.p12 = p12;
            this.p3 = p3;
            this.worn = worn;
            this.roles = roles;
            this.live = live;
        }

        Saved withP12(Layout v) { return new Saved(v, p3, worn, roles, live); }

        Saved withP3(Layout v) { return new Saved(p12, v, worn, roles, live); }

        Saved withWorn(Loadouts.Worn v) { return new Saved(p12, p3, v, roles, live); }

        Saved withRoles(Map<String, Layout> v) { return new Saved(p12, p3, worn, v, live); }

        Saved withLive(Map<String, Layout> v) { return new Saved(p12, p3, worn, roles, v); }
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<String, Saved> cache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Layout>> live = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Pair<String, Integer>> given = new ConcurrentHashMap<>();

    private static String owner() {
        UUID o = PlayerFiles.owner();
        return o != null ? o.toString() : "";
    }

    private static ConcurrentHashMap<String, Layout> lives() {
        String key = owner();
        ConcurrentHashMap<String, Layout> v = live.get(key);
        if (v != null) return v;
        ConcurrentHashMap<String, Layout> made = new ConcurrentHashMap<>();
        Map<String, Layout> sl = saved().live;
        if (sl != null) sl.forEach((k, l) -> { if (l.slots != null) made.put(k, l); });
        ConcurrentHashMap<String, Layout> prev = live.putIfAbsent(key, made);
        return prev != null ? prev : made;
    }

    private static void keepLives() { write(saved().withLive(new LinkedHashMap<>(lives()))); }

    public static void forgetLocal() { cache.remove(""); }

    public static void forget(UUID id) { cache.remove(id.toString()); live.remove(id.toString()); given.remove(id.toString()); }

    private static Saved saved() {
        Saved have = cache.get(owner());
        if (have != null) return have;
        Saved s;
        try {
            String text = PlayerFiles.read(PlayerFiles.HOTBAR);
            s = text != null ? gson.fromJson(text, Saved.class) : null;
        } catch (Throwable t) {
            Ec.LOG.error("[ec] p3sim hotbar load failed", t);
            PlayerFiles.setAsideLocal(PlayerFiles.HOTBAR);
            s = null;
        }
        if (s == null) s = new Saved();
        cache.put(owner(), s);
        return s;
    }

    private static String partKey(boolean p3) { return p3 ? "P3" : "P12"; }

    private static String key(boolean p3) { return partKey(p3) + "/" + P3Sim.myClass().name(); }

    private static String skillKey(boolean p3) { return P3Plan.skillName() + "/" + key(p3); }

    private static boolean isSkillKey(String k, boolean p3) {
        if (!k.endsWith("/" + key(p3))) return false;
        int n = 0;
        for (int i = 0; i < k.length(); i++) if (k.charAt(i) == '/') n++;
        return n == 2;
    }

    private static Layout savedLayout(boolean p3) {
        Map<String, Layout> roles = saved().roles;
        if (roles != null) {
            Layout l = roles.get(key(p3));
            if (l == null) l = roles.get(skillKey(p3));
            if (l != null) return l;
        }
        return p3 ? saved().p3 : saved().p12;
    }

    private static Layout layout(boolean p3) {
        Layout s = savedLayout(p3);
        if (s == null) s = lives().get(key(p3));
        if (s == null && saved().roles != null) for (Layout o : saved().roles.values()) if (o.slots != null) return o;
        return s;
    }

    public static Loadouts.Worn worn(boolean p3) {
        Layout s = savedLayout(p3);
        if (s != null) return s.worn != null ? s.worn : saved().worn;
        Layout l = lives().get(key(p3));
        return l != null ? l.worn : null;
    }

    public static boolean has(boolean p3) { return savedLayout(p3) != null; }

    private static Layout capture(ServerPlayer p, Loadouts.Worn worn) { return capture(p, worn, p.getInventory().getSelectedSlot()); }

    private static Layout capture(ServerPlayer p, Loadouts.Worn worn, int selected) {
        Inventory inv = p.getInventory();
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();
        for (int i = 0; i <= 35; i++) {
            ItemStack s = inv.getItem(i);
            String id;
            if (SimItems.isSlot9(s)) id = "SKYBLOCK_MENU";
            else {
                String raw = SimItems.idOf(s);
                id = raw == null ? null : raw.equals("MAXOR_ENERGY_CRYSTAL") ? "SKYBLOCK_MENU" : raw;
            }
            if (id == null) continue;
            if (id.endsWith("_MASK")) continue;
            slots.putIfAbsent(id, i);
        }
        return slots.isEmpty() ? null : new Layout(slots, selected, worn);
    }

    public static void keep(ServerPlayer p) {
        if (Masks.ghost.get()) return;
        Pair<String, Integer> g = given.get(owner());
        if (g == null) return;
        String k = g.first;
        int held = g.second;
        Layout was = lives().get(k);
        Loadouts.Worn worn = k.contains("/P3/") && P3Sim.terrorAtTerms() ? (was != null ? was.worn : null) : Loadouts.capture(p);
        Layout l = capture(p, worn, held);
        if (l != null) { lives().put(k, l); keepLives(); }
    }

    public static void gave(boolean p3, int held) { given.put(owner(), Pair.of(key(p3), held)); }

    public static void forgetGiven() { given.remove(owner()); }

    public static void dropLive(boolean p3) { lives().remove(key(p3)); given.remove(owner()); keepLives(); }

    public static Pair<List<Pair<Integer, ItemStack>>, Integer> arrange(List<Pair<Integer, ItemStack>> defaults, boolean p3) {
        Layout l = layout(p3);
        if (l == null) return Pair.of(defaults, 3);
        HashSet<Integer> taken = new HashSet<>();
        taken.add(Masks.SPARE_SLOT);
        ArrayList<Pair<Integer, ItemStack>> placed = new ArrayList<>();
        ArrayList<Pair<Integer, ItemStack>> left = new ArrayList<>();
        for (Pair<Integer, ItemStack> d : defaults) {
            String id = SimItems.idOf(d.second);
            Integer want = id != null ? l.slots.get(id) : null;
            if (want != null && want >= 0 && want <= 35 && taken.add(want)) placed.add(Pair.of(want, d.second)); else left.add(d);
        }
        for (Pair<Integer, ItemStack> d : left) {
            int at;
            if (!taken.contains(d.first)) at = d.first;
            else {
                Integer free = null;
                for (int it = 0; it <= 35; it++) if (!taken.contains(it)) { free = it; break; }
                if (free == null) throw new NoSuchElementException("Collection contains no element matching the predicate.");
                at = free;
            }
            taken.add(at);
            placed.add(Pair.of(at, d.second));
        }
        return Pair.of(placed, Math.clamp(l.selected, 0, 8));
    }

    public static String save(ServerPlayer p, boolean p3) {
        Layout l = capture(p, Loadouts.capture(p));
        if (l == null) return "§cnothing to save: no sim items in your inventory";
        lives().put(key(p3), l);
        Saved s = saved();
        LinkedHashMap<String, Layout> roles = new LinkedHashMap<>();
        if (s.roles != null) s.roles.forEach((k, v) -> { if (!isSkillKey(k, p3)) roles.put(k, v); });
        roles.put(key(p3), l);
        for (Layout o : roles.values()) if (o != l) sync(o, l);
        if (s.p12 != null) sync(s.p12, l);
        if (s.p3 != null) sync(s.p3, l);
        for (Layout o : lives().values()) if (o != l) sync(o, l);
        write(s.withRoles(roles).withLive(new LinkedHashMap<>(lives())));
        return "§7saved your §f" + part(p3) + "§7 hotbar (" + l.slots.size() + " items, holding slot " + (l.selected + 1) + ") with your armour, helmet and pet; Reset Items brings it back";
    }

    // items both layouts have go where the saved one has them; whatever sat there swaps into the old slot
    private static void sync(Layout target, Layout src) {
        if (target.slots == null || src.slots == null) return;
        LinkedHashMap<String, Integer> t = new LinkedHashMap<>(target.slots);
        src.slots.forEach((id, slot) -> {
            Integer old = t.get(id);
            if (old == null || old.equals(slot)) return;
            for (Map.Entry<String, Integer> e : t.entrySet()) if (e.getValue().equals(slot)) { e.setValue(old); break; }
            t.put(id, slot);
        });
        target.slots = t;
    }

    public static String reset(boolean p3) {
        lives().remove(key(p3)); given.remove(owner()); keepLives();
        if (!has(p3)) return "§7the " + part(p3) + " hotbar is already the default";
        Saved s = saved().withLive(new LinkedHashMap<>(lives()));
        Map<String, Layout> roles = s.roles != null ? s.roles : Map.of();
        boolean any = false;
        for (String k : roles.keySet()) if (k.equals(key(p3)) || isSkillKey(k, p3)) { any = true; break; }
        if (any) {
            LinkedHashMap<String, Layout> kept = new LinkedHashMap<>();
            roles.forEach((k, v) -> { if (!k.equals(key(p3)) && !isSkillKey(k, p3)) kept.put(k, v); });
            write(s.withRoles(kept));
        } else {
            boolean otherLeft = p3 ? s.p12 != null : s.p3 != null;
            write(p3 ? s.withP3(null).withWorn(otherLeft ? s.worn : null) : s.withP12(null).withWorn(otherLeft ? s.worn : null));
        }
        return "§7" + part(p3) + " hotbar back to the " + (has(p3) ? "one saved for every class" : "default");
    }

    private static String part(boolean p3) { return (p3 ? "P3" : "P1/P2") + " " + Roles.label(P3Sim.myClass()); }

    private static void write(Saved s) {
        cache.put(owner(), s);
        Ec.safely("p3sim hotbar save", () -> PlayerFiles.write(PlayerFiles.HOTBAR, gson.toJson(s)));
    }
}
