package com.teslamaps.features;

// Ported from HypixelSimpleChat's ChatScreenMixin (hsc$searchKeys) + ChatHudMixin (hsc$applySearch)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.chattabs.ChatTabs;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.Locale;

public final class ChatSearch {

    private static boolean searching;
    private static String[] tokens = new String[0];
    private static String applied;
    private static String label = "";

    private ChatSearch() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChatSearch").section("Chat")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Chat Search (Ctrl+F)", () -> c.chatSearch, v -> c.chatSearch = v)
                            .desc("Ctrl+F in open chat turns the input into a search: chat filters live (with the active tab) and shows the match count. Esc or Enter ends it.")
                            .tags("search", "find", "ctrl+f", "history");
                });
    }

    public static boolean enabled() {
        return cfg().chatSearch && cfg().section("Chat");
    }

    public static boolean active() {
        return searching && tokens.length > 0;
    }

    public static boolean matches(GuiMessage m) {
        String[] tk = tokens;
        if (!searching || tk.length == 0) return true;
        String s = StringUtil.strip(m.content().getString()).toLowerCase(Locale.ROOT);
        for (String t : tk) if (!s.contains(t)) return false;
        return true;
    }

    /** Ctrl+F toggles, Esc/Enter end. True = key consumed. */
    public static boolean onKey(int key, boolean ctrl, EditBox input) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_F && ctrl) {
            if (!searching && !enabled()) return false;
            searching = !searching;
            input.setValue("");
            applied = null;
            if (!searching) clear();
            return true;
        }
        if (searching && (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
            searching = false;
            input.setValue("");
            clear();
            return true;
        }
        return false;
    }

    public static void onClose() {
        if (searching) {
            searching = false;
            clear();
        }
    }

    public static void render(GuiGraphicsExtractor gfx, EditBox input) {
        if (!searching || input == null) return;
        String q = input.getValue();
        if (!q.equals(applied)) {
            applied = q;
            int n = apply(q);
            label = q.isBlank() ? "Search · type to filter" : "Search · " + n + (n == 1 ? " match" : " matches");
        }
        Font font = Minecraft.getInstance().font;
        int w = font.width(label);
        int x = input.getX() + input.getWidth() - w - 2;
        int y = input.getY() - 13;
        gfx.fill(x - 3, y - 2, x + w + 3, y + font.lineHeight + 1, 0xC0000000);
        gfx.text(font, label, x, y, 0xFF55FFFF);
    }

    private static int apply(String q) {
        String t = q.trim().toLowerCase(Locale.ROOT);
        tokens = t.isEmpty() ? new String[0] : t.split("\\s+");
        ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
        boolean tabs = ChatTabs.isFilterApplied();
        ChatTabs.Channel ch = ChatTabs.selected();
        chat.setVisibleMessageFilter(m -> (!tabs || ChatTabs.isVisible(ch, m)) && matches(m));
        chat.resetChatScroll();
        if (tokens.length == 0) return 0;
        int n = 0;
        for (GuiMessage m : ((ChatComponentAccessor) chat).teslamaps$getAllMessages()) {
            if ((!tabs || ChatTabs.isVisible(ch, m)) && matches(m)) n++;
        }
        return n;
    }

    private static void clear() {
        tokens = new String[0];
        applied = null;
        if (ChatTabs.isFilterApplied()) {
            ChatTabs.reapply();
        } else {
            ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
            chat.setVisibleMessageFilter(m -> true);
            chat.resetChatScroll();
        }
    }
}
