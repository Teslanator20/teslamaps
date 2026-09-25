package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** One gate for every text drawn onto a slot: display mode, hold/toggle key, per-source switches. */
public class SlotText {

    public static final String[] MODES = {"Always", "Hold To Show", "Press To Toggle", "Hold To Hide", "Off"};

    public static final String ENCHANT = "Enchant Books", SKILL = "Skill Level", DUNGEON = "Dungeon Levels",
            HOTF_LEVEL = "HotF Perk Level", HOTF_TOKENS = "HotF Tokens", SHARD = "Shard Tier",
            EVOLVING = "Evolving Bonus", MENU = "Menu Tips", ACCESSORY = "Accessory Status",
            PARTY_FINDER = "Party Finder";
    public static final List<String> SOURCES = List.of(ENCHANT, SKILL, DUNGEON, HOTF_LEVEL, HOTF_TOKENS,
            SHARD, EVOLVING, MENU, ACCESSORY, PARTY_FINDER);

    private static boolean held, toggled;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlotText")
                .section("Inventory")
                .when(() -> cfg().slotTextControl)
                .tickAlways(SlotText::tick)
                .config("Inventory", b -> {
            TeslaMapsConfig c = cfg();
            b.label("Slot Text");
            b.toggle("Slot Text Control", () -> c.slotTextControl, v -> c.slotTextControl = v)
                    .desc("One mode and key for all text drawn on slots (book names, levels, tiers)");
            b.dropdown("  Mode", MODES, () -> c.slotTextMode, v -> c.slotTextMode = v);
            b.keybind("  Slot Text Key", () -> c.slotTextKey, v -> c.slotTextKey = v);
            b.searchList("  Shown Sources", () -> SOURCES, s -> !c.slotTextDisabled.contains(s),
                    s -> { if (!c.slotTextDisabled.remove(s)) c.slotTextDisabled.add(s); });
        });
    }

    private static void tick() {
        boolean down = KeyNames.isDown(cfg().slotTextKey);
        if (down && !held) toggled = !toggled;
        held = down;
    }

    /** False while the mode or the source switch hides this source. Always true with control off. */
    public static boolean shown(String source) {
        TeslaMapsConfig c = cfg();
        if (!c.slotTextControl || !c.section("Inventory")) return true;
        if (c.slotTextDisabled.contains(source)) return false;
        return switch (c.slotTextMode) {
            case "Hold To Show" -> held;
            case "Press To Toggle" -> toggled;
            case "Hold To Hide" -> !held;
            case "Off" -> false;
            default -> true;
        };
    }

    /** Corner text on a 16px item at (x, y), shrunk to fit. corner: 0 TL, 1 TR, 2 BL, 3 BR. */
    public static void draw(GuiGraphicsExtractor ctx, Font font, String text, int x, int y, int corner, int color) {
        int w = font.width(text);
        float s = w > 16 ? 16f / w : 1f;
        float tx = (corner & 1) == 1 ? 17 - w * s : 0;
        float ty = corner >= 2 ? 17 - 8 * s : 0;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + tx, y + ty);
        ctx.pose().scale(s, s);
        ctx.text(font, text, 0, 0, color, true);
        ctx.pose().popMatrix();
    }
}
