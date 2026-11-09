// Ported from ADM's p3sim/clear/state/ReadyUpMenu.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.DungeonClass;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.Party;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.Terminals;
import com.teslamaps.adm.sim.clear.bots.BotsModule;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.Runs;
import com.teslamaps.config.TeslaMapsConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

public final class ReadyUpMenu {
    private ReadyUpMenu() {}

    private static final List<String> CLASSES = List.of("Healer", "Mage", "Berserk", "Archer", "Tank");

    private static final Map<String, Integer> LEVELS = Map.of("Healer", 50, "Mage", 48, "Berserk", 49, "Archer", 49, "Tank", 47);

    private static final String ORB_TEX = "ba49f9de12c813329f38d0af2da0f32f32a6dcd52c3221d19090c256376c418c";

    private static final HashSet<UUID> notReady = new HashSet<>();

    public static void reset() { notReady.clear(); }

    public static boolean holding() { return !notReady.isEmpty(); }

    public static void open(ServerPlayer p) {
        Run run = Runs.current;
        if (run == null) return;
        if (run.stage != RunStage.COUNTDOWN) return;
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Window(id, inv, p), Component.literal("Ready Up")));
    }

    private static Item icon(String cls) {
        return switch (cls) {
            case "Healer" -> Items.SPLASH_POTION;
            case "Mage" -> Items.BLAZE_ROD;
            case "Berserk" -> Items.IRON_SWORD;
            case "Archer" -> Items.BOW;
            default -> Items.LEATHER_CHESTPLATE;
        };
    }

    private static ItemStack texHead(String hash, String name) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        return SimItems.head(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)), name);
    }

    private static String colourOf(Run run, ServerPlayer p) {
        PlayerRecord rec = run.state.players.get(p.getUUID());
        Object v = rec == null ? null : rec.extras.get("nameColour");
        return v instanceof String s ? s : "§b";
    }

    private static boolean eqIc(String a, String b) { return a == null ? b == null : a.equalsIgnoreCase(b); }

    private static void choose(ServerPlayer p, String cls) {
        Run run = Runs.current;
        if (run == null) return;
        if (run.stage != RunStage.COUNTDOWN) { p.closeContainer(); return; }
        PlayerRecord cur = run.state.players.get(p.getUUID());
        if (eqIc(cur == null ? null : cur.dungeonClass, cls)) return;
        PlayerRecord rec = run.state.players.get(p.getUUID());
        if (rec == null) return;
        rec.dungeonClass = cls.toUpperCase(Locale.ROOT);
        if (Sim.isHost(p)) {
            List<DungeonClass> all = Party.CLASSES();
            int idx = -1;
            for (int i = 0; i < all.size(); i++) if (all.get(i).name().equalsIgnoreCase(cls)) { idx = i; break; }
            P3Sim.classS.setIndex(Math.max(idx, 0));
            Ec.mc().execute(TeslaMapsConfig::save);
        }
        Sim.chat(StateText.selectedClass(p.getGameProfile().name(), colourOf(run, p), cls));
        BotsModule.reclass(run);
    }

    private static void toggleReady(ServerPlayer p) {
        Run run = Runs.current;
        if (run == null) return;
        if (run.stage != RunStage.COUNTDOWN) { p.closeContainer(); return; }
        boolean now = notReady.remove(p.getUUID());
        if (!now) notReady.add(p.getUUID());
        Sim.chat(now ? StateText.ready(p.getGameProfile().name(), colourOf(run, p)) : StateText.notReady(p.getGameProfile().name(), colourOf(run, p)));
    }

    public static class Window extends ChestMenu {
        private final ServerPlayer sp;

        public Window(int id, Inventory inv, ServerPlayer sp) {
            super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(54), 6);
            this.sp = sp;
            draw();
        }

        private void draw() {
            Container c = getContainer();
            Run run = Runs.current;
            for (int i = 0; i < 54; i++) c.setItem(i, Terminals.FILLER());
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(sp.getGameProfile()));
            head.set(DataComponents.CUSTOM_NAME, Component.literal("§b" + sp.getGameProfile().name()).withStyle(s -> s.withItalic(false)));
            c.setItem(4, head);
            boolean ready = !notReady.contains(sp.getUUID());
            c.setItem(13, ready ? Terminals.named(Items.STAINED_GLASS_PANE.lime(), "§aReady") : Terminals.named(Items.STAINED_GLASS_PANE.red(), "§cNot Ready"));
            PlayerRecord me = run == null ? null : run.state.players.get(sp.getUUID());
            String mine = me == null ? null : me.dungeonClass;
            for (int i = 0; i < CLASSES.size(); i++) {
                String cls = CLASSES.get(i);
                boolean yours = cls.equalsIgnoreCase(mine);
                ItemStack it = Terminals.named(icon(cls), "§a[Lvl " + LEVELS.get(cls) + "] " + cls, 1, yours);
                it.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal(yours ? "§aCurrently selected!" : "§eClick to select!").withStyle(s -> s.withItalic(false)))));
                it.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                c.setItem(29 + i, it);
                int n = 0;
                if (run != null) for (PlayerRecord r : run.state.players.values()) if (eqIc(r.dungeonClass, cls)) n++;
                c.setItem(38 + i, n == 0 ? Terminals.named(Items.DYE.gray(), "§70 Players") : Terminals.named(Items.DYE.lime(), "§a" + n + " Player(s)", 1, yours));
            }
            c.setItem(45, texHead(ORB_TEX, "§aDungeon Orb"));
            c.setItem(49, Terminals.named(Items.BARRIER, "§cClose"));
            c.setItem(53, Terminals.named(Items.STAINED_GLASS.lime(), "§aToggle Auto Ready Up"));
        }

        @Override public void clicked(int slot, int button, ContainerInput input, Player p) {
            if (slot == 49) { sp.closeContainer(); return; }
            else if (slot == 13) toggleReady(sp);
            else if (slot >= 29 && slot <= 33) choose(sp, CLASSES.get(slot - 29));
            else return;
            Sim.sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            draw();
            broadcastChanges();
        }

        @Override public boolean stillValid(Player p) { return true; }

        @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
    }
}
