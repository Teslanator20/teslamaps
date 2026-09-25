package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.player.PlayerTracker.DungeonPlayer;
import com.teslamaps.mixin.BossHealthOverlayAccessor;
import com.teslamaps.render.MapRenderer;
import com.teslamaps.render.PlayerHeadRenderer;
import net.minecraft.client.gui.components.LerpingBossEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.glfw.GLFW;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LeapOverlay {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final Map<String, Integer> CLASS_COLORS = new HashMap<>();
    private static final Map<String, String> CLASS_LETTERS = new HashMap<>();
    private static final Map<String, Integer> CLASS_DEFAULT_QUADRANT = new HashMap<>();
    private static final Map<String, Integer> CLASS_PRIORITY = new HashMap<>();

    static {
        // Odin's class palette, same as the map player markers
        CLASS_COLORS.put("Archer", 0xFFFFAA00);     // Gold
        CLASS_COLORS.put("Berserk", 0xFFAA0000);    // Dark Red
        CLASS_COLORS.put("Healer", 0xFFFF55FF);     // Light Purple
        CLASS_COLORS.put("Mage", 0xFF55FFFF);       // Aqua
        CLASS_COLORS.put("Tank", 0xFF00AA00);       // Dark Green

        CLASS_LETTERS.put("Archer", "A");
        CLASS_LETTERS.put("Berserk", "B");
        CLASS_LETTERS.put("Healer", "H");
        CLASS_LETTERS.put("Mage", "M");
        CLASS_LETTERS.put("Tank", "T");

        CLASS_DEFAULT_QUADRANT.put("Archer", 0);
        CLASS_DEFAULT_QUADRANT.put("Berserk", 1);
        CLASS_DEFAULT_QUADRANT.put("Healer", 2);
        CLASS_DEFAULT_QUADRANT.put("Mage", 3);
        CLASS_DEFAULT_QUADRANT.put("Tank", 3);

        CLASS_PRIORITY.put("Berserk", 0);
        CLASS_PRIORITY.put("Tank", 1);
        CLASS_PRIORITY.put("Archer", 2);
        CLASS_PRIORITY.put("Healer", 2);
        CLASS_PRIORITY.put("Mage", 2);
    }

    private static final LeapPlayer[] leapPlayers = new LeapPlayer[4];
    private static boolean menuOpen = false;

    // boss bar names that mean we're in the boss room (Watcher excluded: that's the blood room)
    private static final String[] BOSS_NAMES = {
        "Bonzo", "Scarf", "Professor", "Thorn", "Livid", "Sadan", "Maxor", "Storm", "Goldor", "Necron"
    };

    // snapshot of the leap map drawn this frame (MapRenderer's shared state gets clobbered by the HUD map)
    private static boolean leapMapShown = false;
    private static int leapMapX, leapMapY, leapMapW, leapMapH;
    private static final List<MapRenderer.PlayerMarker> leapMarkers = new ArrayList<>();

    private static boolean inBossRoom() {
        if (DungeonManager.isInBoss()) return true;
        if (mc.gui == null) return false;
        var events = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).teslamaps$getEvents();
        if (events == null) return false;
        for (LerpingBossEvent ev : events.values()) {
            if (ev.getName() == null) continue;
            String n = ev.getName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
            for (String b : BOSS_NAMES) if (n.contains(b)) return true;
        }
        return false;
    }

    // Odin-style layout: boxes fill the screen quadrants around the center
    private static final float[] hoverAnim = new float[4];
    private static long lastAnimTime = 0;

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().leapOverlay) return false;
        if (!DungeonManager.isInDungeon()) return false;
        if (mc.screen == null) return false;

        if (mc.screen instanceof ContainerScreen containerScreen) {
            String title = containerScreen.getTitle().getString();
            String cleanTitle = ChatFormatting.stripFormatting(title);
            return "Spirit Leap".equals(cleanTitle) || "Teleport to Player".equals(cleanTitle);
        }
        return false;
    }

    private static final Pattern LEAPED_PATTERN = Pattern.compile("You have teleported to (\\w{1,16})!");
    private static final Pattern DOOR_OPENER_PATTERN = Pattern.compile("(\\w{1,16}) opened a WITHER door!");

    private static String lastDoorOpener = null;

    // Fast Leap boss phase, driven by F7/M7 boss chat lines (verified against the dungeon logs).
    private enum BossPhase { NONE, ARCHER, BERSERK, HEALER }
    private static BossPhase bossPhase = BossPhase.NONE;
    private static long berserkUntilMs = 0L;
    private static int crystalCount = 0;        // "2/2 Energy Crystals are now active!" occurrences
    private static long berserkScheduledMs = 0L; // delayed -> berserk after the 2nd crystal message

    public static String getLastDoorOpener() { return lastDoorOpener; }

    public static void tick() {
        if (berserkScheduledMs != 0L && System.currentTimeMillis() >= berserkScheduledMs) {
            bossPhase = BossPhase.BERSERK;
            berserkUntilMs = Long.MAX_VALUE;
            berserkScheduledMs = 0L;
        }
    }

    public static void onChatMessage(String message) {
        if (!DungeonManager.isInDungeon()) return;

        Matcher door = DOOR_OPENER_PATTERN.matcher(message);
        if (door.find()) {
            lastDoorOpener = door.group(1);
            TeslaMaps.debug("[LeapOverlay] Last door opener: {}", lastDoorOpener);
        }

        String clean = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (clean.contains("[BOSS] Maxor:") && clean.contains("WELL! WELL! WELL!")) {
            bossPhase = BossPhase.ARCHER;
        } else if (clean.contains("[BOSS] Maxor:") && clean.contains("YOU TRICKED ME!")) {
            bossPhase = BossPhase.BERSERK;
            berserkUntilMs = Long.MAX_VALUE; // stays on berserk until the 10s window below
        } else if (clean.contains("[BOSS] Maxor:") && clean.contains("I'M TOO YOUNG TO DIE AGAIN!")) {
            bossPhase = BossPhase.BERSERK;
            berserkUntilMs = System.currentTimeMillis() + 10_000L;
        } else if (clean.contains("[BOSS] Storm:") && clean.contains("Oof")) {
            bossPhase = BossPhase.HEALER;
        } else if (clean.contains("2/2 Energy Crystals are now active!")) {
            crystalCount++;
            if (crystalCount == 2) berserkScheduledMs = System.currentTimeMillis() + 1500L; // 2nd time -> berserk after 1.5s
        }

        Matcher m = LEAPED_PATTERN.matcher(clean);
        if (m.find()) {
            com.teslamaps.features.HidePlayers.onLeap();
            if (TeslaMapsConfig.get().leapAnnounce && mc.getConnection() != null) {
                mc.getConnection().sendCommand("pc Leaped to " + m.group(1) + "!");
            }
        }
    }

    public static void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!shouldRender()) {
            menuOpen = false;
            Arrays.fill(hoverAnim, 0f);
            lastAnimTime = 0;
            return;
        }

        ContainerScreen screen = (ContainerScreen) mc.screen;
        if (screen == null) return;

        boolean selfDead = selfDead();
        if (!menuOpen) openedDead = selfDead;
        if (TeslaMapsConfig.get().leapCloseOnDeath && selfDead && !openedDead && mc.player != null
                && !LegitMode.blocksCheats()) {
            mc.player.closeContainer();
            return;
        }

        // scan every frame: chest contents arrive after the menu opens, and death states change mid-menu
        scanLeapPlayers(screen);
        menuOpen = true;

        pollKeybinds(screen);

        boolean hasPlayers = false;
        for (LeapPlayer p : leapPlayers) {
            if (p != null) {
                hasPlayers = true;
                break;
            }
        }
        if (!hasPlayers) return;

        Font textRenderer = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        context.fill(0, 0, screenWidth, screenHeight, 0x90101010);

        float cxs = screenWidth / 2f, cys = screenHeight / 2f;
        boolean link = TeslaMapsConfig.get().leapHoverLink;
        boolean mouseInMap = leapMapShown && leapMapW > 0 && mouseX >= leapMapX && mouseX <= leapMapX + leapMapW
                && mouseY >= leapMapY && mouseY <= leapMapY + leapMapH;
        String headHover = link && mouseInMap ? hoveredHead(mouseX, mouseY) : null;
        int linkQuad = -1;
        if (headHover != null) {
            for (int i = 0; i < 4; i++) if (leapPlayers[i] != null && headHover.equals(leapPlayers[i].name)) linkQuad = i;
        }

        boolean wasShown = leapMapShown;
        leapMapShown = false;
        if (TeslaMapsConfig.get().leapShowMap && DungeonManager.isInDungeon() && !inBossRoom()) {
            float mapScale = TeslaMapsConfig.get().mapScale;
            int mw = MapRenderer.mapW();
            int mapBaseX = mw > 0 ? screenWidth / 2 - mw / 2 : screenWidth / 2 - 60;
            String highlight = headHover;
            if (link && !(wasShown && mouseInMap)) {
                LeapPlayer q = leapPlayers[(mouseX >= cxs ? 1 : 0) + (mouseY >= cys ? 2 : 0)];
                if (q != null && !q.isDead) highlight = q.name;
            }
            MapRenderer.setHighlight(highlight);
            MapRenderer.renderAt(context, mapBaseX, 6, mapScale, true);
            MapRenderer.setHighlight(null);
            // snapshot now — the HUD map (if it renders this frame) overwrites MapRenderer's shared state
            leapMapX = MapRenderer.mapX(); leapMapY = MapRenderer.mapY();
            leapMapW = MapRenderer.mapW(); leapMapH = MapRenderer.mapH();
            leapMarkers.clear(); leapMarkers.addAll(MapRenderer.getPlayerMarkers());
            leapMapShown = true;
        }

        float s = TeslaMapsConfig.get().leapScale;
        float boxW = 200f * s, boxH = 75f * s;
        float xOff = 12f * s;
        float yOff = 9f * s;
        float cx = screenWidth / 2f, cy = screenHeight / 2f;

        long now = System.currentTimeMillis();
        float dt = lastAnimTime == 0 ? 16f : Math.min(100f, now - lastAnimTime);
        lastAnimTime = now;
        int hoverQuad = (mouseX >= cx ? 1 : 0) + (mouseY >= cy ? 2 : 0);
        if (link && mouseInMap) hoverQuad = linkQuad;
        for (int i = 0; i < 4; i++) {
            float target = (i == hoverQuad && leapPlayers[i] != null && !leapPlayers[i].isDead) ? 1f : 0f;
            float step = dt / 200f;
            hoverAnim[i] += Math.signum(target - hoverAnim[i]) * Math.min(step, Math.abs(target - hoverAnim[i]));
        }

        for (int i = 0; i < 4; i++) {
            LeapPlayer player = leapPlayers[i];
            if (player == null) continue;

            float bx = (i % 2 == 0) ? cx - xOff - boxW : cx + xOff;
            float by = (i / 2 == 0) ? cy - yOff - boxH : cy + yOff;
            float e = hoverAnim[i] * 6f * s;
            renderPlayerBox(context, textRenderer, player, bx - e, by - e, boxW + 2 * e, boxH + 2 * e, s);
            if (i == linkQuad) {
                int x0 = (int) (bx - e), y0 = (int) (by - e), x1 = (int) (bx + boxW + e), y1 = (int) (by + boxH + e);
                int col = CLASS_COLORS.getOrDefault(player.dungeonClass, 0xFFFFFFFF);
                context.fill(x0, y0, x1, y0 + 1, col);
                context.fill(x0, y1 - 1, x1, y1, col);
                context.fill(x0, y0, x0 + 1, y1, col);
                context.fill(x1 - 1, y0, x1, y1, col);
            }
        }
    }

    private static boolean openedDead = false;

    private static boolean selfDead() {
        if (mc.player == null) return false;
        if (!mc.player.isAlive()) return true;
        String self = mc.player.getName().getString();
        for (DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (self.equals(dp.getName())) return !dp.isAlive();
        }
        return false;
    }

    // Skyblocker DungeonMap.isPlayerHovered: nearest head under the cursor
    private static String hoveredHead(int mouseX, int mouseY) {
        float r = Math.max(4f, 5f * TeslaMapsConfig.get().mapScale * TeslaMapsConfig.get().playerHeadScale);
        String best = null;
        double bestD = r * r;
        for (MapRenderer.PlayerMarker m : leapMarkers) {
            if (m.self() || m.name() == null) continue;
            double d = (m.x() - mouseX) * (m.x() - mouseX) + (m.y() - mouseY) * (double) (m.y() - mouseY);
            if (d <= bestD) { bestD = d; best = m.name(); }
        }
        return best;
    }

    private static void renderPlayerBox(GuiGraphicsExtractor context, Font textRenderer,
                                         LeapPlayer player, float fx, float fy, float fw, float fh, float s) {
        int x = (int) fx, y = (int) fy, w = (int) fw, h = (int) fh;
        int classColor = CLASS_COLORS.getOrDefault(player.dungeonClass, 0xFFFFFFFF);
        int bg = player.isDead ? 0xBF4A1414 : 0xBF3B3B3B;

        // chamfered corners, closest plain fills get to Odin's rounded rect
        int r = Math.max(2, (int) (5 * s));
        context.fill(x + r, y, x + w - r, y + h, bg);
        context.fill(x, y + r, x + r, y + h - r, bg);
        context.fill(x + w - r, y + r, x + w, y + h - r, bg);

        int headSize = (int) (fh * 0.75f);
        int headX = x + (int) (fh * 0.125f);
        int headY = y + (h - headSize) / 2;
        if (TeslaMapsConfig.get().leapShowHeads && player.uuid != null) {
            PlayerHeadRenderer.drawPlayerHead(context, headX, headY, headSize, player.uuid);
        } else {
            context.fill(headX, headY, headX + headSize, headY + headSize, classColor);
            String letter = CLASS_LETTERS.getOrDefault(player.dungeonClass, "?");
            float ls = headSize / 14f;
            drawScaledText(context, textRenderer, letter,
                    headX + (headSize - textRenderer.width(letter) * ls) / 2f,
                    headY + (headSize - 9f * ls) / 2f, ls, 0xFFFFFFFF);
        }

        float textX = headX + headSize + 8f * s;
        float nameScale = fh / 52f;
        float maxNameWidth = fx + fw - textX - 6f * s;
        if (textRenderer.width(player.name) * nameScale > maxNameWidth) {
            nameScale = maxNameWidth / textRenderer.width(player.name);
        }
        drawScaledText(context, textRenderer, player.name, textX, y + fh * 0.2f, nameScale, classColor);

        String sub = player.isDead ? "DEAD" : (player.dungeonClass != null ? player.dungeonClass : "Unknown");
        int subColor = player.isDead ? 0xFFFF5555 : 0xFFFFFFFF;
        drawScaledText(context, textRenderer, sub, textX, y + fh * 0.2f + 12f * nameScale, fh / 68f, subColor);
    }

    private static void drawScaledText(GuiGraphicsExtractor context, Font font, String text,
                                        float x, float y, float scale, int color) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(font, text, 0, 0, color, true);
        pose.popMatrix();
    }

    private static final Set<Integer> heldLeapKeys = new HashSet<>();

    private static void pollKeybinds(ContainerScreen screen) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().handle();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if ("Class".equals(c.leapKeybindMode)) {
            pollKey(handle, c.leapKeyArcher, () -> leapToClass(screen, "Archer"));
            pollKey(handle, c.leapKeyBerserk, () -> leapToClass(screen, "Berserk"));
            pollKey(handle, c.leapKeyHealer, () -> leapToClass(screen, "Healer"));
            pollKey(handle, c.leapKeyMage, () -> leapToClass(screen, "Mage"));
            pollKey(handle, c.leapKeyTank, () -> leapToClass(screen, "Tank"));
        } else {
            pollKey(handle, c.leapKeyTL, () -> leapToQuadrant(screen, 0));
            pollKey(handle, c.leapKeyTR, () -> leapToQuadrant(screen, 1));
            pollKey(handle, c.leapKeyBL, () -> leapToQuadrant(screen, 2));
            pollKey(handle, c.leapKeyBR, () -> leapToQuadrant(screen, 3));
        }
        pollKey(handle, c.leapKeyLastDoor, () -> leapToLastDoorOpener(screen));
        pollKey(handle, c.fastLeapKey, () -> fastLeap(screen));
    }

    private static boolean inBossArea() {
        if (mc.player == null || !DungeonManager.isInDungeon()) return false;
        if (DungeonManager.isInBoss()) return true;
        return com.teslamaps.scanner.ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null;
    }

    // Fast Leap: in the clear -> last door opener. In the boss -> arena zone, then boss phase, else door opener.
    private static void fastLeap(ContainerScreen screen) {
        if (!inBossArea()) {
            if (lastDoorOpener != null) { announceLeap("Door Opener (" + lastDoorOpener + ")"); leapToLastDoorOpener(screen); }
            return;
        }

        String zone = zoneClass();
        if (zone != null) { announceLeap(zone); leapToClass(screen, zone); return; }

        switch (bossPhase) {
            case ARCHER -> { announceLeap("Archer"); leapToClass(screen, "Archer"); }
            case BERSERK -> {
                if (System.currentTimeMillis() < berserkUntilMs) { announceLeap("Berserk"); leapToClass(screen, "Berserk"); }
                // after the 10s window: do nothing until Storm's "Oof"
            }
            case HEALER -> { announceLeap("Healer"); leapToClass(screen, "Healer"); }
            default -> {
                if (lastDoorOpener != null) { announceLeap("Door Opener (" + lastDoorOpener + ")"); leapToLastDoorOpener(screen); }
            }
        }
    }

    private static void announceLeap(String target) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[TeslaMaps] §fLeaping to " + target));
        }
    }

    // Returns the class to leap to based on the player's position in the F7/M7 boss arena, or null.
    private static String zoneClass() {
        if (mc.player == null) return null;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (within(x, y, z, 54, 63, 43, 20) || within(x, y, z, 54, 64, 79, 20)) return "Healer";
        if (y >= 100 && y <= 145) {
            if (inBox(x, z, 92, 109, 54, 122)) return "Archer";
            if (inBox(x, z, 36, 77, 127, 141)) return "Healer";
            if (inBox(x, z, -3, 16, 51, 116)) return "Mage";
        }
        return null;
    }

    private static boolean inBox(double x, double z, double xMin, double xMax, double zMin, double zMax) {
        return x >= xMin && x <= xMax && z >= zMin && z <= zMax;
    }

    private static boolean within(double x, double y, double z, double cx, double cy, double cz, double r) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private static void leapToLastDoorOpener(ContainerScreen screen) {
        if (lastDoorOpener == null) return;
        for (LeapPlayer p : leapPlayers) {
            if (p != null && !p.isDead && lastDoorOpener.equals(p.name)) { leapToPlayer(screen, p.name); return; }
        }
        TeslaMaps.debug("[LeapOverlay] Last door opener '{}' not a leap target", lastDoorOpener);
    }

    private static void pollKey(long handle, int key, Runnable action) {
        if (key == -1) return;
        boolean down = com.teslamaps.utils.KeyNames.isDown(key);
        boolean was = heldLeapKeys.contains(key);
        if (down && !was) action.run();
        if (down) heldLeapKeys.add(key); else heldLeapKeys.remove(key);
    }

    private static void leapToQuadrant(ContainerScreen screen, int q) {
        LeapPlayer p = leapPlayers[q];
        if (p == null || p.isDead) return;
        leapToPlayer(screen, p.name);
    }

    private static void leapToClass(ContainerScreen screen, String clazz) {
        for (LeapPlayer p : leapPlayers) {
            if (p != null && !p.isDead && clazz.equals(p.dungeonClass)) { leapToPlayer(screen, p.name); return; }
        }
    }

    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (!shouldRender()) return false;
        if (button != 0) return false; // Only left click

        ContainerScreen screen = (ContainerScreen) mc.screen;
        if (screen == null) return false;

        if (TeslaMapsConfig.get().leapShowMap && leapMapShown) {
            int mx = leapMapX, my = leapMapY, mw = leapMapW, mh = leapMapH;
            boolean inMap = mw > 0 && mouseX >= mx && mouseX <= mx + mw && mouseY >= my && mouseY <= my + mh;
            List<String> targets = leapTargetNames(screen);

            if (TeslaMapsConfig.get().debugMode) {
                StringBuilder mk = new StringBuilder();
                for (MapRenderer.PlayerMarker m : leapMarkers)
                    mk.append(m.self() ? "*" : "").append(m.name()).append("(").append(m.x()).append(",").append(m.y()).append(") ");
                TeslaMaps.debug("[LeapDbg] click {},{} | map {},{},{},{} inMap={} | markers: {}| targets: {}",
                        (int) mouseX, (int) mouseY, mx, my, mw, mh, inMap, mk, targets);
            }

            if (inMap) {
                String nearest = null;
                double best = Double.MAX_VALUE;
                for (MapRenderer.PlayerMarker m : leapMarkers) {
                    if (m.self() || m.name() == null || !targets.contains(m.name())) continue;
                    double d = (m.x() - mouseX) * (m.x() - mouseX) + (m.y() - mouseY) * (m.y() - mouseY);
                    if (d < best) { best = d; nearest = m.name(); }
                }
                if (nearest == null && targets.size() == 1) nearest = targets.get(0);
                if (nearest != null) leapToPlayer(screen, nearest);
                return true; // consume the click whether or not a player was found
            }
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int halfWidth = screenWidth / 2;
        int halfHeight = screenHeight / 2;

        int quadrant;
        if (mouseX < halfWidth && mouseY < halfHeight) {
            quadrant = 0; // Top-left
        } else if (mouseX >= halfWidth && mouseY < halfHeight) {
            quadrant = 1; // Top-right
        } else if (mouseX < halfWidth && mouseY >= halfHeight) {
            quadrant = 2; // Bottom-left
        } else {
            quadrant = 3; // Bottom-right
        }

        LeapPlayer player = leapPlayers[quadrant];
        if (player == null) {
            return true; // Still consume click even if no player in quadrant
        }

        if (player.isDead) {
            TeslaMaps.debug("[LeapOverlay] Cannot leap to dead player: {}", player.name);
            return true;
        }

        leapToPlayer(screen, player.name);
        return true;
    }

    // head names can carry a prefix ("✦ Name") — take the last word, like Odin
    private static String headName(ItemStack stack) {
        String n = ChatFormatting.stripFormatting(stack.getHoverName().getString());
        if (n == null) return null;
        n = n.trim();
        int sp = n.lastIndexOf(' ');
        return sp >= 0 ? n.substring(sp + 1) : n;
    }

    private static List<String> leapTargetNames(ContainerScreen screen) {
        List<String> names = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) continue;
            String n = headName(stack);
            if (n != null && !n.isEmpty() && !names.contains(n)) names.add(n);
        }
        return names;
    }

    private static void leapToPlayer(ContainerScreen screen, String playerName) {
        var handler = screen.getMenu();

        for (Slot slot : handler.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) continue;

            String itemName = headName(stack);
            if (itemName != null && itemName.equalsIgnoreCase(playerName)) {
                if (mc.gameMode != null && mc.player != null) {
                    mc.gameMode.handleContainerInput(handler.containerId, slot.index, 0,
                        net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
                    TeslaMaps.debug("[LeapOverlay] Leaping to {}", playerName);
                }
                return;
            }
        }
        TeslaMaps.LOGGER.warn("[LeapOverlay] Could not find slot for player: {}", playerName);
    }

    // Build from the tracker (tab-list order, dead players included) so quadrants stay stable when
    // someone dies — their chest head disappears, but the box just flips to DEAD instead of reshuffling.
    private static void scanLeapPlayers(ContainerScreen screen) {
        Arrays.fill(leapPlayers, null);

        String self = mc.player != null ? mc.player.getName().getString() : null;
        List<LeapPlayer> allPlayers = new ArrayList<>();
        for (DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName() == null || dp.getName().equals(self)) continue;
            String clazz = "EMPTY".equals(dp.getDungeonClass()) ? null : dp.getDungeonClass();
            allPlayers.add(new LeapPlayer(dp.getName(), clazz, !dp.isAlive(), -1, dp.getUuid()));
        }

        // tracker empty (e.g. Teleport to Player outside a tracked run) -> fall back to the chest heads
        if (allPlayers.isEmpty()) {
            for (Slot slot : screen.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                ItemStack stack = slot.getItem();
                if (stack.isEmpty() || stack.getItem() != Items.PLAYER_HEAD) continue;
                String itemName = headName(stack);
                if (itemName == null || itemName.isEmpty()) continue;
                allPlayers.add(new LeapPlayer(itemName, null, false, slot.index, null));
            }
        }

        sortPlayers(allPlayers);
    }

    private static int classOrder(String c) {
        if (c == null) return 99;
        return switch (c) {
            case "Archer" -> 0; case "Berserk" -> 1; case "Healer" -> 2; case "Mage" -> 3; case "Tank" -> 4;
            default -> 98;
        };
    }

    private static void sortPlayers(List<LeapPlayer> players) {
        String mode = TeslaMapsConfig.get().leapSortMode;
        if ("Odin".equals(mode)) { odinSort(players); return; }

        switch (mode) {
            case "Class A-Z" -> players.sort(Comparator.comparingInt((LeapPlayer p) -> classOrder(p.dungeonClass)).thenComparing(p -> p.name.toLowerCase()));
            case "Name A-Z" -> players.sort(Comparator.comparing(p -> p.name.toLowerCase()));
            case "Custom" -> {
                List<String> order = TeslaMapsConfig.get().leapCustomOrder;
                players.sort(Comparator.comparingInt(p -> {
                    int i = order.indexOf(p.name.toLowerCase());
                    return i == -1 ? Integer.MAX_VALUE : i;
                }));
            }
            default -> { } // "None": keep menu order
        }
        for (int i = 0; i < players.size() && i < 4; i++) leapPlayers[i] = players.get(i);
    }

    private static void odinSort(List<LeapPlayer> players) {
        players.sort((a, b) -> {
            int prioA = CLASS_PRIORITY.getOrDefault(a.dungeonClass, 99);
            int prioB = CLASS_PRIORITY.getOrDefault(b.dungeonClass, 99);
            return Integer.compare(prioA, prioB);
        });

        List<LeapPlayer> secondRound = new ArrayList<>();

        for (LeapPlayer player : players) {
            int defaultQuadrant = CLASS_DEFAULT_QUADRANT.getOrDefault(player.dungeonClass, 0);
            if (leapPlayers[defaultQuadrant] == null) {
                leapPlayers[defaultQuadrant] = player;
            } else {
                secondRound.add(player);
            }
        }

        for (LeapPlayer player : secondRound) {
            for (int i = 0; i < 4; i++) {
                if (leapPlayers[i] == null) {
                    leapPlayers[i] = player;
                    break;
                }
            }
        }
    }

    public static void reset() {
        Arrays.fill(leapPlayers, null);
        menuOpen = false;
        leapMapShown = false;
        bossPhase = BossPhase.NONE;
        berserkUntilMs = 0L;
        crystalCount = 0;
        berserkScheduledMs = 0L;
        lastDoorOpener = null;
    }

    private static class LeapPlayer {
        final String name;
        final String dungeonClass;
        final boolean isDead;
        final int slotId;
        final UUID uuid;

        LeapPlayer(String name, String dungeonClass, boolean isDead, int slotId, UUID uuid) {
            this.name = name;
            this.dungeonClass = dungeonClass;
            this.isDead = isDead;
            this.slotId = slotId;
            this.uuid = uuid;
        }
    }
}
