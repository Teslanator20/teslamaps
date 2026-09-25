package com.teslamaps.features;

// Ported from Skysoft's ExperimentationTableHelper.kt / ExperimentationTableHelperState.kt / SuperpairsMemory.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExperimentHelper {

    private enum Game { CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }
    private enum Phase { MEMORY, INPUT, INACTIVE }

    private static final Pattern TITLE = Pattern.compile("^(Chronomatron|Ultrasequencer|Superpairs) \\([^)]+\\)$");
    private static final Pattern ROUND = Pattern.compile("^Round: (\\d+)$");
    private static final Set<String> COLORS = Set.of("red", "orange", "yellow", "lime", "green", "cyan",
            "light_blue", "blue", "purple", "pink");
    private static final int ROUND_SLOT = 4;
    private static final int PHASE_SLOT = 49;
    private static final int NEXT_FILL = 0x9030FF30, NEXT_OUTLINE = 0xFF30FF30;
    private static final int UP_FILL = 0x80FFD740, UP_OUTLINE = 0xFFFFD740;
    private static final int REST_FILL = 0xB0101010;

    private static Feature feature;
    private static AbstractContainerScreen<?> screen;
    private static int menuSize;
    private static Game game;
    private static Phase phase = Phase.INACTIVE;
    private static int round;

    private static final List<String> chronoSeq = new ArrayList<>();
    private static final Map<Integer, String> chronoActive = new HashMap<>();
    private static int chronoPlayback;
    private static boolean chronoValid;
    private static boolean chronoReady;

    private static final TreeMap<Integer, Integer> ultraMemory = new TreeMap<>();
    private static List<Integer> ultraSeq = List.of();
    private static int inputIndex;

    private static final Set<Integer> pairBoard = new HashSet<>();
    private static final Map<Integer, ItemStack> pairCards = new HashMap<>();

    private ExperimentHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ExperimentHelper").section("Inventory").when(() -> cfg().experimentHelper)
                .tickAlways(ExperimentHelper::tick)
                .config("Inventory", b -> {
                    b.toggle("Experimentation Table Helper", () -> cfg().experimentHelper, v -> cfg().experimentHelper = v)
                            .desc("Highlights the next clicks in the add-on experiments. Visual only, never clicks.");
                    b.toggle("  Chronomatron", () -> cfg().experimentChronomatron, v -> cfg().experimentChronomatron = v);
                    b.toggle("  Ultrasequencer", () -> cfg().experimentUltrasequencer, v -> cfg().experimentUltrasequencer = v);
                    b.toggle("  Superpairs (keep revealed cards)", () -> cfg().experimentSuperpairs, v -> cfg().experimentSuperpairs = v);
                });
    }

    private static boolean enabled(Game g) {
        TeslaMapsConfig c = cfg();
        return switch (g) {
            case CHRONOMATRON -> c.experimentChronomatron;
            case ULTRASEQUENCER -> c.experimentUltrasequencer;
            case SUPERPAIRS -> c.experimentSuperpairs;
        };
    }

    // ---------------------------------------------------------------- tick

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> s) || !SkyblockUtils.isInSkyblock()) {
            detach();
            return;
        }
        if (s != screen) {
            detach();
            Game g = gameFromTitle(StringUtil.stripTrim(s.getTitle().getString()));
            if (g == null || !enabled(g)) return;
            screen = s;
            game = g;
            menuSize = s.getMenu().slots.size() - 36;
        }
        if (game == null) return;
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() <= PHASE_SLOT) return;
        updateRound(slots.get(ROUND_SLOT).getItem());
        Phase p = phaseOf(slots.get(PHASE_SLOT).getItem());
        if (p != null) updatePhase(p);
        switch (game) {
            case ULTRASEQUENCER -> {
                if (phase != Phase.MEMORY) break;
                for (int i = 0; i < menuSize; i++) {
                    int n = ultraNumber(slots.get(i).getItem());
                    if (n > 0) ultraMemory.put(n, i);
                }
            }
            case SUPERPAIRS -> {
                for (int i = 0; i < menuSize; i++) {
                    ItemStack st = slots.get(i).getItem();
                    if (hiddenCard(st)) pairBoard.add(i);
                    else if (!st.isEmpty() && pairBoard.contains(i)) pairCards.put(i, st.copy());
                }
            }
            default -> {}
        }
    }

    private static void detach() {
        screen = null;
        game = null;
        phase = Phase.INACTIVE;
        round = 0;
        chronoSeq.clear();
        chronoActive.clear();
        chronoPlayback = 0;
        chronoValid = false;
        chronoReady = false;
        ultraMemory.clear();
        ultraSeq = List.of();
        inputIndex = 0;
        pairBoard.clear();
        pairCards.clear();
    }

    /** Menu slot update; chronomatron notes are too short to catch by polling. */
    public static void onMenuSlotChanged(AbstractContainerMenu menu, int slotId, ItemStack stack) {
        if (game != Game.CHRONOMATRON || screen == null || screen.getMenu() != menu) return;
        if (slotId < 0 || slotId >= menuSize) return;
        if (slotId == ROUND_SLOT) {
            updateRound(stack);
            return;
        }
        if (slotId == PHASE_SLOT) {
            Phase p = phaseOf(stack);
            if (p != null) updatePhase(p);
            return;
        }
        String color = tileColor(stack);
        boolean active = color != null && itemPath(stack).endsWith("_terracotta");
        if (!active) {
            chronoActive.remove(slotId);
            return;
        }
        if (phase != Phase.MEMORY) return;
        if (color.equals(chronoActive.get(slotId))) return;
        chronoActive.remove(slotId);
        boolean newNote = !chronoActive.containsValue(color);
        chronoActive.put(slotId, color);
        if (!newNote || !chronoValid) return;
        String expected = chronoPlayback < chronoSeq.size() ? chronoSeq.get(chronoPlayback) : null;
        if (color.equals(expected)) chronoPlayback++;
        else if (expected != null) chronoValid = false;
        else if (round > 0 && chronoSeq.size() < round) {
            chronoSeq.add(color);
            chronoPlayback++;
        }
    }

    public static void onSlotClick(AbstractContainerScreen<?> s, Slot slot, ContainerInput input) {
        if (s != screen || slot == null || game == null || phase != Phase.INPUT) return;
        if (input != ContainerInput.PICKUP && input != ContainerInput.THROW) return;
        int idx = slot.index;
        if (idx < 0 || idx >= menuSize) return;
        if (game == Game.CHRONOMATRON) {
            String color = tileColor(slot.getItem());
            if (inputIndex < chronoSeq.size() && chronoSeq.get(inputIndex).equals(color)) inputIndex++;
        } else if (game == Game.ULTRASEQUENCER) {
            if (inputIndex < ultraSeq.size() && ultraSeq.get(inputIndex) == idx) inputIndex++;
        }
    }

    private static void updateRound(ItemStack st) {
        Matcher m = ROUND.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
        if (m.matches()) round = Integer.parseInt(m.group(1));
    }

    private static void updatePhase(Phase p) {
        if (phase == p) return;
        phase = p;
        switch (p) {
            case MEMORY -> {
                chronoActive.clear();
                chronoPlayback = 0;
                chronoValid = true;
                chronoReady = false;
                ultraMemory.clear();
                ultraSeq = List.of();
                inputIndex = 0;
            }
            case INPUT -> {
                chronoReady = chronoValid && chronoSeq.size() == round && chronoPlayback == round;
                List<Integer> keys = new ArrayList<>(ultraMemory.keySet());
                boolean contiguous = true;
                for (int i = 0; i < keys.size(); i++) if (keys.get(i) != i + 1) contiguous = false;
                ultraSeq = contiguous ? new ArrayList<>(ultraMemory.values()) : List.of();
                inputIndex = 0;
            }
            case INACTIVE -> {
                chronoReady = false;
                ultraSeq = List.of();
                inputIndex = 0;
            }
        }
    }

    // ---------------------------------------------------------------- render

    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (game == null || screen == null || Minecraft.getInstance().screen != screen) return;
        if (feature == null || !feature.active()) return;
        int idx = slot.index;
        if (idx < 0 || idx >= menuSize || slot.container == Minecraft.getInstance().player.getInventory()) return;
        int x = slot.x, y = slot.y;
        switch (game) {
            case SUPERPAIRS -> {
                ItemStack card = pairCards.get(idx);
                if (card == null || !hiddenCard(slot.getItem())) return;
                ctx.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
                ctx.item(card, x, y);
                ctx.fill(x, y, x + 16, y + 16, 0x30000000);
            }
            case CHRONOMATRON -> {
                if (phase != Phase.INPUT || !chronoReady || inputIndex >= chronoSeq.size()) return;
                String color = tileColor(slot.getItem());
                if (color == null) return;
                if (color.equals(chronoSeq.get(inputIndex))) box(ctx, x, y, NEXT_FILL, NEXT_OUTLINE);
                else if (inputIndex + 1 < chronoSeq.size() && color.equals(chronoSeq.get(inputIndex + 1)))
                    box(ctx, x, y, UP_FILL, UP_OUTLINE);
            }
            case ULTRASEQUENCER -> {
                if (phase != Phase.INPUT) return;
                int pos = ultraSeq.indexOf(idx);
                if (pos < inputIndex) return;
                if (pos == inputIndex) box(ctx, x, y, NEXT_FILL, NEXT_OUTLINE);
                else if (pos == inputIndex + 1) box(ctx, x, y, UP_FILL, UP_OUTLINE);
                else ctx.fill(x, y, x + 16, y + 16, REST_FILL);
                Font font = Minecraft.getInstance().font;
                String label = Integer.toString(pos + 1);
                ctx.text(font, label, x + (16 - font.width(label)) / 2, y + 4, 0xFFFFFFFF);
            }
        }
    }

    private static void box(GuiGraphicsExtractor ctx, int x, int y, int fill, int outline) {
        ctx.fill(x, y, x + 16, y + 16, fill);
        ctx.outline(x - 1, y - 1, 18, 18, outline);
    }

    // ---------------------------------------------------------------- item helpers

    private static Game gameFromTitle(String title) {
        Matcher m = TITLE.matcher(title);
        if (!m.matches()) return null;
        return switch (m.group(1)) {
            case "Chronomatron" -> Game.CHRONOMATRON;
            case "Ultrasequencer" -> Game.ULTRASEQUENCER;
            default -> Game.SUPERPAIRS;
        };
    }

    private static Phase phaseOf(ItemStack st) {
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        if (name.equals("Remember the pattern!")) return Phase.MEMORY;
        if (name.startsWith("Timer: ")) return Phase.INPUT;
        return null;
    }

    private static String itemPath(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    private static String tileColor(ItemStack st) {
        if (st.isEmpty()) return null;
        String path = itemPath(st);
        String color;
        if (path.endsWith("_terracotta")) color = path.substring(0, path.length() - "_terracotta".length());
        else if (path.endsWith("_stained_glass")) color = path.substring(0, path.length() - "_stained_glass".length());
        else return null;
        return COLORS.contains(color) ? color : null;
    }

    private static int ultraNumber(ItemStack st) {
        if (st.isEmpty()) return 0;
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        if (name.isEmpty() || name.length() > 3) return 0;
        for (int i = 0; i < name.length(); i++) if (!Character.isDigit(name.charAt(i))) return 0;
        return Integer.parseInt(name);
    }

    private static boolean hiddenCard(ItemStack st) {
        return !st.isEmpty() && itemPath(st).equals("cyan_stained_glass");
    }
}
