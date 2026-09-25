package com.teslamaps.slayer;

// Ported from Skyblocker's CallMaddox (fail only)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class CallMaddox {

    private CallMaddox() {}

    public static void register() {
        Feature.of("CallMaddox").section("Combat").config("Combat", b -> {
            TeslaMapsConfig c = b.config();
            b.toggle("Call Maddox After Fail", () -> c.callMaddoxOnFail, v -> c.callMaddoxOnFail = v)
                    .desc("After a failed slayer quest a clickable chat line runs /call slayer. Not in the Rift.")
                    .tags("maddox", "batphone", "call slayer", "fail");
        });
    }

    /** From SlayerHUD.onSlayerFailed. */
    static void onFail() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.callMaddoxOnFail || !c.section("Combat")) return;
        if (SkyblockUtils.getArea().contains("Rift")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] ")
                .append(Component.literal("[Click to call Maddox]").withStyle(s -> s
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/call slayer"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("/call slayer"))))));
    }
}
