package com.teslamaps.features.rift;

// After SkyHanni's UbikQuickClose.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/** Once Ubik's Split or Steal round is over, any slot click closes the menu. */
public final class UbikQuickClose {

    private UbikQuickClose() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("UbikQuickClose").section("Rift").when(() -> cfg().riftUbikQuickClose)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Ubik Quick Close", () -> c.riftUbikQuickClose, v -> c.riftUbikQuickClose = v)
                            .desc("When the Split or Steal game is over, a click anywhere in the menu closes it")
                            .tags("ubik", "split or steal", "mountaintop", "close");
                });
    }

    /** From HandledScreenClickMixin; true = click swallowed, menu closed. */
    public static boolean onSlotClick(AbstractContainerScreen<?> cs, ContainerInput type) {
        TeslaMapsConfig c = cfg();
        if (!c.riftUbikQuickClose || !c.section("Rift") || type == ContainerInput.THROW) return false;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Split or Steal")) return false;
        if (!SkyblockUtils.isInArea("The Rift")) return false;
        var slots = cs.getMenu().slots;
        if (slots.size() <= 4 || slots.get(4).getItem().is(Items.CLOCK)) return false;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null && mc.screen == cs) mc.player.closeContainer(); });
        return true;
    }
}
