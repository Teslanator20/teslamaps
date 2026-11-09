package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Terminals.kt

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

public final class Terminals {
    private Terminals() {}

    private static final KRandom R = KRandom.DEFAULT;

    public enum Type {
        ORDER(4), PANES(5), RUBIX(5), STARTS(5), SELECT(6), MELODY(6);

        public final int rows;

        Type(int rows) { this.rows = rows; }
    }

    private static <T> T random(List<T> l) { return l.get(R.nextInt(l.size())); }

    private static <T> List<T> shuffled(List<T> l) {
        ArrayList<T> c = new ArrayList<>(l);
        KRandom.shuffle(c, R);
        return c;
    }

    private static List<Integer> range(int from, int to) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = from; i <= to; i++) out.add(i);
        return out;
    }

    @SafeVarargs
    private static List<Integer> concat(List<Integer>... parts) {
        ArrayList<Integer> out = new ArrayList<>();
        for (List<Integer> p : parts) out.addAll(p);
        return out;
    }

    public static Type randomType() {
        return lucky(10, (Type t) -> t == Type.MELODY ? 1 : 0, () -> {
            List<Type> ok = new ArrayList<>();
            for (Type t : Type.values()) if (t != Type.MELODY || !P3Sim.noMelodies()) ok.add(t);
            return random(ok);
        });
    }

    public static <T> T weighted(List<Pair<T, Integer>> w) {
        int sum = 0;
        for (Pair<T, Integer> p : w) sum += p.second;
        int r = KRandom.DEFAULT.nextInt(sum);
        for (Pair<T, Integer> p : w) {
            if (r < p.second) return p.first;
            r -= p.second;
        }
        return w.get(w.size() - 1).first;
    }

    private static int chance(double x) { return (int) x + (R.nextDouble() < x - (int) x ? 1 : 0); }

    public static <T> T lucky(int most, Function<T, Integer> cost, Supplier<T> draw) {
        double luck = P3Sim.termLuck();
        if (luck == 0.0) return draw.get();
        int n = Math.max(chance(1 + (most - 1) * Math.min(Math.abs(luck) / 0.6, 1.0)), 1);
        List<T> draws = new ArrayList<>();
        for (int i = 0; i < n; i++) draws.add(draw.get());
        T best = draws.get(0);
        int bestC = cost.apply(best);
        for (int i = 1; i < draws.size(); i++) {
            T d = draws.get(i);
            int c = cost.apply(d);
            if (luck > 0 ? c < bestC : c > bestC) { best = d; bestC = c; }
        }
        return best;
    }

    public static int beyond(int clicks, int worst) {
        double luck = P3Sim.termLuck();
        double f0 = Math.clamp((Math.abs(luck) - 0.4) / 0.6, 0.0, 1.0);
        double f = f0 * f0;
        if (f <= 0.0) return clicks;
        int to = luck > 0 ? 1 : worst;
        return Math.clamp(chance(clicks + (to - clicks) * f), Math.min(1, clicks), Math.max(worst, clicks));
    }

    public static ItemStack named(Item item, String name) { return named(item, name, 1, false, null); }

    public static ItemStack named(Item item, String name, int count) { return named(item, name, count, false, null); }

    public static ItemStack named(Item item, String name, int count, boolean glint) { return named(item, name, count, glint, null); }

    public static ItemStack named(Item item, String name, int count, boolean glint, ChatFormatting color) {
        ItemStack s = new ItemStack(item, count);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> {
            var it = st.withItalic(false);
            return color != null ? it.withColor(color) : it;
        }));
        if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    public static ItemStack FILLER() {
        ItemStack it = named(Items.STAINED_GLASS_PANE.black(), "");
        it.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
        return it;
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)); }

    public static void open(ServerPlayer player, Station station) { open(player, station, null); }

    public static void open(ServerPlayer player, Station station, Type type) {
        Type keep = station.term != null && Fight.forcedTerminal() == null ? station.term.type : null;
        Term term = Term.create(type != null ? type : keep != null ? keep : station.nextType());
        station.term = term;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv, term, station), Component.literal(term.title())));
        Sim.sound(SoundEvents.LEVER_CLICK, 0.5f, 1f, null, SoundSource.BLOCKS);
    }

    // ------------------------------------------------------------------ the puzzles

    public abstract static class Term {
        public final Type type;
        public final ItemStack[] items;
        public boolean done = false;

        public Term(Type type) {
            this.type = type;
            this.items = new ItemStack[type.rows * 9];
            for (int i = 0; i < items.length; i++) items[i] = FILLER();
        }

        public abstract String title();

        public int size() { return type.rows * 9; }

        public abstract boolean click(int slot, int button, ContainerInput input);

        public abstract boolean solved();

        public void tick(int t) {}

        public static Term create(Type t) {
            return switch (t) {
                case ORDER -> new Order();
                case PANES -> new Panes();
                case RUBIX -> new Rubix();
                case STARTS -> new Starts();
                case SELECT -> new Select();
                case MELODY -> new Melody();
            };
        }
    }

    public static class Order extends Term {
        private final List<Integer> slots = concat(range(11, 15), range(20, 24));
        private int next = 1;

        public Order() {
            super(Type.ORDER);
            List<Integer> order = shuffled(slots);
            double luck = P3Sim.termLuck();
            int steps = chance(Math.pow(1500.0, Math.abs(luck)) - 1);
            int cost = walk(order);
            for (int k = 0; k < steps; k++) {
                int i = R.nextInt(10); int j = R.nextInt(10);
                Collections.swap(order, i, j);
                int c = walk(order);
                if (luck > 0 ? c <= cost : c >= cost) cost = c; else Collections.swap(order, i, j);
            }
            for (int i = 0; i < order.size(); i++) items[order.get(i)] = named(Items.STAINED_GLASS_PANE.red(), "" + (i + 1), i + 1, false, ChatFormatting.GREEN);
        }

        @Override public String title() { return "Click in order!"; }

        @Override
        public boolean click(int slot, int button, ContainerInput input) {
            ItemStack it = items[slot];
            if (it.getItem() != Items.STAINED_GLASS_PANE.red() || it.getCount() != next) return false;
            items[slot] = named(Items.STAINED_GLASS_PANE.lime(), "" + next, next, false, ChatFormatting.GREEN);
            next++;
            return true;
        }

        @Override public boolean solved() { return next > 10; }

        private int walk(List<Integer> o) {
            int sum = 0;
            for (int i = 0; i + 1 < o.size(); i++) {
                int a = o.get(i), b = o.get(i + 1);
                sum += Math.abs(a % 9 - b % 9) + Math.abs(a / 9 - b / 9);
            }
            return sum;
        }
    }

    public static class Panes extends Term {
        private final List<Integer> slots = concat(range(11, 15), range(20, 24), range(29, 33));

        public Panes() {
            super(Type.PANES);
            int drawn = lucky(10, (Integer n) -> -n, () -> weighted(List.of(Pair.of(0, 8), Pair.of(1, 24), Pair.of(2, 36), Pair.of(3, 44), Pair.of(4, 40), Pair.of(5, 16), Pair.of(6, 5), Pair.of(7, 4), Pair.of(8, 3), Pair.of(9, 1))));
            int on = 15 - beyond(15 - drawn, 15);
            Set<Integer> lit = new HashSet<>(shuffled(slots).subList(0, Math.min(on, slots.size())));
            for (int s : slots) items[s] = pane(lit.contains(s));
        }

        @Override public String title() { return "Correct all the panes!"; }

        private ItemStack pane(boolean on) {
            return on ? named(Items.STAINED_GLASS_PANE.lime(), "On", 1, false, ChatFormatting.GREEN) : named(Items.STAINED_GLASS_PANE.red(), "Off", 1, false, ChatFormatting.RED);
        }

        @Override
        public boolean click(int slot, int button, ContainerInput input) {
            if (!slots.contains(slot)) return false;
            items[slot] = pane(items[slot].getItem() == Items.STAINED_GLASS_PANE.red());
            return true;
        }

        @Override
        public boolean solved() {
            for (int s : slots) if (items[s].getItem() != Items.STAINED_GLASS_PANE.lime()) return false;
            return true;
        }
    }

    public static class Rubix extends Term {
        private final List<Integer> slots = List.of(12, 13, 14, 21, 22, 23, 30, 31, 32);
        private final List<Pair<Item, String>> cycle = List.of(Pair.of(Items.STAINED_GLASS_PANE.red(), "Red"), Pair.of(Items.STAINED_GLASS_PANE.orange(), "Orange"), Pair.of(Items.STAINED_GLASS_PANE.yellow(), "Yellow"), Pair.of(Items.STAINED_GLASS_PANE.green(), "Green"), Pair.of(Items.STAINED_GLASS_PANE.blue(), "Blue"));
        private final int[] colour = new int[45];

        public Rubix() {
            super(Type.RUBIX);
            int want = beyond(lucky(10, (Integer n) -> n, () -> weighted(List.of(Pair.of(4, 3), Pair.of(5, 4), Pair.of(6, 4), Pair.of(7, 6), Pair.of(8, 7), Pair.of(9, 7), Pair.of(10, 2)))), 10);
            if (want >= 4) {
                do { for (int s : slots) colour[s] = R.nextInt(5); } while (solved() || minClicks() != want);
            } else {
                do {
                    int t = R.nextInt(5);
                    for (int s : slots) colour[s] = t;
                    int left = want;
                    for (int s : shuffled(slots)) {
                        if (left == 0) break;
                        int d = left >= 2 && R.nextBoolean() ? 2 : 1;
                        colour[s] = (t + (R.nextBoolean() ? d : 5 - d)) % 5;
                        left -= d;
                    }
                } while (solved() || minClicks() != want);
            }
            for (int s : slots) set(s);
        }

        @Override public String title() { return "Change all to same color!"; }

        private void set(int slot) {
            Pair<Item, String> c = cycle.get(colour[slot]);
            items[slot] = named(c.first, c.second, 1, false, ChatFormatting.GREEN);
        }

        @Override
        public boolean click(int slot, int button, ContainerInput input) {
            if (!slots.contains(slot)) return false;
            colour[slot] = (colour[slot] + (button == 1 && input == ContainerInput.PICKUP ? 4 : 1)) % 5;
            set(slot);
            return true;
        }

        @Override
        public boolean solved() {
            for (int s : slots) if (colour[s] != colour[slots.get(0)]) return false;
            return true;
        }

        public int minClicks() {
            int best = Integer.MAX_VALUE;
            for (int t = 0; t < 5; t++) {
                int sum = 0;
                for (int s : slots) {
                    int f = (t - colour[s] + 5) % 5;
                    sum += Math.min(f, 5 - f);
                }
                best = Math.min(best, sum);
            }
            return best;
        }
    }

    public static class Starts extends Term {
        private final List<Integer> slots = concat(range(10, 16), range(19, 25), range(28, 34));
        private final char letter;
        private final String title;
        private final HashSet<Integer> picked;

        public Starts() {
            super(Type.STARTS);
            letter = random(STARTS_POOL).second.charAt(0);
            title = "What starts with: '" + letter + "'?";
            List<Pair<String, String>> right = new ArrayList<>();
            List<Pair<String, String>> wrong = new ArrayList<>();
            for (Pair<String, String> p : STARTS_POOL) {
                if (p.second.charAt(0) == letter) right.add(p); else wrong.add(p);
            }
            int n = beyond(lucky(10, (Integer x) -> x, () -> weighted(List.of(Pair.of(2, 1), Pair.of(3, 5), Pair.of(4, 14), Pair.of(5, 21), Pair.of(6, 32), Pair.of(7, 43), Pair.of(8, 27), Pair.of(9, 20), Pair.of(10, 16), Pair.of(11, 3), Pair.of(12, 4)))), 16);
            List<Pair<String, String>> picks = new ArrayList<>();
            for (int i = 0; i < n; i++) picks.add(random(right));
            for (int i = 0; i < slots.size() - n; i++) picks.add(random(wrong));
            picks = shuffled(picks);
            for (int i = 0; i < slots.size(); i++) {
                Pair<String, String> p = picks.get(i);
                items[slots.get(i)] = named(item(p.first), p.second, 1, false, ChatFormatting.GREEN);
            }
            picked = new HashSet<>();
        }

        @Override public String title() { return title; }

        private boolean want(ItemStack s) { return s.getHoverName().getString().startsWith(String.valueOf(letter)); }

        @Override
        public boolean click(int slot, int button, ContainerInput input) {
            ItemStack s = items[slot];
            if (!slots.contains(slot) || !want(s) || picked.contains(slot)) return false;
            picked.add(slot);
            ItemStack c = s.copy();
            c.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            items[slot] = c;
            return true;
        }

        @Override
        public boolean solved() {
            for (int s : slots) if (want(items[s]) && !picked.contains(s)) return false;
            return true;
        }
    }

    public static class Select extends Term {
        private final List<Integer> slots = concat(range(10, 16), range(19, 25), range(28, 34), range(37, 43));
        private final Colour target = random(COLOURS);
        private final String title = "Select all the " + target.title + " items!";
        private final Set<Item> wanted;
        private final HashSet<Integer> picked;

        public Select() {
            super(Type.SELECT);
            List<Colour> rest = new ArrayList<>(COLOURS);
            rest.remove(target);
            List<Colour> families = new ArrayList<>();
            families.add(target);
            families.addAll(shuffled(rest).subList(0, 4));
            Set<Colour> sixes = lucky(10, (Set<Colour> s) -> s.contains(target) ? 1 : 0, () -> new LinkedHashSet<>(shuffled(families).subList(0, 3)));
            int base = sixes.contains(target) ? 6 : 5;
            int mine = beyond(base, 12);
            List<Colour> others = new ArrayList<>(families);
            others.remove(target);
            Map<Colour, Integer> sizes = new LinkedHashMap<>();
            if (mine == base) {
                for (Colour c : families) sizes.put(c, sixes.contains(c) ? 6 : 5);
            } else {
                for (int i = 0; i < others.size(); i++) sizes.put(others.get(i), (28 - mine) / 4 + (i < (28 - mine) % 4 ? 1 : 0));
                sizes.put(target, mine);
            }
            List<Pair<String, String>> picks = new ArrayList<>();
            for (Colour c : families) {
                Integer k = sizes.get(c);
                if (k == null) throw new java.util.NoSuchElementException("Key " + c + " is missing in the map.");
                for (int i = 0; i < k; i++) picks.add(random(c.items));
            }
            picks = shuffled(picks);
            for (int i = 0; i < slots.size(); i++) {
                Pair<String, String> p = picks.get(i);
                items[slots.get(i)] = named(item(p.first), p.second, 1, false, ChatFormatting.GREEN);
            }
            Set<Item> w = new HashSet<>();
            for (Pair<String, String> p : target.items) w.add(item(p.first));
            wanted = w;
            picked = new HashSet<>();
        }

        @Override public String title() { return title; }

        private boolean want(ItemStack s) { return wanted.contains(s.getItem()); }

        @Override
        public boolean click(int slot, int button, ContainerInput input) {
            ItemStack s = items[slot];
            if (!slots.contains(slot) || !want(s) || picked.contains(slot)) return false;
            picked.add(slot);
            ItemStack c = s.copy();
            c.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            items[slot] = c;
            return true;
        }

        @Override
        public boolean solved() {
            for (int s : slots) if (want(items[s]) && !picked.contains(s)) return false;
            return true;
        }
    }

    public static class Melody extends Term {
        public static final int LANES = 3;

        private int row = 0;
        private int lime = 1;
        private int dir = 1;
        private int target;
        private boolean locked = false;
        private int lastStep = -1;
        private int frozen = 0;

        public Melody() {
            super(Type.MELODY);
            if (P3Sim.firstClickMelody()) target = 1;
            else target = lucky(8, (Integer c) -> stepsTo(c), () -> R.nextInt(1, 6));
            draw();
        }

        @Override public String title() { return "Click the button on time!"; }

        private void draw() {
            for (int i = 0; i < items.length; i++) items[i] = FILLER();
            int bottom = (LANES + 1) * 9;
            for (int c = 1; c <= 5; c++) { items[c] = named(Items.STAINED_GLASS_PANE.black(), ""); items[bottom + c] = named(Items.STAINED_GLASS_PANE.black(), ""); }
            items[target] = named(Items.STAINED_GLASS_PANE.magenta(), "");
            items[bottom + target] = named(Items.STAINED_GLASS_PANE.magenta(), "");
            for (int r = 0; r < LANES; r++) {
                for (int c = 1; c <= 5; c++) {
                    int slot = (r + 1) * 9 + c;
                    if (r != row) items[slot] = named(Items.STAINED_GLASS_PANE.white(), "");
                    else if (c == lime) items[slot] = named(Items.STAINED_GLASS_PANE.lime(), "");
                    else items[slot] = named(Items.STAINED_GLASS_PANE.red(), "");
                }
                items[(r + 1) * 9 + 7] = r == row
                    ? button(named(Items.DYED_TERRACOTTA.lime(), "Lock In Slot", 1, false, ChatFormatting.GREEN))
                    : button(named(Items.DYED_TERRACOTTA.red(), "Row Not Active", 1, false, ChatFormatting.RED));
            }
        }

        private ItemStack button(ItemStack s) {
            List<Component> lore = new ArrayList<>();
            for (String l : List.of("Click this button when the Green", "lines up with the Purple!"))
                lore.add(Component.literal(l).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)));
            s.set(DataComponents.LORE, new ItemLore(lore));
            return s;
        }

        @Override
        public void tick(int t) {
            if (t <= 0) lastStep = -1;
            if (t <= 0 || t % 10 != 0 || t == lastStep) return;
            lastStep = t;
            if (frozen > 0) frozen--;
            else {
                if (lime + dir < 1 || lime + dir > 5) dir = -dir;
                lime += dir;
            }
            if (locked) {
                final int last = target;
                locked = false;
                row++;
                target = lucky(8, (Integer c) -> stepsTo(c), () -> {
                    List<Integer> opts = new ArrayList<>();
                    for (int c = 1; c <= 5; c++) if (c != last) opts.add(c);
                    return random(opts);
                });
            }
            draw();
        }

        @Override
        public boolean click(int slot, int button, ContainerInput input) {
            boolean lock = slot % 9 == 7 && slot / 9 >= 1 && slot / 9 <= LANES;
            if (!lock) return false;
            if (slot != (row + 1) * 9 + 7 || locked || lime != target) {
                if (!(locked && slot == (row + 1) * 9 + 7)) frozen = 2;
                return false;
            }
            if (row == LANES - 1) { row = LANES; return true; }
            locked = true;
            return true;
        }

        @Override public boolean solved() { return row >= LANES; }

        private int stepsTo(int c) {
            int l = lime; int d = dir; int k = 0;
            while (l != c && k < 10) { if (l + d < 1 || l + d > 5) d = -d; l += d; k++; }
            return k;
        }
    }

    // ------------------------------------------------------------------ the window

    public static class TerminalMenu extends ChestMenu {
        public final Term term;
        public final Station station;
        private final ServerPlayer player;
        private final int opened = Fight.serverTick;
        private int answeredAt = -1;
        private final java.util.ArrayDeque<Integer> counted = new java.util.ArrayDeque<>();
        private ContainerSynchronizer sync = null;
        private boolean refilled = false;

        public TerminalMenu(int id, Inventory inv, Term term, Station station) {
            super(menuType(term.type.rows), id, inv, new SimpleContainer(term.size()), term.type.rows);
            this.term = term;
            this.station = station;
            this.player = (ServerPlayer) inv.player;
            Terminals.open.add(this);
            for (int i = 0; i < term.size(); i++) getContainer().setItem(i, term.items[i].copy());
        }

        @Override
        public void setSynchronizer(ContainerSynchronizer s) {
            sync = s;
            super.setSynchronizer(new ContainerSynchronizer() {
                @Override
                public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] data) {
                    for (int i = 0; i < items.size(); i++) {
                        ItemStack it = items.get(i);
                        if (i < term.size() || !it.isEmpty()) s.sendSlotChange(menu, i, it);
                    }
                }

                @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) { s.sendSlotChange(menu, slot, stack); }

                @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) { s.sendCarriedChange(menu, stack); }

                @Override public void sendDataChange(AbstractContainerMenu menu, int id, int value) { s.sendDataChange(menu, id, value); }

                @Override public RemoteSlot createSlot() { return s.createSlot(); }
            });
        }

        public void tick() {
            Sim.asPlayer(player, false, () -> {
                if (player.containerMenu != this) { open.remove(this); return null; }
                int age = Fight.serverTick - opened;
                term.tick(age);
                if (Fight.serverTick > answeredAt) { copyIn(); broadcastChanges(); }
                if (!refilled && age >= 5) {
                    refilled = true;
                    if (sync != null) {
                        List<ItemStack> all = new ArrayList<>();
                        for (Slot sl : slots) all.add(sl.getItem());
                        sync.sendInitialData(this, all, getCarried(), new int[0]);
                    }
                    player.connection.send(new ClientboundSetCursorItemPacket(ItemStack.EMPTY));
                }
                return null;
            });
        }

        private void copyIn() {
            for (int i = 0; i < term.size(); i++) {
                if (!ItemStack.matches(getContainer().getItem(i), term.items[i])) getContainer().setItem(i, term.items[i].copy());
            }
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player p) {
            if (term.done || slot < 0 || slot >= term.size()) return;
            Fight.afterPing("terminal click", () -> {
                if (player.containerMenu != this || term.done) return;
                int now = Fight.serverTick;
                if (P3Sim.clickLimit()) {
                    while (!counted.isEmpty() && counted.peekFirst() <= now - 10) counted.removeFirst();
                    if (counted.size() >= 5) return;
                    counted.addLast(now);
                }
                if (!term.click(slot, button, input)) return;
                Sim.sound(SoundEvents.NOTE_BLOCK_PLING, 8f, 4.047619f, null, SoundSource.BLOCKS);
                answeredAt = now;
                if (term.solved()) {
                    term.done = true;
                    player.closeContainer();
                    station.complete(Sim.me());
                    player.connection.send(new ClientboundContainerClosePacket(0));
                }
            });
        }

        @Override
        public void broadcastFullState() { broadcastChanges(); }

        @Override
        public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }

        @Override
        public boolean stillValid(Player p) { return !term.done; }

        @Override
        public void removed(Player p) { super.removed(p); open.remove(this); }
    }

    private static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private static final ArrayList<TerminalMenu> open = new ArrayList<>();

    public static void tick() { for (TerminalMenu m : new ArrayList<>(open)) m.tick(); }

    public static void closeAll() {
        for (ServerPlayer p : Sim.players()) if (p.containerMenu instanceof TerminalMenu) p.closeContainer();
        open.clear();
    }

    public static void closeFor(Station station) { closeFor(station, null); }

    public static void closeFor(Station station, ServerPlayer except) {
        for (ServerPlayer p : Sim.players()) if (p != except && p.containerMenu instanceof TerminalMenu m && m.station == station) p.closeContainer();
    }

    public static boolean inUse(Station station) {
        for (TerminalMenu m : open) if (m.station == station) return true;
        return false;
    }

    // ------------------------------------------------------------------ item pools (Hypixel's, 1.8 names)

    public static class Colour {
        public final String title;
        public final List<Pair<String, String>> items;

        public Colour(String title, List<Pair<String, String>> items) { this.title = title; this.items = items; }
    }

    private static Colour colour(String id, String title, String name, Pair<String, String> dye) { return colour(id, title, name, dye, name + " Wool"); }

    private static Colour colour(String id, String title, String name, Pair<String, String> dye, String wool) {
        return new Colour(title, List.of(Pair.of(id + "_stained_glass", name + " Stained Glass"), Pair.of(id + "_terracotta", name + " Stained Clay"), Pair.of(id + "_wool", wool), dye));
    }

    public static final List<Colour> COLOURS = List.of(
        colour("white", "WHITE", "White", Pair.of("bone_meal", "Bone Meal"), "Wool"),
        colour("orange", "ORANGE", "Orange", Pair.of("orange_dye", "Orange Dye")),
        colour("magenta", "MAGENTA", "Magenta", Pair.of("magenta_dye", "Magenta Dye")),
        colour("light_blue", "LIGHT BLUE", "Light Blue", Pair.of("light_blue_dye", "Light Blue Dye")),
        colour("yellow", "YELLOW", "Yellow", Pair.of("yellow_dye", "Dandelion Yellow")),
        colour("lime", "LIME", "Lime", Pair.of("lime_dye", "Lime Dye")),
        colour("pink", "PINK", "Pink", Pair.of("pink_dye", "Pink Dye")),
        colour("gray", "GRAY", "Gray", Pair.of("gray_dye", "Gray Dye")),
        colour("light_gray", "SILVER", "Silver", Pair.of("light_gray_dye", "Light Gray Dye")),
        colour("cyan", "CYAN", "Cyan", Pair.of("cyan_dye", "Cyan Dye")),
        colour("purple", "PURPLE", "Purple", Pair.of("purple_dye", "Purple Dye")),
        colour("blue", "BLUE", "Blue", Pair.of("lapis_lazuli", "Lapis Lazuli")),
        colour("brown", "BROWN", "Brown", Pair.of("cocoa_beans", "Cocoa Bean")),
        colour("green", "GREEN", "Green", Pair.of("green_dye", "Cactus Green")),
        colour("red", "RED", "Red", Pair.of("red_dye", "Rose Red")),
        colour("black", "BLACK", "Black", Pair.of("ink_sac", "Ink Sack"))
    );

    public static final List<Pair<String, String>> STARTS_POOL = parsePool("""
        acacia_door=Acacia Door|apple=Apple|armor_stand=Armor Stand|arrow=Arrow|baked_potato=Baked Potato|beef=Raw Beef|birch_door=Birch Door
        blaze_powder=Blaze Powder|blaze_rod=Blaze Rod|bone=Bone|book=Book|bow=Bow|bowl=Bowl|bread=Bread|brewing_stand=Brewing Stand|brick=Brick
        bucket=Bucket|cake=Cake|carrot=Carrot|carrot_on_a_stick=Carrot on a Stick|cauldron=Cauldron|chainmail_boots=Chainmail Boots
        chainmail_chestplate=Chainmail Chestplate|chainmail_helmet=Chainmail Helmet|chainmail_leggings=Chainmail Leggings|chest_minecart=Storage Minecart
        chicken=Raw Chicken|clay_ball=Clay|clock=Watch|coal=Coal|cod=Raw Fish|command_block_minecart=Minecart with Command Block
        comparator=Redstone Comparator|compass=Compass|cooked_beef=Steak|cooked_chicken=Roast Chicken|cooked_cod=Cooked Fish|cooked_mutton=Cooked Mutton
        cooked_porkchop=Cooked Porkchop|cooked_rabbit=Cooked Rabbit|cookie=Cookie|dark_oak_door=Dark Oak Door|diamond=Diamond|diamond_axe=Diamond Axe
        diamond_boots=Diamond Boots|diamond_chestplate=Diamond Chestplate|diamond_helmet=Diamond Helmet|diamond_hoe=Diamond Hoe|diamond_leggings=Diamond Leggings
        diamond_pickaxe=Diamond Pickaxe|diamond_sword=Diamond Sword|diamond_horse_armor=Diamond Horse Armor|egg=Egg|emerald=Emerald|enchanted_book=Enchanted Book
        ender_eye=Eye of Ender|ender_pearl=Ender Pearl|experience_bottle=Bottle o' Enchanting|feather=Feather|fermented_spider_eye=Fermented Spider Eye
        filled_map=Map|fire_charge=Fire Charge|firework_rocket=Firework Rocket|firework_star=Firework Star|fishing_rod=Fishing Rod|flint=Flint
        flint_and_steel=Flint and Steel|flower_pot=Flower Pot|furnace_minecart=Powered Minecart|ghast_tear=Ghast Tear|glass_bottle=Glass Bottle
        glistering_melon_slice=Glistering Melon|glowstone_dust=Glowstone Dust|gold_ingot=Gold Ingot|gold_nugget=Gold Nugget|golden_apple=Golden Apple
        golden_carrot=Golden Carrot|golden_axe=Gold Axe|golden_boots=Gold Boots|golden_chestplate=Gold Chestplate|golden_helmet=Gold Helmet|golden_hoe=Gold Hoe
        golden_leggings=Gold Leggings|golden_pickaxe=Gold Pickaxe|golden_shovel=Gold Shovel|golden_sword=Gold Sword|golden_horse_armor=Gold Horse Armor
        gunpowder=Gunpowder|hopper_minecart=Minecart with Hopper|ink_sac=Ink Sac|iron_axe=Iron Axe|iron_boots=Iron Boots|iron_chestplate=Iron Chestplate
        iron_helmet=Iron Helmet|iron_hoe=Iron Hoe|iron_leggings=Iron Leggings|iron_pickaxe=Iron Pickaxe|iron_shovel=Iron Shovel|iron_sword=Iron Sword
        iron_door=Iron Door|iron_horse_armor=Iron Horse Armor|iron_ingot=Iron Ingot|item_frame=Item Frame|jungle_door=Jungle Door|lava_bucket=Lava Bucket
        lead=Lead|leather=Leather|leather_boots=Leather Boots|leather_chestplate=Leather Chestplate|leather_helmet=Leather Helmet|leather_leggings=Leather Leggings
        magma_cream=Magma Cream|map=Empty Map|melon_seeds=Melon Seeds|melon_slice=Melon|milk_bucket=Milk Bucket|minecart=Minecart|mushroom_stew=Mushroom Stew
        mutton=Raw Mutton|name_tag=Name Tag|nether_brick=Nether Brick|nether_star=Nether Star|nether_wart=Nether Wart|oak_boat=Boat|oak_door=Wooden Door
        oak_sign=Oak Sign|painting=Painting|paper=Paper|poisonous_potato=Poisonous Potato|polar_bear_spawn_egg=Spawn Egg|porkchop=Raw Porkchop|potato=Potato
        potion=Water Bottle|prismarine_crystals=Prismarine Crystals|prismarine_shard=Prismarine Shard|pumpkin_pie=Pumpkin Pie|pumpkin_seeds=Pumpkin Seeds
        quartz=Nether Quartz|rabbit=Raw Rabbit|rabbit_foot=Rabbit Foot|rabbit_hide=Rabbit Hide|rabbit_stew=Rabbit Stew|red_bed=Bed|redstone=Redstone
        repeater=Redstone Repeater|rotten_flesh=Rotten Flesh|saddle=Saddle|shears=Shears|skeleton_skull=Skull Item|slime_ball=Slime Ball|snowball=Snowball
        spider_eye=Spider Eye|spruce_door=Spruce Door|stick=Stick|stone_axe=Stone Axe|stone_hoe=Stone Hoe|stone_pickaxe=Stone Pickaxe|stone_shovel=Stone Shovel
        stone_sword=Stone Sword|string=String|sugar=Sugar|sugar_cane=Sugar Cane|tnt_minecart=Minecart with TNT|water_bucket=Water Bucket|wheat=Wheat
        wheat_seeds=Seeds|wooden_axe=Wooden Axe|wooden_hoe=Wooden Hoe|wooden_pickaxe=Wooden Pickaxe|wooden_shovel=Wooden Shovel|wooden_sword=Wooden Sword
        writable_book=Book and Quill|written_book=Written Book
        """);

    private static List<Pair<String, String>> parsePool(String text) {
        List<Pair<String, String>> out = new ArrayList<>();
        for (String raw : text.split("[|\n]")) {
            String it = raw.trim();
            if (!it.contains("=")) continue;
            int eq = it.indexOf('=');
            out.add(Pair.of(it.substring(0, eq), it.substring(eq + 1)));
        }
        return Collections.unmodifiableList(out);
    }
}
