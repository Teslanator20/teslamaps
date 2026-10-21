package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Loadouts.kt

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.Pair;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;

public final class Loadouts {
    private Loadouts() {}

    private static final class Entry {
        final int slot;
        final String item;
        final String name;
        final List<String> lore;
        final String tex;
        final Integer dye;
        final boolean glint;
        final String style;
        final String id;

        Entry(int slot, String item, String name, List<String> lore, String tex, Integer dye, boolean glint, String style, String id) {
            this.slot = slot;
            this.item = item;
            this.name = name;
            this.lore = lore;
            this.tex = tex;
            this.dye = dye;
            this.glint = glint;
            this.style = style;
            this.id = id;
        }
    }

    private static Map<Integer, Entry> entries;

    private static synchronized Map<Integer, Entry> entries() {
        if (entries != null) return entries;
        HashMap<Integer, Entry> out = new HashMap<>();
        Ec.safely("p3sim loadouts.json", () -> {
            String text;
            try (InputStream in = Loadouts.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/loadouts.json")) {
                if (in == null) return;
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            for (JsonElement e : JsonParser.parseString(text).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                List<String> lore = new ArrayList<>();
                for (JsonElement l : o.getAsJsonArray("lore")) lore.add(l.getAsString());
                JsonElement dye = o.get("dye");
                JsonElement glint = o.get("glint");
                String name = str(o, "name");
                out.put(o.get("slot").getAsInt(), new Entry(o.get("slot").getAsInt(), o.get("item").getAsString(), name != null ? name : "",
                    lore, str(o, "tex"), dye != null ? dye.getAsInt() : null, glint != null && glint.getAsBoolean(), str(o, "style"), str(o, "id")));
            }
        });
        entries = out;
        return out;
    }

    private static String str(JsonObject o, String k) {
        JsonElement v = o.get(k);
        return v != null && !v.isJsonNull() ? v.getAsString() : null;
    }

    private static List<Component> loreLines(List<String> lore) {
        List<Component> out = new ArrayList<>();
        for (String l : lore) out.add(Component.literal(l).withStyle(st -> st.withItalic(false)));
        return out;
    }

    private static ItemStack stack(Entry e) {
        ItemStack s = e.tex != null ? SimItems.head(e.tex, e.name)
            : Terminals.named(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(e.item)), e.name);
        if (!e.lore.isEmpty()) s.set(DataComponents.LORE, new ItemLore(loreLines(e.lore)));
        if (e.glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (e.dye != null) s.set(DataComponents.DYED_COLOR, new DyedItemColor(e.dye));
        if (e.id != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", e.id);
            tag.putBoolean("p3sim", true);
            s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        if (e.style != null) SimItems.hy(s, null, e.style);
        return s;
    }

    private static Entry withHelmet(Entry e, String line) {
        List<String> lore = new ArrayList<>();
        for (String it : e.lore) lore.add(it.startsWith("§r§7Helmet: ") ? line : it);
        return new Entry(e.slot, e.item, e.name, lore, e.tex, e.dye, e.glint, e.style, e.id);
    }

    private static ItemStack terrorHelmet() {
        Entry e = entries().get(54);
        if (e == null) return ItemStack.EMPTY;
        List<String> lore = new ArrayList<>();
        for (String it : e.lore) lore.add(it.replace("(3/4)", "(4/4)").replace("after 7s", "after 10s"));
        return stack(new Entry(e.slot, e.item, e.name, lore, e.tex, e.dye, e.glint, e.style, e.id));
    }

    private enum Helm { RACING, MASK, TERROR, WISE }

    private static final class Def {
        final int slot;
        final String name;
        final SimItems.ArmorSet set;
        final Helm helm;
        final boolean phoenix;
        final String mask;

        Def(int slot, String name, SimItems.ArmorSet set, Helm helm, boolean phoenix, String mask) {
            this.slot = slot;
            this.name = name;
            this.set = set;
            this.helm = helm;
            this.phoenix = phoenix;
            this.mask = mask;
        }

        Def(int slot, String name, SimItems.ArmorSet set, Helm helm, boolean phoenix) { this(slot, name, set, helm, phoenix, null); }
    }

    private static final List<Def> defs = List.of(
        new Def(24, "Cat terms", SimItems.ArmorSet.WISE, Helm.RACING, false),
        new Def(23, "Phoenix terms", SimItems.ArmorSet.WISE, Helm.RACING, true),
        new Def(34, "Terror", SimItems.ArmorSet.TERROR, Helm.MASK, false, "BONZO_MASK"),
        new Def(41, "Mask terms", SimItems.ArmorSet.MAXOR, Helm.MASK, false, "SPIRIT_MASK")
    );

    private static String maskId(ItemStack s) {
        String id = SimItems.idOf(s);
        if (id == null) return null;
        return id.startsWith("STARRED_") ? id.substring("STARRED_".length()) : id;
    }

    private static String wantedMask() { return P3Sim.wornMask() == 0 ? "SPIRIT_MASK" : "BONZO_MASK"; }

    public static final class Worn {
        public String head;
        public String chest;
        public String legs;
        public String feet;
        public boolean phoenix;

        public Worn(String head, String chest, String legs, String feet, boolean phoenix) {
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
            this.phoenix = phoenix;
        }

        public Worn(String head, String chest, String legs, String feet) { this(head, chest, legs, feet, false); }

        public Worn(String head, String chest, String legs) { this(head, chest, legs, null, false); }

        public Worn(String head, String chest) { this(head, chest, null, null, false); }

        public Worn(String head) { this(head, null, null, null, false); }

        public Worn() { this(null, null, null, null, false); }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Worn w)) return false;
            return phoenix == w.phoenix && Objects.equals(head, w.head) && Objects.equals(chest, w.chest) && Objects.equals(legs, w.legs) && Objects.equals(feet, w.feet);
        }

        @Override public int hashCode() { return Objects.hash(head, chest, legs, feet, phoenix); }

        @Override public String toString() { return "Worn(head=" + head + ", chest=" + chest + ", legs=" + legs + ", feet=" + feet + ", phoenix=" + phoenix + ")"; }
    }

    public static Worn capture(ServerPlayer p) {
        return new Worn(SimItems.idOf(p.getItemBySlot(EquipmentSlot.HEAD)), SimItems.idOf(p.getItemBySlot(EquipmentSlot.CHEST)),
            SimItems.idOf(p.getItemBySlot(EquipmentSlot.LEGS)), SimItems.idOf(p.getItemBySlot(EquipmentSlot.FEET)), P3Sim.phoenix());
    }

    private static Helm helmFor(String id) {
        if (id == null) return null;
        if (id.equals("RACING_HELMET")) return Helm.RACING;
        if (id.equals("TERROR_HELMET")) return Helm.TERROR;
        if (id.equals("WISE_WITHER_HELMET")) return Helm.WISE;
        if (id.endsWith("_MASK")) return Helm.MASK;
        return null;
    }

    public static void applyWorn(ServerPlayer p, Worn w) {
        List<Pair<EquipmentSlot, String>> pieces = Arrays.asList(Pair.of(EquipmentSlot.CHEST, w.chest), Pair.of(EquipmentSlot.LEGS, w.legs), Pair.of(EquipmentSlot.FEET, w.feet));
        for (Pair<EquipmentSlot, String> it : pieces) {
            if (it.second == null) continue;
            ItemStack piece = SimItems.armorPiece(it.second);
            if (piece != null) SimItems.wear(p, it.first, piece);
        }
        Helm h = helmFor(w.head);
        if (h == Helm.MASK) {
            P3Sim.setWornMask(w.head.endsWith("SPIRIT_MASK") ? 0 : 1);
            String cur = SimItems.idOf(p.getItemBySlot(EquipmentSlot.HEAD));
            if (cur != null && cur.endsWith("_MASK")) {
                if (P3Sim.realMasks()) Masks.equip(p);
                else SimItems.wear(p, EquipmentSlot.HEAD, P3Sim.wornMask() == 0 ? Masks.SPIRIT_MASK() : Masks.BONZO_MASK());
            } else wearHelmet(p, h);
        } else if (h != null) wearHelmet(p, h);
        P3Sim.setPhoenix(w.phoenix);
        Fight.applySpeed(p);
    }

    public static void applySaved(ServerPlayer p, boolean p3) {
        Worn w = HotbarLayout.worn(p3);
        if (w != null && w.head != null && w.head.endsWith("_MASK")) P3Sim.setWornMask(w.head.endsWith("SPIRIT_MASK") ? 0 : 1);
        Masks.equip(p);
        SimItems.equipArmor(p, SimItems.ArmorSet.MAXOR);
        if (w != null) applyWorn(p, w);
    }

    private static final class Custom {
        String name = "";
        Worn worn = new Worn();

        Custom() {}

        Custom(String name, Worn worn) {
            this.name = name;
            this.worn = worn;
        }
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<String, List<Custom>> customLists = new ConcurrentHashMap<>();

    private static String owner() {
        UUID o = PlayerFiles.owner();
        return o != null ? o.toString() : "";
    }

    public static void forgetLocal() { customLists.remove(""); }

    public static void forget(UUID id) { customLists.remove(id.toString()); }

    private static List<Custom> customs() {
        List<Custom> have = customLists.get(owner());
        if (have != null) return have;
        List<Custom> l;
        try {
            String text = PlayerFiles.read(PlayerFiles.LOADOUTS);
            Custom[] arr = text != null ? gson.fromJson(text, Custom[].class) : null;
            l = arr != null ? new ArrayList<>(Arrays.asList(arr)) : null;
        } catch (Throwable t) {
            Ec.LOG.error("[ec] p3sim loadouts load failed", t);
            PlayerFiles.setAsideLocal(PlayerFiles.LOADOUTS);
            l = null;
        }
        if (l == null) l = new ArrayList<>();
        customLists.put(owner(), l);
        return l;
    }

    private static void saveCustoms() {
        Ec.safely("p3sim loadouts save", () -> PlayerFiles.write(PlayerFiles.LOADOUTS, gson.toJson(customs().toArray(new Custom[0]))));
    }

    private static final List<Integer> CUSTOM_SLOTS = List.of(12, 13, 14, 15, 16, 17, 30, 31, 32, 33, 39, 40, 42, 43);
    private static final int SAVE_SLOT = 47;

    private static String pretty(String id) {
        if (id == null) return "none";
        String s = id.startsWith("STARRED_") ? id.substring("STARRED_".length()) : id;
        String[] words = s.toLowerCase(Locale.ROOT).split("_", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String w = words[i];
            sb.append(w.isEmpty() ? w : w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1));
        }
        return sb.toString();
    }

    private static ItemStack stack(Custom c) {
        ItemStack piece = c.worn.chest != null ? SimItems.armorPiece(c.worn.chest) : null;
        ItemStack s = piece != null ? piece.copy() : Terminals.named(Items.BOOK, c.name);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("§a" + c.name).withStyle(st -> st.withItalic(false)));
        List<String> lore = List.of("§7Helmet: §f" + pretty(c.worn.head), "§7Chestplate: §f" + pretty(c.worn.chest), "§7Leggings: §f" + pretty(c.worn.legs),
            "§7Boots: §f" + pretty(c.worn.feet), "§7Pet: §f" + (c.worn.phoenix ? "Phoenix" : "Black Cat"), "", "§eLeft-click to equip!", "§cShift-click to delete!");
        s.set(DataComponents.LORE, new ItemLore(loreLines(lore)));
        return s;
    }

    private static void saveCurrent(ServerPlayer p) {
        Worn w = capture(p);
        for (Custom it : customs()) if (it.worn.equals(w)) { Sim.chatStyled("§cThat is already saved as " + it.name + "."); return; }
        if (customs().size() >= CUSTOM_SLOTS.size()) { Sim.chatStyled("§cNo free loadout slot: shift-click one to delete it."); return; }
        int n = 1;
        while (true) {
            boolean taken = false;
            for (Custom it : customs()) if (it.name.equals("Custom " + n)) { taken = true; break; }
            if (!taken) break;
            n++;
        }
        customs().add(new Custom("Custom " + n, w));
        saveCustoms();
        Sim.chatStyled("§aSaved your gear and pet as Custom " + n + ".");
    }

    private static void equipCustom(ServerPlayer p, Custom c) {
        if (capture(p).equals(c.worn)) { Sim.chatStyled("§c" + c.name + " is already equipped!"); return; }
        Fight.later(1, "loadout " + c.name, () -> {
            applyWorn(p, c.worn);
            sound("minecraft:block.lever.click", 0.5f, 1f, SoundSource.BLOCKS);
            Sim.chatStyled("§aYou equipped " + c.name + "!");
            sound("minecraft:entity.horse.saddle", 1f, 1f, SoundSource.NEUTRAL);
            p.inventoryMenu.broadcastChanges();
            if (p.containerMenu instanceof LoadoutsMenu m) m.refresh();
        });
    }

    public static void open(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new LoadoutsMenu(id, inv, p), Component.literal("(1/3) Loadouts")));
    }

    public static boolean isHelmet(ItemStack s) {
        String id = SimItems.idOf(s);
        if (id == null) return false;
        return id.endsWith("_MASK") || id.endsWith("_HELMET");
    }

    private static boolean wearsHelm(Helm h, ItemStack s) {
        String id = SimItems.idOf(s);
        if (id == null) return false;
        return switch (h) {
            case RACING -> id.equals("RACING_HELMET");
            case TERROR -> id.equals("TERROR_HELMET");
            case WISE -> id.equals("WISE_WITHER_HELMET");
            case MASK -> id.endsWith("_MASK");
        };
    }

    private static String chestId(SimItems.ArmorSet set) {
        return switch (set) {
            case MAXOR -> "MITHRIL_COAT";
            case TERROR -> "TERROR_CHESTPLATE";
            case WISE -> "WISE_WITHER_CHESTPLATE";
        };
    }

    private static boolean matches(ServerPlayer p, Def d) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        return Objects.equals(SimItems.idOf(p.getItemBySlot(EquipmentSlot.CHEST)), chestId(d.set)) && wearsHelm(d.helm, head) && P3Sim.phoenix() == d.phoenix &&
            (d.mask == null || Objects.equals(maskId(head), d.mask));
    }

    private static void wearHelmet(ServerPlayer p, Helm h) {
        Inventory inv = p.getInventory();
        ItemStack old = p.getItemBySlot(EquipmentSlot.HEAD).copy();
        String wantMask = wantedMask();
        if (wearsHelm(h, old) && (h != Helm.MASK || Objects.equals(maskId(old), wantMask))) return;
        Integer from = null;
        for (int it = 0; it < 36; it++) {
            if (wearsHelm(h, inv.getItem(it)) && (h != Helm.MASK || Objects.equals(maskId(inv.getItem(it)), wantMask))) { from = it; break; }
        }
        if (from != null) {
            SimItems.wear(p, EquipmentSlot.HEAD, inv.getItem(from).copy());
            inv.setItem(from, old);
        } else {
            switch (h) {
                case RACING -> SimItems.equipHelmet(p, false);
                case WISE -> SimItems.equipHelmet(p, true);
                case TERROR -> SimItems.wear(p, EquipmentSlot.HEAD, terrorHelmet());
                case MASK -> SimItems.wear(p, EquipmentSlot.HEAD, wantMask.equals("SPIRIT_MASK") ? Masks.SPIRIT_MASK() : Masks.BONZO_MASK());
            }
            if (!old.isEmpty()) {
                Integer slot = null;
                if (inv.getItem(Masks.SPARE_SLOT).isEmpty()) slot = Masks.SPARE_SLOT;
                else for (int it = 9; it < 36; it++) if (inv.getItem(it).isEmpty()) { slot = it; break; }
                if (slot != null) inv.setItem(slot, old);
            }
        }
        if (h == Helm.MASK) {
            String id = SimItems.idOf(p.getItemBySlot(EquipmentSlot.HEAD));
            if (id != null) P3Sim.setWornMask(id.endsWith("SPIRIT_MASK") ? 0 : 1);
        }
    }

    private static void sound(String id, float vol, float pitch, SoundSource src) {
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(id));
        if (e != null) Sim.sound(e, vol, pitch, null, src);
    }

    private static void wear(ServerPlayer p, Def d) {
        SimItems.equipArmor(p, d.set, d.helm == Helm.TERROR);
        if (d.mask != null) P3Sim.setWornMask(d.mask.equals("SPIRIT_MASK") ? 0 : 1);
        wearHelmet(p, d.helm);
        P3Sim.setPhoenix(d.phoenix);
        Fight.applySpeed(p);
    }

    public static void wearTerror(ServerPlayer p) {
        for (Def it : defs) if (it.name.equals("Terror")) { wear(p, it); return; }
    }

    private static void equip(ServerPlayer p, Def d) {
        if (matches(p, d)) { Sim.chatStyled("§c" + d.name + " is already equipped!"); return; }
        Fight.later(1, "loadout " + d.name, () -> {
            wear(p, d);
            sound("minecraft:block.lever.click", 0.5f, 1f, SoundSource.BLOCKS);
            Sim.chatStyled("§aYou equipped " + d.name + "!");
            sound("minecraft:entity.horse.saddle", 1f, 1f, SoundSource.NEUTRAL);
            p.inventoryMenu.broadcastChanges();
            if (p.containerMenu instanceof LoadoutsMenu m) m.refresh();
        });
    }

    public static class LoadoutsMenu extends ChestMenu {
        private final ServerPlayer sp;

        public LoadoutsMenu(int id, Inventory inv, ServerPlayer sp) {
            super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(54), 6);
            this.sp = sp;
            draw();
        }

        @Override
        public void setSynchronizer(ContainerSynchronizer synchronizer) { super.setSynchronizer(new SimItems.PerSlotSync(synchronizer, sp)); }

        public void refresh() { draw(); broadcastFullState(); }

        private void draw() {
            var c = getContainer();
            for (int i = 0; i < 54; i++) c.setItem(i, Terminals.FILLER());
            for (Map.Entry<Integer, Entry> en : entries().entrySet()) {
                int slot = en.getKey();
                Entry e = en.getValue();
                if (slot < 54) c.setItem(slot, stack(slot == 34 ? withHelmet(e, "§r§7Helmet: §r§9Bonzo's Mask") : e));
            }
            ItemStack head = sp.getItemBySlot(EquipmentSlot.HEAD).copy();
            c.setItem(11, head.isEmpty() ? Terminals.named(Items.GRAY_STAINED_GLASS_PANE, "§7Empty Helmet Slot") : head);
            List<Pair<Integer, EquipmentSlot>> armour = List.of(Pair.of(20, EquipmentSlot.CHEST), Pair.of(29, EquipmentSlot.LEGS), Pair.of(38, EquipmentSlot.FEET));
            for (Pair<Integer, EquipmentSlot> it : armour) {
                ItemStack s = sp.getItemBySlot(it.second).copy();
                if (!s.isEmpty()) c.setItem(it.first, s);
            }
            List<Custom> cs = customs();
            for (int i = 0; i < cs.size(); i++) if (i < CUSTOM_SLOTS.size()) c.setItem(CUSTOM_SLOTS.get(i), stack(cs.get(i)));
            ItemStack save = Terminals.named(Items.LIME_DYE, "§aSave current as loadout");
            save.set(DataComponents.LORE, new ItemLore(loreLines(List.of("§7Saves what you wear and your pet", "§7as a new loadout.", "", "§eClick to save!"))));
            c.setItem(SAVE_SLOT, save);
            if (P3Sim.phoenix()) c.setItem(21, SimItems.head(Masks.PHOENIX_TEX, "§7[Lvl 100] §5Phoenix"));
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player p) {
            if (slot == 49) { sp.closeContainer(); return; }
            int ci = CUSTOM_SLOTS.indexOf(slot);
            if (slot == SAVE_SLOT) saveCurrent(sp);
            else if (ci >= 0 && ci < customs().size()) {
                Custom cu = customs().get(ci);
                if (button == 1 || input == ContainerInput.QUICK_MOVE) {
                    customs().remove(ci); saveCustoms(); Sim.chatStyled("§cDeleted loadout " + cu.name + ".");
                } else if (button == 0) equipCustom(sp, cu);
            } else if (button == 0) {
                for (Def it : defs) if (it.slot == slot) { equip(sp, it); break; }
            }
            draw();
            broadcastFullState();
        }

        @Override
        public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }

        @Override
        public boolean stillValid(Player p) { return true; }
    }
}
