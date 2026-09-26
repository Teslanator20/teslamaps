package com.teslamaps.features;

// Toast list like WpcMod's SpamFilter notifications (slide in from the right), Skytils' Spam Hider HUD as concept
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Where the chat filter's "Redirect" action puts a line: a movable toast list or the action bar. */
public final class ChatSpamHud {

    public static final String[] TARGETS = {"Spam HUD", "Action Bar"};
    private static final int MAX = 6;
    private static final int ROW = 11;
    private static final long SLIDE_MS = 200;

    private record Toast(Component text, int width, long shownAt, long until) {}

    private static final List<Toast> toasts = new ArrayList<>();
    private static HudEditElement el;

    private ChatSpamHud() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        el = HudEditElement.of("chatSpamHud", "Chat Spam HUD").section("Chat")
                .enabled(ChatSpamHud::used)
                .size(() -> 180, () -> ROW * 3)
                .preview((g, m, x, y, s) -> draw(g, m, x, y, s, List.of(
                        sample("§6[Sacks] §a+12"), sample("§7There are blocks in the way!"), sample("§6+50 Combo"))));
        Feature.of("ChatSpamHud").section("Chat").when(ChatSpamHud::used)
                .tick(ChatSpamHud::tick)
                .hud("chatspamhud", ChatSpamHud::render);
    }

    private static Toast sample(String s) {
        Component c = Component.literal(s);
        return new Toast(c, Minecraft.getInstance().font.width(c), 0, Long.MAX_VALUE);
    }

    /** Only while some action is set to Redirect. */
    private static TeslaMapsConfig usedCfg;
    private static int usedVersion;
    private static boolean usedGroups;

    private static boolean used() {
        TeslaMapsConfig c = cfg();
        if ("Redirect".equals(c.chatFilterAction) || "Redirect".equals(c.chatUserFilterAction)) return true;
        if (c != usedCfg || usedVersion != TeslaMapsConfig.version()) {
            usedCfg = c;
            usedVersion = TeslaMapsConfig.version();
            usedGroups = c.chatFilterGroupActions != null && c.chatFilterGroupActions.containsValue("Redirect");
        }
        return usedGroups;
    }

    public static void push(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (cfg().chatRedirectTarget.equals("Action Bar")) {
            mc.gui.setOverlayMessage(line, false);
            return;
        }
        long now = System.currentTimeMillis();
        toasts.add(new Toast(line, mc.font.width(line), now, now + (long) (cfg().chatRedirectSeconds * 1000)));
        while (toasts.size() > MAX) toasts.remove(0);
    }

    private static void tick() {
        if (toasts.isEmpty()) return;
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now >= t.until);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (toasts.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, el.x(), el.y(), el.scale(), toasts);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Toast> list) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        long now = System.currentTimeMillis();
        int row = 0;
        for (Toast t : list) {
            float slide = t.shownAt == 0 ? 1f : Math.min(1f, (now - t.shownAt) / (float) SLIDE_MS);
            int dx = (int) ((1f - slide) * (t.width + 8));
            com.teslamaps.render.gui.HudStyle.panel(g, dx - 2, row - 1, t.width + 4, ROW, 2);
            g.text(mc.font, t.text, dx, row + 1, 0xFFFFFFFF);
            row += ROW + 1;
        }
        pose.popMatrix();
    }
}
