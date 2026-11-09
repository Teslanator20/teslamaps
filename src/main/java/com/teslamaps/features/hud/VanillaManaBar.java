package com.teslamaps.features.hud;

// Ported from Skyblocker's skyblock/fancybars/VanillaStyleManaBar.java (sprites too)
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class VanillaManaBar {

    private enum Notch { CONTAINER, MANA, OVERFLOW, OVERFLOW_DARK }

    private static final Identifier CONTAINER = id("container"), CONTAINER_BLINK = id("container_blink");
    private static final Identifier MANA_FULL = id("mana_full"), MANA_HALF = id("mana_half");
    private static final Identifier MANA_FULL_BLINK = id("mana_full_blink"), MANA_HALF_BLINK = id("mana_half_blink");
    private static final Identifier OF_FULL = id("overflow_full"), OF_HALF = id("overflow_half");
    private static final Identifier OF_FULL_BLINK = id("overflow_full_blink"), OF_HALF_BLINK = id("overflow_half_blink");
    private static final Identifier OFD_FULL = id("overflow_dark_full"), OFD_HALF = id("overflow_dark_half");
    private static final Identifier OFD_FULL_BLINK = id("overflow_dark_full_blink"), OFD_HALF_BLINK = id("overflow_dark_half_blink");
    private static final Identifier FOOD_EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");
    private static final Identifier FOOD_HALF_HUNGER = Identifier.withDefaultNamespace("hud/food_half_hunger");
    private static final Identifier FOOD_EMPTY_HUNGER = Identifier.withDefaultNamespace("hud/food_empty_hunger");

    private static volatile int mana, maxMana, overflow;
    private static volatile boolean seen;

    private static int lastMana, lastOverflow, manaBlinkStart, overflowBlinkStart;
    private static long blinkEnd;

    private VanillaManaBar() {}

    private static Identifier id(String n) {
        return Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, "bars/vanilla_mana/" + n);
    }

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return cfg().vanillaManaBar && seen && maxMana > 0 && SkyblockUtils.isInSkyblock()
                && mc.gameMode != null && mc.gameMode.canHurtPlayer();
    }

    public static void register() {
        Feature.of("VanillaManaBar").when(VanillaManaBar::active)
                .hud("vanillamanabar", VanillaManaBar::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Vanilla Mana Bar");
                    b.toggle("Vanilla Style Mana Bar", () -> c.vanillaManaBar, v -> c.vanillaManaBar = v)
                            .desc("Mana as icons in place of the hunger bar, overflow mana in its own colour.")
                            .tags("mana", "hunger", "food");
                    b.toggle("  Blink On Mana Loss", () -> c.vanillaManaBarBlink, v -> c.vanillaManaBarBlink = v);
                    b.toggle("  Use Hunger Sprites", () -> c.vanillaManaBarHungerSprites, v -> c.vanillaManaBarHungerSprites = v);
                });
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original ->
                (g, d) -> { if (!active() || com.teslamaps.features.GlobalRender.off()) original.extractRenderState(g, d); });
    }

    /** Raw action bar text, main thread. "800/1,355✎ Mana     400ʬ" */
    public static void onActionBar(String text) {
        if (text == null) return;
        String raw = com.teslamaps.utils.StringUtil.strip(text);
        int i = raw.indexOf('✎');
        if (i < 0) i = raw.indexOf('');
        if (i < 0) return;
        int slash = -1, start = i;
        while (start > 0) {
            char ch = raw.charAt(start - 1);
            if (ch == '/') { if (slash >= 0) break; slash = start - 1; }
            else if (!Character.isDigit(ch) && ch != ',') break;
            start--;
        }
        if (slash < 0) return;
        int cur = parse(raw, start, slash), max = parse(raw, slash + 1, i);
        if (cur < 0 || max <= 0) return;
        int of = 0;
        int o = raw.indexOf('ʬ');
        if (o < 0) o = raw.indexOf('');
        if (o > 0) {
            int s = o;
            while (s > 0 && (Character.isDigit(raw.charAt(s - 1)) || raw.charAt(s - 1) == ',')) s--;
            of = Math.max(0, parse(raw, s, o));
        }
        mana = cur;
        maxMana = max;
        overflow = of;
        seen = true;
    }

    private static int parse(String s, int from, int to) {
        long v = 0;
        boolean any = false;
        for (int k = from; k < to; k++) {
            char ch = s.charAt(k);
            if (ch == ',') continue;
            if (!Character.isDigit(ch)) return -1;
            v = v * 10 + (ch - '0');
            any = true;
            if (v > Integer.MAX_VALUE / 4) return -1;
        }
        return any ? (int) v : -1;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        int value = mana, max = maxMana, of = overflow;
        long now = System.currentTimeMillis();
        boolean blinkOn = cfg().vanillaManaBarBlink;
        if (blinkOn && lastMana + lastOverflow > value + of && value != max) {
            if (blinkEnd <= now) {
                manaBlinkStart = lastMana;
                overflowBlinkStart = lastOverflow;
            }
            blinkEnd = blinkEnd >= now ? ((now + 1000) / 300 * 300) + blinkEnd % 300 : now + 1000;
        }
        boolean blinking = blinkOn && blinkEnd > now && (blinkEnd - now) / 150 % 2 == 1;
        if (blinkEnd <= now) {
            manaBlinkStart = 0;
            overflowBlinkStart = 0;
        }
        lastMana = value;
        lastOverflow = of;

        int manaHalf = (int) ((long) value * 40 / max), manaN = (manaHalf + 1) / 2;
        int manaBHalf = (int) ((long) manaBlinkStart * 40 / max), manaBN = (manaBHalf + 1) / 2;
        int ofHalf = (int) ((long) of * 40 / max), ofN = (ofHalf + 1) / 2;
        int ofBHalf = (int) ((long) overflowBlinkStart * 40 / max), ofBN = (ofBHalf + 1) / 2;
        boolean hunger = cfg().vanillaManaBarHungerSprites;

        for (int i = 0; i < 20; i++) {
            int row = i / 10, col = i % 10;
            boolean mN = i < manaN, mHalf = mN && manaN - 1 == i && manaHalf % 2 == 1;
            boolean mbN = i < manaBN, mbHalf = mbN && manaBN - 1 == i && manaBHalf % 2 == 1;
            boolean oN = i < ofN, oHalf = oN && ofN - 1 == i && ofHalf % 2 == 1;
            boolean obN = i < ofBN, obHalf = obN && ofBN - 1 == i && ofBHalf % 2 == 1;

            notch(g, hunger, col, row, Notch.CONTAINER, false, blinking);
            if (manaN > 0) {
                if (oN) notch(g, hunger, col, row, Notch.OVERFLOW_DARK, oHalf, blinking);
                if (mbN && blinking) notch(g, hunger, col, row, Notch.MANA, mbHalf, true);
                if (mN) notch(g, hunger, col, row, Notch.MANA, mHalf, false);
            } else {
                if (mbN && blinking) notch(g, hunger, col, row, Notch.MANA, mbHalf, true);
                if (obN && blinking) notch(g, hunger, col, row, Notch.OVERFLOW, obHalf, true);
                if (oN) notch(g, hunger, col, row, Notch.OVERFLOW, oHalf, false);
            }
        }
    }

    private static void notch(GuiGraphicsExtractor g, boolean hunger, int col, int row, Notch type, boolean half, boolean blink) {
        int top = g.guiHeight() - 39;
        int right = g.guiWidth() / 2 + 91;
        Identifier tex = hunger ? switch (type) {
            case CONTAINER -> FOOD_EMPTY;
            case MANA -> half ? FOOD_HALF : FOOD_FULL;
            case OVERFLOW -> half ? FOOD_HALF_HUNGER : FOOD_FULL_HUNGER;
            case OVERFLOW_DARK -> FOOD_EMPTY_HUNGER;
        } : switch (type) {
            case CONTAINER -> blink ? CONTAINER_BLINK : CONTAINER;
            case MANA -> half ? (blink ? MANA_HALF_BLINK : MANA_HALF) : (blink ? MANA_FULL_BLINK : MANA_FULL);
            case OVERFLOW -> half ? (blink ? OF_HALF_BLINK : OF_HALF) : (blink ? OF_FULL_BLINK : OF_FULL);
            case OVERFLOW_DARK -> half ? (blink ? OFD_HALF_BLINK : OFD_HALF) : (blink ? OFD_FULL_BLINK : OFD_FULL);
        };
        g.blitSprite(RenderPipelines.GUI_TEXTURED, tex, right - col * 8 - 9, top - row * 10, 9, 9);
    }
}
