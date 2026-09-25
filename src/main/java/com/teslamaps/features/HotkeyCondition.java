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

    public static final String[] TYPES = {"None", "Holding Item", "Inventory Contains", "Sneaking", "On SkyBlock",
            "In Dungeon", "Dungeon Class", "Garden Plot", "Mayor / Perk"};
    public static final java.util.Set<String> VALUE_TYPES = java.util.Set.of("Dungeon Class", "Garden Plot", "Mayor / Perk");
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
        boolean r = raw(mc, kb);
        return kb.negate && HotkeyRules.enabled() ? !r : r;
    }

    private static boolean raw(Minecraft mc, TeslaMapsConfig.Keybind kb) {
        String type = kb.cond == null ? "None" : kb.cond;
        String v = kb.condValue == null ? "" : kb.condValue.trim();
        return switch (type) {
            case "In Dungeon" -> com.teslamaps.dungeon.DungeonManager.isInDungeon();
            case "Dungeon Class" -> {
                String cls = com.teslamaps.player.PlayerTracker.getLocalClass();
                yield cls != null && (v.isEmpty() || anyEquals(v, cls));
            }
            case "Garden Plot" -> {
                String plot = PestData.currentPlot();
                yield plot != null && (v.isEmpty() || anyEquals(v, plot));
            }
            case "Mayor / Perk" -> {
                if (v.isEmpty()) yield true;
                boolean hit = false;
                for (String s : v.split(",")) {
                    String t = s.trim();
                    if (t.isEmpty()) continue;
                    if (t.equalsIgnoreCase(com.teslamaps.api.ElectionApi.mayor())
                            || t.equalsIgnoreCase(com.teslamaps.api.ElectionApi.minister())
                            || com.teslamaps.api.ElectionApi.hasPerk(t)) { hit = true; break; }
                }
                yield hit;
            }
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

    private static boolean anyEquals(String list, String value) {
        for (String s : list.split(",")) if (s.trim().equalsIgnoreCase(value.trim())) return true;
        return false;
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
        boolean rules = HotkeyRules.enabled() && (kb.seqKey != -1 || kb.cooldownMs > 0 || kb.delayMs > 0
                || (kb.actions != null && !kb.actions.isBlank()));
        if ("None".equals(type) && !islands && !rules) return "§8?";
        return "§e?";
    }
}
