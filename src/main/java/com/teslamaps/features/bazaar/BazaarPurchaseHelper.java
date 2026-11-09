package com.teslamaps.features.bazaar;

// After SkyHanni's BazaarApi purchaseHelper
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks the item our own /bz jumps searched for, until a buy / sell line for it shows up. */
public final class BazaarPurchaseHelper {

    private static final Pattern TRANSACTION = Pattern.compile(
            "^\\[Bazaar] (?:Bought|Sold|Buy Order Setup!|Sell Offer Setup!) [\\d,]+x (.+?) for [\\d,.]+ coins");
    private static final long EXPECT_MS = 5 * 60_000;
    private static final Map<String, Long> expected = new HashMap<>();
    private static String searched;
    private static Object cachedScreen;
    private static int[] marked = new int[0];
    private static int ticks;

    private BazaarPurchaseHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BazaarPurchaseHelper").when(() -> cfg().bzPurchaseHelper)
                .tick(BazaarPurchaseHelper::tick)
                .serverChat(BazaarPurchaseHelper::onChat);
        ClientSendMessageEvents.COMMAND.register(cmd -> {
            if (!cfg().bzPurchaseHelper || expected.isEmpty()) return;
            String c = cmd.trim();
            String lower = c.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("bz ") && !lower.startsWith("bazaar ")) return;
            String q = c.substring(c.indexOf(' ') + 1).trim().toLowerCase(Locale.ROOT);
            Long at = expected.remove(q);
            if (at != null && System.currentTimeMillis() - at < EXPECT_MS) searched = q;
        });
    }

    /** Before our own /bz jump (or a chat link that runs one). */
    public static void expect(String query) {
        if (query == null || !cfg().bzPurchaseHelper) return;
        long now = System.currentTimeMillis();
        expected.values().removeIf(t -> now - t > EXPECT_MS);
        expected.put(query.trim().toLowerCase(Locale.ROOT), now);
    }

    private static void onChat(String raw) {
        if (searched == null) return;
        Matcher m = TRANSACTION.matcher(StringUtil.stripTrim(raw));
        if (m.find() && m.group(1).trim().equalsIgnoreCase(searched)) {
            searched = null;
            marked = new int[0];
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (searched == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)
                || !cs.getTitle().getString().contains("Bazaar")) {
            cachedScreen = null;
            marked = new int[0];
            return;
        }
        if (cs != cachedScreen) { cachedScreen = cs; ticks = 0; }
        if (ticks++ % 5 != 0) return;
        var slots = cs.getMenu().slots;
        int[] out = new int[slots.size()];
        int n = 0;
        for (int i = 9; i <= 44 && i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            if (StringUtil.stripTrim(s.getItem().getHoverName().getString()).equalsIgnoreCase(searched)) out[n++] = i;
        }
        marked = java.util.Arrays.copyOf(out, n);
    }

    public static void render(GuiGraphicsExtractor g) {
        int[] m = marked;
        if (m.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || cs != cachedScreen) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        var slots = cs.getMenu().slots;
        for (int i : m) {
            if (i >= slots.size()) continue;
            Slot s = slots.get(i);
            int x = acc.getX() + s.x, y = acc.getY() + s.y;
            g.fill(x, y, x + 16, y + 16, 0x6655FF55);
        }
    }
}
