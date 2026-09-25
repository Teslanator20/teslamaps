package com.teslamaps.features.mining;

// Ported from SkyOcean's Scathas.kt / SpawnedWorm.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Worm / Scatha spawn title, ping while it lives, 30 s spawn cooldown and a nicer Scatha pet drop line. */
public final class ScathaAlerts {

    private static final String WORM_TEXTURE = "df03ad96092f3f789902436709cdf69de6b727c121b3c2daef9ffa1ccaed186c";
    private static final String SPAWN = "You hear the sound of something approaching...";
    private static final Pattern PET_DROP = Pattern.compile("PET DROP! Scatha(?: \\(\\+([\\d,]+)%? ?✯ Magic Find\\))?");
    private static final long SPAWN_WINDOW_MS = 10_000, COOLDOWN_MS = 30_000;

    private static Feature feature;
    private static long lastSpawn;
    private static boolean cooldown;
    private static ArmorStand worm;
    private static final Set<UUID> announced = new HashSet<>();
    private static int plings;
    private static Object lastLevel;
    private static int ticks;

    private ScathaAlerts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ScathaAlerts").section("Mining").when(() -> cfg().scathaAlerts)
                .tick(ScathaAlerts::tick)
                .serverChat(ScathaAlerts::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Scatha Alerts", () -> c.scathaAlerts, v -> c.scathaAlerts = v)
                            .desc("Crystal Hollows worm farming: spawn title (Worm or Scatha), 30 s spawn cooldown and Scatha pet drop.")
                            .tags("scatha", "worm", "crystal hollows", "pet drop", "cooldown");
                    b.toggle("  Spawn Title", () -> c.scathaWormTitle, v -> c.scathaWormTitle = v);
                    b.toggle("  Ping While Alive", () -> c.scathaAliveSound, v -> c.scathaAliveSound = v)
                            .desc("XP sound every 5 ticks while the worm lives.");
                    b.toggle("  Cooldown Over Title", () -> c.scathaCooldownTitle, v -> c.scathaCooldownTitle = v);
                    b.toggle("  Rewrite Pet Drop", () -> c.scathaPetRewrite, v -> c.scathaPetRewrite = v)
                            .desc("Adds the rarity to the Scatha PET DROP line.");
                    b.toggle("  Pet Drop Title", () -> c.scathaPetTitle, v -> c.scathaPetTitle = v);
                });
    }

    private static boolean inHollows() {
        return SkyblockUtils.isInArea("Crystal Hollows");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worm = null;
            cooldown = false;
            announced.clear();
            plings = 0;
        }
        ticks++;
        if (plings > 0 && ticks % 3 == 0) {
            plings--;
            sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f);
        }
        long now = System.currentTimeMillis();
        if (worm != null && (worm.isRemoved() || !worm.isAlive())) worm = null;
        if (worm != null && cfg().scathaAliveSound && ticks % 5 == 0) sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f);

        if (cooldown && now - lastSpawn >= COOLDOWN_MS) {
            cooldown = false;
            if (cfg().scathaCooldownTitle && inHollows()) {
                title("§7Scatha Cooldown Over", "", 20);
                sound(SoundEvents.NOTE_BLOCK_PLING.value(), 2f);
            }
        }

        if (worm != null || now - lastSpawn > SPAWN_WINDOW_MS || !inHollows()) return;
        for (ArmorStand as : com.teslamaps.utils.EntityCache.armorStands()) {
            if (as.isRemoved() || announced.contains(as.getUUID())) continue;
            if (!WORM_TEXTURE.equals(SoulweaverHider.headTextureUrl(as.getItemBySlot(EquipmentSlot.HEAD)))) continue;
            int hp = Math.round(as.getMaxHealth());
            if (hp != 5 && hp != 10) continue;
            announced.add(as.getUUID());
            worm = as;
            if (!cfg().scathaWormTitle) break;
            if (hp == 10) {
                title("§6§lScatha", "§c§lPRAY TO RNGESUS!", 30);
                plings = 5;
            } else {
                title("§f§lWorm", "§7just a worm...", 30);
            }
            break;
        }
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (text.equals(SPAWN)) {
            if (!inHollows()) return;
            lastSpawn = System.currentTimeMillis();
            cooldown = true;
            return;
        }
        if (!cfg().scathaPetTitle || !text.startsWith("PET DROP! Scatha")) return;
        String rarity = rarityFromCodes(raw);
        if (rarity == null) return;
        title("§6Scatha Pet!", rarityCode(rarity) + "§l" + rarity, 60);
        sound(SoundEvents.ANVIL_LAND, 2f);
    }

    /** From ChatMixin's rewrite chain. */
    public static Component rewrite(Component message) {
        if (feature == null || !cfg().scathaAlerts || !cfg().scathaPetRewrite || !feature.active()) return message;
        String raw = message.getString();
        if (!raw.contains("PET DROP!") || !raw.contains("Scatha")) return message;
        Matcher m = PET_DROP.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return message;
        String rarity = rarityFromCodes(raw);
        if (rarity == null) rarity = rarityFromStyle(message);
        if (rarity == null) return message;
        String code = rarityCode(rarity);
        String line = "§6§lPET DROP! " + code + "§l" + rarity + " " + code + "Scatha";
        if (m.group(1) != null) line += " §b(+" + m.group(1) + "% ✯ Magic Find)";
        return Component.literal(line);
    }

    private static String rarityFromCodes(String raw) {
        int i = raw.indexOf("Scatha");
        if (i < 0) return null;
        for (int j = i - 1; j > 0; j--) {
            if (raw.charAt(j - 1) != '§') continue;
            ChatFormatting f = ChatFormatting.getByCode(raw.charAt(j));
            if (f == null || !f.isColor()) continue;
            return rarityOf(f);
        }
        return null;
    }

    private static String rarityFromStyle(Component message) {
        Optional<String> r = message.visit((Style style, String part) -> {
            if (!part.contains("Scatha")) return Optional.empty();
            TextColor c = style.getColor();
            if (c == null) return Optional.empty();
            for (ChatFormatting f : ChatFormatting.values()) {
                if (f.isColor() && f.getColor() != null && f.getColor() == c.getValue()) {
                    String name = rarityOf(f);
                    return name == null ? Optional.empty() : Optional.of(name);
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return r.orElse(null);
    }

    private static String rarityOf(ChatFormatting f) {
        return switch (f) {
            case WHITE -> "COMMON";
            case GREEN -> "UNCOMMON";
            case BLUE -> "RARE";
            case DARK_PURPLE -> "EPIC";
            case GOLD -> "LEGENDARY";
            default -> null;
        };
    }

    private static String rarityCode(String rarity) {
        return switch (rarity) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            default -> "§6";
        };
    }

    private static void title(String title, String sub, int stay) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, stay, 5);
        mc.gui.setTitle(Component.literal(title));
        mc.gui.setSubtitle(Component.literal(sub));
    }

    private static void sound(SoundEvent ev, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, 1f));
    }
}
