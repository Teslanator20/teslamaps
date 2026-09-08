package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Throws the vanilla item-activation animation (the totem pop) plus a particle
 * burst when a rare drop lands: slayer/fishing drops, dungeon chest rewards and
 * dyes. Only the drops worth looking up from your screen for, not every line
 * Hypixel prints "RARE DROP!" on.
 */
public class RareDropEffects {

    private static final Pattern RARE_DROP = Pattern.compile(
            "^\\s*(?!.*:)(?<tier>RARE|VERY RARE|CRAZY RARE|INSANE) DROP!\\s+(?<item>.+?)(?:\\s+\\(\\+\\d+%? \\u272F Magic Find\\))?$");
    private static final Pattern DUNGEON_REWARD = Pattern.compile(
            "^\\s{3,}(?!.*:)(?:RARE REWARD!\\s+)?(?<item>.+)$");
    private static final Pattern DYE_DROP = Pattern.compile(
            "WOW! (?:\\[[A-Z+]+] )?(?<player>[A-Za-z0-9_]+) found (?:a|an) (?<dye>[A-Za-z ]+ Dye)(?: #[\\d,]+)?!");

    /** Slayer and fishing drops that get the animation. */
    private static final Map<String, String> RARE_DROPS = Map.ofEntries(
            // Zombie
            Map.entry("Scythe Blade", "SCYTHE_BLADE"),
            Map.entry("Shard Of The Shredded", "SHARD_OF_THE_SHREDDED"),
            Map.entry("Severed Hand", "SEVERED_HAND"),
            Map.entry("Warden Heart", "WARDEN_HEART"),
            // Spider
            Map.entry("Shriveled Wasp", "SHRIVELED_WASP"),
            Map.entry("Digested Mosquito", "DIGESTED_MOSQUITO"),
            Map.entry("Ensnared Snail", "ENSNARED_SNAIL"),
            Map.entry("Primordial Eye", "PRIMORDIAL_EYE"),
            // Wolf
            Map.entry("Overflux Capacitor", "OVERFLUX_CAPACITOR"),
            // Enderman
            Map.entry("End Stone Idol", "ENDSTONE_IDOL"),
            Map.entry("Judgement Core", "JUDGEMENT_CORE"),
            // Blaze
            Map.entry("High Class Archfiend Dice", "HIGH_CLASS_ARCHFIEND_DICE"),
            // Fishing
            Map.entry("Radioactive Vial", "RADIOACTIVE_VIAL"),
            Map.entry("Tiki Mask", "TIKI_MASK"),
            Map.entry("Titanoboa Shed", "TITANOBOA_SHED"));

    /** Dungeon chest rewards that get the animation. */
    private static final Map<String, String> DUNGEON_DROPS = Map.ofEntries(
            // M3
            Map.entry("First Master Star", "FIRST_MASTER_STAR"),
            // M4
            Map.entry("Second Master Star", "SECOND_MASTER_STAR"),
            // M5
            Map.entry("Third Master Star", "THIRD_MASTER_STAR"),
            Map.entry("Shadow Fury", "SHADOW_FURY"),
            // M6
            Map.entry("Giant's Sword", "GIANTS_SWORD"),
            Map.entry("Fourth Master Star", "FOURTH_MASTER_STAR"),
            // M7
            Map.entry("Necron Dye", "DYE_NECRON"),
            Map.entry("Dark Claymore", "DARK_CLAYMORE"),
            Map.entry("Necron's Handle", "NECRON_HANDLE"),
            Map.entry("Shiny Necron's Handle", "NECRON_HANDLE"),
            Map.entry("Shadow Warp", "SHADOW_WARP_SCROLL"),
            Map.entry("Wither Shield", "WITHER_SHIELD_SCROLL"),
            Map.entry("Implosion", "IMPLOSION_SCROLL"),
            Map.entry("Fifth Master Star", "FIFTH_MASTER_STAR"));

    public static void onChatMessage(String raw) {
        if (!SkyblockUtils.isInSkyblock()) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.rareDropEffects && !config.rareDungeonDropEffects && !config.rareDyeDropEffects) return;

        String text = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");

        try {
            if (config.rareDropEffects) {
                Matcher drop = RARE_DROP.matcher(text);
                if (drop.matches()) {
                    String item = drop.group("item").trim();
                    play(RARE_DROPS.get(item), drop.group("tier") + " DROP!", item, ParticleTypes.SCRAPE);
                    return;
                }
            }

            if (config.rareDungeonDropEffects) {
                Matcher reward = DUNGEON_REWARD.matcher(text);
                if (reward.matches()) {
                    String item = reward.group("item").trim();
                    play(DUNGEON_DROPS.get(item), "RARE REWARD!", item, ParticleTypes.PORTAL);
                    return;
                }
            }

            if (config.rareDyeDropEffects) {
                Matcher dye = DYE_DROP.matcher(text);
                if (dye.find()) {
                    String id = dyeId(dye.group("dye"));
                    if (dye.group("player").equals(Minecraft.getInstance().getUser().getName())) {
                        play(id, "DYE DROP!", dye.group("dye"), ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER);
                    } else {
                        // Someone else's drop: fetch the skull now so your own is instant.
                        SkyblockItems.prefetch(id);
                    }
                }
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("Rare drop effect failed: {}", e.toString());
        }
    }

    /**
     * Plays the effect on demand, for checking what it looks like without waiting
     * on a drop. An empty name walks through the drop tables one entry per call.
     */
    private static final String[] TEST_TIERS = {"RARE", "VERY RARE", "CRAZY RARE", "INSANE"};

    public static String test(String name) {
        // an explicit tier can be put in front of the name, else they cycle
        String tier = null;
        if (name != null) {
            for (String candidate : TEST_TIERS) {
                if (name.toUpperCase(Locale.ROOT).startsWith(candidate + " ")) {
                    tier = candidate;
                    name = name.substring(candidate.length()).trim();
                    break;
                }
            }
        }

        if (name == null || name.isBlank()) {
            java.util.List<String> names = new java.util.ArrayList<>(RARE_DROPS.keySet());
            names.addAll(DUNGEON_DROPS.keySet());
            names.add("Necron Dye");
            name = names.get(Math.floorMod(testIndex, names.size()));
        }
        testIndex++;

        String id = lookup(RARE_DROPS, name);
        ParticleOptions particle = ParticleTypes.SCRAPE;
        String headline = (tier != null ? tier : TEST_TIERS[Math.floorMod(testIndex, TEST_TIERS.length)])
                + " DROP!";

        if (id == null) {
            id = lookup(DUNGEON_DROPS, name);
            particle = ParticleTypes.PORTAL;
            headline = "RARE REWARD!";
        }
        if (id == null && name.toLowerCase(Locale.ROOT).endsWith(" dye")) {
            id = dyeId(name);
            particle = ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER;
            headline = "DYE DROP!";
        }
        // Anything else is taken as a raw skyblock item id, so any item can be previewed.
        if (id == null) id = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "").trim().replace(' ', '_');

        play(id, headline, name, particle);
        return name;
    }

    private static int testIndex = 0;

    private static String lookup(Map<String, String> table, String name) {
        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static void play(String id, String headline, String itemName, ParticleOptions particle) {
        if (id == null) return;
        SkyblockItems.resolve(id, stack -> show(stack, headline, itemName, id, particle));
    }

    /** Hypixel's own drop tiers, in the colours it prints them with. */
    private static int tierColor(String headline) {
        String h = headline.toUpperCase(Locale.ROOT);
        if (h.contains("INSANE")) return 0xFF5555;
        if (h.contains("CRAZY RARE")) return 0xFF55FF;
        if (h.contains("VERY RARE")) return 0xAA00AA;
        if (h.contains("PET DROP") || h.contains("REWARD")) return 0xFFAA00;
        if (h.contains("DYE")) return 0xFF55FF;
        return 0x5555FF;
    }

    private static void show(ItemStack stack, String headline, String itemName, String id, ParticleOptions particle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stack.isEmpty()) return;

        mc.particleEngine.createTrackingEmitter(mc.player, particle, 30);
        mc.gameRenderer.displayItemActivation(stack);

        text = itemName;
        subText = headline.toUpperCase(Locale.ROOT);
        priceText = "";
        subColor = tierColor(headline);
        // the item's own name already carries its rarity colour
        var named = stack.getHoverName().getStyle().getColor();
        textColor = named != null ? named.getValue() : 0xFFFFFF;
        shownAt = System.currentTimeMillis();

        if (TeslaMapsConfig.get().rareDropPrice) {
            // Arrives a moment later and just appends itself to the line that is
            // already on screen.
            ItemPrice.resolve(id, coins -> priceText = " (+" + ItemPrice.format(coins) + ")");
        }
    }

    private static String text = "";
    private static String subText = "";
    private static String priceText = "";
    private static long shownAt = 0L;

    private static int textColor = 0xFFFFFF;
    private static int subColor = 0x5555FF;
    private static final long FADE_IN_MS = 250L;
    private static final long FADE_OUT_MS = 500L;
    private static final long TOTAL_MS =
            net.minecraft.client.renderer.ScreenEffectRenderer.ITEM_ACTIVATION_ANIMATION_LENGTH * 50L;

    /**
     * Same geometry as a vanilla title: item line at 4x ten pixels above the
     * middle, the drop line at 2x below it.
     */
    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().rareDropText) return;

        long age = System.currentTimeMillis() - shownAt;
        if (age < 0 || age > TOTAL_MS) return;

        double fade = 1.0;
        if (age < FADE_IN_MS) fade = age / (double) FADE_IN_MS;
        long left = TOTAL_MS - age;
        if (left < FADE_OUT_MS) fade = Math.min(fade, left / (double) FADE_OUT_MS);

        int alpha = (int) (255 * fade);
        if (alpha <= 8) return;

        Minecraft mc = Minecraft.getInstance();
        var pose = context.pose();

        // Below the item animation but well clear of the health and hotbar rows.
        float cx = context.guiWidth() / 2f;
        float cy = context.guiHeight() / 2f;

        pose.pushMatrix();
        pose.translate(cx, cy + 54);
        pose.scale(2f, 2f);
        context.centeredText(mc.font, subText, 0, 0, (alpha << 24) | subColor);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate(cx, cy + 78);
        pose.scale(3f, 3f);
        context.centeredText(mc.font, text + priceText, 0, 0, (alpha << 24) | textColor);
        pose.popMatrix();
    }

    /** "Necron Dye" -> DYE_NECRON. */
    private static String dyeId(String dyeName) {
        String base = dyeName.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(" dye")) {
            base = base.substring(0, base.length() - 4);
        }
        return "DYE_" + base.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", "").trim().replace(' ', '_');
    }
}
