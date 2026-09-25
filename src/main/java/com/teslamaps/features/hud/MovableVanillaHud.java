package com.teslamaps.features.hud;

// SkyHanni features/gui/moveablehud/*, Bax VanillaHudMixin, Skysoft TabListPositionEditor.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class MovableVanillaHud {

    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier HOTBAR_SEL = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final Identifier XP_BG = Identifier.withDefaultNamespace("hud/experience_bar_background");
    private static final Identifier XP_FG = Identifier.withDefaultNamespace("hud/experience_bar_progress");
    private static final String SAMPLE_ACTION = "§c1,234/1,234❤     §a500❈ Defense     §b800/800✎ Mana";
    private static final String SAMPLE_ITEM = "§dHeroic Hyperion ✪✪✪✪✪";

    /** One vanilla element: its default box, and the stored box it is moved to. */
    private static class Slot {
        final BooleanSupplier on;
        final IntSupplier defX, defY, w, h, cfgX, cfgY;
        final java.util.function.DoubleSupplier scale;

        Slot(BooleanSupplier on, IntSupplier defX, IntSupplier defY, IntSupplier w, IntSupplier h,
             IntSupplier cfgX, IntSupplier cfgY, java.util.function.DoubleSupplier scale) {
            this.on = on; this.defX = defX; this.defY = defY; this.w = w; this.h = h;
            this.cfgX = cfgX; this.cfgY = cfgY; this.scale = scale;
        }

        int x() {
            int v = cfgX.getAsInt();
            if (v >= 0) return v;
            // unplaced: keep it centred where vanilla has it
            float s = (float) scale.getAsDouble();
            return defX.getAsInt() + w.getAsInt() / 2 - (int) (w.getAsInt() * s / 2);
        }

        int y() {
            int v = cfgY.getAsInt();
            if (v >= 0) return v;
            float s = (float) scale.getAsDouble();
            return defY.getAsInt() + h.getAsInt() - (int) (h.getAsInt() * s);
        }

        boolean active() {
            return on.getAsBoolean() && master();
        }

        HudElement wrap(HudElement original) {
            return (g, d) -> {
                if (!active()) { original.extractRenderState(g, d); return; }
                float s = (float) scale.getAsDouble();
                int bx = x(), by = y(), dx = defX.getAsInt(), dy = defY.getAsInt();
                if (bx == dx && by == dy && s == 1f) { original.extractRenderState(g, d); return; }
                g.pose().pushMatrix();
                g.pose().translate(bx, by);
                g.pose().scale(s, s);
                g.pose().translate(-dx, -dy);
                original.extractRenderState(g, d);
                g.pose().popMatrix();
            };
        }
    }

    private static int tabLeft, tabWidth = 200, tabHeight = 90;
    private static int timer;

    private MovableVanillaHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean master() {
        TeslaMapsConfig c = cfg();
        return c.moveVanillaHud && (c.moveVanillaHudEverywhere || SkyblockUtils.isInSkyblock());
    }

    private static int sw() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    private static int sh() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }

    public static void register() {
        Slot hotbar = new Slot(() -> cfg().moveHotbar, () -> sw() / 2 - 91, () -> sh() - 22, () -> 182, () -> 22,
                () -> cfg().vHotbarX, () -> cfg().vHotbarY, () -> cfg().vHotbarScale);
        Slot xp = new Slot(() -> cfg().moveXpBar, () -> sw() / 2 - 91, () -> sh() - 35, () -> 182, () -> 11,
                () -> cfg().vXpBarX, () -> cfg().vXpBarY, () -> cfg().vXpBarScale);
        Slot action = new Slot(() -> cfg().moveActionBar, () -> sw() / 2 - 100, () -> sh() - 72, () -> 200, () -> 9,
                () -> cfg().vActionBarX, () -> cfg().vActionBarY, () -> cfg().vActionBarScale);
        Slot item = new Slot(() -> cfg().moveHeldItemName, () -> sw() / 2 - 100, MovableVanillaHud::itemNameY, () -> 200, () -> 9,
                () -> cfg().vHeldItemX, () -> cfg().vHeldItemY, () -> cfg().vHeldItemScale);
        Slot tab = new Slot(() -> cfg().moveTabList, () -> tabLeft, () -> 9, () -> tabWidth, () -> tabHeight,
                () -> cfg().vTabListX, () -> cfg().vTabListY, () -> cfg().vTabListScale) {
            @Override int y() {
                int v = cfgY.getAsInt();
                return v >= 0 ? v : 9;
            }
        };

        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, hotbar::wrap);
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, xp::wrap);
        HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, xp::wrap);
        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, action::wrap);
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, item::wrap);
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, tab::wrap);

        element("vHotbar", "Hotbar", hotbar, (g, mc) -> {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 0, 0, 182, 22);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SEL, -1, -1, 24, 23);
        });
        element("vXpBar", "XP Bar", xp, (g, mc) -> {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BG, 0, 6, 182, 5);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_FG, 182, 5, 0, 0, 0, 6, 120, 5);
            String lvl = "30";
            int x = (182 - mc.font.width(lvl)) / 2;
            g.text(mc.font, lvl, x + 1, 0, 0xFF000000, false);
            g.text(mc.font, lvl, x - 1, 0, 0xFF000000, false);
            g.text(mc.font, lvl, x, 1, 0xFF000000, false);
            g.text(mc.font, lvl, x, -1, 0xFF000000, false);
            g.text(mc.font, lvl, x, 0, 0xFF80FF20, false);
        });
        element("vActionBar", "Action Bar", action, (g, mc) ->
                g.centeredText(mc.font, SAMPLE_ACTION, 100, 0, 0xFFFFFFFF));
        element("vHeldItem", "Held Item Name", item, (g, mc) ->
                g.centeredText(mc.font, SAMPLE_ITEM, 100, 0, 0xFFFFFFFF));
        element("vTabList", "Tab List", tab, MovableVanillaHud::tabPreview);

        Feature.of("MovableVanillaHud").when(() -> cfg().moveVanillaHud && cfg().moveTabList)
                .tick(MovableVanillaHud::tick);
        Feature.of("MovableVanillaHudConfig").config("Interface", b -> {
            TeslaMapsConfig c = cfg();
            b.label("Movable Vanilla HUD");
            b.toggle("Movable Vanilla HUD", () -> c.moveVanillaHud, v -> c.moveVanillaHud = v)
                    .desc("Move and scale vanilla HUD parts. Each one shows up in /tmap gui once switched on.")
                    .tags("hotbar", "xp bar", "action bar", "item name", "tab list", "move");
            b.toggle("  Hotbar", () -> c.moveHotbar, v -> c.moveHotbar = v);
            b.toggle("  XP Bar", () -> c.moveXpBar, v -> c.moveXpBar = v).desc("Bar and level number");
            b.toggle("  Action Bar", () -> c.moveActionBar, v -> c.moveActionBar = v);
            b.toggle("  Held Item Name", () -> c.moveHeldItemName, v -> c.moveHeldItemName = v);
            b.toggle("  Tab List", () -> c.moveTabList, v -> c.moveTabList = v);
            b.toggle("  Also Outside SkyBlock", () -> c.moveVanillaHudEverywhere, v -> c.moveVanillaHudEverywhere = v);
            b.button("  Reset Positions", () -> {
                c.vHotbarX = c.vHotbarY = c.vXpBarX = c.vXpBarY = c.vActionBarX = c.vActionBarY = -1;
                c.vHeldItemX = c.vHeldItemY = c.vTabListX = c.vTabListY = -1;
                c.vHotbarScale = c.vXpBarScale = c.vActionBarScale = c.vHeldItemScale = c.vTabListScale = 1.0f;
                TeslaMapsConfig.save();
            });
        });
    }

    private interface Draw {
        void draw(GuiGraphicsExtractor g, Minecraft mc);
    }

    private static void element(String prefix, String label, Slot s, Draw preview) {
        HudEditElement.of(prefix, label)
                .enabled(s::active)
                .position(s::x, s::y)
                .size(s.w, s.h)
                .preview((g, mc, x, y, sc) -> {
                    g.pose().pushMatrix();
                    g.pose().translate(x, y);
                    g.pose().scale(sc, sc);
                    preview.draw(g, mc);
                    g.pose().popMatrix();
                });
    }

    private static int itemNameY() {
        Minecraft mc = Minecraft.getInstance();
        int y = sh() - 59;
        if (mc.gameMode != null && !mc.gameMode.canHurtPlayer()) y += 14;
        return y;
    }

    private static void tabPreview(GuiGraphicsExtractor g, Minecraft mc) {
        Scoreboard sb = mc.level == null ? null : mc.level.getScoreboard();
        if (sb == null || mc.getConnection() == null) {
            HudStyle.panel(g, 0, 0, tabWidth, tabHeight);
            return;
        }
        g.pose().translate(-tabLeft, -9);
        mc.gui.getTabList().extractRenderState(g, sw(), sb, sb.getDisplayObjective(DisplaySlot.LIST));
    }

    // same layout maths as PlayerTabOverlay, so the box matches what vanilla draws
    private static void tick() {
        if (++timer % 10 != 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.level == null) return;
        PlayerTabOverlay overlay = mc.gui.getTabList();
        Font font = mc.font;
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.LIST);
        Collection<PlayerInfo> all = mc.getConnection().getListedOnlinePlayers();
        int count = Math.min(80, all.size());
        int nameW = 0, scoreW = 0, i = 0;
        for (PlayerInfo p : all) {
            if (i++ >= 80) break;
            nameW = Math.max(nameW, font.width(overlay.getNameForDisplay(p)));
            if (obj != null && obj.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(ScoreHolder.fromGameProfile(p.getProfile()), obj);
                if (info != null) {
                    int w = font.width(ReadOnlyScoreInfo.safeFormatValue(info, obj.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT)));
                    if (w > 0) scoreW = Math.max(scoreW, font.width(" ") + w);
                }
            }
        }
        if (obj != null && obj.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) scoreW = 90;
        int columns = 1, rows = count;
        while (rows > 20) {
            columns++;
            rows = (count + columns - 1) / columns;
        }
        int screen = sw();
        boolean heads = mc.isLocalServer() || mc.getConnection().getConnection().isEncrypted();
        int slot = Math.min(columns * ((heads ? 9 : 0) + nameW + scoreW + 13), screen - 50) / columns;
        var acc = (com.teslamaps.mixin.PlayerTabOverlayAccessor) overlay;
        int lineW = slot * columns + (columns - 1) * 5;
        int extra = 0;
        Component header = acc.teslamaps$getHeader(), footer = acc.teslamaps$getFooter();
        if (header != null) {
            List<FormattedCharSequence> l = font.split(header, screen - 50);
            for (FormattedCharSequence s : l) lineW = Math.max(lineW, font.width(s));
            extra += l.size() * 9 + 1;
        }
        if (footer != null) {
            List<FormattedCharSequence> l = font.split(footer, screen - 50);
            for (FormattedCharSequence s : l) lineW = Math.max(lineW, font.width(s));
            extra += l.size() * 9 + 1;
        }
        tabLeft = screen / 2 - lineW / 2 - 1;
        tabWidth = lineW / 2 * 2 + 2;
        tabHeight = rows * 9 + 1 + extra;
    }
}
