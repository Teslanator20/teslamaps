package com.teslamaps.features.pets;

// Swap title after NoammAddons' PetDisplay (autoPetTitles) / legitcatmod's PetChangeTitles,
// max-level title after Bax's PetNotification
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;

public final class PetTitles {

    private static final String SAMPLE = "§6Golden Dragon";
    private static String text = null;
    private static long until;
    private static HudEditElement hud;

    private PetTitles() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PetSwapTitle").section("Skills").when(() -> cfg().petSwapTitle)
                .hud("petswaptitle", PetTitles::render);
        hud = HudEditElement.of("petSwapTitle", "Pet Swap Title").section("Skills")
                .enabled(() -> cfg().petSwapTitle)
                .position(() -> x(width(SAMPLE, cfg().petSwapTitleScale)), () -> cfg().petSwapTitleY)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
    }

    private static int width(String s, float scale) {
        return (int) (Minecraft.getInstance().font.width(s) * scale);
    }

    private static int x(int w) {
        int x = cfg().petSwapTitleX;
        return x >= 0 ? x : (Minecraft.getInstance().getWindow().getGuiScaledWidth() - w) / 2;
    }

    /** Raw chat line with § codes, from PetTracker before its own gate. */
    static void onChat(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.petSwapTitle && !c.petMaxedTitle) return;
        String line = raw.trim();
        if (c.petSwapTitle) {
            Matcher m = PetTracker.AUTOPET.matcher(line);
            if (m.find()) {
                swap(m.group("rarity"), m.group("pet"), m.group("skin") != null || m.group("altskin") != null);
                return;
            }
            if (c.petSwapTitleSummon) {
                m = PetTracker.SUMMON.matcher(line);
                if (m.matches()) {
                    swap(m.group("rarity"), m.group("pet"), m.group("skin") != null);
                    return;
                }
            }
        }
        if (c.petMaxedTitle) maxed(line);
    }

    private static void swap(String rarity, String pet, boolean skin) {
        TeslaMapsConfig c = cfg();
        if (c.petSwapTitleDungeonOnly && !com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;
        text = "§" + rarity + pet.trim() + (skin ? " §d✦" : "");
        until = System.currentTimeMillis() + (long) (Math.max(0.5f, c.petSwapTitleSeconds) * 1000);
        if (c.petSwapTitleSound) play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f);
    }

    private static void maxed(String raw) {
        String clean = StringUtil.strip(raw).trim();
        Matcher m = PetTracker.LEVEL_UP.matcher(clean);
        if (!m.matches()) return;
        String pet = m.group("pet").trim();
        int level = Integer.parseInt(m.group("level"));
        if (level < PetConstants.maxLevel(PetConstants.internalName(pet))) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTitle(Component.literal("§a§lPet is Maxed!"));
        mc.gui.hud.setSubtitle(Component.literal(color(raw, pet) + "[" + pet + "]"));
        play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f);
    }

    /** The color code right before the pet name in the raw line. */
    private static String color(String raw, String pet) {
        int at = raw.indexOf(pet);
        for (int i = Math.min(at, raw.length()) - 2; i >= 0; i--) {
            if (raw.charAt(i) != '§') continue;
            char c = Character.toLowerCase(raw.charAt(i + 1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) return "§" + c;
        }
        return "§6";
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 1f));
    }

    private static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        String t = text;
        if (t == null) return;
        if (System.currentTimeMillis() > until) {
            text = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float scale = hud.scale();
        draw(ctx, mc, x(width(t, scale)), cfg().petSwapTitleY, scale, t);
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String t) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(mc.font, t, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
