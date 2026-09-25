package com.teslamaps.features;

// Ported from SkyHanni's FocusMode.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class FocusMode {

    private static final String SEPARATOR = "-----------------";

    private static Feature feature;
    private static boolean active;

    private FocusMode() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("FocusMode").when(() -> cfg().focusMode)
                .config("Tooltips & Value", b -> {
                    b.label("Focus Mode");
                    b.toggle("Focus Mode", () -> cfg().focusMode, v -> cfg().focusMode = v)
                            .desc("Tooltips show only the item name. Toggle with the key, or keep it always on.");
                    b.toggle("  Always On", () -> cfg().focusModeAlways, v -> cfg().focusModeAlways = v);
                    b.keybind("  Toggle Key (in menus)", () -> cfg().focusModeKey, v -> cfg().focusModeKey = v);
                    b.toggle("  Skip Menu & Bazaar Items", () -> cfg().focusModeSkipMenus, v -> cfg().focusModeSkipMenus = v);
                    b.info("Auction House keeps the price and seller block");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active()) onTooltip(stack, lines);
        });
    }

    /** From ContainerKeyMixin. */
    public static boolean onKey(int key) {
        TeslaMapsConfig c = cfg();
        if (!c.focusMode || c.focusModeAlways || c.focusModeKey == -1 || key != c.focusModeKey) return false;
        active = !active;
        return true;
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !SkyblockUtils.isInSkyblock()) return;
        TeslaMapsConfig c = cfg();
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (c.focusModeSkipMenus) {
            String id = ItemUtil.skyblockId(stack);
            if (id.isEmpty() || id.equals("SKYBLOCK_MENU")) return;
            if (title.startsWith("Bazaar")) return;
        }
        boolean hint = !c.focusModeAlways && c.focusModeKey != -1;
        String keyName = hint ? KeyNames.display(c.focusModeKey) : "";
        if (!active && !c.focusModeAlways) {
            if (hint) lines.add(1, Component.literal("§7Press " + keyName + " to enable Focus Mode"));
            return;
        }
        List<Component> out = new ArrayList<>();
        out.add(lines.get(0));
        if (hint) out.add(Component.literal("§7Focus Mode on, press " + keyName + " to disable"));
        if (title.startsWith("Auctions")) {
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).getString().contains(SEPARATOR)) continue;
                for (int j = i; j < lines.size() && j < i + 20; j++) out.add(lines.get(j));
                break;
            }
        }
        lines.clear();
        lines.addAll(out);
    }
}
