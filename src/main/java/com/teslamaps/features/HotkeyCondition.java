package com.teslamaps.features;

// Concept from Bax's ConditionFilter.java (condition + pass/fail outputs + island filter per rule)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class HotkeyCondition {

    public static final String[] TYPES = {"None", "Holding Item", "Inventory Contains", "Sneaking", "On SkyBlock"};
    public static final String[] MODES = {"Name", "Lore", "SkyBlock ID"};

    private HotkeyCondition() {}

    public static boolean hasFail(TeslaMapsConfig.Keybind kb) {
        return TeslaMapsConfig.get().hotkeyConditions && kb.failMessage != null && !kb.failMessage.isBlank();
    }

    public static boolean islandOk(TeslaMapsConfig.Keybind kb) {
        if (kb.islands == null || kb.islands.isBlank()) return true;
        String area = SkyblockUtils.getArea();
        for (String s : kb.islands.split(",")) {
            if (!s.isBlank() && s.trim().equalsIgnoreCase(area)) return true;
        }
        return false;
    }

    public static boolean passes(TeslaMapsConfig.Keybind kb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String type = kb.cond == null ? "None" : kb.cond;
        return switch (type) {
            case "Holding Item" -> matches(mc.player.getMainHandItem(), kb);
            case "Inventory Contains" -> {
                Inventory inv = mc.player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (matches(inv.getItem(i), kb)) yield true;
                }
                yield false;
            }
            case "Sneaking" -> mc.player.isShiftKeyDown();
            case "On SkyBlock" -> SkyblockUtils.isInSkyblock();
            default -> true;
        };
    }

    private static boolean matches(ItemStack st, TeslaMapsConfig.Keybind kb) {
        if (st.isEmpty()) return false;
        String want = kb.condValue == null ? "" : kb.condValue.trim().toLowerCase(Locale.ROOT);
        if (want.isEmpty()) return true;
        String mode = kb.condMode == null ? "Name" : kb.condMode;
        return switch (mode) {
            case "Lore" -> {
                for (String l : ItemUtil.loreLines(st)) {
                    if (StringUtil.strip(l).toLowerCase(Locale.ROOT).contains(want)) yield true;
                }
                yield false;
            }
            case "SkyBlock ID" -> ItemUtil.skyblockId(st).toLowerCase(Locale.ROOT).equals(want);
            default -> StringUtil.strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(want);
        };
    }

    public static String summary(TeslaMapsConfig.Keybind kb) {
        String type = kb.cond == null ? "None" : kb.cond;
        boolean islands = kb.islands != null && !kb.islands.isBlank();
        if ("None".equals(type) && !islands) return "§8?";
        return "§e?";
    }
}
