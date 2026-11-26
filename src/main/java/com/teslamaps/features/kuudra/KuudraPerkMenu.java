package com.teslamaps.features.kuudra;

// Ported from IQ's Auto Cannon Close / Middle Click in Perk Menu (a/aA.java)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

public final class KuudraPerkMenu {

    private static long closeUntil = 0;
    private static int closeIn = -1;

    private KuudraPerkMenu() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraPerkMenu").section("Kuudra").legit()
                .when(() -> (cfg().kuuCannonClose || cfg().kuuPerkMiddleClick) && Kuudra.inKuudra())
                .serverChat(KuudraPerkMenu::onChat)
                .tick(KuudraPerkMenu::tick)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Perk Menu");
                    b.toggle("Close Perk Menu After Cannon", () -> c.kuuCannonClose, v -> c.kuuCannonClose = v).cheat()
                            .desc("Closes the Perk Menu right after buying Human Cannonball");
                    b.toggle("Kuudra Perk Menu Middle Click", () -> c.kuuPerkMiddleClick, v -> c.kuuPerkMiddleClick = v).cheat()
                            .desc("Left clicks in the Perk Menu are sent as middle clicks");
                });
    }

    private static boolean perkMenuOpen() {
        return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> s
                && s.getTitle().getString().contains("Perk Menu");
    }

    private static void onChat(String raw) {
        if (!cfg().kuuCannonClose || !StringUtil.strip(raw).contains("You purchased Human Cannonball!")) return;
        closeUntil = System.currentTimeMillis() + 500;
        if (perkMenuOpen()) closeIn = 2;
    }

    private static void tick() {
        var p = Minecraft.getInstance().player;
        if (p == null || !cfg().kuuCannonClose) return;
        if (closeIn > 0 && --closeIn == 0) {
            closeIn = -1;
            if (perkMenuOpen()) {
                p.closeContainer();
                closeUntil = System.currentTimeMillis() + 500;
            }
        }
        if (System.currentTimeMillis() < closeUntil && perkMenuOpen()) p.closeContainer();
    }

    /** HandledScreenClickMixin.slotClicked. True = replaced by a middle click. */
    public static boolean middleClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button, ContainerInput type) {
        TeslaMapsConfig c = cfg();
        if (!c.kuuPerkMiddleClick || button != 0 || type != ContainerInput.PICKUP || slot == null || slot.container instanceof Inventory) return false;
        if (!c.section("Kuudra") || LegitMode.blocksCheats() || !Kuudra.inKuudra()) return false;
        if (!screen.getTitle().getString().contains("Perk Menu")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null || mc.hasShiftDown()) return false;
        mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotId, 2, ContainerInput.CLONE, mc.player);
        return true;
    }
}
