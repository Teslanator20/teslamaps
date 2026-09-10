package com.teslamaps.safari;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// One Safari run at a time, rebuilt purely from chat. Loot share counts for the dex (the shard and
// the milestone progress are shared), but not as a personal catch.
public class SafariTracker {

    private static final Pattern COLORS = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private static final Pattern CAUGHT = Pattern.compile(
            "^CAPTURE! You caught (?:a|an|the) (?:SPARKLING )?(.+?) and (?:gained|received) .+$");
    private static final Pattern FOUND = Pattern.compile(
            "^CAPTURE! You found the (.+?), and as a reward it gave you .+$");
    private static final Pattern LOOT_SHARE = Pattern.compile(
            "^LOOT SHARE! You received .+ from (\\S+) (?:catching (?:a|an)|finding the) (?:SPARKLING )?(.+)!$");
    private static final Pattern SPARKLING = Pattern.compile("^SPARKLING! (\\S+) caught a SPARKLING (.+)!$");
    private static final Pattern THREW = Pattern.compile("^You threw a Critter Capsule at the (.+)!$");
    private static final Pattern ESCAPED = Pattern.compile("^The (.+) escaped your Critter Capsule!$");
    private static final Pattern DODGED = Pattern.compile("^The (.+) dodged your critter capsule and started running away!$");
    private static final Pattern HOTSPOT = Pattern.compile("^HOTSPOT! Your Hunting Hotspot is the (\\w+) Biome!$");
    private static final Pattern FLOOR_DROP = Pattern.compile("^FLOOR DROP! You found (.+) on the ground!$");
    private static final Pattern HEAD_START = Pattern.compile("^HEAD START! You started with (?:a|an) (.+)!$");
    private static final Pattern GEM_PLACED = Pattern.compile("^You placed the (\\w+) Gem on its podium!$");
    private static final Pattern CAPTURED = Pattern.compile("Captured Mobs:?\\s*(\\d+)");

    private static final Set<String> caughtSelf = new HashSet<>();
    private static final Set<String> caughtAny = new HashSet<>();
    private static final Set<String> gotAway = new HashSet<>();
    private static final Map<SafariData.Biome, Integer> perBiome = new EnumMap<>(SafariData.Biome.class);

    private static long runStart = 0;
    private static int capsules = 0;
    private static int escapes = 0;
    private static int floorDrops = 0;
    private static int bells = 0;
    private static int incense = 0;
    private static final Set<String> gems = new LinkedHashSet<>();
    private static SafariData.Biome hotspot = null;
    private static String headStart = null;
    private static boolean wumpaAwake = false;
    private static boolean gemsDone = false;

    // one alert per run per subject
    private static final Set<String> alerted = new HashSet<>();
    private static final Set<java.util.UUID> sparklingSeen = new HashSet<>();

    private static boolean wasInSafari = false;
    private static int boardZeroTicks = 0;

    // "not full" warning: one check per run, right after entry (run starts within ~1s)
    private static final int FULL_CHECK_DELAY = 20; // 1s
    private static int fullCheckTicks = 0;
    private static boolean fullChecked = false;
    private static long warnUntil = 0L;
    private static int warnCount = 0;

    /** Run-relative completion times, so the HUD and the announce agree on one number. */
    private static final Map<SafariData.Biome, Long> biomeDoneAt = new EnumMap<>(SafariData.Biome.class);
    /** Everything but the biome's rare critter - that one only spawns every few runs. */
    private static final Map<SafariData.Biome, Long> biomeAlmostAt = new EnumMap<>(SafariData.Biome.class);
    private static long dexDoneAt = 0L;

    private static long lastHideyhoAnswer = 0L;
    private static final java.util.Random RANDOM = new java.util.Random();

    public static boolean isActive() {
        return runStart != 0 && SafariData.inSafari();
    }

    public static boolean hasCaught(SafariData.Critter c) {
        return caughtAny.contains(c.name());
    }

    // Runs are bounded by leaving, never by chat: the reward summary of the old run usually lands
    // AFTER the entry message of the new one, so it must not touch a run that is already going.
    public static void tick() {
        boolean in = SafariData.inSafari();
        if (wasInSafari && !in) {
            finishRun();
            clearRun();
        }
        if (in) {
            ensureRun();
            checkBoardReset();
            checkFull();
            RecatchSpots.tick();
        }
        wasInSafari = in;
    }

    /** Warn once per run when the instance holds fewer than 4 players. */
    private static void checkFull() {
        if (warnUntil != 0 && System.currentTimeMillis() < warnUntil && countPlayers() >= 4) warnUntil = 0;
        if (fullChecked || !TeslaMapsConfig.get().safariWarnNotFull) return;
        if (++fullCheckTicks < FULL_CHECK_DELAY) return;
        fullChecked = true;

        int players = countPlayers();
        if (players <= 0 || players >= 4) return;

        warnCount = players;
        warnUntil = System.currentTimeMillis() + 5_000;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    "§a[TeslaMaps] §c§lPARTY NOT FULL §7- only §f" + players + "§7/4 players in the Safari"));
        }
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.safariAlertVolume > 0) {
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), c.safariAlertVolume, 0.6f);
            com.teslamaps.utils.TickScheduler.schedule(6, () ->
                    LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), c.safariAlertVolume, 0.6f));
        }
    }

    private static int countPlayers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        int n = 0;
        for (var p : mc.level.players()) {
            if (p.getUUID().version() == 4) n++; // skip NPCs / fake players
        }
        return n;
    }

    /**
     * On a laggy server the area never flips between two runs, so the old run keeps running. The
     * scoreboard counter going back to 0 while we still hold catches is the second run boundary.
     */
    private static void checkBoardReset() {
        if (caughtSelf.isEmpty()) { boardZeroTicks = 0; return; }
        int captured = boardCaptured();
        if (captured != 0) { boardZeroTicks = 0; return; }
        if (++boardZeroTicks < 40) return;   // the board flickers on a laggy join, want a steady 0
        boardZeroTicks = 0;
        finishRun();
        clearRun();
        runStart = System.currentTimeMillis();
    }

    /** -1 when the line is absent, so a missing scoreboard never counts as a reset. */
    private static int boardCaptured() {
        for (String line : com.teslamaps.utils.ScoreboardUtils.getScoreboardLines()) {
            Matcher m = CAPTURED.matcher(com.teslamaps.utils.ScoreboardUtils.cleanLine(line));
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    /** The tab list area lags a second behind the teleport, so the first messages start the run. */
    private static void ensureRun() {
        if (runStart != 0) return;
        clearRun();
        runStart = System.currentTimeMillis();
    }

    /** Anything shorter than a minute is a misfire, not a run. */
    private static void finishRun() {
        if (runStart == 0) return;
        long duration = System.currentTimeMillis() - runStart;
        if (duration < 60_000 || caughtAny.isEmpty()) return;

        SafariHistory.Run run = new SafariHistory.Run();
        run.startedAt = runStart;
        run.durationMs = duration;
        run.dex = caughtAny.size();
        run.own = caughtSelf.size();
        run.capsules = capsules;
        run.escapes = escapes;
        run.floorDrops = floorDrops;
        run.hotspot = hotspot == null ? null : hotspot.label;

        int oldBest = SafariHistory.bestDex();
        SafariHistory.add(run);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String line = "§a[TeslaMaps] §7Run saved: §f" + SafariHistory.time(duration)
                    + " §7dex §f" + run.dex + "§7/" + SafariData.ALL.size() + " §7own §f" + run.own;
            if (run.dex > oldBest) line += " §6§lNEW PB!";
            mc.player.sendSystemMessage(Component.literal(line));
        }
    }

    private static void clearRun() {
        RecatchSpots.reset();
        caughtSelf.clear();
        caughtAny.clear();
        gotAway.clear();
        perBiome.clear();
        gems.clear();
        alerted.clear();
        sparklingSeen.clear();
        capsules = 0;
        escapes = 0;
        floorDrops = 0;
        bells = 0;
        incense = 0;
        biomeDoneAt.clear();
        biomeAlmostAt.clear();
        dexDoneAt = 0L;
        hotspot = null;
        headStart = null;
        wumpaAwake = false;
        gemsDone = false;
        boardZeroTicks = 0;
        fullCheckTicks = 0;
        fullChecked = false;
        warnUntil = 0;
        runStart = 0;
    }

    public static void onChatMessage(String raw) {
        if (!TeslaMapsConfig.get().safariTracker) return;
        String msg = COLORS.matcher(raw).replaceAll("").trim();

        Matcher m;
        if ((m = CAUGHT.matcher(msg)).matches() || (m = FOUND.matcher(msg)).matches()) {
            record(m.group(1), true);
            return;
        }
        if ((m = LOOT_SHARE.matcher(msg)).matches()) {
            record(m.group(2), false);
            return;
        }
        if ((m = SPARKLING.matcher(msg)).matches()) {
            record(m.group(2), false);
            alert("sparkling-chat-" + m.group(2), "§d§lSPARKLING §f" + m.group(2) + " §7caught by " + m.group(1));
            return;
        }
        if ((m = THREW.matcher(msg)).matches()) {
            ensureRun();
            capsules++;
            RecatchSpots.onThrow(SafariData.byName(m.group(1)));
            return;
        }
        if ((m = ESCAPED.matcher(msg)).matches() || (m = DODGED.matcher(msg)).matches()) {
            ensureRun();
            escapes++;
            SafariData.Critter c = SafariData.byName(m.group(1));
            RecatchSpots.onEscape(c);
            if (c != null && !caughtAny.contains(c.name())) gotAway.add(c.name());
            return;
        }
        // floor drops also exist in Torrhus and Galatea, so this one must not start a run
        if (FLOOR_DROP.matcher(msg).matches()) {
            if (runStart != 0) floorDrops++;
            return;
        }
        if ((m = HOTSPOT.matcher(msg)).matches()) {
            ensureRun();
            for (SafariData.Biome b : SafariData.Biome.values()) {
                if (b.label.equalsIgnoreCase(m.group(1))) hotspot = b;
            }
            return;
        }
        if ((m = HEAD_START.matcher(msg)).matches()) {
            ensureRun();
            headStart = m.group(1);
            if (headStart.equals("Soothing Incense")) incense++;
            return;
        }
        if (msg.startsWith("You used the Soothing Incense to light the candle!")) {
            incense++;
            if (incense >= 4) alert("doomspiral", "§5§lDOOMSPIRAL §7ritual complete - stay still");
            return;
        }
        if (msg.equals("Your ritual summoned a Doomspiral into this world. Stay still.")) {
            incense = 0;
            return;
        }
        if ((m = GEM_PLACED.matcher(msg)).matches()) {
            gems.add(m.group(1));
            if (gems.size() >= 3) gemsDone = true;
            if (gems.size() >= 3) alert("gemzie", "§5§lGEMZIE §7all three gems placed");
            return;
        }
        if (msg.startsWith("A rumbling sound can be heard, and the door at the back of the chamber opens")) {
            gemsDone = true;
            // no ping and no caps for this one, the server already announces the door itself
            alerted.add("gemzie");
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§7Gems done"));
            return;
        }
        if (msg.equals("You rang the bell!")) { bells++; return; }
        if (msg.startsWith("You hear the sound of massive footsteps")) {
            alert("wumpa-spawn", "§6§lWUMPA §7has spawned in the Icy Biome");
            return;
        }
        if (msg.equals("The Wumpa has awoken.")) {
            wumpaAwake = true;
            alert("wumpa-awake", "§6§lWUMPA §cawake");
        }
    }

    private static void record(String name, boolean self) {
        SafariData.Critter c = SafariData.byName(name);
        if (c == null) return;
        ensureRun();

        if (self) caughtSelf.add(c.name());
        RecatchSpots.onCaught(c);
        gotAway.remove(c.name());
        if (caughtAny.add(c.name())) {
            perBiome.merge(c.biome(), 1, Integer::sum);
            if (c.rarity() == SafariData.Rarity.LEGENDARY) {
                alert("legendary-" + c.name(), "§6§l" + c.name().toUpperCase() + " §7caught");
            }
            TeslaMapsConfig cfg = TeslaMapsConfig.get();
            int have = perBiome.getOrDefault(c.biome(), 0);
            int total = SafariData.countIn(c.biome());
            if (have >= total && biomeDoneAt.putIfAbsent(c.biome(), elapsed()) == null
                    && cfg.safariAnnounceBiomeDone) {
                String at = SafariHistory.time(biomeDoneAt.get(c.biome()));
                alert("biome-" + c.biome(), c.biome().color + "§l" + c.biome().label.toUpperCase()
                        + " §7done after §f" + at);
                share(c.biome().label + " done after " + at);
            }
            // Macaw only spawns some runs, so Forest usually ends one short
            if (c.biome() == SafariData.Biome.FOREST && have == total - 1 && !caughtAny.contains("Macaw")
                    && biomeAlmostAt.putIfAbsent(c.biome(), elapsed()) == null
                    && cfg.safariAnnounceBiomeDone) {
                String at = SafariHistory.time(biomeAlmostAt.get(c.biome()));
                alert("biome-almost-forest", SafariData.Biome.FOREST.color
                        + "§lFOREST §7done except §fMacaw §7after §f" + at);
                share("Forest done except Macaw after " + at);
            }
            if (caughtAny.size() >= SafariData.ALL.size() && dexDoneAt == 0) {
                dexDoneAt = elapsed();
                String at = SafariHistory.time(dexDoneAt);
                if (cfg.safariAnnounceUniqueDone) {
                    alert("dex-complete", "§6§lFULL DEX §7done after §f" + at
                            + " §7- all " + SafariData.ALL.size() + " uniques");
                    share("Full dex done after " + at);
                }
            }
            if (wumpaMissing() == 0) alert("wumpa-ready", "§6§lWUMPA §7ready - 8/8 Icy critters caught");
        }
    }

    /** Sparkling critters spawn WAX_ON particles, which beats waiting for someone to catch it. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        if (!TeslaMapsConfig.get().safariSparklingAlert || !SafariData.inSafari()) return;
        if (packet.getParticle().getType() != ParticleTypes.WAX_ON.getType()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 at = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, AABB.ofSize(at, 1.0, 1.0, 1.0), a -> true)) {
            if (as.getCustomName() == null) continue;
            String name = COLORS.matcher(as.getCustomName().getString()).replaceAll("");
            SafariData.Critter c = null;
            for (SafariData.Critter candidate : SafariData.ALL) {
                if (name.contains(candidate.name())) { c = candidate; break; }
            }
            if (c == null || !sparklingSeen.add(as.getUUID())) continue;

            String pos = as.blockPosition().getX() + " " + as.blockPosition().getY() + " " + as.blockPosition().getZ();
            alert("sparkling-" + as.getUUID(), "§d§lSPARKLING §f" + c.name() + " §7at §f" + pos);

            if (TeslaMapsConfig.get().safariSparklingParty) {
                String line = "SPARKLING " + c.name() + " at " + pos;
                mc.execute(() -> {
                    if (mc.getConnection() != null) mc.getConnection().sendCommand("pc " + line);
                });
            }
            return;
        }
    }

    private static long elapsed() {
        return runStart == 0 ? 0 : System.currentTimeMillis() - runStart;
    }

    public static Map<SafariData.Biome, Long> biomeTimes() { return biomeDoneAt; }

    /** One party-chat line listing what the run still needs, grouped per biome. */
    public static String missingLine() {
        if (runStart == 0) return "No Safari run active";

        List<String> parts = new ArrayList<>();
        int total = 0;
        for (SafariData.Biome b : SafariData.Biome.values()) {
            List<String> names = new ArrayList<>();
            for (SafariData.Critter c : SafariData.ALL) {
                if (c.biome() == b && !caughtAny.contains(c.name())) names.add(c.name());
            }
            if (names.isEmpty()) continue;
            total += names.size();
            parts.add(b.label + ": " + String.join(", ", names));
        }
        if (parts.isEmpty()) return "Full dex - nothing missing (" + SafariHistory.time(elapsed()) + ")";

        String line = "Missing " + total + "/" + SafariData.ALL.size() + " - " + String.join(" | ", parts);
        return line.length() <= 240 ? line : line.substring(0, 237) + "...";
    }

    public static long dexTime() { return dexDoneAt; }

    /** Posts a completion time to party chat, opt-in. */
    private static void share(String line) {
        if (!TeslaMapsConfig.get().safariShareTimeParty) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() != null) mc.getConnection().sendCommand("pc " + line);
        });
    }

    private static void alert(String key, String text) {
        if (!alerted.add(key)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + text));
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.safariAlertVolume > 0) {
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), c.safariAlertVolume, 1.4f);
        }
    }

    /**
     * Hideyho asks a yes/no question, this clicks the yes for you. It runs the command the server
     * put on the component, so nothing is guessed or typed.
     */
    public static void onChatComponent(Component message) {
        if (!TeslaMapsConfig.get().safariAutoHideyho || !SafariData.inSafari()) return;
        if (!message.getString().contains("Select an option:")) return;

        for (Component sibling : message.getSiblings()) {
            String plain = COLORS.matcher(sibling.getString()).replaceAll("").trim();
            if (!plain.equals("[Sure]")) continue;
            if (!(sibling.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run)) continue;

            Minecraft mc = Minecraft.getInstance();
            String cmd = run.command();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            String finalCmd = cmd;

            TeslaMapsConfig c = TeslaMapsConfig.get();
            long now = System.currentTimeMillis();
            // Hypixel repeats the prompt, and answering every copy is a command-spam block.
            if (now - lastHideyhoAnswer < c.safariAutoHideyhoMinMs) return;
            lastHideyhoAnswer = now;

            int jitter = Math.max(0, c.safariAutoHideyhoJitterMs);
            int delay = Math.max(0, c.safariAutoHideyhoMinMs) + (jitter == 0 ? 0 : RANDOM.nextInt(jitter + 1));
            com.teslamaps.utils.TickScheduler.schedule(Math.max(1, delay / 50), () -> {
                if (mc.getConnection() != null) mc.getConnection().sendCommand(finalCmd);
            });
            return;
        }
    }

    private static int wumpaMissing() {
        int missing = 0;
        for (SafariData.Critter c : SafariData.wumpaRequirements()) {
            if (!caughtAny.contains(c.name())) missing++;
        }
        return missing;
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        TeslaMapsConfig c = TeslaMapsConfig.get();

        long secs = runStart == 0 ? 0 : (System.currentTimeMillis() - runStart) / 1000;
        out.add(String.format("§bCritter Safari §8| §f%d:%02d", secs / 60, secs % 60));
        out.add("§7Dex: §a" + caughtAny.size() + "§7/§f" + SafariData.ALL.size()
                + " §8(§7own §f" + caughtSelf.size() + "§8)");

        StringBuilder biomes = new StringBuilder();
        for (SafariData.Biome b : SafariData.Biome.values()) {
            int have = perBiome.getOrDefault(b, 0);
            int total = SafariData.countIn(b);
            if (biomes.length() > 0) biomes.append(" §8| ");
            biomes.append(b.color).append(b.label).append(" ")
                  .append(have >= total ? "§a" : "§f").append(have).append("§7/").append(total);
        }
        out.add(biomes.toString());

        out.add("§7Capsules: §f" + capsules + " §8| §7Escapes: §c" + escapes + " §8| §7Drops: §e" + floorDrops);

        if (c.safariShowPb) {
            int pb = SafariHistory.bestDex();
            if (pb > 0) {
                String ahead = caughtAny.size() > pb ? " §6§lPB!" : "";
                out.add("§7PB: §f" + pb + "§7/" + SafariData.ALL.size() + ahead);
            }
        }

        if (c.safariShowHotspot && hotspot != null) out.add("§7Hotspot: " + hotspot.color + hotspot.label);
        if (c.safariShowHeadStart && headStart != null) out.add("§7Head Start: §f" + headStart);
        if (incense > 0 && incense < 4) out.add("§7Incense: §f" + incense + "§7/4");
        if (!gemsDone && !gems.isEmpty()) out.add("§7Gems: §f" + gems.size() + "§7/3");
        if (bells > 0) out.add("§7Bells: §f" + bells + "§7/7");

        int wumpa = 8 - wumpaMissing();
        if (wumpa < 8) out.add("§7Wumpa: §f" + wumpa + "§7/8");
        else if (!caughtAny.contains("Wumpa")) out.add("§6Wumpa: §aready" + (wumpaAwake ? " §c(awake)" : ""));

        if (c.safariChecklist) {
            SafariData.Biome shown = checklistBiome();
            if (shown != null) {
                out.add(shown.color + "§l" + shown.label + " Biome");
                for (SafariData.Critter cr : SafariData.ALL) {
                    if (cr.biome() != shown) continue;
                    out.add(checklistLine(cr, c.safariChecklistIcons));
                }
            }
        }
        return out;
    }

    /** "Auto" follows the biome you are standing in, otherwise the picked one. */
    private static SafariData.Biome checklistBiome() {
        String pick = TeslaMapsConfig.get().safariChecklistBiome;
        if (pick != null && !pick.equalsIgnoreCase("Auto")) {
            for (SafariData.Biome b : SafariData.Biome.values()) {
                if (b.label.equalsIgnoreCase(pick)) return b;
            }
        }
        SafariData.Biome here = SafariData.currentBiome();
        return here != null ? here : hotspot;
    }

    // Private markers for the checklist icons. They never reach the screen - draw() consumes them.
    private static final char MARK_CHECK = '\u0001';
    private static final char MARK_CROSS = '\u0002';
    private static final char MARK_DOT = '\u0003';

    private static boolean isMarker(char c) {
        return c == MARK_CHECK || c == MARK_CROSS || c == MARK_DOT;
    }

    /** Same line without its icon marker, for anything that treats it as plain text. */
    public static String plain(String line) {
        return !line.isEmpty() && isMarker(line.charAt(0)) ? line.substring(1) : line;
    }

    private static String checklistLine(SafariData.Critter cr, boolean icons) {
        boolean caught = caughtAny.contains(cr.name());
        boolean away = gotAway.contains(cr.name());

        String color = caught ? "§a" : away ? "§c" : "§7";
        if (!icons) return "  " + color + cr.name();

        // marker is stripped in draw() and replaced by a drawn icon; the spaces reserve its slot
        char marker = caught ? MARK_CHECK : away ? MARK_CROSS : MARK_DOT;
        return marker + "   " + color + cr.name();
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.safariTracker) return;

        Minecraft mc = Minecraft.getInstance();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;

        if (warnUntil != 0 && System.currentTimeMillis() < warnUntil && SafariData.inSafari()) {
            drawWarn(context, mc);
        }

        if (!c.safariHud || !isActive()) return;
        draw(context, mc, c.safariHudX, c.safariHudY, c.safariHudScale, lines());
    }

    private static void drawWarn(GuiGraphicsExtractor context, Minecraft mc) {
        String top = "§c§lPARTY NOT FULL";
        String sub = "§fonly " + warnCount + "/4 players";
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(context.guiWidth() / 2f, context.guiHeight() / 3f);
        pose.scale(2.5f, 2.5f);
        context.text(mc.font, top, -mc.font.width(top) / 2, 0, 0xFFFFFFFF);
        pose.popMatrix();
        pose.pushMatrix();
        pose.translate(context.guiWidth() / 2f, context.guiHeight() / 3f + 26);
        pose.scale(1.5f, 1.5f);
        context.text(mc.font, sub, -mc.font.width(sub) / 2, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, lines());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int ly = 0;
        for (String line : lines) {
            if (!line.isEmpty() && isMarker(line.charAt(0))) {
                char marker = line.charAt(0);
                if (marker == MARK_CHECK) {
                    com.teslamaps.render.gui.TeslaGui.checkIcon(context, 1, ly, 8, 0xFF55DD55);
                } else if (marker == MARK_CROSS) {
                    com.teslamaps.render.gui.TeslaGui.crossIcon(context, 1, ly, 7, 0xFFDD5555);
                } else {
                    context.fill(3, ly + 3, 6, ly + 6, 0xFF6A6A6A);
                }
                context.text(mc.font, line.substring(1), 0, ly, 0xFFFFFFFF);
            } else {
                context.text(mc.font, line, 0, ly, 0xFFFFFFFF);
            }
            ly += mc.font.lineHeight + 1;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(net.minecraft.client.gui.Font font) {
        List<String> lines = lines();
        int w = 0;
        for (String line : lines) {
            if (!line.isEmpty() && isMarker(line.charAt(0))) line = line.substring(1);
            w = Math.max(w, font.width(line));
        }
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
