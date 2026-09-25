package com.teslamaps.features;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.ItemCycleRule;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Clicking a hotbar slot inside an inventory swaps a rule's target item into it, when the
 * rule's island / mouse button / clicked item match. One click, one swap, like Slot Binds.
 */
public class ItemCycle {

    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ItemCycle").section("Inventory").when(() -> cfg().itemCycle)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Item Cycle", () -> c.itemCycle, v -> c.itemCycle = v)
                            .desc("Click a hotbar slot in your inventory to swap a rule's item into it.");
                    b.info("/tmap itemcycle add <item|hand> [island=X|!X] [button=left|right] [clicked=Y]");
                    b.button("  List Rules", () -> list(null));
                });
    }

    public static boolean onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput input) {
        if (feature == null || !feature.active() || cfg().itemCycleRules.isEmpty()) return false;
        if (input != ContainerInput.PICKUP || slot == null) return false;
        if (!(slot.container instanceof Inventory) || slot.getContainerSlot() > 8) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !screen.getMenu().getCarried().isEmpty()) return false;

        ItemStack clicked = slot.getItem();
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        for (ItemCycleRule r : cfg().itemCycleRules) {
            if (r.target == null || r.target.isEmpty()) continue;
            if (r.button >= 0 && r.button != button) continue;
            if (!islandMatches(r.island, area)) continue;
            if (r.clicked != null && !r.clicked.isEmpty() && !matches(clicked, r.clicked)) continue;
            if (matches(clicked, r.target)) return false;

            for (Slot s : screen.getMenu().slots) {
                if (s == slot || !(s.container instanceof Inventory) || s.getContainerSlot() > 35) continue;
                if (!matches(s.getItem(), r.target)) continue;
                mc.gameMode.handleContainerInput(screen.getMenu().containerId, s.index, slot.getContainerSlot(),
                        ContainerInput.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    private static boolean islandMatches(String island, String area) {
        if (island == null || island.isEmpty()) return true;
        boolean neg = island.startsWith("!");
        String want = (neg ? island.substring(1) : island).toLowerCase(Locale.ROOT).trim();
        boolean in = !want.isEmpty() && area.contains(want);
        return neg != in;
    }

    /** uuid, skyblock id or vanilla id ("minecraft:stick" or "stick"). */
    private static boolean matches(ItemStack stack, String want) {
        if (stack.isEmpty()) return false;
        String w = want.trim();
        String uuid = ItemUtil.itemUuid(stack);
        if (uuid != null && uuid.equalsIgnoreCase(w)) return true;
        String sb = ItemUtil.skyblockId(stack);
        if (!sb.isEmpty() && sb.equalsIgnoreCase(w)) return true;
        String vanilla = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return vanilla.equalsIgnoreCase(w) || vanilla.equalsIgnoreCase("minecraft:" + w);
    }

    private static String idOf(ItemStack stack) {
        String uuid = ItemUtil.itemUuid(stack);
        if (uuid != null && !uuid.isEmpty()) return uuid;
        String sb = ItemUtil.skyblockId(stack);
        return sb.isEmpty() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : sb;
    }

    private static void msg(FabricClientCommandSource src, String s) {
        Component c = Component.literal("§b[TeslaMaps] §7" + s);
        if (src != null) src.sendFeedback(c);
        else if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(c);
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("itemcycle")
                .executes(c -> list(c.getSource()))
                .then(ClientCommands.literal("list").executes(c -> list(c.getSource())))
                .then(ClientCommands.literal("clear").executes(c -> {
                    cfg().itemCycleRules.clear();
                    TeslaMapsConfig.save();
                    msg(c.getSource(), "Cleared all item cycle rules");
                    return 1;
                }))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                            int n = IntegerArgumentType.getInteger(c, "n");
                            if (n > cfg().itemCycleRules.size()) { msg(c.getSource(), "§cNo rule " + n); return 0; }
                            cfg().itemCycleRules.remove(n - 1);
                            TeslaMapsConfig.save();
                            msg(c.getSource(), "Removed rule " + n);
                            return 1;
                        })))
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("rule", StringArgumentType.greedyString())
                                .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "rule")))));
    }

    private static int add(FabricClientCommandSource src, String args) {
        ItemCycleRule r = new ItemCycleRule();
        for (String part : args.trim().split("\\s+")) {
            String p = part.toLowerCase(Locale.ROOT);
            if (p.startsWith("island=")) r.island = part.substring(7).replace('_', ' ');
            else if (p.startsWith("button=")) r.button = p.endsWith("right") ? 1 : p.endsWith("left") ? 0 : -1;
            else if (p.startsWith("clicked=")) r.clicked = resolve(part.substring(8));
            else if (r.target.isEmpty()) r.target = resolve(part);
        }
        if (r.target == null || r.target.isEmpty()) { msg(src, "§cNo target item (id, uuid or 'hand')"); return 0; }
        cfg().itemCycleRules.add(r);
        TeslaMapsConfig.save();
        msg(src, "Added rule " + cfg().itemCycleRules.size() + ": " + describe(r));
        return 1;
    }

    private static String resolve(String s) {
        if (!s.equalsIgnoreCase("hand")) return s;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        ItemStack held = mc.player.getMainHandItem();
        return held.isEmpty() ? "" : idOf(held);
    }

    private static String describe(ItemCycleRule r) {
        StringBuilder sb = new StringBuilder("§f").append(r.target);
        if (r.island != null && !r.island.isEmpty()) sb.append(" §7island=§f").append(r.island);
        if (r.button >= 0) sb.append(" §7button=§f").append(r.button == 1 ? "right" : "left");
        if (r.clicked != null && !r.clicked.isEmpty()) sb.append(" §7clicked=§f").append(r.clicked);
        return sb.toString();
    }

    private static int list(FabricClientCommandSource src) {
        if (cfg().itemCycleRules.isEmpty()) {
            msg(src, "No item cycle rules. /tmap itemcycle add <item|hand> [island=X] [button=left|right] [clicked=Y]");
            return 1;
        }
        int i = 0;
        for (ItemCycleRule r : cfg().itemCycleRules) msg(src, (++i) + ". " + describe(r));
        return 1;
    }
}
