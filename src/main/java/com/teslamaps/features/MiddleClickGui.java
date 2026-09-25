package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Ported from Devonian's MiddleClickGui
public class MiddleClickGui {

    private static final List<String> AVOID_GUIS = List.of(
            "Wardrobe", "Drill Anvil", "Anvil", "Storage", "The Hex", "Composter", "Auctions", "Abiphone",
            "Chest", "Large Chest", "Stats Tuning", "Ender Chest", "Personal Vault", "You    ");
    private static final Pattern TERMINALS = Pattern.compile(
            "^(Click in order!|Select all the .+? items!|What starts with: '.+?'\\?|Change all to same color!|Correct all the panes!|Click the button on time!)$");
    private static final Set<String> AVOID_ITEMS = Set.of("Reforge Item", "Salvage Items", "Experience Bottles");

    public static boolean handle(AbstractContainerScreen<?> cs, Slot slot, int slotId, int button, ContainerInput type) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.middleClickGui || !c.section("Inventory")) return false;
        if (button != 0 || type != ContainerInput.PICKUP || slot == null || slot.container instanceof Inventory) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || !mc.player.containerMenu.getCarried().isEmpty()) return false;
        if (mc.hasControlDown() || !SkyblockUtils.isInSkyblock()) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !ItemUtil.skyblockId(stack).isEmpty()) return false;

        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (title.contains(" Minion ") || title.contains("Backpack") || TERMINALS.matcher(title).matches()) return false;
        for (String g : AVOID_GUIS) if (title.startsWith(g)) return false;
        for (String g : c.middleClickGuiBlacklist) if (title.startsWith(g)) return false;
        if (AVOID_ITEMS.contains(StringUtil.stripTrim(stack.getHoverName().getString()))) return false;
        for (String l : ItemUtil.loreLines(stack)) if (l.toLowerCase().contains("-click")) return false;

        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slotId, 2, ContainerInput.CLONE, mc.player);
        return true;
    }

    public static boolean onKey(AbstractContainerScreen<?> cs, int key) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.middleClickGui || key == -1 || key != c.middleClickGuiBlacklistKey) return false;
        if (cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (title.isEmpty()) return false;
        boolean removed = c.middleClickGuiBlacklist.remove(title);
        if (!removed) c.middleClickGuiBlacklist.add(title);
        TeslaMapsConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                "§bMiddle Click GUI blacklist " + (removed ? "§cremoved " : "§aadded ") + "§b" + title));
        return true;
    }
}
