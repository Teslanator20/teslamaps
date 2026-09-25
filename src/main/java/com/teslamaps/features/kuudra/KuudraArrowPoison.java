package com.teslamaps.features.kuudra;

// Ported from Skyblocker's skyblock/crimson/kuudra/ArrowPoisonWarning.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** Title + sound when swapping to a bow in the DPS phase without (enough) Toxic Arrow Poison. */
public final class KuudraArrowPoison {

    private static int lastSlot = -1;

    private KuudraArrowPoison() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraArrowPoison").section("Kuudra")
                .when(() -> cfg().kuudraArrowPoisonWarn)
                .tick(KuudraArrowPoison::tick)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("DPS");
                    b.toggle("Arrow Poison Warning", () -> c.kuudraArrowPoisonWarn, v -> c.kuudraArrowPoisonWarn = v)
                            .desc("Swapping to a bow after the Ballista is built shows 'No Arrow Poison' or 'Low Arrow Poison' for 3s with a sound.")
                            .tags("toxic", "poison", "bow");
                    b.slider("  Low Below", 1, 64, () -> (float) c.kuudraArrowPoisonMin, v -> c.kuudraArrowPoisonMin = Math.round(v), 0);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Inventory inv = mc.player.getInventory();
        int slot = inv.getSelectedSlot();
        if (slot == lastSlot) return;
        lastSlot = slot;
        // slot 8 = SkyBlock menu
        if (slot == 8 || KuudraContext.phase() < KuudraContext.STUN) return;
        if (!(inv.getItem(slot).getItem() instanceof BowItem)) return;
        int amount = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && "TOXIC_ARROW_POISON".equals(ItemUtil.skyblockId(s))) amount += s.getCount();
        }
        String title = amount == 0 ? "§aNo Arrow Poison" : amount < cfg().kuudraArrowPoisonMin ? "§aLow Arrow Poison" : null;
        if (title == null) return;
        mc.gui.setTimes(0, 60, 5);
        mc.gui.setTitle(Component.literal(title));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
    }
}
