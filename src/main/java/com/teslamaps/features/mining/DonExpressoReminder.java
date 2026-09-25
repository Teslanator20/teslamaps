package com.teslamaps.features.mining;

// Ported from SkyHanni's DonExpressoFeedingReminder.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

/** Clickable teleport line when Don Expresso is full and you still carry Tasty Mithril. */
public final class DonExpressoReminder {

    private static final String FULL = "[NPC] Don Expresso: I DON'T FEEL SO GOOD...";

    private DonExpressoReminder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DonExpressoReminder").section("Mining").when(() -> cfg().donExpressoReminder)
                .serverChat(DonExpressoReminder::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Don Expresso Feeding Reminder", () -> c.donExpressoReminder, v -> c.donExpressoReminder = v)
                            .desc("When Don Expresso says he is full and you still have Tasty Mithril, a chat line lets you teleport to him with one click.")
                            .tags("don expresso", "tasty mithril", "mithril gourmand", "dwarven");
                });
    }

    private static void onChat(String text) {
        if (!StringUtil.stripTrim(text).equals(FULL)) return;
        if (!SkyblockUtils.isInArea("Dwarven Mines")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !hasTastyMithril(mc)) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §6Don Expresso §eis full! Click to teleport to him.")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/tptodonexpresso"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to teleport to Don Expresso")))));
    }

    private static boolean hasTastyMithril(Minecraft mc) {
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
            if ("MITHRIL_GOURMAND".equals(ItemUtil.skyblockId(st))) return true;
        }
        return false;
    }
}
