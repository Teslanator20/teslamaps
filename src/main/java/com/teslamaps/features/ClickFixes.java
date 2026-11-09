package com.teslamaps.features;

// Ported from SkyHanni's HuntrapMisclickPrevention and MiddleClickFix
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

public final class ClickFixes {

    private static Feature inv, shards;
    private static long lastTrapMsg;

    private ClickFixes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        inv = Feature.of("ClickFixes").section("Inventory")
                .when(() -> cfg().middleClickFix)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Click Fixes");
                    b.toggle("Middle Click Fix", () -> c.middleClickFix, v -> c.middleClickFix = v)
                            .desc("SkyBlock: a middle click in menus is sent as a real middle click in survival too, so Hypixel's middle-click actions work");
                });
        shards = Feature.of("HuntrapMisclick").section("Shards")
                .when(() -> cfg().huntrapMisclick)
                .config("Shards", b -> b.toggle("Block Empty Trap Clicks", () -> cfg().huntrapMisclick,
                                v -> cfg().huntrapMisclick = v)
                        .desc("Hunting Toolkit: blocks clicks on traps that show Status: EMPTY"));
    }

    public static boolean blockHuntrap(AbstractContainerScreen<?> cs, Slot slot) {
        if (!cfg().huntrapMisclick || shards == null || !shards.active() || slot == null) return false;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Hunting Toolkit")) return false;
        boolean empty = false;
        for (String l : ItemUtil.loreLines(slot.getItem())) {
            if (StringUtil.strip(l).contains("Status: EMPTY")) { empty = true; break; }
        }
        if (!empty) return false;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (now - lastTrapMsg > 10_000 && mc.player != null) {
            lastTrapMsg = now;
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §7Prevented clicking an empty trap in the Hunting Toolkit §8(Shards > Block Empty Trap Clicks)"));
        }
        return true;
    }

    public static boolean middleClickFix() {
        return cfg().middleClickFix && inv != null && inv.active() && SkyblockUtils.isInSkyblock();
    }
}
