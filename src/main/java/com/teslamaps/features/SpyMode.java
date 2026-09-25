package com.teslamaps.features;

// Concept from Oringo Client's SpyMode.java / SpyCommand.java / PeekInventoryGui.java
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NametagMobs;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpyMode {

    private static final int MAX_ITEMS = 54, MAX_PLAYERS = 300;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record Seen(String name, Map<String, ItemStack> items) {}

    private static final Map<String, Seen> seen = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Seen> e) {
            return size() > MAX_PLAYERS;
        }
    };
    private static int ticks;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("SpyMode").section("Inventory")
                .when(() -> cfg().spyMode)
                .tick(SpyMode::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Spy Mode");
                    b.toggle("Spy Mode (/spy <player>)", () -> c.spyMode, v -> c.spyMode = v)
                            .desc("Remembers the held items and armor of players you see this session. /spy <player> shows them in a read-only chest with full tooltips.")
                            .tags("spy", "peek", "equipment", "armor");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(node("spy"));
            d.register(ClientCommands.literal("tmap").then(node("spy")));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> node(String name) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("player", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                            for (Seen s : seen.values()) {
                                String n = s.name.toLowerCase(Locale.ROOT);
                                if (n.startsWith(typed) && !n.equals(typed)) sb.suggest(s.name);
                            }
                            return sb.buildFuture();
                        })
                        .executes(c -> open(c.getSource(), StringArgumentType.getString(c, "player"))));
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !NametagMobs.isRealPlayer(p)) continue;
            String name = p.getName().getString();
            Seen s = null;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = p.getItemBySlot(slot);
                String id = ItemUtil.skyblockId(stack);
                if (id.isEmpty()) continue;
                String uuid = ItemUtil.itemUuid(stack);
                String key = uuid != null && !uuid.isEmpty() ? uuid : id + "|" + stack.getHoverName().getString();
                if (s == null) s = seen.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new Seen(name, new LinkedHashMap<>()));
                if (s.items.size() >= MAX_ITEMS || s.items.containsKey(key)) continue;
                s.items.put(key, stack.copy());
            }
        }
    }

    private static int open(FabricClientCommandSource src, String player) {
        if (feature == null || !feature.active()) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §7Spy Mode is off (Inventory > Spy Mode)."));
            return 0;
        }
        Seen s = seen.get(player.toLowerCase(Locale.ROOT));
        if (s == null || s.items.isEmpty()) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §7No items seen on §f" + player + "§7 yet."));
            return 0;
        }
        List<ItemStack> list = new ArrayList<>(s.items.values());
        list.sort(Comparator.comparing(SpyMode::sortKey));
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(new SpyScreen(s.name, list)));
        return 1;
    }

    private static String sortKey(ItemStack stack) {
        return ItemUtil.skyblockId(stack).replaceFirst("HELMET", "1").replaceFirst("CHESTPLATE", "2")
                .replaceFirst("LEGGINGS", "3").replaceFirst("BOOTS", "4");
    }

    static class SpyScreen extends ContainerScreen {
        SpyScreen(String name, List<ItemStack> items) {
            this(name, items, new SimpleContainer(54), inventory());
        }

        private SpyScreen(String name, List<ItemStack> items, SimpleContainer inv, Inventory own) {
            super(new ChestMenu(MenuType.GENERIC_9x6, -1, own, inv, 6), own, Component.literal("Spy: " + name));
            for (int i = 0; i < items.size() && i < 54; i++) inv.setItem(i, items.get(i));
        }

        private static Inventory inventory() {
            var player = Minecraft.getInstance().player;
            return new Inventory(player, new PlayerEquipment(player));
        }

        @Override
        protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        }
    }
}
