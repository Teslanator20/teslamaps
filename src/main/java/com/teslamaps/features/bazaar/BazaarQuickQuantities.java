package com.teslamaps.features.bazaar;

// Ported from Skyblocker's BazaarQuickQuantities
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.TeslaButton;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

public final class BazaarQuickQuantities {

    private BazaarQuickQuantities() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BazaarQuickQuantities").when(() -> cfg().bzQuickQuantities)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Bazaar Quick Quantities", () -> c.bzQuickQuantities, v -> c.bzQuickQuantities = v)
                            .desc("Amount buttons next to the bazaar \"to order\" sign, plus one with the clipboard number")
                            .tags("bazaar", "amount", "sign", "quantity", "clipboard");
                    b.slider("  Amount 1", 1, 2240, () -> (float) c.bzQuickQty1, v -> c.bzQuickQty1 = Math.round(v), 0);
                    b.slider("  Amount 2", 1, 2240, () -> (float) c.bzQuickQty2, v -> c.bzQuickQty2 = Math.round(v), 0);
                    b.slider("  Amount 3", 1, 71680, () -> (float) c.bzQuickQty3, v -> c.bzQuickQty3 = Math.round(v), 0);
                    b.toggle("  Confirm Sign On Click", () -> c.bzQuickQtyClose, v -> c.bzQuickQtyClose = v);
                });
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            TeslaMapsConfig c = cfg();
            if (!c.bzQuickQuantities || !SkyblockUtils.isInSkyblock()) return;
            if (!(screen instanceof AbstractSignEditScreen)) return;
            String[] messages = ((AbstractSignEditScreenAccessor) screen).getMessages();
            if (messages == null || messages.length < 4) return;
            if (!messages[1].equals("^^^^^^^^^^^^^^^") || !messages[3].equals("to order")) return;
            int x = width / 2 + 50, y = 65;
            add(screen, messages, x, y, String.valueOf(c.bzQuickQty1));
            add(screen, messages, x, y + 20, String.valueOf(c.bzQuickQty2));
            add(screen, messages, x, y + 40, String.valueOf(c.bzQuickQty3));
            String clip = mc.keyboardHandler.getClipboard().replace(",", "").trim();
            if (!clip.isEmpty() && clip.length() <= 7 && clip.chars().allMatch(Character::isDigit)) {
                add(screen, messages, x, y + 60, "§b" + clip);
            }
        });
    }

    private static void add(Screen screen, String[] messages, int x, int y, String label) {
        String value = label.startsWith("§b") ? label.substring(2) : label;
        Screens.getWidgets(screen).add(new TeslaButton(x, y, 50, 18, label, () -> {
            messages[0] = value;
            if (cfg().bzQuickQtyClose) Minecraft.getInstance().execute(screen::onClose);
        }));
    }
}
