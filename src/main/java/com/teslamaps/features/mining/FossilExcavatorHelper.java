package com.teslamaps.features.mining;

// Ported from SkyHanni's ExcavatorTooltipHider.kt and ScrapGFS.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SackApi;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.TeslaButton;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Fossil Excavator: hides dirt / all tooltips while digging, "Get Scrap from Sacks" button in the start menu. */
public final class FossilExcavatorHelper {

    private static final String TITLE = "Fossil Excavator", SCRAP = "SUSPICIOUS_SCRAP";

    private static Feature tooltips, scrap;
    private static Screen cachedScreen;
    private static boolean cachedIn, startMenu;
    private static int ticks, tipTicks;

    private FossilExcavatorHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tooltips = Feature.of("ExcavatorTooltipHider").section("Mining")
                .when(() -> cfg().excavatorHideDirtTooltip || cfg().excavatorHideAllTooltips);
        scrap = Feature.of("ScrapGfs").section("Mining").when(() -> cfg().scrapGfsButton)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Fossil Excavator");
                    b.toggle("Hide Dirt Tooltips", () -> c.excavatorHideDirtTooltip, v -> c.excavatorHideDirtTooltip = v)
                            .desc("While digging in the Fossil Excavator, the Dirt tooltip no longer covers the grid.")
                            .tags("excavator", "fossil", "dirt", "tooltip");
                    b.toggle("  Hide All Tooltips", () -> c.excavatorHideAllTooltips, v -> c.excavatorHideAllTooltips = v);
                    b.toggle("Get Scrap from Sacks Button", () -> c.scrapGfsButton, v -> c.scrapGfsButton = v)
                            .desc("Button next to the Fossil Excavator start menu that runs /gfs Suspicious Scrap. Optional Bazaar button when the sacks are empty.")
                            .tags("scrap", "suspicious scrap", "gfs", "sacks", "excavator", "fossil");
                    b.slider("  Fetch Amount", 1f, 2048f, () -> (float) c.scrapGfsAmount, v -> c.scrapGfsAmount = Math.round(v), 0);
                    b.toggle("  Only If None In Inventory", () -> c.scrapGfsOnlyIfNone, v -> c.scrapGfsOnlyIfNone = v);
                    b.toggle("  Bazaar Button If Sacks Empty", () -> c.scrapGfsBazaar, v -> c.scrapGfsBazaar = v);
                });

        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs) || !scrap.active()) return;
            if (!StringUtil.stripTrim(cs.getTitle().getString()).equals(TITLE)) return;
            int bw = 110, x = Math.max(4, width / 2 - 88 - bw - 6), y = height / 2 - 40;
            TeslaButton gfs = new TeslaButton(x, y, bw, 18, "§aGet Scrap from Sacks", () -> {
                if (mc.getConnection() != null) mc.getConnection().sendCommand("gfs " + SCRAP + " " + Math.max(1, cfg().scrapGfsAmount));
            });
            TeslaButton bz = new TeslaButton(x, y + 22, bw, 16, "§eBuy Scrap on Bazaar", () -> {
                if (mc.getConnection() != null) mc.getConnection().sendCommand("bz Suspicious Scrap");
            });
            gfs.visible = false;
            bz.visible = false;
            Screens.getWidgets(screen).add(gfs);
            Screens.getWidgets(screen).add(bz);
            ScreenEvents.afterTick(screen).register(s -> {
                if (++ticks % 5 != 0) return;
                boolean show = scrap.active() && startMenu(cs)
                        && (!cfg().scrapGfsOnlyIfNone || scrapInInventory(mc) == 0);
                long inSacks = SackApi.amount("Suspicious Scrap");
                gfs.visible = show;
                gfs.setMessage(net.minecraft.network.chat.Component.literal("§aGet " + cfg().scrapGfsAmount + " Scrap"
                        + (inSacks > 0 ? " §7(" + inSacks + ")" : "")));
                bz.visible = show && cfg().scrapGfsBazaar && inSacks == 0;
            });
        });
    }

    private static boolean startMenu(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack s = slot.getItem();
            if (!s.isEmpty() && StringUtil.stripTrim(s.getHoverName().getString()).equals("Start Excavator")) return true;
        }
        return false;
    }

    private static int scrapInInventory(Minecraft mc) {
        if (mc.player == null) return 0;
        int n = 0;
        var items = mc.player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && SCRAP.equals(ItemUtil.skyblockId(s))) n += s.getCount();
        }
        return n;
    }

    /** From HandledScreenClickMixin's tooltip hook. */
    public static boolean hideTooltip(AbstractContainerScreen<?> cs, Slot hovered) {
        if (hovered == null || tooltips == null) return false;
        TeslaMapsConfig c = cfg();
        if (!c.excavatorHideDirtTooltip && !c.excavatorHideAllTooltips) return false;
        if (cs != cachedScreen) {
            cachedScreen = cs;
            cachedIn = StringUtil.stripTrim(cs.getTitle().getString()).equals(TITLE);
            tipTicks = 0;
        }
        if (!cachedIn || hovered.container instanceof Inventory || !tooltips.active()) return false;
        if (tipTicks++ % 10 == 0) startMenu = startMenu(cs);
        if (startMenu) return false;
        if (c.excavatorHideAllTooltips) return true;
        ItemStack item = hovered.getItem();
        return !item.isEmpty() && StringUtil.stripTrim(item.getHoverName().getString()).equals("Dirt");
    }
}
