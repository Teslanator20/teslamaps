package com.teslamaps.features;

// Ported from SkyHanni's SkyHanniItemTracker.handlePossibleRareDrop (item warnings)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chat line / title for any tracked gain over a coin threshold, RARE DROP line or not. */
public final class ValuableDropWarning {

    private static final Pattern SACK_ADD = Pattern.compile("\\+([\\d,]+) (.+?) \\(");
    private static final long TITLE_MS = 2500;

    private static long titleUntil;
    private static double titleValue;
    private static String lastHover = "", lastWarn = "";
    private static long lastWarnMs;
    private static long lastHoverMs;

    private ValuableDropWarning() {}

    private static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.valuableDropWarn && c.section("Render");
    }

    /** Any gain a tracker counts. */
    public static void onGain(String id, long amount) {
        if (amount <= 0 || id == null || !on()) return;
        Double p = PriceManager.getPriceOrNull(id, PriceManager.SOURCE_BAZAAR_SELL);
        if (p == null || p <= 0) return;
        warn(id, amount, p * amount);
    }

    /** From ChatMixin, server lines only: the "Added" hover of a [Sacks] message. */
    public static void onChatComponent(Component message) {
        if (!on()) return;
        String plain = message.getString();
        if (!plain.contains("[Sacks]")) return;
        StringBuilder added = new StringBuilder();
        collect(message, added);
        String text = added.toString();
        if (text.isBlank()) return;
        long now = System.currentTimeMillis();
        if (text.equals(lastHover) && now - lastHoverMs < 500) return;
        lastHover = text;
        lastHoverMs = now;
        Matcher m = SACK_ADD.matcher(text);
        while (m.find()) {
            String id = SkyblockItems.idForName(m.group(2));
            if (id == null) continue;
            try {
                onGain(id, Long.parseLong(m.group(1).replace(",", "")));
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void collect(Component c, StringBuilder added) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            if (h.trim().startsWith("Added")) added.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collect(s, added);
    }

    private static void warn(String id, long amount, double value) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        double chat = c.valuableDropChatValue * 1_000_000.0;
        double title = c.valuableDropTitleValue * 1_000_000.0;
        if (value < chat && (title <= 0 || value < title)) return;
        long nowMs = System.currentTimeMillis();
        String key = id + ":" + amount;
        if (key.equals(lastWarn) && nowMs - lastWarnMs < 1000) return;
        lastWarn = key;
        lastWarnMs = nowMs;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String name = SkyblockItems.coloredName(id);
        if (name == null || name.isEmpty()) name = id;
        String what = (amount > 1 ? "§7" + amount + "x " : "") + name;
        if (value >= chat) {
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §eValuable drop: " + what
                    + " §7(§6" + ItemPrice.format(value) + "§7)"));
        }
        if (title > 0 && value >= title) {
            long now = System.currentTimeMillis();
            if (now < titleUntil && value <= titleValue) return;
            titleUntil = now + TITLE_MS;
            titleValue = value;
            mc.gui.hud.setTimes(0, 40, 10);
            mc.gui.hud.setTitle(Component.literal(what));
            mc.gui.hud.setSubtitle(Component.literal("§6" + ItemPrice.format(value)));
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
        }
    }
}
