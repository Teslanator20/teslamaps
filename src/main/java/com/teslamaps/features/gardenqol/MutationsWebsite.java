package com.teslamaps.features.gardenqol;

// Ported from SkyHanni's MutationsWebsite.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class MutationsWebsite {

    private static final int ICON_SLOT = 8;
    private static final int MIDDLE_SLOT = 22;
    private static final String LINK = "https://skymutations.eu/";

    private static Feature feature;
    private static ItemStack icon;
    private static Object shownOn;
    private static boolean inMenu;
    private static long lastClick;
    private static int ticks;

    private MutationsWebsite() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MutationsWebsite").section("Garden").when(() -> cfg().mutationsWebsite)
                .tick(MutationsWebsite::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Sky Mutations Website Button", () -> c.mutationsWebsite, v -> c.mutationsWebsite = v)
                            .desc("A map in slot 9 of the Carpenter (All Mutations), Crop Guide, Crop Analyzer and All Mutations menus that opens skymutations.eu.")
                            .tags("mutation", "greenhouse", "carpenter", "website");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            inMenu = false;
            shownOn = null;
            return;
        }
        if (shownOn == cs && ticks++ % 10 != 0) return;
        shownOn = cs;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        List<Slot> slots = cs.getMenu().slots;
        boolean carpenter = title.equals("Carpenter") && slots.size() > MIDDLE_SLOT
                && StringUtil.stripTrim(slots.get(MIDDLE_SLOT).getItem().getHoverName().getString()).equals("All Mutations");
        inMenu = carpenter || title.equals("Crop Guide") || title.equals("Crop Analyzer") || title.endsWith(" All Mutations");
    }

    private static ItemStack icon() {
        if (icon == null) {
            ItemStack st = new ItemStack(Items.MAP);
            st.set(DataComponents.CUSTOM_NAME, line("§aSky Mutations"));
            st.set(DataComponents.LORE, new ItemLore(List.of(line("§8(From TeslaMaps)"), line(""),
                    line("§7Click here to open"), line("§7guides and other useful"),
                    line("§7information about mutations"), line("§7on §askymutations.eu"))));
            icon = st;
        }
        return icon;
    }

    private static Component line(String s) {
        return Component.literal(s).withStyle(st -> st.withItalic(false));
    }

    private static boolean applies(Slot slot) {
        if (!inMenu || slot == null || slot.index != ICON_SLOT || slot.container instanceof Inventory) return false;
        if (feature == null || !feature.active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != shownOn) return false;
        ItemStack st = slot.getItem();
        return st.isEmpty() || StringUtil.stripTrim(st.getHoverName().getString()).isEmpty();
    }

    /** Icon and tooltip stack for the slot, or null to keep the original. */
    public static ItemStack replacement(Slot slot) {
        return applies(slot) ? icon() : null;
    }

    public static boolean click(Slot slot) {
        if (!applies(slot)) return false;
        long now = System.currentTimeMillis();
        if (now - lastClick < 2000) return true;
        lastClick = now;
        net.minecraft.util.Util.getPlatform().openUri(LINK);
        return true;
    }
}
