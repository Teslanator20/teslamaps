package com.teslamaps.features.pets;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Session leveling of the main pet and the exp share pets: levels gained, lvl/h, xp/h, time to max.
 * Its HUD replaces the plain Pet HUD while it is on.
 */
public final class PetLevelTracker {

    private PetLevelTracker() {}

    private static final class Track {
        final String key;
        final boolean main;
        String type, rarity, name;
        int startLevel, level;
        double startXp, xp;
        int share = -1;       // PetStore exp share slot, -1 = main or not stored
        boolean startExact;   // startXp came from a real xp read, not the level floor
        double combatSince;   // combat xp since the last level-up
        long levelAt;         // when the last level-up came, 0 = none yet (first level is partial)
        double lastCombatPerLevel, combatSum;
        int fullLevels;

        Track(String key, boolean main) {
            this.key = key;
            this.main = main;
        }
    }

    /** Text row, or a progress bar when bar >= 0. */
    private record Row(String text, float bar, int color) {
        static Row text(String t) { return new Row(t, -1, 0); }
    }
    private static final long IDLE_MS = 300_000;
    private static final int MAIN_BAR = 0xFFFFAA00, SHARE_BAR = 0xFFFF55FF, MAX_BAR = 0xFF55FF55;

    private static final Map<String, Track> tracks = new LinkedHashMap<>();
    private static long activeMs, lastTick, lastProgress;
    private static double lastMainXp = -1;
    private static List<Row> lines = List.of();
    private static int linesW = 100;
    private static int ticks;
    private static HudEditElement hud;
    private static final List<Row> SAMPLE = List.of(
            Row.text("§6§lPet Leveling §7(1h 12m) §8xp ×0.97"),
            Row.text("§eMain §7[Lvl 187] §6Golden Dragon §854.0%"),
            new Row("", 0.54f, MAIN_BAR),
            Row.text(" §a+44 lvl §8| §b36.7/h §8| §b2.1M xp/h §8| §7200 in §b21m"),
            Row.text("§dShare §7[Lvl 74] §6Golden Dragon Egg §812.0%"),
            new Row("", 0.12f, SHARE_BAR),
            Row.text(" §a+66 lvl §8| §b55.0/h §8| §b310k xp/h §8| §7200 in §b3h 5m"));

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final java.nio.file.Path LOG = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("pet_level_log.csv");

    /** Combat xp from the action bar (exact while Combat is maxed), counted per tracked pet. */
    static void onSkillXp(String skill, double amount) {
        if (!cfg().petLevelTracker || !"Combat".equalsIgnoreCase(skill) || amount <= 0) return;
        for (Track t : tracks.values()) {
            if (t.main ? t.key.equals(mainKey()) : true) t.combatSince += amount;
        }
    }

    private static String mainKey() {
        return PetTracker.internalName() == null ? "" : "M|" + PetTracker.internalName() + "|" + PetTracker.rarity();
    }

    /** One full level done: remember combat xp for it and append a csv line. */
    private static void levelDone(Track t, int level) {
        long now = System.currentTimeMillis();
        if (t.levelAt > 0 && t.combatSince > 0) {
            t.lastCombatPerLevel = t.combatSince;
            t.combatSum += t.combatSince;
            t.fullLevels++;
            long secs = (now - t.levelAt) / 1000;
            String line = java.time.LocalDateTime.now().withNano(0) + "," + t.type + "," + (t.main ? "main" : "share")
                    + "," + (level - 1) + "->" + level + "," + Math.round(t.combatSince) + "," + secs + "\n";
            try {
                java.nio.file.Files.createDirectories(LOG.getParent());
                if (!java.nio.file.Files.exists(LOG)) {
                    java.nio.file.Files.writeString(LOG, "time,pet,role,level,combat_xp,seconds\n");
                }
                java.nio.file.Files.writeString(LOG, line, java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException e) {
                com.teslamaps.TeslaMaps.LOGGER.warn("pet level log: {}", e.toString());
            }
        }
        t.combatSince = 0;
        t.levelAt = now;
    }

    public static void register() {
        hud = HudEditElement.of("petLevelTracker", "Pet Leveling").section("Skills")
                .enabled(() -> cfg().petLevelTracker)
                .size(() -> lines.isEmpty() ? width(SAMPLE) : linesW,
                        () -> height(lines.isEmpty() ? SAMPLE : lines))
                .preview((g, mc, x, y, sc) -> {
                    List<Row> l = lines.isEmpty() ? SAMPLE : lines;
                    draw(g, mc, x, y, sc, l, width(l));
                });
        Feature.of("PetLevelTracker").section("Skills").when(() -> cfg().petLevelTracker)
                .tick(PetLevelTracker::tick)
                .hudHiddenInInventory("petleveltracker", PetLevelTracker::render)
                .config("Skills", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Pet Leveling");
                    b.toggle("Pet Leveling HUD", () -> c.petLevelTracker, v -> c.petLevelTracker = v)
                            .desc("Main pet and exp share pets: level, progress bar, levels gained this session, levels/h, xp/h "
                                    + "and time to max level. Replaces the plain Pet HUD while on. Open /pets > Exp Sharing once "
                                    + "so the share pets are known. Reset: /tmap petlevel reset")
                            .tags("pet", "golden dragon", "gdrag", "exp share", "level", "tracker");
                    b.toggle("  Progress Bars", () -> c.petLevelTrackerBars, v -> c.petLevelTrackerBars = v)
                            .desc("Level progress bar under each pet whose xp is known");
                    b.toggle("  Level 100 / Max Alert", () -> c.petLevelMilestone, v -> c.petLevelMilestone = v)
                            .desc("Title + sound when the main or an exp share pet reaches level 100 or its max level");
                });
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("petlevel")
                .then(ClientCommands.literal("reset").executes(c -> {
                    reset();
                    c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fPet leveling session reset"));
                    return 1;
                }));
    }

    static void reset() {
        tracks.clear();
        activeMs = 0;
        lastProgress = 0;
        lastMainXp = -1;
        lines = List.of();
    }

    /** Stored exp share pet of this type that a level-up line to newLevel most likely belongs to. */
    private static int shareSlotFor(String type, int newLevel) {
        PetStore.Pet[] slots = PetStore.expShareSlots();
        int below = -1, any = -1;
        for (int i = 0; i < slots.length; i++) {
            PetStore.Pet p = slots[i];
            if (p == null || !type.equals(p.type)) continue;
            if (any < 0) any = i;
            if (p.level < newLevel && (below < 0 || p.level > slots[below].level)) below = i;
        }
        return below >= 0 ? below : any;
    }

    /** Whether a "leveled up to level N" line of the main pet's type belongs to the main pet. */
    static boolean isMain(String type, int mainLevel, int newLevel) {
        if (mainLevel > 0 && newLevel == mainLevel + 1) return true;
        int slot = shareSlotFor(type, newLevel);
        PetStore.Pet share = slot < 0 ? null : PetStore.expShareSlots()[slot];
        int dShare = share != null && share.level < newLevel ? newLevel - share.level : Integer.MAX_VALUE;
        if (dShare == 1) return false;
        if (mainLevel <= 0) return share == null;            // main level unknown yet
        if (newLevel == mainLevel) return true;                // tab widget was faster than chat
        int dMain = newLevel > mainLevel ? newLevel - mainLevel : Integer.MAX_VALUE;
        return dMain <= dShare && dMain != Integer.MAX_VALUE;
    }

    static void onLevelUp(String name, String type, int level, boolean main) {
        if (!cfg().petLevelTracker || !cfg().section("Skills")) return;
        lastProgress = System.currentTimeMillis();
        milestone(name, type, level, main);
        if (main) {
            Track t = mainTrack();
            if (t != null) {
                t.level = level;
                levelDone(t, level);
            }
            return;
        }
        int slot = shareSlotFor(type, level);
        if (slot >= 0) {
            PetStore.Pet p = PetStore.expShareSlots()[slot];
            PetTracker.shareLevelUp(p, level);
            PetStore.setLevel(p, level);
            Track t = shareTrack(slot, p);
            t.level = level;
            t.xp = p.exp;
            levelDone(t, level);
            return;
        }
        // no share pet of this type known (Exp Sharing menu never opened): one track per type from chat alone
        String key = "C|" + type;
        Track t = tracks.get(key);
        if (t == null) {
            t = new Track(key, false);
            t.type = type;
            t.rarity = type.equals(PetTracker.internalName()) && PetTracker.rarity() != null ? PetTracker.rarity() : "LEGENDARY";
            t.startLevel = level - 1;
            t.startXp = PetConstants.levelToXp(type, t.rarity, level - 1);
            tracks.put(key, t);
        }
        t.name = name.trim();
        t.level = level;
        t.xp = PetConstants.levelToXp(type, t.rarity, level);
        levelDone(t, level);
    }

    private static void milestone(String name, String type, int level, boolean main) {
        if (!cfg().petLevelMilestone) return;
        int max = PetConstants.maxLevel(type);
        if (level != max && !(level == 100 && max > 100)) return;
        if (level == max && cfg().petMaxedTitle) return; // PetTitles already shows it
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTitle(Component.literal(level == max ? "§a§lMAX LEVEL!" : "§6§lLEVEL 100!"));
        mc.gui.hud.setSubtitle(Component.literal((main ? "§e" : "§d") + name.trim() + " §7" + (main ? "(main)" : "(exp share)")));
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f));
    }

    private static Track mainTrack() {
        // wait for a known level and rarity, else the session starts at "+187 lvl" or on the wrong xp table
        if (!PetTracker.hasPet() || PetTracker.internalName() == null || PetTracker.level() <= 0
                || PetTracker.rarity() == null) return null;
        String key = "M|" + PetTracker.internalName() + "|" + PetTracker.rarity();
        Track t = tracks.get(key);
        if (t == null) {
            t = new Track(key, true);
            t.type = PetTracker.internalName();
            t.rarity = PetTracker.rarity();
            t.startLevel = PetTracker.level();
            t.startXp = mainXp();
            t.startExact = PetTracker.totalXp() >= 0;
            t.level = PetTracker.level();
            tracks.put(key, t);
        } else if (!t.startExact && PetTracker.totalXp() >= 0 && PetTracker.level() == t.startLevel) {
            // first real xp read before any level-up: start from it, not from the level floor
            t.startXp = PetTracker.totalXp();
            t.startExact = true;
        }
        t.name = PetTracker.displayName();
        if (PetTracker.level() > t.level) t.level = PetTracker.level();
        t.xp = Math.max(t.xp, mainXp());
        return t;
    }

    private static double mainXp() {
        double xp = PetTracker.totalXp();
        return xp >= 0 ? xp : PetConstants.levelToXp(PetTracker.internalName(), PetTracker.rarity(), PetTracker.level());
    }

    private static Track shareTrack(int slot, PetStore.Pet p) {
        String uuid = PetStore.shareUuid(slot);
        String key = "S|" + (uuid == null ? slot : uuid);
        Track t = tracks.get(key);
        if (t == null) {
            t = new Track(key, false);
            t.type = p.type;
            t.rarity = p.rarity;
            t.startLevel = p.level;
            t.startXp = p.exp;
            t.level = p.level;
            t.xp = p.exp;
            tracks.put(key, t);
        }
        t.share = slot;
        t.name = PetConstants.displayName(p.type);
        return t;
    }

    private static void tick() {
        if (++ticks < 20) return;
        ticks = 0;
        long now = System.currentTimeMillis();
        Track main = mainTrack();
        if (main != null) {
            double xp = mainXp();
            if (lastMainXp >= 0 && xp > lastMainXp + 0.5) lastProgress = now;
            lastMainXp = xp;
        }
        PetStore.Pet[] slots = PetStore.expShareSlots();
        int active = com.teslamaps.api.ElectionApi.hasPerk("Sharing is Caring") ? 3 : 1;
        List<Track> share = new ArrayList<>();
        java.util.Set<String> storedTypes = new java.util.HashSet<>();
        for (int i = 0; i < slots.length && i < active; i++) {
            PetStore.Pet p = slots[i];
            if (p == null || p.type == null) continue;
            Track t = shareTrack(i, p);
            t.level = Math.max(t.level, p.level);
            t.xp = p.exp;
            share.add(t);
            storedTypes.add(p.type);
        }
        for (Track t : tracks.values()) {
            if (!t.main && t.share < 0 && !storedTypes.contains(t.type)) share.add(t);
        }

        if (lastTick > 0 && lastProgress > 0 && now - lastProgress < IDLE_MS) activeMs += now - lastTick;
        lastTick = now;
        double hours = activeMs / 3_600_000.0;

        List<Row> out = new ArrayList<>();
        out.add(Row.text("§6§lPet Leveling §7(" + duration(activeMs) + ")" + calibText()));
        if (main != null) addPet(out, main, "§eMain", hours, true);
        for (Track t : share) addPet(out, t, "§dShare", hours, t.share >= 0);
        if (share.isEmpty()) out.add(Row.text("§8No exp share pets known §7(/pets > Exp Sharing)"));
        lines = out;
        linesW = width(out);
    }

    private static void addPet(List<Row> out, Track t, String label, double hours, boolean exact) {
        int max = PetConstants.maxLevel(t.type);
        String head = label + " §7[Lvl " + t.level + "] " + rarityCode(t.rarity) + t.name;
        boolean maxed = t.level >= max;
        if (maxed) head += " §a§lMAX";
        else if (exact) head += " §8" + String.format(Locale.ROOT, "%.1f", progress(t) * 100) + "%";
        else head += " §8(open /pets > Exp Sharing)";
        out.add(Row.text(head));
        if ((exact || maxed) && cfg().petLevelTrackerBars) {
            out.add(new Row("", maxed ? 1f : progress(t), maxed ? MAX_BAR : t.main ? MAIN_BAR : SHARE_BAR));
        }
        out.add(Row.text(" " + stats(t, hours, true)));
        if (t.fullLevels > 0) {
            out.add(Row.text(" §7Combat XP/lvl: §b" + shortFormat(t.lastCombatPerLevel)
                    + " §8(avg §7" + shortFormat(t.combatSum / t.fullLevels) + "§8, " + t.fullLevels + " lvls)"));
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<Row> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l, linesW);
    }

    private static int width(List<Row> rows) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (Row r : rows) if (r.bar < 0) w = Math.max(w, mc.font.width(r.text));
        return w + 8;
    }

    private static int height(List<Row> rows) {
        int h = 6;
        for (Row r : rows) h += r.bar < 0 ? 10 : 5;
        return h;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<Row> rows, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, height(rows));
        int ry = 4;
        for (Row r : rows) {
            if (r.bar < 0) {
                g.text(mc.font, r.text, 4, ry, 0xFFFFFFFF, true);
                ry += 10;
            } else {
                g.fill(4, ry - 1, w - 4, ry + 2, 0xFF2A2A2A);
                g.fill(4, ry - 1, 4 + Math.round((w - 8) * r.bar), ry + 2, r.color);
                ry += 5;
            }
        }
        g.pose().popMatrix();
    }

    private static float progress(Track t) {
        double cost = PetConstants.costForLevel(t.type, t.rarity, t.level);
        if (cost <= 0) return 1;
        double into = t.xp - PetConstants.levelToXp(t.type, t.rarity, t.level);
        return (float) Math.max(0, Math.min(1, into / cost));
    }

    private static String stats(Track t, double hours, boolean xpRate) {
        int max = PetConstants.maxLevel(t.type);
        int gained = t.level - t.startLevel;
        StringBuilder sb = new StringBuilder("§a+").append(gained).append(" lvl");
        if (hours > 0.01) {
            sb.append(" §8| §b").append(String.format(Locale.ROOT, "%.1f", gained / hours)).append("/h");
            double rate = Math.max(0, t.xp - t.startXp) / hours;
            if (rate > 0) {
                if (xpRate) sb.append(" §8| §b").append(shortFormat(rate)).append(" xp/h");
                if (t.level < max) {
                    double left = PetConstants.levelToXp(t.type, t.rarity, max) - t.xp;
                    sb.append(" §8| §7").append(max).append(" in §b").append(duration((long) (left / rate * 3_600_000)));
                }
            }
        }
        return sb.toString();
    }

    /** Estimate correction learned from level-ups, shown once it has a sample. */
    private static String calibText() {
        StringBuilder sb = new StringBuilder();
        if (PetTracker.calibSamples() > 0) {
            sb.append(" §8xp ×").append(String.format(Locale.ROOT, "%.2f", PetTracker.calib()));
        }
        if (PetTracker.shareCalibSamples() > 0) {
            sb.append(" §8share ×").append(String.format(Locale.ROOT, "%.2f", PetTracker.shareCalib()));
        }
        return sb.toString();
    }

    private static String duration(long ms) {
        long mins = ms / 60_000;
        if (mins < 60) return mins + "m";
        long h = mins / 60;
        if (h < 48) return h + "h " + (mins % 60) + "m";
        return (h / 24) + "d " + (h % 24) + "h";
    }

    private static String shortFormat(double v) {
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.ROOT, "%.0fk", v / 1_000);
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String rarityCode(String rarity) {
        if (rarity == null) return "§7";
        return switch (rarity) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§7";
        };
    }
}
