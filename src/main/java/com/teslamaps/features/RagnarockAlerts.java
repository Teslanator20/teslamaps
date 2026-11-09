package com.teslamaps.features;

// Ported from Odin's Ragnarock.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RagnarockAlerts {

    private static final Pattern CANCEL = Pattern.compile("^Ragnarock was cancelled due to (?:being hit|taking damage)!$");
    private static final Pattern STRENGTH = Pattern.compile("^Strength: \\+([\\d,]+)");

    private RagnarockAlerts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RagnarockAlerts").section("Combat").when(() -> cfg().ragnarockAlerts)
                .serverChat(RagnarockAlerts::onChat)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Ragnarock");
                    b.toggle("Ragnarock Alerts", () -> c.ragnarockAlerts, v -> c.ragnarockAlerts = v)
                            .desc("Title when you cast the Ragnarock Axe or it gets cancelled, plus the strength you gained.")
                            .tags("rag", "ragnarock", "axe", "cancel", "strength");
                    b.toggle("  Cast Title", () -> c.ragnarockCastTitle, v -> c.ragnarockCastTitle = v);
                    b.toggle("  Cancel Title", () -> c.ragnarockCancelTitle, v -> c.ragnarockCancelTitle = v);
                    b.toggle("  Strength Gained Message", () -> c.ragnarockStrengthMessage, v -> c.ragnarockStrengthMessage = v)
                            .desc("Item strength x 1.5, in chat");
                    b.channel("    Announce Strength", () -> c.ragnarockStrengthChannel, v -> c.ragnarockStrengthChannel = v);
                });
    }

    private static void onChat(String text) {
        if (!cfg().ragnarockCancelTitle) return;
        if (CANCEL.matcher(StringUtil.stripTrim(text)).matches()) title("§cRagnarock Cancelled!");
    }

    /** handleSoundEvent, both threads. */
    public static void onSound(ClientboundSoundPacket packet) {
        TeslaMapsConfig c = cfg();
        if (!c.ragnarockAlerts || !c.section("Combat")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null) return;
        if (packet.getPitch() != 1.4920635f) return;
        String path = packet.getSound().value().location().getPath();
        if (!path.contains("wolf") || !path.endsWith("death")) return;
        if (!SkyblockUtils.isInSkyblock()) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!"RAGNAROCK_AXE".equals(ItemUtil.skyblockId(held))) return;
        if (c.ragnarockCastTitle) title("§aCasted Rag");
        if (!c.ragnarockStrengthMessage) return;
        int str = strength(held);
        if (str <= 0) return;
        int gained = (int) (str * 1.5);
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Gained strength: §4" + gained));
        Announce.send(c.ragnarockStrengthChannel, "Gained strength from Ragnarock: " + gained);
    }

    private static int strength(ItemStack st) {
        for (String l : ItemUtil.loreLines(st)) {
            Matcher m = STRENGTH.matcher(l.trim());
            if (m.find()) {
                try { return Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return 0; }
            }
        }
        return 0;
    }

    private static void title(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 30, 10);
        mc.gui.hud.setTitle(Component.literal(text));
    }
}
