package com.teslamaps.features.theend;

// Ported from GanKura's DragonHandler / DragonStatusHud / RareDropScanner, live weight from SkyHanni's DragonFeatures.kt,
// drop counters after SkyblockAddons' DragonTracker
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.CropStats;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.ServerTicks;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DragonFight {

    public static final List<String> TYPES = List.of("Protector", "Old", "Unstable", "Young", "Strong", "Wise", "Superior");

    private static final Pattern EYE_PLACED = Pattern.compile("placed a (?:powerful )?Summoning Eye! \\((\\d)/8\\)");
    private static final Pattern EYE_PLACED_8 = Pattern.compile("placed a (?:powerful )?Summoning Eye! Brace yourselves! \\(8/8\\)");
    private static final Pattern EYE_SELF = Pattern.compile("^☬ You placed a (?:powerful )?Summoning Eye!");
    private static final Pattern EYE_RECOVERED = Pattern.compile("^You recovered a (?:powerful )?Summoning Eye!");
    private static final Pattern SPAWN = Pattern.compile("The (?:.*?)(Protector|Old|Unstable|Young|Strong|Wise|Superior) Dragon has spawned!");
    private static final Pattern DOWN = Pattern.compile("^(PROTECTOR|OLD|UNSTABLE|YOUNG|STRONG|WISE|SUPERIOR) DRAGON DOWN!$");
    private static final Pattern DAMAGE = Pattern.compile("^Your Damage: ([\\d,]+)(?: \\(NEW RECORD!\\))? \\(Position #([\\d,]+)\\)");
    private static final Pattern TOP = Pattern.compile("^(1st|2nd|3rd) Damager - (?:.*\\] )?([A-Za-z0-9_]+) - ([\\d,]+)");
    private static final Pattern TAB_EYES = Pattern.compile("Eyes placed: (\\d)/8");
    private static final Pattern TAB_TYPE = Pattern.compile("Dragon: \\((.+)\\)");
    private static final Pattern TAB_DAMAGE = Pattern.compile("^(\\w+): ([\\d.]+[kKmMbB]?)❤$");
    private static final Pattern SB_DAMAGE = Pattern.compile("^Your Damage: ([\\d.,kKmMbB]+)");

    private static final int LQ_LEGENDARY = 450;
    private static final int LQ_EPIC = 449;
    private static final int LOOT_SCAN_TICKS = 1200;

    private static String eggState = "Scanning";
    private static int eyes, playerEyes;
    private static String type;
    private static long spawnTargetTicks, fightStartTicks, fightEndTicks;
    private static String top1Name, top2Name, top3Name;
    private static long top1Damage, top2Damage, top3Damage;
    private static long lastChat;
    private static double liveDamage, liveTopDamage;
    private static int livePlace;
    private static int lootScanTicks;
    private static boolean lootScanning, petFound, aotdFound;

    private static List<String> statusLines = List.of();
    private static List<String> dropLines = List.of();
    private static int statusW, dropW;
    private static Object lastLevel;
    private static int ticks;
    private static HudEditElement statusHud, dropHud;

    private DragonFight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        statusHud = HudEditElement.of("dragonStatus", "Dragon Status").section("The End").ctx(HudEditElement.HudCtx.THE_END)
                .enabled(() -> cfg().endProtector && cfg().dragonTracker && cfg().dragonStatusHud)
                .size(() -> Math.max(statusW, 110), () -> CropStats.linesHeight(Math.max(statusLines.size(), 5)))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, sampleStatus(), 130));
        dropHud = HudEditElement.of("dragonDrops", "Dragon Drops").section("The End").ctx(HudEditElement.HudCtx.THE_END)
                .enabled(() -> cfg().endProtector && cfg().dragonTracker && cfg().dragonDropTracker)
                .size(() -> Math.max(dropW, 110), () -> CropStats.linesHeight(Math.max(dropLines.size(), 9)))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, buildDropLines(), Math.max(dropW, 130)));
        Feature.of("DragonFight").section("The End").when(() -> cfg().endProtector && cfg().dragonTracker)
                .tick(DragonFight::tick)
                .serverChat(DragonFight::onChat)
                .hudHiddenInInventory("dragonstatus", DragonFight::renderStatus)
                .hudHiddenInInventory("dragondrops", DragonFight::renderDrops);
    }

    /** Rows under the Protector Tracker in TheEndCategory. */
    public static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("  Ender Dragon Tracker", () -> c.dragonTracker, v -> c.dragonTracker = v)
                .desc("Dragon's Nest: egg state, eyes, dragon type, spawn alert, DPS and loot quality after the kill")
                .tags("dragon", "superior", "eyes", "loot quality", "weight");
        b.toggle("    Status HUD", () -> c.dragonStatusHud, v -> c.dragonStatusHud = v)
                .desc("Egg Ready/Hatching (countdown)/Spawned/Respawning, eyes placed (yours) and the type. Position in /tmap gui");
        b.toggle("    Live Weight", () -> c.dragonWeightLine, v -> c.dragonWeightLine = v)
                .desc("Current weight in the status HUD from your eyes, placement (tab Dragon widget) and damage (scoreboard)");
        b.toggle("    Spawn Title", () -> c.dragonSpawnTitle, v -> c.dragonSpawnTitle = v)
                .desc("\"<TYPE> DRAGON!\" when the dragon spawns, only for the types picked below");
        b.searchList("      Alert Types", () -> TYPES, t -> c.dragonAlertTypes.contains(t),
                t -> { if (!c.dragonAlertTypes.remove(t)) c.dragonAlertTypes.add(t); });
        b.toggle("      Superior: Wither Sound", () -> c.dragonSuperiorSound, v -> c.dragonSuperiorSound = v);
        b.toggle("    DPS In Chat", () -> c.dragonDpsChat, v -> c.dragonDpsChat = v)
                .desc("Fight time and your DPS after DRAGON DOWN, hover for the top 3");
        b.toggle("    Loot Quality In Chat", () -> c.dragonLootQualityChat, v -> c.dragonLootQualityChat = v)
                .desc("Placement + 100 x your eyes + damage share; hover shows if a Legendary/Epic Ender Dragon pet was possible");
        b.toggle("    Drop Tracker HUD", () -> c.dragonDropTracker, v -> c.dragonDropTracker = v)
                .desc("Dragons with your eyes: last 3 types, since Superior / AOTD / pet, Ender Dragon pets, AOTDs, eyes placed. Position in /tmap gui")
                .tags("drags since", "pet", "aotd");
        b.toggle("      Drop Alert", () -> c.dragonDropAlert, v -> c.dragonDropAlert = v)
                .desc("\"DROP!\" title and sound when an Ender Dragon pet or AOTD drops for you");
        b.button("      Reset Drop Counts", () -> {
            c.dragonTrackSinceSuperior = 0;
            c.dragonTrackSinceAotd = 0;
            c.dragonTrackSincePet = 0;
            c.dragonTrackEyes = 0;
            c.dragonLegendaryPets = 0;
            c.dragonEpicPets = 0;
            c.dragonAotds = 0;
            c.dragonTrackLast.clear();
            TeslaMapsConfig.save();
        });
    }

    private static void reset() {
        eggState = "Scanning";
        eyes = playerEyes = 0;
        type = null;
        spawnTargetTicks = fightStartTicks = fightEndTicks = 0;
        top1Name = top2Name = top3Name = null;
        top1Damage = top2Damage = top3Damage = 0;
        liveDamage = liveTopDamage = 0;
        livePlace = 0;
        lootScanning = false;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return ServerTicks.now(mc.level != null ? mc.level.getGameTime() : 0);
    }

    private static void onChat(String raw) {
        if (!EndProtector.inArea()) return;
        String msg = StringUtil.stripTrim(raw);
        if (msg.isEmpty()) return;
        TeslaMapsConfig c = cfg();

        if (msg.contains("Summoning Eye!")) {
            Matcher m = EYE_PLACED.matcher(msg);
            if (m.find()) {
                eyes = Integer.parseInt(m.group(1));
                eggState = "Ready";
                if (EYE_SELF.matcher(msg).find()) selfEye(c, 1);
                lastChat = System.currentTimeMillis();
                return;
            }
            if (EYE_PLACED_8.matcher(msg).find()) {
                eyes = 8;
                eggState = "Hatching";
                if (EYE_SELF.matcher(msg).find()) selfEye(c, 1);
                spawnTargetTicks = now() + 180;
                lastChat = System.currentTimeMillis();
                return;
            }
            if (EYE_RECOVERED.matcher(msg).find()) {
                if (playerEyes > 0) selfEye(c, -1);
                if (eyes > 0) eyes--;
                eggState = "Ready";
                lastChat = System.currentTimeMillis();
                return;
            }
        }

        if (msg.endsWith("Dragon has spawned!")) {
            Matcher m = SPAWN.matcher(msg);
            if (m.find()) {
                String t = m.group(1);
                eggState = "Hatched";
                type = t;
                eyes = 8;
                spawnTargetTicks = 0;
                fightStartTicks = now();
                fightEndTicks = 0;
                top1Name = top2Name = top3Name = null;
                top1Damage = top2Damage = top3Damage = 0;
                liveDamage = liveTopDamage = 0;
                livePlace = 0;
                alert(c, t);
                lastChat = System.currentTimeMillis();
            }
            return;
        }

        if (msg.endsWith("DRAGON DOWN!")) {
            Matcher m = DOWN.matcher(msg);
            if (!m.matches()) return;
            String t = m.group(1).charAt(0) + m.group(1).substring(1).toLowerCase(Locale.ROOT);
            eggState = "Respawning";
            spawnTargetTicks = 0;
            type = null;
            eyes = 0;
            fightEndTicks = now();
            lastChat = System.currentTimeMillis();
            if (playerEyes > 0) countKill(c, t);
            lootScanning = true;
            lootScanTicks = 0;
            petFound = aotdFound = false;
            return;
        }

        if (fightEndTicks > 0 && now() - fightEndTicks < 400) {
            Matcher top = TOP.matcher(msg);
            if (top.find()) {
                try {
                    String name = top.group(2);
                    long dmg = Long.parseLong(top.group(3).replace(",", ""));
                    switch (top.group(1)) {
                        case "1st" -> { top1Name = name; top1Damage = dmg; }
                        case "2nd" -> { top2Name = name; top2Damage = dmg; }
                        default -> { top3Name = name; top3Damage = dmg; }
                    }
                } catch (NumberFormatException ignored) {}
                return;
            }
            Matcher dmg = DAMAGE.matcher(msg);
            if (dmg.find()) {
                report(dmg);
                return;
            }
        }

        if (msg.contains("The Dragon Egg has spawned!")) {
            eggState = "Ready";
            type = null;
            eyes = 0;
            playerEyes = 0;
            spawnTargetTicks = 0;
            lastChat = System.currentTimeMillis();
        }
    }

    private static void selfEye(TeslaMapsConfig c, int d) {
        playerEyes = Math.max(0, playerEyes + d);
        if (c.dragonDropTracker) {
            c.dragonTrackEyes = Math.max(0, c.dragonTrackEyes + d);
            TeslaMapsConfig.save();
        }
    }

    private static void alert(TeslaMapsConfig c, String t) {
        if (!c.dragonSpawnTitle || !c.dragonAlertTypes.contains(t)) return;
        Minecraft mc = Minecraft.getInstance();
        MutableComponent title = Component.literal(t.toUpperCase(Locale.ROOT) + " DRAGON!").withStyle(typeFormat(t));
        if ("Superior".equals(t)) {
            title.withStyle(ChatFormatting.BOLD);
            if (c.dragonSuperiorSound) LoudSound.play(SoundEvents.WITHER_SPAWN, 1.0f, 1.0f);
        }
        mc.gui.hud.setTimes(5, 50, 10);
        mc.gui.hud.setTitle(title);
        mc.gui.hud.setSubtitle(Component.empty());
    }

    private static void countKill(TeslaMapsConfig c, String t) {
        if (!c.dragonDropTracker) return;
        c.dragonTrackSinceSuperior = "Superior".equals(t) ? 0 : c.dragonTrackSinceSuperior + 1;
        c.dragonTrackSinceAotd++;
        c.dragonTrackSincePet++;
        c.dragonTrackLast.add(0, t);
        while (c.dragonTrackLast.size() > 3) c.dragonTrackLast.remove(c.dragonTrackLast.size() - 1);
        TeslaMapsConfig.save();
    }

    private static void report(Matcher m) {
        long end = fightEndTicks;
        fightEndTicks = 0;
        long myDamage;
        int pos;
        try {
            myDamage = Long.parseLong(m.group(1).replace(",", ""));
            pos = Integer.parseInt(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        double secs = fightStartTicks > 0 && end > fightStartTicks ? (end - fightStartTicks) / 20.0 : 0;
        int eyesNow = playerEyes;
        TickScheduler.schedule(10, () -> print(myDamage, secs, lootQuality(pos, myDamage, top1Damage, eyesNow)));
    }

    static int placement(int pos, double damage) {
        if (damage < 1) return 10;
        return switch (pos) {
            case 1 -> 300;
            case 2 -> 250;
            case 3 -> 200;
            case 4 -> 125;
            case 5 -> 110;
            case 6, 7, 8 -> 100;
            case 9, 10 -> 90;
            case 11, 12 -> 80;
            default -> 70;
        };
    }

    static int lootQuality(int pos, double damage, double first, int eyes) {
        if (first <= 0 && pos == 1) first = damage;
        double ratio = first > 0 ? 100.0 * damage / first : 0;
        return (int) (placement(pos, damage) + 100 * eyes + ratio);
    }

    private static void print(long myDamage, double secs, int lq) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        if (c.dragonDpsChat && secs > 0) {
            MutableComponent msg = Component.literal(String.format("§dYour Dragon DPS: §l§o%s §r§7(%.1fs) ", dps(myDamage / secs), secs));
            if (top1Damage > 0) {
                MutableComponent hover = Component.literal("§d§lTop 3 DPS\n");
                hover.append(Component.literal("§e#1 §f" + top1Name + " §7- §d§l§o" + dps(top1Damage / secs)));
                if (top2Damage > 0) hover.append(Component.literal("\n§6#2 §f" + top2Name + " §7- §d§l§o" + dps(top2Damage / secs)));
                if (top3Damage > 0) hover.append(Component.literal("\n§c#3 §f" + top3Name + " §7- §d§l§o" + dps(top3Damage / secs)));
                msg.append(Component.literal("§d§l§o[HOVER]").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
            }
            mc.player.sendSystemMessage(msg);
        }
        if (c.dragonLootQualityChat) {
            String col = lq >= LQ_LEGENDARY ? "§a" : "§c";
            MutableComponent msg = Component.literal("§dYour Dragon Loot Quality: " + col + "§l§o" + lq + " §r");
            MutableComponent hover = Component.literal("§d§lDrops\n");
            hover.append(Component.literal(dropLine("§7[Lvl 1] §6Ender Dragon", lq, LQ_LEGENDARY)));
            hover.append(Component.literal("\n" + dropLine("§7[Lvl 1] §5Ender Dragon", lq, LQ_EPIC)));
            msg.append(Component.literal("§d§l§o[HOVER]").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
            mc.player.sendSystemMessage(msg);
        }
    }

    private static String dropLine(String name, int lq, int need) {
        return name + " §7- " + (lq >= need ? "§a✔" : "§c✘") + " §8(" + need + ")";
    }

    private static String dps(double v) {
        return v >= 1000 ? String.format("%,.1fk", v / 1000.0) : String.format("%,.1f", v);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!EndProtector.inArea()) {
            statusLines = List.of();
            dropLines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        if (c.dragonDropTracker && lootScanning) scanLoot(mc, c);
        if (++ticks % 10 == 0) {
            readTab();
            if (c.dragonWeightLine && "Hatched".equals(eggState)) readScoreboard();
        }
        if (ticks % 2 != 0) return;
        List<String> s = c.dragonStatusHud ? buildStatus(c) : List.of();
        List<String> d = c.dragonDropTracker ? buildDropLines() : List.of();
        statusW = width(mc, s);
        dropW = width(mc, d);
        statusLines = s;
        dropLines = d;
    }

    private static int width(Minecraft mc, List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    private static void readTab() {
        List<String> tab = TabListUtils.getTabListLines();
        boolean eyeLine = false, spawned = false, respawning = false, inWidget = false;
        int scanned = 0, place = 0, idx = 0;
        String scannedType = null;
        String me = Minecraft.getInstance().getUser().getName();
        double topDmg = 0;
        for (String raw : tab) {
            String l = StringUtil.stripTrim(raw);
            if (l.isEmpty()) { inWidget = false; continue; }
            String low = l.toLowerCase(Locale.ROOT);
            if (low.contains("egg respawning")) respawning = true;
            if (low.contains("dragon spawned")) spawned = true;
            Matcher e = TAB_EYES.matcher(l);
            if (e.find()) { eyeLine = true; scanned = Integer.parseInt(e.group(1)); }
            Matcher t = TAB_TYPE.matcher(l);
            if (t.find()) { scannedType = t.group(1); inWidget = true; idx = 0; continue; }
            if (inWidget) {
                Matcher d = TAB_DAMAGE.matcher(l);
                if (d.matches()) {
                    idx++;
                    if (idx == 1) topDmg = NumberUtil.parseSuffixed(d.group(2));
                    if (d.group(1).equalsIgnoreCase(me)) place = idx;
                } else if (idx > 0) inWidget = false;
            }
        }
        liveTopDamage = topDmg;
        livePlace = place;

        if ("Scanning".equals(eggState)) {
            if (respawning) {
                eggState = "Respawning";
                eyes = playerEyes = 0;
                type = null;
            } else if (spawned) {
                eyes = 8;
                if (scannedType != null) {
                    eggState = "Hatched";
                    type = scannedType;
                } else eggState = "Hatching";
            } else if (eyeLine) {
                eggState = "Ready";
                eyes = scanned;
                type = null;
            }
        } else if ("Ready".equals(eggState) && eyeLine && System.currentTimeMillis() - lastChat > 3000) {
            eyes = scanned;
            if (scanned == 0) playerEyes = 0;
        }
    }

    private static void readScoreboard() {
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = SB_DAMAGE.matcher(ScoreboardUtils.cleanLine(raw).trim());
            if (m.find()) {
                liveDamage = NumberUtil.parseSuffixed(m.group(1));
                return;
            }
        }
    }

    private static String typeColor(String t) {
        if (t == null) return "§d";
        return switch (t) {
            case "Protector" -> "§8";
            case "Old" -> "§7";
            case "Unstable" -> "§5";
            case "Young" -> "§f";
            case "Strong" -> "§c";
            case "Wise" -> "§b";
            case "Superior" -> "§e";
            default -> "§d";
        };
    }

    private static ChatFormatting typeFormat(String t) {
        return switch (t) {
            case "Protector" -> ChatFormatting.DARK_GRAY;
            case "Old" -> ChatFormatting.GRAY;
            case "Unstable" -> ChatFormatting.DARK_PURPLE;
            case "Young" -> ChatFormatting.WHITE;
            case "Strong" -> ChatFormatting.RED;
            case "Wise" -> ChatFormatting.AQUA;
            case "Superior" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    private static List<String> buildStatus(TeslaMapsConfig c) {
        List<String> out = new ArrayList<>(5);
        out.add("§d§lDragon Status");
        String egg;
        if ("Hatching".equals(eggState)) {
            double rem = Math.max(0, spawnTargetTicks - ServerTicks.now());
            egg = rem > 0 ? String.format("Egg: §eHatching §c(%.1fs)", rem / 20.0) : "Egg: §eHatching §e(Soon)";
        } else if ("Hatched".equals(eggState)) {
            egg = "§cEgg: Hatched (Spawned)";
        } else {
            String col = switch (eggState) {
                case "Ready" -> "§a";
                case "Respawning" -> "§7";
                case "Scanning" -> "§8";
                default -> "§f";
            };
            egg = "Egg: " + col + eggState;
        }
        out.add(egg);
        if ("Respawning".equals(eggState)) {
            out.add("§7§mEyes placed: 0/8");
        } else if (!"Scanning".equals(eggState)) {
            String e = eyes == 8 ? "§cEyes placed: 8/8" : "Eyes placed: §e" + eyes + "§7/§a8";
            if (playerEyes > 0) e += " §b(" + playerEyes + ")";
            out.add(e);
        }
        if (type != null) out.add("Type: " + typeColor(type) + type);
        if (c.dragonWeightLine && "Hatched".equals(eggState)) {
            int place = livePlace > 0 ? livePlace : 6;
            out.add("§6Current Weight: §f" + lootQuality(place, liveDamage, liveTopDamage, playerEyes)
                    + (livePlace > 0 ? " §7(#" + livePlace + ")" : ""));
        }
        return out;
    }

    private static List<String> sampleStatus() {
        return List.of("§d§lDragon Status", "§cEgg: Hatched (Spawned)", "§cEyes placed: 8/8 §b(2)", "Type: §eSuperior",
                "§6Current Weight: §f412 §7(#2)");
    }

    private static List<String> buildDropLines() {
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>(9);
        out.add("§5§lDragon Drops");
        StringBuilder last = new StringBuilder("§7Last: ");
        if (c.dragonTrackLast.isEmpty()) last.append("§8-");
        for (int i = 0; i < c.dragonTrackLast.size(); i++) {
            if (i > 0) last.append("§7, ");
            String t = c.dragonTrackLast.get(i);
            last.append(typeColor(t)).append(t);
        }
        out.add(last.toString());
        out.add("§eSince Superior§f: " + c.dragonTrackSinceSuperior);
        out.add("§6Since AOTD§f: " + c.dragonTrackSinceAotd);
        out.add("§dSince Pet§f: " + c.dragonTrackSincePet);
        out.add("§7[Lvl 1] §6Ender Dragon§f: " + c.dragonLegendaryPets);
        out.add("§7[Lvl 1] §5Ender Dragon§f: " + c.dragonEpicPets);
        out.add("§6Aspect of the Dragons§f: " + c.dragonAotds);
        out.add("§5Eyes Placed§f: " + c.dragonTrackEyes);
        return out;
    }

    private static void scanLoot(Minecraft mc, TeslaMapsConfig c) {
        if (++lootScanTicks > LOOT_SCAN_TICKS || (petFound && aotdFound)) {
            lootScanning = false;
            return;
        }
        if (lootScanTicks % 5 != 0) return;
        AABB box = mc.player.getBoundingBox().inflate(30.0);
        for (Entity e : mc.level.getEntities((Entity) null, box, x -> (x instanceof ArmorStand || x instanceof ItemEntity) && x.hasCustomName())) {
            Component name = e.getCustomName();
            if (name == null) continue;
            String text = name.getString();
            if (!petFound && e instanceof ArmorStand && text.contains("[Lvl 1] Ender Dragon")) {
                if (hasColor(name, ChatFormatting.GOLD)) {
                    c.dragonLegendaryPets++;
                    petDrop(mc, c, ChatFormatting.GOLD, c.dragonLegendaryPets);
                } else if (hasColor(name, ChatFormatting.DARK_PURPLE)) {
                    c.dragonEpicPets++;
                    petDrop(mc, c, ChatFormatting.DARK_PURPLE, c.dragonEpicPets);
                }
            } else if (!aotdFound && text.contains("Aspect of the Dragons")) {
                aotdFound = true;
                c.dragonAotds++;
                c.dragonTrackSinceAotd = 0;
                TeslaMapsConfig.save();
                notifyDrop(mc, c, Component.literal("Aspect of the Dragons").withStyle(ChatFormatting.GOLD), c.dragonAotds);
            }
        }
    }

    private static void petDrop(Minecraft mc, TeslaMapsConfig c, ChatFormatting rarity, int count) {
        petFound = true;
        c.dragonTrackSincePet = 0;
        TeslaMapsConfig.save();
        notifyDrop(mc, c, Component.literal("[Lvl 1] ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Ender Dragon").withStyle(rarity)), count);
    }

    private static void notifyDrop(Minecraft mc, TeslaMapsConfig c, Component item, int count) {
        if (!c.dragonDropAlert) return;
        mc.gui.hud.setTimes(5, 60, 20);
        mc.gui.hud.setTitle(Component.literal("DROP!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        mc.gui.hud.setSubtitle(Component.empty().append(item).append(Component.literal(" x" + count).withStyle(ChatFormatting.GRAY)));
        LoudSound.play(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.5f);
    }

    private static boolean hasColor(Component text, ChatFormatting target) {
        TextColor want = TextColor.fromLegacyFormat(target);
        if (want == null) return false;
        if (want.equals(text.getStyle().getColor())) return true;
        if (text.getString().contains(target.toString())) return true;
        for (Component s : text.getSiblings()) if (hasColor(s, target)) return true;
        return false;
    }

    private static void renderStatus(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = statusLines;
        if (l.isEmpty() || statusHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        CropStats.drawLines(g, mc, statusHud.x(), statusHud.y(), statusHud.scale(), l, statusW);
    }

    private static void renderDrops(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = dropLines;
        if (l.isEmpty() || dropHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        CropStats.drawLines(g, mc, dropHud.x(), dropHud.y(), dropHud.scale(), l, dropW);
    }
}
