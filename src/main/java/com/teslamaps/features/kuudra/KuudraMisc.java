package com.teslamaps.features.kuudra;

// Damage title hide from IQ (a/f.java), perk block from IQ (a/a.java) / Odin's RemovePerks.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.regex.Pattern;

public final class KuudraMisc {

    private KuudraMisc() {}

    private static final Pattern DAMAGE_TITLE = Pattern.compile("^[^\\d]*[\\d.,]+[KMBT]?/[\\d.,]+[KMBT]?.?$");
    private static final Pattern USELESS_PERK = Pattern.compile(
            "^(?:Steady Hands|Mining Frenzy|Bomberman|Auto Revive|Elle's Lava Rod|Elle's Pickaxe)(?: [IVX]+)?$");

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraMisc").section("Kuudra")
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Misc");
                    b.toggle("Hide Kuudra Damage Title", () -> c.kuuHideDamageTitle, v -> c.kuuHideDamageTitle = v)
                            .desc("Hides the \"240M/240M\" damage title in Kuudra");
                    b.toggle("Block Useless Kuudra Perks", () -> c.kuuBlockPerks, v -> c.kuuBlockPerks = v)
                            .desc("Perk Menu: blocks clicks on Steady Hands, Mining Frenzy, Bomberman, Auto Revive, Elle's Lava Rod and Elle's Pickaxe");
                });
    }

    /** Title and subtitle packets, main thread. */
    public static boolean hideTitle(String text) {
        TeslaMapsConfig c = cfg();
        if (!c.kuuHideDamageTitle || text.indexOf('/') < 0 || !c.section("Kuudra") || !KuudraBuild.inKuudra()) return false;
        return DAMAGE_TITLE.matcher(StringUtil.stripTrim(text)).matches();
    }

    public static boolean blockPerkClick(AbstractContainerScreen<?> screen, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.kuuBlockPerks || slot == null || !slot.hasItem() || !c.section("Kuudra")) return false;
        if (!screen.getTitle().getString().contains("Perk Menu")) return false;
        return USELESS_PERK.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString())).matches();
    }
}
