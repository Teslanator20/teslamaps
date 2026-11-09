package com.teslamaps.features.foraging;

// After SkyHanni's ForagingBeaconSolver.kt / ForagingBeaconWarning.kt and Skyblocker's TunerSolver.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Moonglade Beacon: tuning solver ("Tune Frequency" / "Upgrade Signal Strength") and a ready alert. */
public final class ForagingBeacon {

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "cyan", "purple", "blue", "brown", "green", "red"};
    private static final int[] SPEED_TICKS = {52, 42, 32, 22, 12};
    private static final String[] PITCHES = {"Low", "Normal", "High"};
    private static final float[] PITCH_VALUES = {0.0952381f, 0.7936508f, 1.4920635f};
    private static final int GREEN = 0xC855FF55;
    private static final List<String> SAMPLE = List.of("§d§lForaging Beacon Solver", " §7Ref Color: §aLime",
            " §7Ref Speed: §aSpeed 3", " §7Ref Pitch: §aNormal");

    /** One set of controls; the upgrade menu has a normal and an enchanted (glinting) one. */
    private static final class Tune {
        final boolean enchanted;
        int colorSlot, speedSlot, pitchSlot;
        int refColor = -1, ourColor = -1, refSpeed = -1, ourSpeed = -1, refPitch = -1, ourPitch = -1;
        int refSlot = -1, ourSlot = -1;
        long refMoved = Long.MIN_VALUE / 2, ourMoved = Long.MIN_VALUE / 2, lastRefTick = -1;
        final List<Integer> intervals = new ArrayList<>();
        final List<Integer> pitchBuf = new ArrayList<>();

        Tune(boolean enchanted) { this.enchanted = enchanted; }

        void layout(boolean upgrade) {
            int off = upgrade && !enchanted ? -9 : 0;
            colorSlot = 46 + off;
            speedSlot = 48 + off;
            pitchSlot = 50 + off;
        }

        Integer offset(int slot) {
            if (slot == colorSlot) return diff(refColor, ourColor, COLORS.length);
            if (slot == speedSlot) return diff(refSpeed, ourSpeed, SPEED_TICKS.length);
            if (slot == pitchSlot) return diff(refPitch, ourPitch, PITCHES.length);
            return null;
        }
    }

    private static Feature feature;
    private static AbstractContainerMenu menu;
    private static boolean upgrade;
    private static Tune normal = new Tune(false), enchanted = new Tune(true);
    private static final List<float[]> sounds = new ArrayList<>();
    private static List<String> lines = List.of();
    private static int width = 80;
    private static long lastAlert;
    private static int ticks;

    private ForagingBeacon() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("beaconSolver", "Foraging Beacon Solver").ctx(HudEditElement.HudCtx.FORAGING).section("Foraging")
                .enabled(() -> cfg().beaconSolver)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        feature = Feature.of("ForagingBeaconSolver").section("Foraging").when(() -> cfg().beaconSolver)
                .tick(ForagingBeacon::tick)
                .hudForeground("beaconsolver", ForagingBeacon::renderHud);
        Feature.of("ForagingBeaconAlert").section("Foraging").when(() -> cfg().beaconReadyAlert)
                .tick(ForagingBeacon::alertTick)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Moonglade Beacon");
                    b.toggle("Foraging Beacon Solver", () -> c.beaconSolver, v -> c.beaconSolver = v)
                            .desc("Tune Frequency / Upgrade Signal Strength: the right buttons turn green, the number is "
                                    + "the clicks still needed. Speed is measured from the reference pane, pitch from its sound. Panel in /tmap gui.")
                            .tags("moonglade", "beacon", "tune", "frequency", "signal", "galatea");
                    b.toggle("  Prevent Over-Clicking", () -> c.beaconSolverOverClick, v -> c.beaconSolverOverClick = v)
                            .desc("Blocks clicks on a button that is already right. Hold Ctrl to click anyway.");
                    b.toggle("Beacon Ready Alert", () -> c.beaconReadyAlert, v -> c.beaconReadyAlert = v)
                            .desc("Title and sound when the Moonglade Beacon tab widget says Cooldown: AVAILABLE, at most every 9 min.");
                });
    }

    // --- solver ---

    private static boolean open() {
        Minecraft mc = Minecraft.getInstance();
        return menu != null && mc.player != null && mc.player.containerMenu == menu && feature != null && feature.active();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !Foraging.onForagingIsland()) {
            close();
            return;
        }
        if (cs.getMenu() != menu) {
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            boolean up = title.equals("Upgrade Signal Strength");
            if (!up && !title.equals("Tune Frequency")) {
                close();
                return;
            }
            menu = cs.getMenu();
            upgrade = up;
            normal = new Tune(false);
            enchanted = new Tune(true);
            normal.layout(up);
            enchanted.layout(up);
            sounds.clear();
            ItemStack legs = mc.player.getItemBySlot(EquipmentSlot.LEGS);
            if ("MUSIC_PANTS".equals(ItemUtil.skyblockId(legs))) {
                mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §cThe beacon solver may not work with Stereo Pants on!"));
            }
        }
        long now = ServerTickTimer.now();
        read(now);
        resolveSounds(now);
        buildLines();
    }

    private static void close() {
        menu = null;
        lines = List.of();
        sounds.clear();
    }

    private static void read(long now) {
        List<Slot> slots = menu.slots;
        if (slots.size() < 54) return;
        for (Tune t : upgrade ? new Tune[]{normal, enchanted} : new Tune[]{normal}) {
            int ref = -1, refC = -1, our = -1, ourC = -1;
            for (int i = 10; i <= 16; i++) {
                ItemStack s = slots.get(i).getItem();
                int c = paneColor(s);
                if (c >= 0 && s.hasFoil() == t.enchanted) { ref = i; refC = c; }
            }
            for (int i = 28; i <= 34; i++) {
                ItemStack s = slots.get(i).getItem();
                int c = paneColor(s);
                if (c >= 0 && s.hasFoil() == t.enchanted) { our = i; ourC = c; }
            }
            if (refC >= 0) t.refColor = refC;
            if (ref >= 0 && ref != t.refSlot) {
                if (t.refSlot >= 0 && t.lastRefTick >= 0) {
                    int d = (int) (now - t.lastRefTick);
                    if (d > 0) {
                        t.intervals.add(d);
                        if (t.intervals.size() > 10) t.intervals.remove(0);
                        if (!upgrade || t.intervals.size() >= 3) t.refSpeed = speed(t.intervals);
                    }
                }
                t.lastRefTick = now;
                t.refSlot = ref;
                t.refMoved = now;
            }
            if (our >= 0 && our != t.ourSlot) {
                t.ourSlot = our;
                t.ourMoved = now;
            }
            int c = lore(slots.get(t.colorSlot).getItem(), "Current color: ");
            t.ourColor = c >= 0 ? c : ourC;
            t.ourSpeed = lore(slots.get(t.speedSlot).getItem(), "Current speed: ");
            t.ourPitch = lore(slots.get(t.pitchSlot).getItem(), "Current pitch: ");
        }
    }

    private static int paneColor(ItemStack s) {
        if (s.isEmpty()) return -1;
        String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (!path.endsWith("_stained_glass_pane")) return -1;
        return index(COLORS, path.substring(0, path.length() - "_stained_glass_pane".length()));
    }

    private static int lore(ItemStack s, String prefix) {
        if (s.isEmpty()) return -1;
        for (String line : ItemUtil.loreLines(s)) {
            String t = line.trim();
            if (!t.startsWith(prefix)) continue;
            String v = t.substring(prefix.length()).trim();
            if (prefix.startsWith("Current color")) return index(COLORS, v.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
            if (prefix.startsWith("Current pitch")) {
                for (int i = 0; i < PITCHES.length; i++) if (PITCHES[i].equalsIgnoreCase(v)) return i;
                return -1;
            }
            try {
                int n = Integer.parseInt(v);
                return n >= 1 && n <= 5 ? n - 1 : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static int index(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return -1;
    }

    private static int speed(List<Integer> recent) {
        List<Integer> sorted = new ArrayList<>(recent);
        sorted.sort(null);
        int n = sorted.size();
        double median = n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
        double sum = 0;
        int count = 0;
        for (int v : recent) {
            if (v >= median * 0.8 && v <= median * 1.2) {
                sum += v;
                count++;
            }
        }
        if (count == 0) return -1;
        double avg = sum / count;
        int best = 0;
        for (int i = 1; i < SPEED_TICKS.length; i++) {
            if (Math.abs(SPEED_TICKS[i] - avg) < Math.abs(SPEED_TICKS[best] - avg)) best = i;
        }
        return best;
    }

    private static Integer diff(int ref, int ours, int size) {
        if (ref < 0 || ours < 0) return null;
        int off = ((ref - ours) % size + size) % size;
        return off > size / 2 ? off - size : off;
    }

    public static boolean listening() {
        return menu != null;
    }

    /** From SoundPlayMixin. */
    public static void onSound(String id, float pitch) {
        if (menu == null || !id.equals("minecraft:block.note_block.bass")) return;
        for (int i = 0; i < PITCH_VALUES.length; i++) {
            if (Math.abs(PITCH_VALUES[i] - pitch) < 0.001f) {
                sounds.add(new float[]{i, ServerTickTimer.now()});
                if (sounds.size() > 20) sounds.remove(0);
                return;
            }
        }
    }

    // a note belongs to the reference when its pane moved within a tick and ours did not
    private static void resolveSounds(long now) {
        for (int k = sounds.size() - 1; k >= 0; k--) {
            float[] s = sounds.get(k);
            long at = (long) s[1];
            if (now - at < 1) continue;
            sounds.remove(k);
            Tune hit = null;
            int hits = 0;
            for (Tune t : upgrade ? new Tune[]{normal, enchanted} : new Tune[]{normal}) {
                boolean ref = Math.abs(t.refMoved - at) <= 1, ours = Math.abs(t.ourMoved - at) <= 1;
                if (ours) hits += 2;
                else if (ref) {
                    hit = t;
                    hits++;
                }
            }
            if (hits != 1 || hit == null) continue;
            hit.pitchBuf.add((int) s[0]);
            if (hit.pitchBuf.size() > 6) hit.pitchBuf.remove(0);
            if (hit.pitchBuf.stream().distinct().count() > 2) hit.pitchBuf.clear();
            if (hit.pitchBuf.size() < 3) continue;
            int[] counts = new int[PITCHES.length];
            for (int p : hit.pitchBuf) counts[p]++;
            int best = 0;
            for (int i = 1; i < counts.length; i++) if (counts[i] > counts[best]) best = i;
            hit.refPitch = best;
        }
    }

    private static void buildLines() {
        List<String> out = new ArrayList<>();
        add(out, normal, "§d§lForaging Beacon Solver");
        if (upgrade) add(out, enchanted, "§aEnchanted Tuning");
        lines = out;
        width = width(out);
    }

    private static void add(List<String> out, Tune t, String title) {
        out.add(title);
        out.add(" §7Ref Color: " + (t.refColor < 0 ? "§eUnknown" : "§a" + pretty(COLORS[t.refColor])));
        out.add(" §7Ref Speed: " + (t.refSpeed < 0 ? "§eCalculating.." : "§aSpeed " + (t.refSpeed + 1)));
        out.add(" §7Ref Pitch: " + (t.refPitch < 0 ? "§eListening.." : "§a" + PITCHES[t.refPitch]));
    }

    private static String pretty(String c) {
        StringBuilder sb = new StringBuilder();
        for (String p : c.split("_")) sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }

    private static Integer offsetAt(Slot slot) {
        if (slot.container instanceof Inventory) return null;
        int id = slot.index;
        Integer o = normal.offset(id);
        if (o == null && upgrade) o = enchanted.offset(id);
        return o;
    }

    /** From SlotBackgrounds. */
    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (!open()) return;
        Integer o = offsetAt(slot);
        if (o != null && o == 0) ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREEN);
    }

    /** From LoreSlotTextMixin. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (!open()) return;
        Integer o = offsetAt(slot);
        if (o == null || o == 0) return;
        var font = Minecraft.getInstance().font;
        String t = "§a" + o;
        ctx.text(font, t, slot.x + 17 - font.width(t), slot.y + 9, 0xFFFFFFFF, true);
    }

    /** From HandledScreenClickMixin; true blocks the click. */
    public static boolean blockClick(Slot slot) {
        if (slot == null || !cfg().beaconSolverOverClick || !open()) return false;
        if (Minecraft.getInstance().hasControlDown()) return false;
        Integer o = offsetAt(slot);
        return o != null && o == 0;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || !open()) return;
        TeslaMapsConfig c = cfg();
        draw(g, Minecraft.getInstance(), c.beaconSolverX, c.beaconSolverY, c.beaconSolverScale, lines, width);
    }

    private static int width(List<String> ls) {
        int w = 0;
        for (String l : ls) w = Math.max(w, Minecraft.getInstance().font.width(l));
        return w;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int pad = HudStyle.padding();
        HudStyle.panel(g, -pad, -pad, w + pad * 2, ls.size() * 10 + pad * 2);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 0, i * 10 + 1, 0xFFFFFFFF);
        pose.popMatrix();
    }

    // --- ready alert ---

    private static void alertTick() {
        if (++ticks % 20 != 0 || !Foraging.onForagingIsland()) return;
        long now = System.currentTimeMillis();
        if (now - lastAlert < 9 * 60_000L) return;
        List<String> tab = TabListUtils.getOrderedLines();
        for (int i = 0; i < tab.size(); i++) {
            if (!StringUtil.strip(tab.get(i)).contains("Moonglade Beacon")) continue;
            for (int j = i + 1; j < tab.size() && j <= i + 6; j++) {
                String l = StringUtil.strip(tab.get(j));
                if (l.isBlank()) break;
                if (!l.trim().equals("Cooldown: AVAILABLE")) continue;
                lastAlert = now;
                Minecraft mc = Minecraft.getInstance();
                mc.gui.hud.setTimes(5, 40, 10);
                mc.gui.hud.setTitle(Component.literal("§aBeacon Ready"));
                if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
                return;
            }
            return;
        }
    }
}
